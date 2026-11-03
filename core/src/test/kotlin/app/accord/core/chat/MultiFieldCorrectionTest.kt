package app.accord.core.chat

import kotlin.test.*

/** accord-nsu.21.6: a correction naming two fields is never applied to one of them. Profile 12 / validator 15. */
class MultiFieldCorrectionTest {
    private val ic14 = IndependentFinancialBattery.cases.single { it.id == "ic14" }
    private val ctx = IndependentFinancialBattery.context

    private fun decide(text: String, profile: Int): ChatDecision {
        val validator = if (profile >= 12) 15 else 14
        val result = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMlNoThink, profileVersion = profile)
            .interpret(InterpretRequest(text, ctx))
        return ChatValidator.evaluate(text, result.raw, ctx, version = validator)
    }

    @Test fun `profile eleven applies only the date and profile twelve applies nothing`() {
        val old = assertIs<ChatDecision.Correct>(decide(ic14.text, 11))
        assertEquals(EntryChange.Date(java.time.LocalDate.of(2027, 8, 12)), old.plan.change)
        assertIs<ChatDecision.Refuse>(decide(ic14.text, 12))
    }

    @Test fun `a forged date proposal for a two field request is accepted by validator fourteen and refused from fifteen`() {
        val date = Proposal.Correct("s5", CorrectionField.Date, date = DateExpression(DateKind.DayOfMonth, "dia 12"))
        assertIs<ChatDecision.Correct>(ChatValidator.evaluate(ic14.text, date, ctx, version = 14))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(ic14.text, date, ctx, version = 15))
        val amount = Proposal.Correct("s5", CorrectionField.Amount, text = "60")
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(ic14.text, amount, ctx, version = 15))
    }

    @Test fun `two fields in either order and with any pair are refused`() {
        for (text in listOf(
            "corrige o valor para 60 e a data para dia 12",
            "mude a data para ontem e o pagamento para pix",
            "troque a categoria para Pet e o valor para 80",
            "a data certa é dia 4 e a descrição é bolo",
            "troca a descrição para sandália e a data para dia 4",
            "corrija o valor para 60 e a categoria para Casa",
            "ajusta o valor para 75 e o pagamento para dinheiro",
        )) assertIs<ChatDecision.Refuse>(decide(text, 12), text)
    }

    @Test fun `single field corrections and new records keep working`() {
        for (c in IndependentFinancialBattery.cases.filter { it.group == "correct" && it.expected is ReducedBattery.Expected.Correct }) {
            val d = ChatValidator.evaluate(c.text, c.oracle, c.context, version = 15)
            assertEquals((c.expected as ReducedBattery.Expected.Correct).change, assertIs<ChatDecision.Correct>(d, c.id).plan.change, c.id)
        }
        for (text in listOf("corrija a data para o dia 12", "ajusta o valor para 60 reais", "o pagamento certo foi no crédito")) {
            assertIs<ChatDecision.Correct>(decide(text, 12), text)
        }
        assertIs<ChatDecision.Correct>(decide("troca a descrição para data do boleto", 12))
        assertIs<ChatDecision.Register>(decide("ração 60 dia 12 no pix", 12))
    }

    @Test fun `historical validators keep the previous outcome`() {
        for (version in 10..14) {
            val forged = Proposal.Correct("s5", CorrectionField.Date, date = DateExpression(DateKind.DayOfMonth, "dia 12"))
            assertIs<ChatDecision.Correct>(ChatValidator.evaluate(ic14.text, forged, ctx, version = version), "validator $version")
        }
    }
}
