package app.accord.persistence

import app.accord.core.chat.ChatFeedback
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.FeedbackField
import app.accord.core.chat.FeedbackVote
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Joinha do recibo (accord-8q5): voto vigente por recibo, só neste aparelho, retido por 30 dias. */
class ChatFeedbackTest : ChatTestBase() {
    private suspend fun Env.receiptId(): String {
        fake.on("delivery 68 no cartão", reg("delivery", "68", category = categoryRef("alimentacao.delivery"), payment = ChatPayment.Card))
        say("delivery 68 no cartão")
        return store.snapshot().receipts.single().id
    }

    @Test
    fun `joinha positivo guarda so o recibo, o voto e o instante e tocar de novo o retira`() = runBlocking<Unit> {
        val e = env()
        val id = e.receiptId()
        e.coordinator.rate(id, FeedbackVote.Up)
        val vote = e.store.snapshot().feedback.single()
        assertEquals(id, vote.receiptId)
        assertEquals(FeedbackVote.Up, vote.vote)
        assertNull(vote.field)
        assertTrue(vote.votedAtMillis > 0)

        e.coordinator.rate(id, FeedbackVote.Up)
        assertTrue(e.store.snapshot().feedback.isEmpty())
    }

    @Test
    fun `joinha para baixo aceita o campo errado, que completa o mesmo voto`() = runBlocking<Unit> {
        val e = env()
        val id = e.receiptId()
        e.coordinator.rate(id, FeedbackVote.Down)
        assertNull(e.store.snapshot().feedback.single().field)

        e.coordinator.rate(id, FeedbackVote.Down, FeedbackField.Category)
        val vote = e.store.snapshot().feedback.single()
        assertEquals(FeedbackVote.Down, vote.vote)
        assertEquals(FeedbackField.Category, vote.field)

        // Cada motivo alterna sua seleção; o voto continua um só por recibo.
        e.coordinator.rate(id, FeedbackVote.Down, FeedbackField.Date)
        assertEquals(setOf(FeedbackField.Category, FeedbackField.Date), e.store.snapshot().feedback.single().fields)
        e.coordinator.rate(id, FeedbackVote.Down, FeedbackField.Category)
        assertEquals(setOf(FeedbackField.Date), e.store.snapshot().feedback.single().fields)
        assertEquals(e.store.receipt(id)!!.operationId, e.store.snapshot().feedback.single().assessedOperationId)
    }

    @Test
    fun `trocar para joinha positivo descarta o campo e o campo nao vale no positivo`() = runBlocking<Unit> {
        val e = env()
        val id = e.receiptId()
        e.coordinator.rate(id, FeedbackVote.Down, FeedbackField.Value)
        e.coordinator.rate(id, FeedbackVote.Up, FeedbackField.Value)
        val vote = e.store.snapshot().feedback.single()
        assertEquals(FeedbackVote.Up, vote.vote)
        assertNull(vote.field)
    }

    @Test
    fun `voto em recibo que nao existe e ignorado`() = runBlocking<Unit> {
        val e = env()
        e.coordinator.rate("nao-existe", FeedbackVote.Up)
        assertTrue(e.store.snapshot().feedback.isEmpty())
    }

    @Test
    fun `votos com mais de 30 dias sao expurgados e os recentes ficam`() = runBlocking<Unit> {
        val e = env()
        val id = e.receiptId()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        e.store.saveFeedback(ChatFeedback("velho", FeedbackVote.Up, null, now - 31 * day))
        e.store.saveFeedback(ChatFeedback("limite", FeedbackVote.Down, FeedbackField.Payment, now - 29 * day))
        e.coordinator.rate(id, FeedbackVote.Up)

        e.coordinator.purgeExpiredFeedback()
        assertEquals(setOf("limite", id), e.store.feedback().map { it.receiptId }.toSet())
    }

    @Test
    fun `limpar o historico apaga os votos e o arquivo de transferencia nao os leva`() = runBlocking<Unit> {
        val e = env()
        val id = e.receiptId()
        e.coordinator.rate(id, FeedbackVote.Down, FeedbackField.Understood)
        val text = e.store.exportText("ana|espaco")
        assertTrue("Understood" !in text && "UNDERSTOOD" !in text && "feedback" !in text.lowercase())

        e.store.clear()
        assertTrue(e.store.feedback().isEmpty())
    }
}
