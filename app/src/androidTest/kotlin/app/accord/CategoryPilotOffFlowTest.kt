package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.accord.core.chat.MessageState
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.UnconfiguredSpaceService
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Real build configuration and default factory, without injecting DisabledChatPilot. */
class CategoryPilotOffFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun defaultBuildInterpretsWithLocalRulesAndNeedsNoModel() = runBlocking<Unit> {
        Assume.assumeFalse("Build without accord.categoryPilot=true", BuildConfig.CATEGORY_PILOT)
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val previous = app.container
        val container = AppContainer(app, InMemoryAccessStore(DevIdentity.snapshot()), UnconfiguredSpaceService)
        container.awaitReady()
        container.chat.filterNotNull().first()
        container.clearChat()
        assertFalse("O sugestor não troca o interpretador do Chat", container.categoryPilot.replacesInterpreter)
        assertTrue("ADR 0036: sem motor injetado o Chat usa as regras locais", container.usesRuleInterpreter)
        var activity: ActivityScenario<MainActivity>? = null
        try {
            app.container = container
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithContentDescription("Chat").performClick()
            compose.onNodeWithText("Mensagem do Chat").performTextInput("ração 80 no cartão")
            compose.onNodeWithText("Enviar").performClick()
            compose.waitUntil(15_000) { runBlocking {
                container.access.runtime.value!!.chatStore!!.snapshot().receipts.size == 1
            } }
            val snapshot = container.access.runtime.value!!.chatStore!!.snapshot()
            assertNotEquals(MessageState.NoModel, snapshot.messages.single().state)
            assertTrue("Sem modelo no P0, nenhum aviso de modelo no Chat", compose.onAllNodesWithText("Modelo", substring = true).fetchSemanticsNodes().isEmpty())
        } finally {
            activity?.close()
            container.clearChat()
            app.container = previous
        }
    }

    @Test fun unclearSentenceKeepsMessageExplainsHowToRewriteAndOffersOnlyManualRegistration() = runBlocking<Unit> {
        Assume.assumeFalse("Build without accord.categoryPilot=true", BuildConfig.CATEGORY_PILOT)
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val previous = app.container
        val container = AppContainer(app, InMemoryAccessStore(DevIdentity.snapshot()), UnconfiguredSpaceService)
        container.awaitReady()
        container.chat.filterNotNull().first()
        container.clearChat()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            app.container = container
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithContentDescription("Chat").performClick()
            compose.onNodeWithText("Mensagem do Chat").performTextInput("bom dia, tudo bem")
            compose.onNodeWithText("Enviar").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Não entendi esta frase", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(container.access.runtime.value!!.chatStore!!.snapshot().receipts.isEmpty())
            compose.onAllNodesWithText("Registrar manualmente").onLast().assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText("Tentar de novo").fetchSemanticsNodes().isEmpty())
        } finally {
            activity?.close()
            container.clearChat()
            app.container = previous
        }
    }
}
