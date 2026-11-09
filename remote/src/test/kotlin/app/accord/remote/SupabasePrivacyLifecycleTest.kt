package app.accord.remote

import app.accord.core.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Conta/SQL/Auth reais somente locais; fixtures novas removidas no finally. */
class SupabasePrivacyLifecycleTest {
    private fun <T> ok(result: RemoteResult<T>): T = assertIs<RemoteResult.Ok<T>>(result).value
    private fun state(result: RemoteResult<String>) = Json.parseToJsonElement(ok(result)).jsonObject["status"]!!.jsonPrimitive.content

    private suspend fun fixture(block: suspend (SupabaseSpaceService, RawApi, AuthSession, AuthSession, String, String) -> Unit) {
        val config = LocalSupabase.requireConfig()
        check(URI(config.apiUrl).host in setOf("127.0.0.1", "localhost", "::1"))
        val service = SupabaseSpaceService(config.apiUrl, config.publishableKey); val api = RawApi(config)
        val users = mutableListOf<AuthSession>(); val space = UUID.randomUUID().toString(); val request = UUID.randomUUID().toString()
        try {
            val a = ok(service.signUp("lifecycle-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-1")).also(users::add)
            val b = ok(service.signUp("lifecycle-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-2")).also(users::add)
            ok(service.createSpace(a.accessToken, space, "Casa teste de exclusão"))
            val invite = ok(service.generateInvite(a.accessToken, space)); ok(service.joinWithCode(b.accessToken, invite.code))
            block(service, api, a, b, space, request)
        } finally {
            val invites = api.admin("GET", "/rest/v1/space_invites?space_id=eq.$space&select=id").body as? JsonArray
            invites.orEmpty().forEach { row -> api.admin("DELETE", "/rest/v1/space_invite_uses?invite_id=eq.${row.jsonObject["id"]!!.jsonPrimitive.content}") }
            api.admin("DELETE", "/rest/v1/space_invites?space_id=eq.$space")
            api.admin("DELETE", "/rest/v1/applied_operations?space_id=eq.$space")
            api.admin("DELETE", "/rest/v1/space_entities?space_id=eq.$space")
            api.admin("DELETE", "/rest/v1/space_members?space_id=eq.$space")
            api.admin("DELETE", "/rest/v1/spaces?id=eq.$space")
            api.admin("DELETE", "/rest/v1/privacy_space_deletion?id=eq.$request")
            api.admin("DELETE", "/rest/v1/privacy_account_deletion?id=eq.$request")
            users.forEach { user ->
                api.admin("DELETE", "/rest/v1/space_invite_uses?account_id=eq.${user.accountId}")
                api.admin("DELETE", "/rest/v1/invite_attempts?account_id=eq.${user.accountId}")
                api.admin("DELETE", "/rest/v1/privacy_space_notice?account_id=eq.${user.accountId}")
                api.admin("DELETE", "/auth/v1/admin/users/${user.accountId}")
            }
        }
    }

    @Test fun `exclusao remove Auth preserva outro membro e retoma sem sessao`() = runBlocking<Unit> {
        fixture { service, api, a, b, space, id ->
            val proof = "a".repeat(64)
            assertEquals(RemoteFailure.InvalidCredentials, assertIs<RemoteResult.Failed>(service.privacy(a.accessToken,
                PrivacyCommand(PrivacyAction.PrepareAccountDeletion, id, proof, "errada"))).failure)
            assertEquals("Prepared", state(service.privacy(a.accessToken, PrivacyCommand(PrivacyAction.PrepareAccountDeletion, id, proof, "senha-ficticia-1"))))
            assertEquals(RemoteFailure.NotMember, assertIs<RemoteResult.Failed>(service.push(a.accessToken, space, emptyList())).failure)
            assertEquals("Completed", state(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, id, proof))))
            assertEquals("Completed", state(service.privacy(null, PrivacyCommand(PrivacyAction.ContinueAccountDeletion, id, proof))))
            assertEquals(space, ok(service.currentSpace(b.accessToken))?.spaceId)
            assertIs<RemoteResult.Failed>(service.signIn(a.email, "senha-ficticia-1"))
            val rows = api.admin("GET", "/rest/v1/space_members?space_id=eq.$space&select=account_id").body as JsonArray
            assertEquals(1, rows.size)
        }
    }

    @Test fun `aprovar usa senha sem revogar sessao corrente e preserva contas`() = runBlocking<Unit> {
        fixture { service, _, a, b, space, id ->
            assertEquals("AwaitingApprovals", state(service.privacy(a.accessToken,
                PrivacyCommand(PrivacyAction.RequestSpaceDeletion, id, password = "senha-ficticia-1", spaceId = space))))
            assertEquals(RemoteFailure.InvalidCredentials, assertIs<RemoteResult.Failed>(service.privacy(b.accessToken,
                PrivacyCommand(PrivacyAction.ApproveSpaceDeletion, id, password = "errada"))).failure)
            assertEquals("Completed", state(service.privacy(b.accessToken, PrivacyCommand(PrivacyAction.ApproveSpaceDeletion, id, password = "senha-ficticia-2"))))
            assertNull(ok(service.currentSpace(a.accessToken))); assertNull(ok(service.currentSpace(b.accessToken)))
            assertTrue(a.email in ok(service.exportData(a.accessToken, DataExportScope.Personal, null)))
            assertTrue("Casa teste de exclusão" in ok(service.privacy(b.accessToken, PrivacyCommand(PrivacyAction.Status))))
        }
    }
}
