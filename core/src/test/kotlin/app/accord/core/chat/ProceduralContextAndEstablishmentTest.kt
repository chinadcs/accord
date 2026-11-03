package app.accord.core.chat

import kotlinx.serialization.json.*
import kotlin.test.*

/** accord-nsu.21.2 e .21.3: perfil 11 / validador 14. O perfil 10 / validador 13 continua reproduzível. */
class ProceduralContextAndEstablishmentTest {
    private val vr15 = RequalificationBattery.cases.single { it.id == "vr15" }
    private val se27 = SelectorComparisonBattery.cases.single { it.id == "se27" }

    private class Run(val offered: List<List<String>>, val decision: ChatDecision)

    private fun validator(profile: Int) = if (profile >= 11) 14 else 13

    private fun run(text: String, ctx: ChatContext, profile: Int): Run {
        val offered = mutableListOf<List<String>>()
        val model = StepModel { _, suffix, _ ->
            val data = Json.parseToJsonElement(suffix.substring(suffix.indexOf('{'), suffix.lastIndexOf('}') + 1)).jsonObject
            offered += data.getValue("descriptions").jsonObject.values.map { it.jsonPrimitive.content }
            StepOutput("null")
        }
        val result = SharedReducedInterpreter(model, PromptStyle.ChatMlNoThink, profileVersion = profile).interpret(InterpretRequest(text, ctx))
        return Run(offered, ChatValidator.evaluate(text, result.raw, ctx, version = validator(profile)))
    }

    private fun candidates(text: String, ctx: ChatContext, procedural: Boolean) =
        PurposeDescriptions(InterpretRequest(text, ctx), refinedPeople = true, refinedVocabulary = true, refinedProcedure = procedural).candidates

    private fun assertNoWrite(d: ChatDecision, label: String) =
        assertTrue(d !is ChatDecision.Register && d !is ChatDecision.Correct && d !is ChatDecision.Answer, "$label: $d")

    // ---- accord-nsu.21.2 ----

    @Test fun `profile ten offers the installment verb as a description and profile eleven does not`() {
        val old = run(vr15.text, vr15.context, 10)
        assertTrue(old.offered.single().contains("dividido"), "${old.offered}")
        val fixed = run(vr15.text, vr15.context, 11)
        assertTrue(fixed.offered.isEmpty(), "A única opção legítima não precisa consultar o modelo: ${fixed.offered}")
        val entry = assertIs<ChatDecision.Register>(fixed.decision).entry
        assertEquals((vr15.expected as ReducedBattery.Expected.Register).entry, entry)
    }

    @Test fun `a forged installment description is rejected only from validator fourteen`() {
        val forged = (vr15.oracle as Proposal.Register).copy(description = "dividido")
        assertEquals("dividido", assertIs<ChatDecision.Register>(ChatValidator.evaluate(vr15.text, forged, vr15.context, version = 13)).entry.description)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(vr15.text, forged, vr15.context, version = 14))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(vr15.text, Proposal.Clarify(QuestionField.Category, emptyList(), forged), vr15.context, version = 14))
    }

    @Test fun `installment wording is never offered while the purpose is kept`() {
        val ctx = vr15.context
        for ((text, purpose) in listOf(
            "sofá de couro 900 no crédito parcelado em 3x" to "sofá de couro",
            "tênis de corrida 480 dividida em 6 vezes no cartão" to "tênis de corrida",
            "geladeira nova 2.400 no crédito dividido em 10 parcelas" to "geladeira nova",
            "parcelei em 6x o tênis de corrida 480 no cartão" to "tênis de corrida",
            "dividimos em 3x o notebook 3.000 no crédito" to "notebook",
            "carregador de pilhas 219 no cartão dividido no crédito" to "carregador de pilhas",
        )) {
            val offered = candidates(text, ctx, procedural = true)
            assertTrue(purpose in offered, "$text -> $offered")
            assertTrue(offered.none { ChatText.fold(it).split(' ').any { w -> w in setOf("dividido", "dividida", "parcelado", "parcelei", "dividimos") } }, "$text -> $offered")
        }
    }

    @Test fun `without the installment marker or payment cue a legitimate noun is not removed`() {
        val ctx = vr15.context
        assertTrue("mesa dividida" in candidates("mesa dividida em 2 partes 300 no pix", ctx, procedural = true))
        assertTrue("organizador dividido" in candidates("organizador dividido 45 no pix", ctx, procedural = true))
        assertTrue("estojo de madeira" in candidates("estojo de madeira 29 no pix", ctx, procedural = true))
        assertTrue("dividido" in candidates(vr15.text, ctx, procedural = false), "o comportamento histórico precisa continuar reproduzível")
    }

    // ---- accord-nsu.21.3 ----

    @Test fun `profile ten refuses the bakery as an unknown bank and profile eleven registers it`() {
        val old = run(se27.text, se27.context, 10)
        assertNoWrite(old.decision, "perfil 10")
        val fixed = run(se27.text, se27.context, 11)
        val entry = assertIs<ChatDecision.Register>(fixed.decision).entry
        assertEquals((se27.expected as ReducedBattery.Expected.Register).entry, entry)
        assertEquals(1200, entry.amount.cents)
        assertEquals(ChatPayment.Debit, entry.payment)
        assertEquals("account-1", entry.accountId)
    }

    @Test fun `ordinary establishments are not banks while unknown institutions still need the account`() {
        val ctx = se27.context
        for (text in listOf("pão integral 12 no débito na padaria", "pão integral 12 no pix na padaria", "mamão 9 no débito no mercado",
            "remédio 31 no pix na farmácia", "pastel 8 no débito na feira", "gasolina 90 no pix no posto")) {
            val d = run(text, ctx, 11).decision
            assertEquals(ChatPayment.Debit.takeIf { "débito" in text } ?: ChatPayment.Pix, assertIs<ChatDecision.Register>(d, text).entry.payment, text)
        }
        for (text in listOf("pão integral 12 no débito Zeta", "pão integral 12 no pix Neon", "pão integral 12 no débito do Bradesco",
            "pão integral 12 no pix no Bradesco", "pão integral 12 no débito na Zeta", "pão integral 12 no pix Banco Inter",
            "pão integral 12 no débito na conta Safra")) {
            val r = run(text, ctx, 11)
            assertNoWrite(r.decision, text)
            val forged = Proposal.Register(description = "pão integral", amount = "12", payment = ChatPayment.Debit, account = "u9")
            assertNoWrite(ChatValidator.evaluate(text, forged, ctx, version = 14), "forjado $text")
        }
    }

    @Test fun `forged default account does not replace an explicit unknown institution`() {
        val ctx = se27.context
        val text = "pão integral 12 no débito Zeta"
        val d = ChatValidator.evaluate(text, Proposal.Register(description = "pão integral", amount = "12", payment = ChatPayment.Debit), ctx, version = 14)
        val ask = assertIs<ChatDecision.Ask>(d)
        assertEquals(QuestionField.Account, ask.question.field)
        assertNull(ask.draft.accountId)
    }

    @Test fun `the bakery syntax is new only in validator fourteen`() {
        val p = se27.oracle as Proposal.Register
        assertNoWrite(ChatValidator.evaluate(se27.text, p, se27.context, version = 13), "validador 13")
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(se27.text, p, se27.context, version = 14))
    }
}
