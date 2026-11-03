package app.accord.core.chat

import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Chat empty-state examples (strings_chat.xml) must work with the product interpreter in a seeded space. */
class ChatExamplesTest {
    private val context = ChatBattery.context()

    private fun decide(text: String): ChatDecision = runBlocking {
        ChatValidator.evaluate(text, RuleInterpreter().interpret(InterpretRequest(text, context)), context)
    }

    @Test fun `the suggested examples are understood without a question about the essentials`() {
        val delivery = assertIs<ChatDecision.Register>(decide("delivery 68 no cartão")).entry
        assertEquals(6800, delivery.amount.cents)
        assertEquals(ChatPayment.Card, delivery.payment)
        val pix = assertIs<ChatDecision.Register>(decide("mercado 35 no pix")).entry
        assertEquals(3500, pix.amount.cents)
        assertEquals(ChatPayment.Pix, pix.payment)
        val income = assertIs<ChatDecision.Register>(decide("salário 6.000")).entry
        assertEquals(600000, income.amount.cents)
        assertEquals(Nature.Income, income.nature)
    }
}
