@file:Suppress("DEPRECATION")
package app.accord

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.StoredSession
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Privacidade e dados aberta pelo gate sem espaço (fora do grafo de navegação e sem bloqueio do app): segue sem a seção
 * Segurança, com o conteúdo de dados de sempre (ADR 0077).
 */
@RunWith(AndroidJUnit4::class)
class PrivacyGateUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private var original: AppContainer? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun close() {
        scenario?.close()
        original?.let { app.container = it }
    }

    @Test fun privacidadeDoGateSemEspacoNaoTemASecaoSeguranca() = runBlocking<Unit> {
        val service = FakeSpaceService()
        val owner = (service.signUp("gate-${UUID.randomUUID()}@exemplo.test", "senha-ficticia-1") as RemoteResult.Ok).value
        val snapshot = AccessSnapshot(session = StoredSession(owner.accountId, owner.email, owner.accessToken, owner.refreshToken, owner.accessExpiresAtMillis))
        original = app.container
        val created = AppContainer(app, InMemoryAccessStore(snapshot), service)
        created.appLock.setEnabled(false)
        created.awaitReady(); app.container = created
        scenario = ActivityScenario.launch(MainActivity::class.java)

        compose.onNodeWithText("Privacidade e dados").performClick()
        compose.onNodeWithText("Baixar meus dados pessoais").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver relatório de uso do Chat").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Privacidade e segurança").assertCountEquals(0)
        compose.onAllNodesWithText("Segurança").assertCountEquals(0)
        compose.onAllNodesWithText("Permitir prints de tela").assertCountEquals(0)
        compose.onAllNodesWithText("Bloqueio desativado").assertCountEquals(0)
    }
}
