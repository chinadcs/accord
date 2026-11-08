package app.accord.remote

import app.accord.core.sync.AppliedVersion
import app.accord.core.sync.AuthSession
import app.accord.core.sync.EntityChangeEnvelope
import app.accord.core.sync.EntityKey
import app.accord.core.sync.InviteCode
import app.accord.core.sync.OperationEnvelope
import app.accord.core.sync.OperationResult
import app.accord.core.sync.PROTOCOL_VERSION
import app.accord.core.sync.RejectReason
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.SpaceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** O que a suíte precisa de um alvo além da porta: voltar o tempo do serviço sem mexer nas sessões. */
interface ContractTarget {
    val service: RemoteSpaceService

    /** Recua a geração dos convites do espaço, como se o tempo tivesse passado no serviço. */
    fun backdateInvites(spaceId: String, millis: Long)
}

/**
 * Suíte de contrato do Serviço remoto do espaço (S2 "Decisões de teste"). Os mesmos cenários rodam
 * contra o fake e, quando disponível, contra o Supabase local, para provar que o fake reproduz o
 * serviço. Verifica comportamento observável pela porta, nunca detalhes internos.
 */
abstract class SpaceServiceContract {
    abstract fun newTarget(): ContractTarget

    protected lateinit var target: ContractTarget
    private val service get() = target.service

    private fun email() = "contrato-${UUID.randomUUID()}@exemplo.test"
    private val password = "senha-de-teste-1"

    protected fun <T> test(block: suspend () -> T) {
        target = newTarget()
        runBlocking(Dispatchers.Default) { block() }
    }

    private suspend fun signUp(): AuthSession = ok(service.signUp(email(), password))

    private fun <T> ok(result: RemoteResult<T>): T {
        assertIs<RemoteResult.Ok<T>>(result, "esperava sucesso, veio $result")
        return result.value
    }

    private fun failure(result: RemoteResult<*>): RemoteFailure {
        assertIs<RemoteResult.Failed>(result, "esperava falha, veio $result")
        return result.failure
    }

    /** Um espaço com [creator] e, se pedido, [partner] já associado por convite. */
    private inner class Couple(val creator: AuthSession, val spaceId: String, var partner: AuthSession? = null)

    private suspend fun newSpace(): Couple {
        val creator = signUp()
        val id = UUID.randomUUID().toString()
        ok(service.createSpace(creator.accessToken, id, "Casa"))
        return Couple(creator, id)
    }

    private suspend fun newCouple(): Couple {
        val couple = newSpace()
        val code = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId)).code
        val partner = signUp()
        ok(service.joinWithCode(partner.accessToken, code))
        couple.partner = partner
        return couple
    }

    private var counter = 0

    private fun op(
        spaceId: String,
        vararg changes: EntityChangeEnvelope,
        id: String = "op-${UUID.randomUUID()}",
        protocol: Int = PROTOCOL_VERSION,
    ) = OperationEnvelope(id, spaceId, protocol, "instalacao-1", 1_000L + counter++, changes.toList())

    private fun change(
        id: String = UUID.randomUUID().toString(),
        base: Long? = null,
        state: String = """{"valor":6800}""",
        type: String = "Movement",
        tombstone: Boolean = false,
        references: List<EntityKey> = emptyList(),
        createIfAbsent: Boolean = false,
    ) = EntityChangeEnvelope(type, id, base, state, tombstone, references, createIfAbsent)

    private suspend fun push(session: AuthSession, spaceId: String, vararg ops: OperationEnvelope): List<OperationResult> =
        ok(service.push(session.accessToken, spaceId, ops.toList()))

    // ---- conta ----

    @Test
    fun `cadastro devolve a sessão na hora, sem etapa de confirmação`() = test {
        val session = signUp()
        assertTrue(session.accessToken.isNotBlank() && session.refreshToken.isNotBlank())
        // A sessão vale: o serviço só diz que ela não participa do espaço citado.
        assertEquals(RemoteFailure.NotMember, failure(service.participants(session.accessToken, UUID.randomUUID().toString())))
    }

    @Test
    fun `cadastro repetido do mesmo e-mail é recusado e a entrada com a senha funciona`() = test {
        val mail = email()
        val first = ok(service.signUp(mail, password))
        assertEquals(RemoteFailure.EmailAlreadyRegistered, failure(service.signUp(mail, password)))
        val again = ok(service.signIn(mail, password))
        assertEquals(first.accountId, again.accountId)
    }

    @Test
    fun `entrada com credencial recusada não diz qual dos dois está errado`() = test {
        val mail = email()
        ok(service.signUp(mail, password))
        val wrongPassword = failure(service.signIn(mail, "outra-senha-2"))
        val unknownEmail = failure(service.signIn(email(), password))
        assertEquals(RemoteFailure.InvalidCredentials, wrongPassword)
        assertEquals(wrongPassword, unknownEmail)
    }

    @Test
    fun `senha curta é recusada`() = test {
        assertEquals(RemoteFailure.WeakPassword, failure(service.signUp(email(), "123")))
    }

    @Test
    fun `token inválido é sessão expirada`() = test {
        assertEquals(RemoteFailure.SessionExpired, failure(service.participants("lixo", "x")))
        assertEquals(RemoteFailure.SessionExpired, failure(service.refresh("lixo")))
    }

    @Test
    fun `renovar entrega nova sessão da mesma conta`() = test {
        val session = signUp()
        val renewed = ok(service.refresh(session.refreshToken))
        assertEquals(session.accountId, renewed.accountId)
        assertNotEquals(session.accessToken, renewed.accessToken)
    }

    @Test
    fun `depois de sair a sessão não renova mais`() = test {
        val session = signUp()
        ok(service.signOut(session.accessToken))
        assertEquals(RemoteFailure.SessionExpired, failure(service.refresh(session.refreshToken)))
    }

    // ---- espaço ----

    @Test
    fun `criar espaço associa o criador e repetir devolve o mesmo espaço`() = test {
        val creator = signUp()
        val id = UUID.randomUUID().toString()
        val first = ok(service.createSpace(creator.accessToken, id, "Casa"))
        val second = ok(service.createSpace(creator.accessToken, id, "Casa"))
        assertEquals(SpaceRef(id, "Casa"), first)
        assertEquals(first, second)
        assertEquals(1, ok(service.participants(creator.accessToken, id)).items.size)
    }

    @Test
    fun `a conta descobre o próprio espaço ao entrar num aparelho novo`() = test {
        val creator = signUp()
        assertEquals(null, ok(service.currentSpace(creator.accessToken)))
        val id = UUID.randomUUID().toString()
        ok(service.createSpace(creator.accessToken, id, "Casa"))
        val again = ok(service.signIn(creator.email, password))
        assertEquals(SpaceRef(id, "Casa"), ok(service.currentSpace(again.accessToken)))
    }

    @Test
    fun `quem já participa de um espaço recebe esse espaço em vez de criar outro`() = test {
        val creator = signUp()
        val first = UUID.randomUUID().toString()
        ok(service.createSpace(creator.accessToken, first, "Casa"))
        val other = ok(service.createSpace(creator.accessToken, UUID.randomUUID().toString(), "Outra"))
        assertEquals(first, other.spaceId)
        assertTrue(other.adoptedExisting)
    }

    @Test
    fun `ID de espaço de outra pessoa não pode ser tomado`() = test {
        val owner = newSpace()
        val stranger = signUp()
        assertEquals(RemoteFailure.NotMember, failure(service.createSpace(stranger.accessToken, owner.spaceId, "Minha")))
    }

    @Test
    fun `quem não participa não vê participantes do espaço`() = test {
        val owner = newSpace()
        val stranger = signUp()
        assertEquals(RemoteFailure.NotMember, failure(service.participants(stranger.accessToken, owner.spaceId)))
    }

    // ---- convite ----

    @Test
    fun `código tem 8 caracteres do alfabeto e vale 3 dias`() = test {
        val couple = newSpace()
        val invite = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId))
        assertEquals(8, invite.code.length)
        assertTrue(invite.code.all { it in InviteCode.ALPHABET })
        assertEquals(InviteCode.VALIDITY_MILLIS, invite.validUntilMillis - invite.generatedAtMillis)
        assertEquals(InviteCode.normalize(invite.code), invite.code)
    }

    @Test
    fun `quem não participa não gera código`() = test {
        val owner = newSpace()
        val stranger = signUp()
        assertEquals(RemoteFailure.NotMember, failure(service.generateInvite(stranger.accessToken, owner.spaceId)))
    }

    @Test
    fun `código associa a segunda pessoa e repetir devolve sucesso sem nova associação`() = test {
        val couple = newSpace()
        val code = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId)).code
        val partner = signUp()
        val joined = ok(service.joinWithCode(partner.accessToken, code))
        val again = ok(service.joinWithCode(partner.accessToken, code))
        assertEquals(couple.spaceId, joined.spaceId)
        assertEquals(joined, again)
        assertEquals(2, ok(service.participants(couple.creator.accessToken, couple.spaceId)).items.size)
    }

    @Test
    fun `o mesmo código serve a várias pessoas ate lotar em quatro`() = test {
        val space = newSpace()
        val code = ok(service.generateInvite(space.creator.accessToken, space.spaceId)).code
        repeat(3) { ok(service.joinWithCode(signUp().accessToken, code)) }
        assertEquals(4, ok(service.participants(space.creator.accessToken, space.spaceId)).items.size)
        val fifth = signUp()
        assertEquals(RemoteFailure.SpaceFull, failure(service.joinWithCode(fifth.accessToken, code)))
        assertEquals(4, ok(service.participants(space.creator.accessToken, space.spaceId)).items.size)
    }

    @Test
    fun `com quatro pessoas no espaço não se gera código, com três ainda se gera`() = test {
        val space = newSpace()
        val code = ok(service.generateInvite(space.creator.accessToken, space.spaceId)).code
        repeat(2) { ok(service.joinWithCode(signUp().accessToken, code)) }
        assertTrue(ok(service.generateInvite(space.creator.accessToken, space.spaceId)).code.isNotBlank())
        val again = ok(service.generateInvite(space.creator.accessToken, space.spaceId)).code
        ok(service.joinWithCode(signUp().accessToken, again))
        assertEquals(RemoteFailure.SpaceFull, failure(service.generateInvite(space.creator.accessToken, space.spaceId)))
    }

    @Test
    fun `invalidar o código impede novas entradas e só membro invalida`() = test {
        val space = newSpace()
        val code = ok(service.generateInvite(space.creator.accessToken, space.spaceId)).code
        assertEquals(RemoteFailure.NotMember, failure(service.revokeInvite(signUp().accessToken, space.spaceId)))
        ok(service.revokeInvite(space.creator.accessToken, space.spaceId))
        assertEquals(null, ok(service.activeInvite(space.creator.accessToken, space.spaceId)))
        assertEquals(RemoteFailure.InvalidInvite, failure(service.joinWithCode(signUp().accessToken, code)))
    }

    @Test
    fun `dentro do prazo o código é aceito e depois do prazo é recusado`() = test {
        val inTime = newSpace()
        val validCode = ok(service.generateInvite(inTime.creator.accessToken, inTime.spaceId)).code
        target.backdateInvites(inTime.spaceId, InviteCode.VALIDITY_MILLIS - 60_000)
        ok(service.joinWithCode(signUp().accessToken, validCode))

        val late = newSpace()
        val lateCode = ok(service.generateInvite(late.creator.accessToken, late.spaceId)).code
        target.backdateInvites(late.spaceId, InviteCode.VALIDITY_MILLIS + 60_000)
        assertEquals(RemoteFailure.InvalidInvite, failure(service.joinWithCode(signUp().accessToken, lateCode)))
    }

    @Test
    fun `convite expirado aparece como expirado e sem o código`() = test {
        val couple = newSpace()
        assertEquals(null, ok(service.activeInvite(couple.creator.accessToken, couple.spaceId)))
        val invite = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId))
        val status = ok(service.activeInvite(couple.creator.accessToken, couple.spaceId))!!
        assertEquals(invite.validUntilMillis, status.validUntilMillis)
        assertFalse(status.expired)
        target.backdateInvites(couple.spaceId, InviteCode.VALIDITY_MILLIS + 60_000)
        assertTrue(ok(service.activeInvite(couple.creator.accessToken, couple.spaceId))!!.expired)
    }

    @Test
    fun `gerar outro código invalida o anterior`() = test {
        val couple = newSpace()
        val first = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId)).code
        val second = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId)).code
        assertNotEquals(first, second)
        assertEquals(RemoteFailure.InvalidInvite, failure(service.joinWithCode(signUp().accessToken, first)))
        ok(service.joinWithCode(signUp().accessToken, second))
    }

    @Test
    fun `código em minúsculas e com espaços é aceito`() = test {
        val couple = newSpace()
        val code = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId)).code
        val typed = " " + code.take(4).lowercase() + " " + code.drop(4).lowercase() + " "
        assertEquals(couple.spaceId, ok(service.joinWithCode(signUp().accessToken, typed)).spaceId)
    }

    @Test
    fun `código inválido não cria espaço`() = test {
        val person = signUp()
        assertEquals(RemoteFailure.InvalidInvite, failure(service.joinWithCode(person.accessToken, "ZZZZZZZZ")))
        val created = ok(service.createSpace(person.accessToken, UUID.randomUUID().toString(), "Casa"))
        assertFalse(created.adoptedExisting)
    }

    @Test
    fun `quem já participa de outro espaço é recusado com explicação`() = test {
        val first = newSpace()
        val second = newSpace()
        val code = ok(service.generateInvite(first.creator.accessToken, first.spaceId)).code
        assertEquals(RemoteFailure.AlreadyInAnotherSpace, failure(service.joinWithCode(second.creator.accessToken, code)))
        assertEquals(1, ok(service.participants(first.creator.accessToken, first.spaceId)).items.size)
    }

    @Test
    fun `tentativas falhas repetidas são bloqueadas, mesmo com o código certo`() = test {
        val couple = newSpace()
        val code = ok(service.generateInvite(couple.creator.accessToken, couple.spaceId)).code
        val guesser = signUp()
        repeat(InviteCode.MAX_FAILED_ATTEMPTS) {
            assertEquals(RemoteFailure.InvalidInvite, failure(service.joinWithCode(guesser.accessToken, "ZZZZZZZ$it")))
        }
        assertIs<RemoteFailure.RateLimited>(failure(service.joinWithCode(guesser.accessToken, code)))
        assertEquals(1, ok(service.participants(couple.creator.accessToken, couple.spaceId)).items.size)
    }

    @Test
    fun `cinco entradas simultâneas com o mesmo código deixam o espaço com exatamente quatro pessoas`() = test {
        repeat(5) {
            val space = newSpace()
            val code = ok(service.generateInvite(space.creator.accessToken, space.spaceId)).code
            val people = List(5) { signUp() }
            val results = people.map { person ->
                kotlinx.coroutines.CoroutineScope(Dispatchers.Default).async { service.joinWithCode(person.accessToken, code) }
            }.awaitAll()
            assertEquals(3, results.count { it is RemoteResult.Ok }, "resultados: $results")
            assertEquals(2, results.count { it is RemoteResult.Failed && it.failure == RemoteFailure.SpaceFull }, "resultados: $results")
            assertEquals(4, ok(service.participants(space.creator.accessToken, space.spaceId)).items.size)
        }
    }

    // ---- envio e recebimento ----

    @Test
    fun `operação nova é aplicada com versão 1 e aparece no recebimento`() = test {
        val couple = newCouple()
        val entity = change()
        val result = push(couple.creator, couple.spaceId, op(couple.spaceId, entity)).single()
        assertIs<OperationResult.Applied>(result)
        assertEquals(1L, result.versions.single().version)
        assertFalse(result.repeated)
        val page = ok(service.pull(couple.partner!!.accessToken, couple.spaceId, 0, 100))
        val received = page.changes.single()
        assertEquals(entity.id, received.id)
        assertEquals(entity.state, received.state)
        assertEquals(1L, received.version)
        assertEquals(couple.creator.accountId, received.authorId)
        assertEquals(received.cursor, page.nextCursor)
    }

    @Test
    fun `reenvio da mesma operação devolve o mesmo resultado e não aplica de novo`() = test {
        val couple = newCouple()
        val operation = op(couple.spaceId, change())
        val first = push(couple.creator, couple.spaceId, operation).single() as OperationResult.Applied
        val second = push(couple.creator, couple.spaceId, operation).single() as OperationResult.Applied
        assertTrue(second.repeated)
        assertEquals(first.versions, second.versions)
        assertEquals(first.cursor, second.cursor)
        assertEquals(1, ok(service.pull(couple.partner!!.accessToken, couple.spaceId, 0, 100)).changes.size)
    }

    @Test
    fun `mesmo ID com conteúdo diferente é recusado como divergente`() = test {
        val couple = newCouple()
        val entity = change()
        val fixed = "op-fixa-${UUID.randomUUID()}"
        push(couple.creator, couple.spaceId, op(couple.spaceId, entity, id = fixed))
        val altered = push(couple.creator, couple.spaceId, op(couple.spaceId, entity.copy(state = """{"valor":8000}"""), id = fixed)).single()
        assertIs<OperationResult.Rejected>(altered)
        assertEquals(RejectReason.DivergentOperation, altered.reason)
        val stored = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.single()
        assertEquals(entity.state, stored.state)
    }

    @Test
    fun `versão base diferente da atual vira conflito e nada é aplicado`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(id, state = """{"v":"68 delivery"}""")))
        push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(id, base = 1, state = """{"v":"68 restaurantes"}""")))
        val late = push(couple.creator, couple.spaceId, op(couple.spaceId, change(id, base = 1, state = """{"v":"80 delivery"}"""))).single()
        assertIs<OperationResult.Conflict>(late)
        val current = late.current.single()
        assertEquals(2L, current.version)
        assertEquals("""{"v":"68 restaurantes"}""", current.state)
        assertEquals(couple.partner!!.accountId, current.authorId)
        assertTrue(late.unchanged.isEmpty())
        assertEquals("""{"v":"68 restaurantes"}""", ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.single().state)
    }

    @Test
    fun `criação concorrente da mesma identidade colide em vez de duplicar`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(id)))
        val result = push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(id))).single()
        assertIs<OperationResult.Conflict>(result)
        assertEquals(1, ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.size)
    }

    @Test
    fun `operação com várias alterações é aplicada inteira ou nenhuma`() = test {
        val couple = newCouple()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(b)))
        val result = push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(a), change(b))).single()
        assertIs<OperationResult.Conflict>(result)
        assertEquals(listOf(b), result.current.map { it.id })
        assertTrue(result.unchanged.isEmpty(), "a entidade nova ainda não existe no serviço")
        val stored = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes
        assertEquals(listOf(b), stored.map { it.id }, "a primeira alteração não pode ter ficado aplicada")
    }

    @Test
    fun `conflito devolve também as outras entidades da operação que seguem na versão base`() = test {
        val couple = newCouple()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(a), change(b)))
        push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(a, base = 1, state = """{"x":2}""")))
        val result = push(couple.creator, couple.spaceId, op(couple.spaceId, change(a, base = 1), change(b, base = 1))).single()
        assertIs<OperationResult.Conflict>(result)
        assertEquals(listOf(a), result.current.map { it.id })
        assertEquals(listOf(b), result.unchanged.map { it.id })
        assertEquals(1L, result.unchanged.single().version)
    }

    @Test
    fun `edições em entidades diferentes são aplicadas sem conflito`() = test {
        val couple = newCouple()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(a)), op(couple.spaceId, change(b)))
        val resultA = push(couple.creator, couple.spaceId, op(couple.spaceId, change(a, base = 1, state = """{"x":1}"""))).single()
        val resultB = push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(b, base = 1, state = """{"x":2}"""))).single()
        assertIs<OperationResult.Applied>(resultA)
        assertIs<OperationResult.Applied>(resultB)
    }

    @Test
    fun `quem não participa não envia nem recebe`() = test {
        val couple = newCouple()
        val stranger = signUp()
        assertEquals(RemoteFailure.NotMember, failure(service.push(stranger.accessToken, couple.spaceId, listOf(op(couple.spaceId, change())))))
        assertEquals(RemoteFailure.NotMember, failure(service.pull(stranger.accessToken, couple.spaceId, 0, 10)))
    }

    @Test
    fun `entidade de outro espaço não pode ser alterada nem citada`() = test {
        val one = newSpace()
        val two = newSpace()
        val entity = change()
        push(one.creator, one.spaceId, op(one.spaceId, entity))
        val take = push(two.creator, two.spaceId, op(two.spaceId, entity.copy(baseVersion = 1))).single()
        assertIs<OperationResult.Rejected>(take)
        assertEquals(RejectReason.ForeignSpace, take.reason)
        val cite = push(two.creator, two.spaceId, op(two.spaceId, change(references = listOf(EntityKey("Movement", entity.id))))).single()
        assertIs<OperationResult.Rejected>(cite)
        assertEquals(RejectReason.ForeignSpace, cite.reason)
        assertTrue(ok(service.pull(two.creator.accessToken, two.spaceId, 0, 10)).changes.isEmpty())
    }

    @Test
    fun `operação para outro espaço no envelope é recusada`() = test {
        val one = newSpace()
        val two = newSpace()
        val result = push(one.creator, one.spaceId, op(two.spaceId, change())).single()
        assertIs<OperationResult.Rejected>(result)
        assertEquals(RejectReason.ForeignSpace, result.reason)
    }

    @Test
    fun `tipo que não é sincronizável é recusado`() = test {
        val couple = newCouple()
        val result = push(couple.creator, couple.spaceId, op(couple.spaceId, change(type = "ChatMessage"))).single()
        assertIs<OperationResult.Rejected>(result)
        assertEquals(RejectReason.TypeNotAllowed, result.reason)
    }

    // ---- categoria removida (ADR 0039) ----

    private fun category(id: String, base: Long?, removed: Boolean) =
        change(id = id, base = base, type = "Category", state = """{"parentId":null,"removed":$removed}""")

    private fun movement(id: String, base: Long?, category: String?, voided: Boolean = false) = change(
        id = id, base = base, type = "Movement", tombstone = voided,
        state = """{"classificationId":${category?.let { "\"$it\"" } ?: "null"},"voided":$voided}""",
    )

    @Test
    fun `remover categoria com lançamento vivo apontando para ela é recusado e nada é gravado`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        val entry = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, category(id, null, false), movement(entry, null, id)))
        val result = push(couple.creator, couple.spaceId, op(couple.spaceId, category(id, 1, true))).single()
        assertIs<OperationResult.Rejected>(result)
        assertEquals(RejectReason.CategoryInUse, result.reason)
        val state = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.first { it.id == id }
        assertEquals(1, state.version)
    }

    @Test
    fun `mover os lançamentos e remover a categoria na mesma operação é aceito`() = test {
        val couple = newCouple()
        val from = UUID.randomUUID().toString()
        val to = UUID.randomUUID().toString()
        val entry = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, category(from, null, false), category(to, null, false), movement(entry, null, from)))
        val result = push(
            couple.creator, couple.spaceId,
            op(couple.spaceId, category(from, 1, true), movement(entry, 1, to)),
        ).single()
        assertIs<OperationResult.Applied>(result)
    }

    @Test
    fun `lançamento novo numa categoria removida é recusado sem gravar e, sem categoria, é aceito`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, category(id, null, false)))
        push(couple.creator, couple.spaceId, op(couple.spaceId, category(id, 1, true)))
        val late = UUID.randomUUID().toString()
        val refused = push(couple.partner!!, couple.spaceId, op(couple.spaceId, movement(late, null, id))).single()
        assertIs<OperationResult.Rejected>(refused)
        assertEquals(RejectReason.CategoryRemoved, refused.reason)
        assertTrue(ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.none { it.id == late })
        assertIs<OperationResult.Applied>(push(couple.partner!!, couple.spaceId, op(couple.spaceId, movement(late, null, null))).single())
    }

    @Test
    fun `registro excluído pode citar categoria removida`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, category(id, null, false)))
        push(couple.creator, couple.spaceId, op(couple.spaceId, category(id, 1, true)))
        val deleted = push(couple.creator, couple.spaceId, op(couple.spaceId, movement(UUID.randomUUID().toString(), null, id, voided = true))).single()
        assertIs<OperationResult.Applied>(deleted)
    }

    @Test
    fun `protocolo não suportado é recusado e o resto do lote segue`() = test {
        val couple = newCouple()
        val results = push(
            couple.creator, couple.spaceId,
            op(couple.spaceId, change(), protocol = 1),
            op(couple.spaceId, change(), protocol = PROTOCOL_VERSION + 1),
            op(couple.spaceId, change()),
        )
        assertEquals(RejectReason.ProtocolUnsupported, (results[0] as OperationResult.Rejected).reason)
        assertEquals(RejectReason.ProtocolUnsupported, (results[1] as OperationResult.Rejected).reason)
        assertIs<OperationResult.Applied>(results[2])
    }

    @Test
    fun `operação vazia e entidade repetida são carga inválida`() = test {
        val couple = newCouple()
        val repeated = change()
        val results = push(couple.creator, couple.spaceId, op(couple.spaceId), op(couple.spaceId, repeated, repeated))
        results.forEach {
            assertIs<OperationResult.Rejected>(it)
            assertEquals(RejectReason.InvalidEnvelope, it.reason)
        }
    }

    @Test
    fun `recebimento é incremental e paginado sem perder alterações`() = test {
        val couple = newCouple()
        val ids = List(5) { UUID.randomUUID().toString() }
        ids.forEach { push(couple.creator, couple.spaceId, op(couple.spaceId, change(it))) }
        val first = ok(service.pull(couple.partner!!.accessToken, couple.spaceId, 0, 2))
        assertEquals(2, first.changes.size)
        assertTrue(first.hasMore)
        val second = ok(service.pull(couple.partner!!.accessToken, couple.spaceId, first.nextCursor, 100))
        assertFalse(second.hasMore)
        assertEquals(ids.toSet(), (first.changes + second.changes).map { it.id }.toSet())
        assertTrue(second.nextCursor > first.nextCursor)
        val empty = ok(service.pull(couple.partner!!.accessToken, couple.spaceId, second.nextCursor, 100))
        assertTrue(empty.changes.isEmpty())
        assertEquals(second.nextCursor, empty.nextCursor)
    }

    @Test
    fun `alteração aparece com a versão mais recente e o autor vem da sessão`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(id)))
        push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(id, base = 1, state = """{"valor":9000}""")))
        val changes = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes
        val latest = changes.single()
        assertEquals(2L, latest.version)
        assertEquals(couple.partner!!.accountId, latest.authorId)
    }

    @Test
    fun `registro de exclusão chega à outra pessoa com a mesma identidade`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(id)))
        push(couple.creator, couple.spaceId, op(couple.spaceId, change(id, base = 1, state = """{"voided":true}""", tombstone = true)))
        val received = ok(service.pull(couple.partner!!.accessToken, couple.spaceId, 0, 100)).changes.single()
        assertEquals(id, received.id)
        assertTrue(received.tombstone)
    }

    @Test
    fun `os dois têm exatamente os mesmos direitos sobre os dados`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(id)))
        val edit = push(couple.creator, couple.spaceId, op(couple.spaceId, change(id, base = 1, state = """{"valor":1}"""))).single()
        assertIs<OperationResult.Applied>(edit)
        val back = push(couple.partner!!, couple.spaceId, op(couple.spaceId, change(id, base = 2, state = """{"valor":2}"""))).single()
        assertIs<OperationResult.Applied>(back)
    }

    // ---- vínculo de conversão da intenção (ADR 0048) ----

    private fun intention(id: String) = change(id, state = """{"name":"Televisão"}""", type = "Intention")

    private fun commitment(id: String, origin: String?, type: String = "CardPurchase", base: Long? = null, extra: String = ""): EntityChangeEnvelope {
        val originField = if (origin != null) "," + quote("originIntentionId") + ":" + quote(origin) else ""
        return change(id, base, "{" + quote("description") + ":" + quote("Televisão") + extra + originField + "}", type)
    }

    /** O JSON do estado pode voltar reformatado (o banco guarda jsonb): confere o campo pelo conteúdo, não pelo espaçamento. */
    private fun hasOrigin(state: String, id: String) = Regex("\"originIntentionId\"\\s*:\\s*\"" + Regex.escape(id) + "\"").containsMatchIn(state)

    private fun quote(text: String) = "\"" + text + "\""

    @Test
    fun `a primeira conversao da intencao e aplicada e a repeticao devolve o mesmo reconhecimento`() = test {
        val couple = newCouple()
        val i = UUID.randomUUID().toString()
        val c1 = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, intention(i)))
        val converted = op(couple.spaceId, commitment(c1, i), id = "op-converter-$c1")
        val first = push(couple.creator, couple.spaceId, converted).single()
        assertIs<OperationResult.Applied>(first)
        val repeated = push(couple.creator, couple.spaceId, converted).single()
        assertIs<OperationResult.Applied>(repeated)
        assertTrue(repeated.repeated)
        assertEquals(first.versions, repeated.versions)
        assertEquals(1, ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.count { it.type == "CardPurchase" })
    }

    @Test
    fun `o segundo compromisso da mesma intencao volta como conflito com o ja reconhecido e nada e aplicado`() = test {
        val couple = newCouple()
        val i = UUID.randomUUID().toString()
        val c1 = UUID.randomUUID().toString()
        val c2 = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, intention(i)))
        push(couple.creator, couple.spaceId, op(couple.spaceId, commitment(c1, i)))
        // O outro aparelho, sem saber, converte a mesma intenção (inclusive em outro tipo de compromisso).
        for (type in listOf("CardPurchase", "Boleto")) {
            val result = push(couple.partner!!, couple.spaceId, op(couple.spaceId, commitment(c2, i, type))).single()
            val conflict = assertIs<OperationResult.Conflict>(result)
            assertEquals(c1, conflict.current.single().id, "a identidade do já reconhecido")
            assertEquals(1, conflict.current.single().version)
        }
        val stored = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes
        assertTrue(stored.none { it.id == c2 }, "o segundo compromisso não foi gravado")
    }

    @Test
    fun `origem inexistente ou de outro espaco e recusada sem aplicar nada`() = test {
        val mine = newCouple()
        val other = newCouple()
        val foreign = UUID.randomUUID().toString()
        push(other.creator, other.spaceId, op(other.spaceId, intention(foreign)))
        val missing = push(mine.creator, mine.spaceId, op(mine.spaceId, commitment(UUID.randomUUID().toString(), UUID.randomUUID().toString()))).single()
        assertEquals(RejectReason.OriginNotFound, assertIs<OperationResult.Rejected>(missing).reason)
        val crossed = push(mine.creator, mine.spaceId, op(mine.spaceId, commitment(UUID.randomUUID().toString(), foreign))).single()
        assertEquals(RejectReason.OriginNotFound, assertIs<OperationResult.Rejected>(crossed).reason)
        assertTrue(ok(service.pull(mine.creator.accessToken, mine.spaceId, 0, 100)).changes.isEmpty())
    }

    @Test
    fun `atualizar o compromisso sem o campo de origem ou com outra origem nao remove nem troca o vinculo`() = test {
        val couple = newCouple()
        val i = UUID.randomUUID().toString()
        val c1 = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, intention(i)))
        push(couple.creator, couple.spaceId, op(couple.spaceId, commitment(c1, i)))
        // Um cliente sem o campo (por exemplo, sem a fase P2) edita o compromisso.
        val omitted = push(couple.partner!!, couple.spaceId, op(couple.spaceId, commitment(c1, null, base = 1, extra = "," + quote("valor") + ":9000"))).single()
        assertIs<OperationResult.Applied>(omitted)
        val stored = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.single { it.id == c1 }
        assertEquals(2, stored.version)
        assertTrue(hasOrigin(stored.state, i), "o vínculo continua: ${stored.state}")
        assertTrue(stored.state.contains("9000"), "a edição foi aplicada")
        // Trocar a origem também não vale.
        val swapped = push(couple.creator, couple.spaceId, op(couple.spaceId, commitment(c1, UUID.randomUUID().toString(), base = 2))).single()
        assertIs<OperationResult.Applied>(swapped)
        val after = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.single { it.id == c1 }
        assertTrue(hasOrigin(after.state, i))
    }

    @Test
    fun `compromisso sem origem continua aceito como antes e nao ganha vinculo depois`() = test {
        val couple = newCouple()
        val i = UUID.randomUUID().toString()
        val c = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, intention(i)))
        assertIs<OperationResult.Applied>(push(couple.creator, couple.spaceId, op(couple.spaceId, commitment(c, null))).single())
        assertIs<OperationResult.Applied>(push(couple.creator, couple.spaceId, op(couple.spaceId, commitment(c, i, base = 1))).single())
        val stored = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.single { it.id == c }
        assertTrue(!stored.state.contains("originIntentionId"), "o vínculo só nasce com o compromisso")
    }

    // ---- evento de aviso (ADR 0044) ----

    private fun alert(id: String, total: Int, createIfAbsent: Boolean = true, base: Long? = null, tombstone: Boolean = false) =
        change(id, base, """{"categoryId":"c1","windowMonday":"2026-10-19","totalCents":$total}""", "AlertEvent", tombstone, createIfAbsent = createIfAbsent)

    @Test
    fun `o segundo aparelho que cria o mesmo evento recebe o canonico, sem conflito nem nova versao`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        val first = push(couple.creator, couple.spaceId, op(couple.spaceId, alert(id, 130_000))).single()
        assertIs<OperationResult.Applied>(first)
        assertEquals(listOf(AppliedVersion("AlertEvent", id, 1)), first.versions)
        assertTrue(first.absorbed.isEmpty())

        val second = push(couple.partner!!, couple.spaceId, op(couple.spaceId, alert(id, 135_000))).single()
        assertIs<OperationResult.Applied>(second)
        assertTrue(second.versions.isEmpty(), "nada foi escrito")
        val canonical = second.absorbed.single()
        assertEquals(id, canonical.id)
        assertEquals(1, canonical.version)
        assertTrue(canonical.state.contains("130000"), "o canônico é o do primeiro reconhecido")
        assertEquals(1, ok(service.pull(couple.partner!!.accessToken, couple.spaceId, 0, 100)).changes.count { it.type == "AlertEvent" })
    }

    @Test
    fun `reenviar a mesma operacao do evento devolve o mesmo resultado`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, alert(id, 130_000)))
        val repeated = op(couple.spaceId, alert(id, 135_000), id = "op-fixa-$id")
        val a = push(couple.partner!!, couple.spaceId, repeated).single()
        val b = push(couple.partner!!, couple.spaceId, repeated).single()
        assertIs<OperationResult.Applied>(a)
        assertIs<OperationResult.Applied>(b)
        assertTrue(b.repeated)
        assertEquals(a.absorbed, b.absorbed)
    }

    @Test
    fun `evento de aviso nao se altera nem se remove`() = test {
        val couple = newCouple()
        val id = UUID.randomUUID().toString()
        push(couple.creator, couple.spaceId, op(couple.spaceId, alert(id, 130_000)))
        val edit = push(couple.creator, couple.spaceId, op(couple.spaceId, alert(id, 1, createIfAbsent = false, base = 1))).single()
        assertEquals(RejectReason.AlertEventImmutable, assertIs<OperationResult.Rejected>(edit).reason)
        val remove = push(couple.creator, couple.spaceId, op(couple.spaceId, alert(id, 1, createIfAbsent = false, base = 1, tombstone = true))).single()
        assertEquals(RejectReason.AlertEventImmutable, assertIs<OperationResult.Rejected>(remove).reason)
        val recreate = push(couple.partner!!, couple.spaceId, op(couple.spaceId, alert(id, 1, createIfAbsent = false))).single()
        assertEquals(RejectReason.AlertEventImmutable, assertIs<OperationResult.Rejected>(recreate).reason)
        val stored = ok(service.pull(couple.creator.accessToken, couple.spaceId, 0, 100)).changes.single { it.id == id }
        assertEquals(1, stored.version)
        assertTrue(stored.state.contains("130000"))
    }

    @Test
    fun `criar se ausente so vale para o evento de aviso`() = test {
        val couple = newCouple()
        val other = push(couple.creator, couple.spaceId, op(couple.spaceId, change(createIfAbsent = true))).single()
        assertEquals(RejectReason.InvalidEnvelope, assertIs<OperationResult.Rejected>(other).reason)
    }

    @Test
    fun `quem nao participa do espaco nao envia evento de aviso`() = test {
        val couple = newCouple()
        val stranger = signUp()
        val failure = failure(service.push(stranger.accessToken, couple.spaceId, listOf(op(couple.spaceId, alert(UUID.randomUUID().toString(), 1)))))
        assertEquals(RemoteFailure.NotMember, failure)
    }
}
