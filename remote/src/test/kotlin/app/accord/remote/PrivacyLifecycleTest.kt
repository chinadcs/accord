package app.accord.remote

import app.accord.core.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

class PrivacyLifecycleTest {
    private fun <T> ok(result: RemoteResult<T>): T = assertIs<RemoteResult.Ok<T>>(result).value
    private fun status(result: RemoteResult<String>) = Json.parseToJsonElement(ok(result)).jsonObject["status"]!!.jsonPrimitive.content
    private suspend fun member(service: FakeSpaceService, n: Int) = ok(service.signUp("privacy-$n@teste.exemplo", "senha-ficticia-$n"))
    private suspend fun join(service: FakeSpaceService, owner: AuthSession, person: AuthSession, space: String) {
        val invite = ok(service.generateInvite(owner.accessToken, space))
        ok(service.joinWithCode(person.accessToken, invite.code))
    }

    @Test fun `senha incorreta preserva acesso e comprovante alheio nao exclui`() = runBlocking<Unit> {
        val service = FakeSpaceService(); val user = member(service, 1); val id = UUID.randomUUID().toString()
        ok(service.createSpace(user.accessToken, id, "Casa"))
        val request = UUID.randomUUID().toString(); val proof = "a".repeat(64)
        assertEquals(RemoteFailure.InvalidCredentials, assertIs<RemoteResult.Failed>(service.privacy(user.accessToken,
            PrivacyCommand(PrivacyAction.PrepareAccountDeletion, request, proof, "errada"))).failure)
        assertNotNull(ok(service.currentSpace(user.accessToken)))
        assertEquals("Prepared", status(service.privacy(user.accessToken, PrivacyCommand(PrivacyAction.PrepareAccountDeletion, request, proof, "senha-ficticia-1"))))
        assertIs<RemoteResult.Failed>(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, request, "b".repeat(64))))
        assertEquals(1, service.accountCount())
    }

    @Test fun `resposta perdida retoma exclusao sem token e preserva outro membro`() = runBlocking<Unit> {
        val service = FakeSpaceService(); val a = member(service, 1); val b = member(service, 2)
        val space = UUID.randomUUID().toString(); ok(service.createSpace(a.accessToken, space, "Casa")); join(service, a, b, space)
        val id = UUID.randomUUID().toString(); val proof = "c".repeat(64)
        service.loseNextResponse()
        assertIs<RemoteResult.Failed>(service.privacy(a.accessToken, PrivacyCommand(PrivacyAction.PrepareAccountDeletion, id, proof, "senha-ficticia-1")))
        assertEquals("Completed", status(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, id, proof))))
        assertEquals("Completed", status(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, id, proof))))
        assertEquals(1, service.accountCount()); assertEquals(1, service.memberCount(space))
        assertNotNull(ok(service.currentSpace(b.accessToken)))
        assertIs<RemoteResult.Failed>(service.refresh(a.refreshToken))
    }

    @Test fun `excluir ultimo membro remove espaco e nao ressuscita no reenvio`() = runBlocking<Unit> {
        val service = FakeSpaceService(); val user = member(service, 1); val space = UUID.randomUUID().toString()
        ok(service.createSpace(user.accessToken, space, "Casa"))
        val id = UUID.randomUUID().toString(); val proof = "d".repeat(64)
        ok(service.privacy(user.accessToken, PrivacyCommand(PrivacyAction.PrepareAccountDeletion, id, proof, "senha-ficticia-1")))
        service.loseNextResponse()
        assertIs<RemoteResult.Failed>(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, id, proof)))
        assertEquals("Completed", status(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, id, proof))))
        assertEquals(0, service.spaceCount()); assertEquals(0, service.accountCount())
    }

    @Test fun `mudanca de membros invalida e cancelamento preserva dados`() = runBlocking<Unit> {
        val service = FakeSpaceService(); val a = member(service, 1); val b = member(service, 2); val c = member(service, 3)
        val space = UUID.randomUUID().toString(); ok(service.createSpace(a.accessToken, space, "Casa")); join(service, a, b, space)
        val cancelled = UUID.randomUUID().toString()
        assertEquals("AwaitingApprovals", status(service.privacy(a.accessToken, PrivacyCommand(PrivacyAction.RequestSpaceDeletion, cancelled, password = "senha-ficticia-1", spaceId = space))))
        assertEquals("Cancelled", status(service.privacy(b.accessToken, PrivacyCommand(PrivacyAction.CancelSpaceDeletion, cancelled))))
        val invalid = UUID.randomUUID().toString()
        ok(service.privacy(a.accessToken, PrivacyCommand(PrivacyAction.RequestSpaceDeletion, invalid, password = "senha-ficticia-1", spaceId = space)))
        join(service, a, c, space)
        assertEquals("Invalidated", status(service.privacy(b.accessToken, PrivacyCommand(PrivacyAction.ApproveSpaceDeletion, invalid, password = "senha-ficticia-2"))))
        assertEquals(1, service.spaceCount())
        val request = UUID.randomUUID().toString()
        ok(service.privacy(a.accessToken, PrivacyCommand(PrivacyAction.RequestSpaceDeletion, request, password = "senha-ficticia-1", spaceId = space)))
        assertEquals("AwaitingApprovals", status(service.privacy(b.accessToken, PrivacyCommand(PrivacyAction.ApproveSpaceDeletion, request, password = "senha-ficticia-2"))))
        assertEquals("Completed", status(service.privacy(c.accessToken, PrivacyCommand(PrivacyAction.ApproveSpaceDeletion, request, password = "senha-ficticia-3"))))
        assertEquals(3, service.accountCount()); assertNull(ok(service.currentSpace(a.accessToken)))
        assertTrue("Casa" in ok(service.privacy(b.accessToken, PrivacyCommand(PrivacyAction.Status))))
    }
}
