package app.accord.remote

import app.accord.core.sync.EntityChangeEnvelope
import app.accord.core.sync.InviteCode
import app.accord.core.sync.OperationEnvelope
import app.accord.core.sync.OperationResult
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeTarget(val fake: FakeSpaceService) : ContractTarget {
    override val service: RemoteSpaceService get() = fake
    override fun backdateInvites(spaceId: String, millis: Long) = fake.backdateInvites(spaceId, millis)
}

/** A suíte de contrato contra o fake; o Supabase local roda a mesma (S2.3). */
class FakeSpaceServiceContractTest : SpaceServiceContract() {
    override fun newTarget(): ContractTarget = FakeTarget(FakeSpaceService())
}

/** O que só o fake permite provar: relógio, falhas injetadas e respostas perdidas. */
class FakeSpaceServiceTest {
    private var now = 1_000_000L
    private val fake = FakeSpaceService(clock = { now }, accessTtlMillis = 1_000)

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    private fun <T> ok(result: RemoteResult<T>): T {
        assertIs<RemoteResult.Ok<T>>(result)
        return result.value
    }

    private fun failure(result: RemoteResult<*>): RemoteFailure {
        assertIs<RemoteResult.Failed>(result)
        return result.failure
    }

    private fun op(spaceId: String, id: String = "op-1") = OperationEnvelope(
        id, spaceId, installationId = "i", localTimeMillis = 1,
        changes = listOf(EntityChangeEnvelope("Movement", "mov-1", null, """{"valor":6800}""")),
    )

    @Test
    fun `convite vale até 3 dias a partir da geração, com limite exclusivo`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        ok(fake.createSpace(creator.accessToken, "esp-1", "Casa"))
        val invite = ok(fake.generateInvite(creator.accessToken, "esp-1"))
        assertEquals(now + InviteCode.VALIDITY_MILLIS, invite.validUntilMillis)

        val partner = ok(fake.signUp("b@x.test", "senha-123"))
        now = invite.validUntilMillis
        val atLimit = ok(fake.signIn("b@x.test", "senha-123"))
        assertEquals(RemoteFailure.InvalidInvite, failure(fake.joinWithCode(atLimit.accessToken, invite.code)))
        now = invite.validUntilMillis - 1
        val justBefore = ok(fake.signIn("b@x.test", "senha-123"))
        assertEquals("esp-1", ok(fake.joinWithCode(justBefore.accessToken, invite.code)).spaceId)
        assertEquals(partner.accountId, justBefore.accountId)
    }

    @Test
    fun `resposta perdida no consumo do convite, seguida de repetição, conclui a mesma associação`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        ok(fake.createSpace(creator.accessToken, "esp-1", "Casa"))
        val code = ok(fake.generateInvite(creator.accessToken, "esp-1")).code
        val partner = ok(fake.signUp("b@x.test", "senha-123"))
        fake.loseNextResponse()
        assertEquals(RemoteFailure.NoNetwork, failure(fake.joinWithCode(partner.accessToken, code)))
        assertEquals("esp-1", ok(fake.joinWithCode(partner.accessToken, code)).spaceId)
        assertEquals(2, fake.memberCount("esp-1"))
    }

    @Test
    fun `resposta perdida no cadastro e na criação do espaço não duplica nada`() = run {
        fake.loseNextResponse()
        assertEquals(RemoteFailure.NoNetwork, failure(fake.signUp("a@x.test", "senha-123")))
        assertEquals(RemoteFailure.EmailAlreadyRegistered, failure(fake.signUp("a@x.test", "senha-123")))
        val session = ok(fake.signIn("a@x.test", "senha-123"))
        fake.loseNextResponse()
        failure(fake.createSpace(session.accessToken, "esp-1", "Casa"))
        ok(fake.createSpace(session.accessToken, "esp-1", "Casa"))
        assertEquals(1, fake.spaceCount())
    }

    @Test
    fun `resposta perdida no envio, seguida de reenvio, aplica uma vez só`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        ok(fake.createSpace(creator.accessToken, "esp-1", "Casa"))
        fake.loseNextResponse()
        assertEquals(RemoteFailure.NoNetwork, failure(fake.push(creator.accessToken, "esp-1", listOf(op("esp-1")))))
        val retry = ok(fake.push(creator.accessToken, "esp-1", listOf(op("esp-1")))).single()
        assertIs<OperationResult.Applied>(retry)
        assertTrue(retry.repeated)
        assertEquals(1L, retry.versions.single().version)
        assertEquals(1, fake.entityCount("esp-1"))
    }

    @Test
    fun `sem rede, serviço pausado, indisponível ou sem quota nada é aplicado e a causa vem identificada`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        ok(fake.createSpace(creator.accessToken, "esp-1", "Casa"))
        val cases = mapOf(
            RemoteFailure.NoNetwork to { fake.online = false },
            RemoteFailure.ServiceUnavailable to { fake.online = true; fake.serviceState = FakeServiceState.Unavailable },
            RemoteFailure.ServicePaused to { fake.serviceState = FakeServiceState.Paused },
            RemoteFailure.QuotaExceeded to { fake.serviceState = FakeServiceState.QuotaExceeded },
        )
        for ((expected, setup) in cases) {
            setup()
            assertEquals(expected, failure(fake.push(creator.accessToken, "esp-1", listOf(op("esp-1")))))
        }
        assertEquals(0, fake.entityCount("esp-1"))
        fake.serviceState = FakeServiceState.Healthy
        assertIs<OperationResult.Applied>(ok(fake.push(creator.accessToken, "esp-1", listOf(op("esp-1")))).single())
    }

    @Test
    fun `token vencido pede renovação e renovar com a mesma conta retoma o envio`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        ok(fake.createSpace(creator.accessToken, "esp-1", "Casa"))
        now += 2_000
        assertEquals(RemoteFailure.SessionExpired, failure(fake.push(creator.accessToken, "esp-1", listOf(op("esp-1")))))
        val renewed = ok(fake.refresh(creator.refreshToken))
        assertEquals(creator.accountId, renewed.accountId)
        assertIs<OperationResult.Applied>(ok(fake.push(renewed.accessToken, "esp-1", listOf(op("esp-1")))).single())
    }

    @Test
    fun `redefinição de senha pelo responsável revoga as sessões e a nova senha entra`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        fake.adminResetPassword("a@x.test", "senha-nova-9")
        assertEquals(RemoteFailure.SessionExpired, failure(fake.refresh(creator.refreshToken)))
        assertEquals(RemoteFailure.InvalidCredentials, failure(fake.signIn("a@x.test", "senha-123")))
        assertEquals(creator.accountId, ok(fake.signIn("a@x.test", "senha-nova-9")).accountId)
    }

    @Test
    fun `nenhuma operação do serviço envia e-mails`() = run {
        val creator = ok(fake.signUp("a@x.test", "senha-123"))
        ok(fake.createSpace(creator.accessToken, "esp-1", "Casa"))
        ok(fake.generateInvite(creator.accessToken, "esp-1"))
        fake.failNext(RemoteFailure.InvalidCredentials)
        failure(fake.signIn("a@x.test", "senha-123"))
        assertTrue(fake.sentEmails.isEmpty())
    }
}
