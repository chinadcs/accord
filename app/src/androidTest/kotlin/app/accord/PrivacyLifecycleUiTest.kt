@file:Suppress("DEPRECATION")
package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.sync.*
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import app.accord.ui.intentions.IntentionDraft
import app.accord.ui.intentions.SharedPreferencesIntentionDrafts
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class PrivacyLifecycleUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private val service = FakeSpaceService()
    private val ownedAccounts = mutableListOf<String>()
    private var original: AppContainer? = null
    private var container: AppContainer? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun <T> ok(result: RemoteResult<T>): T = (result as RemoteResult.Ok<T>).value
    private suspend fun launch(): Pair<AuthSession, String> {
        val owner = ok(service.signUp("privacy-ui-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-1"))
        ownedAccounts += owner.accountId
        val space = UUID.randomUUID().toString(); ok(service.createSpace(owner.accessToken, space, "Casa de teste de privacidade"))
        val snapshot = AccessSnapshot(session = StoredSession(owner.accountId, owner.email, owner.accessToken, owner.refreshToken, owner.accessExpiresAtMillis),
            bindings = mapOf(owner.accountId to SpaceBinding(owner.accountId, space, "Casa de teste de privacidade", firstStepsDismissed = true)))
        original = app.container
        val created = AppContainer(app, InMemoryAccessStore(snapshot), service)
        created.appLock.setEnabled(false)
        created.awaitReady(); container = created; app.container = created
        scenario = ActivityScenario.launch(MainActivity::class.java)
        return owner to space
    }

    @After fun close() {
        scenario?.close()
        container?.let { runBlocking { it.access.signOut(confirmPending = true) } }
        original?.let { app.container = it }
        ownedAccounts.forEach { account -> app.databaseList().filter { it.startsWith("accord-$account-") && it.endsWith(".db") }.forEach(app::deleteDatabase) }
    }

    @Test fun excluirUltimoMembroConfirmaELimpaSomenteBancoDestaConta() = runBlocking<Unit> {
        val (owner, _) = launch()
        container!!.intentionDrafts()!!.save("new", IntentionDraft("Reserva fictícia", "20", null, "rascunho"))
        val otherDrafts = SharedPreferencesIntentionDrafts(app, "outro-titular-teste", "outro-espaco")
        otherDrafts.save("new", IntentionDraft("Outro rascunho fictício", "30", null, ""))
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Privacidade e dados").performScrollTo().performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Excluir minha conta").assertIsEnabled() }.isSuccess }
        compose.onNodeWithText("Excluir minha conta").performScrollTo().performClick()
        compose.onNodeWithText("Sua senha do Accord").performScrollTo().performTextInput("senha-ficticia-1")
        compose.onNodeWithText("Excluir conta definitivamente").performClick()
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithText("Conta excluída").assertIsDisplayed() }.isSuccess }
        assertEquals(0, service.accountCount()); assertEquals(0, service.spaceCount())
        assertFalse(app.databaseList().any { it.startsWith("accord-${owner.accountId}-") && it.endsWith(".db") })
        assertTrue(SharedPreferencesIntentionDrafts.exportAccount(app, owner.accountId).isEmpty())
        assertTrue(otherDrafts.load("new") != null)
        SharedPreferencesIntentionDrafts.eraseSpace(app, "outro-titular-teste", "outro-espaco")
    }

    @Test fun encerramentoPreservaChatEmLeituraESemComandosFinanceiros() = runBlocking<Unit> {
        val (owner, space) = launch()
        val coordinator = container!!.chat.filterNotNull().first()
        coordinator.send("olá")
        val other = ok(service.signUp("privacy-ui-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-2"))
        val invite = ok(service.generateInvite(owner.accessToken, space)); ok(service.joinWithCode(other.accessToken, invite.code))
        val request = UUID.randomUUID().toString()
        ok(service.privacy(owner.accessToken, PrivacyCommand(PrivacyAction.RequestSpaceDeletion, request, password = "senha-ficticia-1", spaceId = space)))
        ok(service.privacy(other.accessToken, PrivacyCommand(PrivacyAction.ApproveSpaceDeletion, request, password = "senha-ficticia-2")))
        container!!.access.reconcilePrivacy()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Chats de espaços encerrados").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Chats de espaços encerrados").performClick()
        compose.onNodeWithText("Casa de teste de privacidade").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("olá").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Enviar").assertDoesNotExist()
        assertEquals(2, service.accountCount())
        assertTrue(container!!.access.archivedChatData(space)?.contains("olá") == true)
    }
}
