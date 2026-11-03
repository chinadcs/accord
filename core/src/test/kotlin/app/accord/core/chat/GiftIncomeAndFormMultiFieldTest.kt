package app.accord.core.chat

import kotlin.test.*

/** accord-nsu.21.12 and .21.13 (profile 16 / validator 19): money received as a gift is income; two fields are recognised by form. */
class GiftIncomeAndFormMultiFieldTest {
    private val ctx = ProtectedQualificationBattery.context

    private fun decide(text: String, profile: Int): ChatDecision {
        val validator = when { profile >= 16 -> 19; else -> 18 }
        val raw = SharedReducedInterpreter({ _, _, _ -> StepOutput(" null") }, PromptStyle.ChatMl, profileVersion = profile).interpret(InterpretRequest(text, ctx)).raw
        return ChatValidator.evaluate(text, raw, ctx, version = validator)
    }

    private fun noEffect(d: ChatDecision, label: String) =
        assertTrue(d !is ChatDecision.Register && d !is ChatDecision.Correct && d !is ChatDecision.Answer, "$label: $d")

    // ---- .21.12 ----

    @Test fun `profile fifteen records a gift of money as an expense and profile sixteen as income`() {
        val text = "mãe me deu 100 de presente em dinheiro"
        assertEquals(Nature.Expense, assertIs<ChatDecision.Register>(decide(text, 15)).entry.nature)
        val entry = assertIs<ChatDecision.Register>(decide(text, 16)).entry
        assertEquals(Nature.Income, entry.nature)
        assertEquals(10000, entry.amount.cents)
        assertEquals("presente", entry.description)
    }

    @Test fun `gifts and transfers received are income without asking how it was paid`() {
        for ((text, cents) in listOf("mae me deu 100 de presente" to 10000L, "vovó me deu 200 de aniversário" to 20000L,
            "meu tio me deu 500 no pix" to 50000L, "me deram 80 de presente" to 8000L)) {
            val d = decide(text, 16)
            val entry = assertIs<ChatDecision.Register>(d, text).entry
            assertEquals(Nature.Income, entry.nature, text)
            assertEquals(cents, entry.amount.cents, text)
        }
    }

    @Test fun `a gift received cannot be forced into an expense by a forged proposal from validator nineteen`() {
        val text = "mãe me deu 100 de presente em dinheiro"
        val forged = Proposal.Register(Nature.Expense, "presente", "100", payment = ChatPayment.Cash)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text, forged, ctx, version = 18))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, forged, ctx, version = 19))
    }

    @Test fun `a discount, change or a transfer between people is not income`() {
        for (text in listOf("o mecânico me deu 20 de desconto no pix", "a moça me deu 5 de troco em dinheiro", "a amiga me mandou 120 do mercado",
            "meu irmão me transferiu 300 pro aluguel", "ela me enviou 80 da pizza")) {
            val d = decide(text, 16)
            assertFalse(d is ChatDecision.Register && d.entry.nature == Nature.Income, "$text: $d")
        }
    }

    // ---- .21.13 ----

    @Test fun `profile fifteen applies one of two corrections without the words value and date and profile sixteen none`() {
        val text = "corrige o último pra 35,50 e ontem"
        assertIs<ChatDecision.Correct>(decide(text, 15))
        noEffect(decide(text, 16), text)
    }

    @Test fun `two fields are recognised by form in any order and pair`() {
        for (text in listOf("corrige pra 12,90 e dia 5", "muda pra 80 no pix", "troca pra cartão e ontem", "corrige pra Lazer e 40",
            "ajusta pra 25 e anteontem", "corrige o último pra dinheiro e 60")) {
            noEffect(decide(text, 16), text)
        }
    }

    @Test fun `a forged single field proposal for a two field request is refused from validator nineteen`() {
        val text = "corrige o último pra 35,50 e ontem"
        val forged = Proposal.Correct("s8", CorrectionField.Amount, text = "35,50")
        assertIs<ChatDecision.Correct>(ChatValidator.evaluate(text, forged, ctx, version = 18))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, forged, ctx, version = 19))
    }

    @Test fun `a date correction without the word date was and stays unresolved, never applied`() {
        // Not a regression: without "data/dia" the field is unknown at every profile; the refusal is safe and recoverable.
        assertEquals(decide("corrige pra ontem", 15)::class, decide("corrige pra ontem", 16)::class)
        noEffect(decide("corrige pra ontem", 16), "corrige pra ontem")
    }

    @Test fun `single field corrections keep working`() {
        for (text in listOf("corrige o último pra 35,50", "corrige a data pra ontem", "corrige o valor para 60", "foi no cartão", "classifica em Lazer",
            "a data certa foi ontem", "errei, o valor era 2.300", "troca a descrição para café com leite", "o pagamento foi no pix da conta Nubank", "corrige o pagamento de 47,90 para pix", "corrige o pagamento de ontem para pix",
            "corrige o valor do mercado para 200", "corrige o mercado para 318", "corrige o valor do pet shop para 85")) {
            assertIs<ChatDecision.Correct>(decide(text, 16), text)
        }
    }
}
