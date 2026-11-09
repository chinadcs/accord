package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatConversationsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepare() = runBlocking<Unit> {
        container.chat.filterNotNull().first().clearHistory()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
    }
    @After fun close() { scenario?.close(); runBlocking { container.chat.value?.clearHistory() } }

    @Test fun criarRenomearTrocarRetomarEExcluirPreservaRascunhosSeparados() = runBlocking<Unit> {
        val coordinator = container.chat.filterNotNull().first()
        val store = container.access.runtime.value!!.chatStore!!
        compose.onNodeWithText("Mensagem do Chat").performTextInput("rascunho da casa")
        compose.onNodeWithText("Conversas").performClick()
        compose.onAllNodesWithText("Renomear")[0].performClick()
        compose.onNodeWithText("Nome da conversa").performTextClearance()
        compose.onNodeWithText("Nome da conversa").performTextInput("Casa")
        compose.onNodeWithText("Salvar").performClick()
        compose.onNodeWithText("Nova conversa").performClick()
        compose.onNodeWithText("Mensagem do Chat").performTextInput("rascunho da viagem")
        val second = store.snapshot().activeConversation!!.id
        compose.onNodeWithText("Conversas").performClick()
        compose.onAllNodesWithText("Renomear")[0].performClick()
        compose.onNodeWithText("Nome da conversa").performTextClearance()
        compose.onNodeWithText("Nome da conversa").performTextInput("Viagem")
        compose.onNodeWithText("Salvar").performClick()
        compose.onNodeWithText("Casa").performClick()
        compose.onNodeWithText("Mensagem do Chat").assertTextContains("rascunho da casa")
        scenario!!.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Mensagem do Chat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mensagem do Chat").assertTextContains("rascunho da casa")
        compose.onNodeWithText("Conversas").performClick()
        compose.onNodeWithText("Viagem").performClick()
        compose.onNodeWithText("Mensagem do Chat").assertTextContains("rascunho da viagem")
        compose.onNodeWithText("Conversas").performClick()
        compose.onAllNodesWithText("Excluir conversa")[0].performClick()
        compose.onNode(hasText("Excluir conversa") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { runBlocking { store.conversation(second) == null } }
        assertEquals(1, store.snapshot().conversations.size)
        assertEquals("rascunho da casa", store.snapshot().activeConversation!!.draftText)
        assertNull(coordinator.send("não salvar em conversa apagada", second))
    }
}
