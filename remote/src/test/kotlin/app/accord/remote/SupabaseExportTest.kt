package app.accord.remote

import app.accord.core.sync.AuthSession
import app.accord.core.sync.DataExportScope
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Exercita Auth real, gateway, Edge Function e RPC local; nunca usa ambiente hospedado. */
class SupabaseExportTest {
    private fun <T> ok(result: RemoteResult<T>): T = assertIs<RemoteResult.Ok<T>>(result).value

    @Test fun `endpoint entrega so dados proprios e bloqueia espaco alheio`() = runBlocking<Unit> {
        val config = LocalSupabase.requireConfig()
        check(URI(config.apiUrl).host in setOf("127.0.0.1", "localhost", "::1"))
        val service = SupabaseSpaceService(config.apiUrl, config.publishableKey)
        val api = RawApi(config)
        val users = mutableListOf<AuthSession>()
        val spaceId = UUID.randomUUID().toString()
        try {
            val owner = ok(service.signUp("export-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-1")).also(users::add)
            val other = ok(service.signUp("export-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-2")).also(users::add)
            ok(service.createSpace(owner.accessToken, spaceId, "Casa exportação teste"))
            val personal = ok(service.exportData(owner.accessToken, DataExportScope.Personal, spaceId))
            assertTrue(owner.email in personal)
            assertFalse(other.email in personal || owner.accessToken in personal || owner.refreshToken in personal)
            assertTrue("Casa exportação teste" in ok(service.exportData(owner.accessToken, DataExportScope.Financial, spaceId)))
            assertEquals(RemoteFailure.NotMember,
                assertIs<RemoteResult.Failed>(service.exportData(other.accessToken, DataExportScope.Financial, spaceId)).failure)
            val forged = api.call("POST", "/functions/v1/privacy", owner.accessToken,
                body = kotlinx.serialization.json.buildJsonObject {
                    put("action", kotlinx.serialization.json.JsonPrimitive("export_personal"))
                    put("account_id", kotlinx.serialization.json.JsonPrimitive(other.accountId))
                })
            assertEquals(400, forged.code)
        } finally {
            api.admin("DELETE", "/rest/v1/space_members?space_id=eq.$spaceId")
            api.admin("DELETE", "/rest/v1/spaces?id=eq.$spaceId")
            users.forEach { api.admin("DELETE", "/auth/v1/admin/users/${it.accountId}") }
        }
    }
}
