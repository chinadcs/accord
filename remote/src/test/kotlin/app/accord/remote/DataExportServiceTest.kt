package app.accord.remote

import app.accord.core.sync.AuthSession
import app.accord.core.sync.DataExportScope
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataExportServiceTest {
    private fun <T> ok(result: RemoteResult<T>): T = assertIs<RemoteResult.Ok<T>>(result).value

    @Test fun `pessoal so do titular e financeiro somente de membro`() = runBlocking<Unit> {
        val service = FakeSpaceService()
        val a: AuthSession = ok(service.signUp("export-a@teste.exemplo", "senha-ficticia-1"))
        val b = ok(service.signUp("export-b@teste.exemplo", "senha-ficticia-2"))
        val space = UUID.randomUUID().toString()
        ok(service.createSpace(a.accessToken, space, "Casa teste"))
        val personal = ok(service.exportData(a.accessToken, DataExportScope.Personal, null))
        assertTrue(a.email in personal)
        assertFalse(b.email in personal || a.accessToken in personal || a.refreshToken in personal)
        assertTrue("Casa teste" in ok(service.exportData(a.accessToken, DataExportScope.Financial, space)))
        assertEquals(RemoteFailure.NotMember,
            assertIs<RemoteResult.Failed>(service.exportData(b.accessToken, DataExportScope.Financial, space)).failure)
        assertEquals(RemoteFailure.SessionExpired,
            assertIs<RemoteResult.Failed>(service.exportData("invalido", DataExportScope.Personal, null)).failure)
    }

    @Test fun `falha de rede nao vira exportacao vazia bem sucedida`() = runBlocking<Unit> {
        val service = FakeSpaceService()
        val user = ok(service.signUp("export-rede@teste.exemplo", "senha-ficticia-1"))
        service.online = false
        assertEquals(RemoteFailure.NoNetwork,
            assertIs<RemoteResult.Failed>(service.exportData(user.accessToken, DataExportScope.Personal, null)).failure)
    }
}
