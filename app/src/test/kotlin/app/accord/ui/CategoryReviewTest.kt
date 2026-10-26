package app.accord.ui

import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQuestion
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatSnapshot
import app.accord.core.chat.MessageState
import app.accord.core.chat.OptionValue
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionOption
import app.accord.core.chat.QuestionState
import app.accord.ui.review.buildCategoryReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryReviewTest {
    private val options = listOf(
        QuestionOption("c1", "Alimentação", OptionValue.Category("alimentacao")),
        QuestionOption("c2", "Mercado", OptionValue.Category("alimentacao.mercado")),
        QuestionOption("c3", "Pet", OptionValue.Category("pet")),
    )

    private fun question(
        id: String,
        message: String,
        at: Long,
        link: QuestionLink = QuestionLink.Entry("e-$id"),
        field: QuestionField = QuestionField.Category,
        state: QuestionState = QuestionState.Open,
    ) = ChatQuestionRecord(id, message, field, ChatQuestion.Reason.Missing, options, link, "Zaffari", 12_000, state, at)

    private fun message(id: String, hints: List<String> = emptyList()) =
        ChatMessage(id, "Zaffari 120 no cartão", 1, MessageState.ToClassify, categoryHints = hints)

    @Test
    fun `categoria sugerida pelo modelo aparece como sugestao e as demais ficam sem`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("m1", listOf("alimentacao.mercado")), message("m2")),
            questions = listOf(question("q1", "m1", 10), question("q2", "m2", 20)),
            receipts = emptyList(),
        )
        val items = buildCategoryReview(snapshot)
        assertEquals(listOf("q1", "q2"), items.map { it.question.id })
        assertEquals("Mercado", items[0].suggestion?.label)
        assertNull(items[1].suggestion, "sem dica do modelo não há sugestão")
    }

    @Test
    fun `so perguntas de categoria abertas de lancamentos ja salvos entram na fila`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("m1", listOf("pet"))),
            questions = listOf(
                question("aberta", "m1", 10),
                question("respondida", "m1", 11, state = QuestionState.Answered),
                question("rascunho", "m1", 12, link = QuestionLink.Draft("d1")),
                question("valor", "m1", 13, field = QuestionField.Amount),
            ),
            receipts = emptyList(),
        )
        assertEquals(listOf("aberta"), buildCategoryReview(snapshot).map { it.question.id })
    }

    @Test
    fun `dica que nao esta entre as opcoes nao vira sugestao`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("m1", listOf("categoria-que-sumiu"))),
            questions = listOf(question("q1", "m1", 10)),
            receipts = emptyList(),
        )
        assertNull(buildCategoryReview(snapshot).single().suggestion)
    }

    @Test
    fun `fila mais antiga primeiro`() {
        val snapshot = ChatSnapshot(
            messages = listOf(message("m1"), message("m2")),
            questions = listOf(question("novo", "m2", 30), question("antigo", "m1", 5)),
            receipts = emptyList(),
        )
        assertEquals(listOf("antigo", "novo"), buildCategoryReview(snapshot).map { it.question.id })
    }
}
