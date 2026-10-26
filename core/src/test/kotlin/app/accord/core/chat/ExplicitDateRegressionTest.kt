package app.accord.core.chat

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExplicitDateRegressionTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun `ano de quatro digitos e preservado com ano curto e data sem ano compativeis`() {
        listOf(
            "23/10/2026" to LocalDate.of(2026, 10, 23),
            "23/10/26" to LocalDate.of(2026, 10, 23),
            "29/02/2024" to LocalDate.of(2024, 2, 29),
            "23/10" to LocalDate.of(2025, 10, 23),
        ).forEach { (text, expected) ->
            assertEquals(DateRead.Ok(expected), ChatText.resolveDate(DateExpression(DateKind.Explicit, text), today), text)
        }
    }

    @Test
    fun `ano malformado nao e truncado nem descartado para salvar outro dia`() {
        listOf("23/10/20261", "23/10/202", "23/10/2", "123/10/2026", "29/02/2025").forEach { text ->
            assertEquals(DateRead.Invalid, ChatText.resolveDate(DateExpression(DateKind.Explicit, text), today), text)
        }
    }

    @Test
    fun `despesa com data futura explicita pede data em vez de salvar em outro ano`() {
        val context = ChatBattery.realisticContext()
        val proposal = Proposal.Register(description = "mercado", amount = "61", payment = ChatPayment.Card,
            date = DateExpression(DateKind.Explicit, "23/10/2026"))
        val decision = ChatValidator.evaluate("mercado 61 em 23/10/2026 no cartão", proposal, context)
        assertEquals(QuestionField.Date, assertIs<ChatDecision.Ask>(decision).question.field)
    }
}
