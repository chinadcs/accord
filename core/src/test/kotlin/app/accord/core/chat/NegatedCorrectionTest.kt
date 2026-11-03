package app.accord.core.chat

import kotlin.test.*

/** accord-nsu.21.14 (profile 17 / validator 20): "X não Y" corrections apply the affirmed value, never the negated one. */
class NegatedCorrectionTest {
    private val ctx = ProtectedQualificationBattery.context

    private fun decide(text: String, profile: Int): ChatDecision {
        val validator = when { profile >= 17 -> 20; else -> 19 }
        val raw = SharedReducedInterpreter({ _, _, _ -> StepOutput(" null") }, PromptStyle.ChatMl, profileVersion = profile).interpret(InterpretRequest(text, ctx)).raw
        return ChatValidator.evaluate(text, raw, ctx, version = validator)
    }

    private fun change(d: ChatDecision) = assertIs<ChatDecision.Correct>(d).plan.change

    private fun noEffect(d: ChatDecision, label: String) =
        assertTrue(d !is ChatDecision.Register && d !is ChatDecision.Correct && d !is ChatDecision.Answer, "$label: $d")

    @Test fun `profile sixteen refuses and profile seventeen applies the affirmed amount`() {
        noEffect(decide("na verdade o último foi 52 não 45", 16), "perfil 16")
        assertEquals(EntryChange.Amount(app.accord.core.Money(5200)), change(decide("na verdade o último foi 52 não 45", 17)))
    }

    @Test fun `the affirmed value wins in either order and for payments`() {
        assertEquals(EntryChange.Amount(app.accord.core.Money(4500)), change(decide("errei, não foi 40 foi 45", 17)))
        assertEquals(EntryChange.Amount(app.accord.core.Money(4500)), change(decide("errei, não foi 40, foi 45", 17)))
        assertEquals(EntryChange.Payment(ChatPayment.Card, null), change(decide("o último foi no crédito não no débito", 17)))
        assertEquals(EntryChange.Payment(ChatPayment.Pix, null), change(decide("na verdade era no pix não no dinheiro", 17)))
        assertEquals(EntryChange.Payment(ChatPayment.Cash, null), change(decide("era dinheiro, não pix", 17)))
    }

    @Test fun `the negated value can never be applied, not even through a forged proposal`() {
        val text = "na verdade o último foi 52 não 45"
        val old = Proposal.Correct("r1", CorrectionField.Amount, text = "45")
        noEffect(ChatValidator.evaluate(text, old, ctx, version = 20), "valor negado")
        val payment = Proposal.Correct("r1", CorrectionField.Payment, payment = ChatPayment.Debit)
        noEffect(ChatValidator.evaluate("o último foi no crédito não no débito", payment, ctx, version = 20), "pagamento negado")
        assertIs<ChatDecision.Correct>(ChatValidator.evaluate(text, Proposal.Correct("r1", CorrectionField.Amount, text = "52"), ctx, version = 20))
    }

    @Test fun `unsupported or unrelated negations stay refused`() {
        for (text in listOf("na vdd parcelei em 3x não em 2x", "não paguei 40", "mercado 35 no pix não no cartão", "não foi 40",
            "pizza e refrigerante 35 no pix não no cartão", "o mercado de ontem foi no cartão, nao no pix")) {
            noEffect(decide(text, 17), text)
        }
    }

    @Test fun `plain corrections and new records are unchanged`() {
        for (text in listOf("corrige o valor para 60", "foi no cartão", "a data certa foi ontem")) {
            assertEquals(decide(text, 16)::class, decide(text, 17)::class, text)
        }
        assertIs<ChatDecision.Register>(decide("mercado 35 no pix", 17))
    }
}
