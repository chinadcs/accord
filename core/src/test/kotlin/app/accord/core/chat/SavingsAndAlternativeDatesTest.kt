package app.accord.core.chat

import kotlin.test.*

/** accord-nsu.21.9 and .21.10, profile 14 / validator 17: savings are not expenses; two dates are never reduced to one. */
class SavingsAndAlternativeDatesTest {
    private val ctx = IndependentFinancialBatteryV2.context

    private fun decide(text: String, profile: Int, context: ChatContext = ctx): ChatDecision {
        val validator = when { profile >= 14 -> 17; profile >= 13 -> 16; else -> 15 }
        val result = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMlNoThink, profileVersion = profile)
            .interpret(InterpretRequest(text, context))
        return ChatValidator.evaluate(text, result.raw, context, version = validator)
    }

    private fun noEffect(d: ChatDecision, label: String) =
        assertTrue(d !is ChatDecision.Register && d !is ChatDecision.Correct && d !is ChatDecision.Answer, "$label: $d")

    @Test fun `profile thirteen records the savings as an expense and profile fourteen does not`() {
        val text = "guardei 300 na poupança no pix"
        assertIs<ChatDecision.Register>(decide(text, 13))
        noEffect(decide(text, 14), text)
    }

    @Test fun `saving verbs with a savings destination are not expenses, also forged`() {
        for (text in listOf("guardei 300 na poupança no pix", "separei 200 para a reserva no pix", "reservei 150 no cofrinho no pix",
            "guardamos 500 na caixinha no pix", "poupei 80 no pix", "poupamos 120 no pix")) {
            noEffect(decide(text, 14), text)
            val forged = Proposal.Register(description = "poupança", amount = Regex("\\d+").find(text)!!.value, payment = ChatPayment.Pix)
            noEffect(ChatValidator.evaluate(text, forged, ctx, version = 17), "forjado $text")
        }
        val forged = Proposal.Register(description = "poupança", amount = "300", payment = ChatPayment.Pix)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate("guardei 300 na poupança no pix", forged, ctx, version = 16))
    }

    @Test fun `ordinary purchases that share a word with savings stay expenses`() {
        for ((text, description) in listOf("caixinha de som 90 no pix" to "caixinha de som", "cofrinho de cerâmica 25 no pix" to "cofrinho de cerâmica",
            "reserva do restaurante 60 no pix" to "reserva do restaurante", "comprei uma poupa de leite 12 no pix" to "poupa de leite")) {
            assertEquals(description, assertIs<ChatDecision.Register>(decide(text, 14), text).entry.description, text)
        }
        assertEquals(ChatDecision.Reply(StandardReply.Query), decide("quanto guardei na poupança?", 14))
    }

    @Test fun `profile thirteen applies one of two dates and profile fourteen applies none`() {
        val text = "corrige a data para ontem ou anteontem"
        assertIs<ChatDecision.Correct>(decide(text, 13))
        noEffect(decide(text, 14), text)
        val forged = Proposal.Correct("s7", CorrectionField.Date, date = DateExpression(DateKind.Yesterday, "ontem"))
        assertIs<ChatDecision.Correct>(ChatValidator.evaluate(text, forged, ctx, version = 16))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, forged, ctx, version = 17))
    }

    @Test fun `alternative dates are refused in new records and answers too`() {
        noEffect(decide("gastei 30 no pix ontem ou anteontem", 14), "registro")
        val question = ctx.copy(question = OpenQuestion("q8", "question-1", QuestionField.Date, EntryCompleter.dateOptions(ctx.today)))
        noEffect(decide("foi ontem ou anteontem", 14, question), "resposta")
        assertEquals(java.time.LocalDate.of(2027, 11, 7), (decide("foi anteontem", 14, question).let { assertIs<ChatDecision.Answer>(it).value as AnswerValue.Day }).date)
    }

    @Test fun `a single yesterday or day before yesterday is unaffected`() {
        assertIs<ChatDecision.Correct>(decide("a data certa foi ontem", 14))
        assertIs<ChatDecision.Correct>(decide("mude a data para anteontem", 14))
        assertIs<ChatDecision.Register>(decide("anteontem paguei 58 no débito pelo corte de cabelo", 14))
        assertIs<ChatDecision.Register>(decide("ontem comprei um vaso de cerâmica por 52 no pix", 14))
    }
}
