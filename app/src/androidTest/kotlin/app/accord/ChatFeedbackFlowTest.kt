package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.accord.core.chat.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.accord.persistence.AccordDatabase
import app.accord.persistence.AccordDatabase_Impl
import app.accord.persistence.RoomChatStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** S8: motivos múltiplos pelo recibo real, persistidos no Room e restaurados na Activity. */
class ChatFeedbackFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun retencaoDeTrintaDiasPreservaOLimiteEApagaSomenteVotosExpirados() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val db = Room.inMemoryDatabaseBuilder<AccordDatabase>(app) { AccordDatabase_Impl() }
            .setDriver(BundledSQLiteDriver()).build()
        val day = 86_400_000L
        var now = 50 * day
        val store = RoomChatStore(db, nowMillis = { now })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            for ((id, time) in listOf("expired" to now - 30 * day - 1, "boundary" to now - 30 * day, "recent" to now - day)) {
                store.saveMessage(ChatMessage("message-$id", "Fixture de retenção", time, MessageState.Applied))
                store.saveReceipt(ChatReceiptRecord(id, "message-$id", "operation-$id", "entry-$id",
                    ReceiptSummary(Nature.Expense, "Fictício", 100, dateIso = "2026-11-09"), ReceiptKind.Registered,
                    ReceiptState.Active, false, createdAtMillis = time))
                store.saveFeedback(ChatFeedback(id, FeedbackVote.Up, null, time))
            }
            val chat = ChatCoordinator(app.container.space(), store, InterpreterSource { null }, app.container.clock, scope, nowMillis = { now })
            chat.purgeExpiredFeedback()
            assertEquals(setOf("boundary", "recent"), store.feedback().map { it.receiptId }.toSet())
            now++
            chat.purgeExpiredFeedback()
            assertEquals(listOf("recent"), store.feedback().map { it.receiptId })
            assertEquals("operation-recent", store.feedback().single().assessedOperationId)
            assertEquals(3, store.snapshot().receipts.size)
            assertEquals(3, store.snapshot().messages.size)
        } finally { scope.cancel(); db.close() }
    }

    @Test fun selecionarDoisMotivosRetirarUmERecriarPreservaOVotoDoRecibo() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val container = app.container
        TestModel.install(container)
        val chat = container.chat.filterNotNull().first()
        chat.clearHistory()
        val text = "mercado de teste 25 no cartão"
        TestModel.interpreter.on(text, Proposal.Register(description = "mercado de teste", amount = "25", payment = ChatPayment.Card))
        chat.send(text)
        val store = container.access.runtime.value!!.chatStore!!
        val receipt = withTimeout(15_000) {
            while (store.snapshot().receipts.isEmpty()) delay(50)
            store.snapshot().receipts.single()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithContentDescription("Chat").performClick()
            fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
            fun fields() = runBlocking { store.snapshot().feedback.single().fields }
            compose.waitUntil(10_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription("Entendeu errado"))
            compose.onNodeWithContentDescription("Entendeu errado").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                runBlocking { store.snapshot().feedback.any { it.receiptId == receipt.id && it.vote == FeedbackVote.Down } } &&
                    compose.onAllNodesWithText("O que errou?").fetchSemanticsNodes().isNotEmpty()
            }
            click("Categoria")
            click("Data")
            compose.waitUntil(10_000) { fields() == setOf(FeedbackField.Category, FeedbackField.Date) }
            compose.onNodeWithText("Categoria").assertIsSelected()
            compose.onNodeWithText("Data").assertIsSelected()
            scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("O que errou?").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Data").performScrollTo().assertIsSelected()
            click("Categoria")
            compose.waitUntil(10_000) { fields() == setOf(FeedbackField.Date) }
            assertEquals(receipt.id, store.snapshot().feedback.single().receiptId)
            compose.onNodeWithContentDescription("Entendeu errado").performScrollTo().assertIsSelected()
        }
        chat.clearHistory()
    }
}
