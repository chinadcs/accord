package app.accord.core.chat

import kotlin.test.*

/** accord-nsu.21.16 (profile 18 / validator 21): everyday queries, possessives, relative dates and long descriptions. */
class EverydayPhrasesTest {
    private val ctx = ProtectedQualificationBattery.context

    private fun decide(text: String, profile: Int): ChatDecision {
        val validator = when { profile >= 18 -> 21; else -> 20 }
        val raw = SharedReducedInterpreter({ _, _, _ -> StepOutput(" null") }, PromptStyle.ChatMl, profileVersion = profile).interpret(InterpretRequest(text, ctx)).raw
        return ChatValidator.evaluate(text, raw, ctx, version = validator)
    }

    private fun description(text: String, profile: Int) = (decide(text, profile) as? ChatDecision.Register)?.entry?.description

    @Test fun `everyday questions get the standard query reply instead of a refusal`() {
        for (text in listOf("sobrou quanto do orçamento de lazer?", "resumo dos gastos de hj", "o que falta pagar?", "o que tem pra pagar amanhã?",
            "saldo da semana", "me mostra o que vence esta semana")) {
            assertIs<ChatDecision.Refuse>(decide(text, 17), "perfil 17: $text")
            assertEquals(ChatDecision.Reply(StandardReply.Query), decide(text, 18), text)
        }
    }

    @Test fun `a question cannot be forced into a record and numeric sentences stay records`() {
        val forged = Proposal.Register(Nature.Expense, "lazer", "30", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate("sobrou 30 de lazer no pix", forged, ctx, version = 20))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate("resumo dos gastos de lazer 30 no pix", forged, ctx, version = 21).let { it })
        assertIs<ChatDecision.Register>(decide("total 35 no pix", 18))
    }

    @Test fun `possessives and a lone clitic are not part of the description`() {
        assertEquals("meu salario", description("caiu meu salario 3500", 17))
        assertEquals("salario", description("caiu meu salario 3500", 18))
        assertEquals("monitor velho", description("vendi meu monitor velho por 300 no pix", 18))
        assertEquals("presente", description("minha mãe me deu 100 de presente em dinheiro", 18))
    }

    @Test fun `a relative date is not a description, also when forged`() {
        assertEquals("semana passada", description("semana passada vendi a cadeira 120 pix", 17))
        assertEquals("cadeira", description("semana passada vendi a cadeira 120 pix", 18))
        val forged = Proposal.Register(Nature.Income, "semana passada", "120")
        assertIs<ChatDecision.Register>(ChatValidator.evaluate("semana passada vendi a cadeira 120 pix", forged, ctx, version = 20))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate("semana passada vendi a cadeira 120 pix", forged, ctx, version = 21))
    }

    @Test fun `a long phrase with a companion keeps the item before the connector`() {
        assertIs<ChatDecision.Refuse>(decide("conserto do notebook com o técnico João 250 pix", 17))
        assertEquals("conserto do notebook", description("conserto do notebook com o técnico João 250 pix", 18))
    }

    @Test fun `ordinary descriptions are unchanged`() {
        for ((text, item) in listOf("ração do Thor 145 no pix" to "ração do Thor", "banho do Thor 80 no pix" to "banho do Thor", "curso curto 175 no pix" to "curso curto",
            "pão na padaria 12 no pix" to "pão na padaria")) {
            assertEquals(item, description(text, 18), text)
        }
    }
}
