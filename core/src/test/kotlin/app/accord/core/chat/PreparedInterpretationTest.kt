package app.accord.core.chat

import kotlinx.serialization.json.*
import kotlin.test.*

/** Scripted choices test reachability and validation, never model quality. Oracles stay out of production. */
class PreparedInterpretationTest {
    private fun label(p: Proposal): String = when (p) {
        is Proposal.Register -> if (p.nature == Nature.Income) "renda" else "despesa"
        is Proposal.Answer -> "responder"
        is Proposal.Correct -> when (p.field) {
            CorrectionField.Amount -> "corrigir_valor"
            CorrectionField.Date -> "corrigir_data"
            CorrectionField.Description -> "corrigir_descricao"
            CorrectionField.Category -> "corrigir_categoria"
            CorrectionField.Payment -> "corrigir_pagamento"
            else -> error("unexpected field")
        }
        Proposal.Query -> "consulta"
        Proposal.Intent -> "intencao"
        is Proposal.Unsupported -> when (p.type) {
            UnsupportedType.Recurrence -> "recorrencia"
            UnsupportedType.Boleto -> "boleto"
            UnsupportedType.Refund -> "estorno"
            UnsupportedType.InvoicePayment -> "fatura"
            UnsupportedType.Cancellation -> "cancelamento"
        }
        else -> "sem_acao"
    }

    private fun scripted(c: ReducedBattery.Case, single: Boolean): ChatDecision {
        val model = StepModel { _, suffix, grammar ->
            val data = Json.parseToJsonElement(suffix.substring(suffix.indexOf('{'), suffix.lastIndexOf('}') + 1)).jsonObject
            val description = (c.oracle as? Proposal.Register)?.description
            val id = data.getValue("descriptions").jsonObject.entries.singleOrNull { it.value.jsonPrimitive.content == description }?.key
            if (description != null) assertNotNull(id, "${c.id}: missing complete literal '$description'")
            val action = label(c.oracle)
            val task = data.getValue("task").jsonPrimitive.content
            if (task != "describe") assertTrue(grammar.contains(JsonPrimitive(JsonPrimitive(action).toString()).toString()), "${c.id}: missing action $action")
            StepOutput(when (task) {
                "single" -> buildJsonObject { put("a", action); put("d", id?.let(::JsonPrimitive) ?: JsonNull) }.toString()
                "describe" -> id?.let { JsonPrimitive(it).toString() } ?: "null"
                else -> JsonPrimitive(action).toString()
            })
        }
        val result = SharedReducedInterpreter(model, PromptStyle.ChatMl, singleCall = single, profileVersion = 4)
            .interpret(InterpretRequest(c.text, c.context))
        return ChatValidator.evaluate(c.text, result.raw, c.context)
    }

    @Test fun `existing qualification actions and full descriptions are reachable in both protocols`() {
        for (single in listOf(false, true)) for (c in QualificationBattery.cases.filter { it.expected != ReducedBattery.Expected.Safe }) {
            val decision = scripted(c, single)
            val grade = ReducedBattery.grade(c, decision)
            assertTrue(grade.match && grade.fields.values.all { it }, "${c.id} single=$single: $decision $grade")
            if (c.expected is ReducedBattery.Expected.Register) assertEquals(c.expected.entry.description, assertIs<ChatDecision.Register>(decision).entry.description, c.id)
        }
    }

    @Test fun `payment brands require whole words and debit inflection is explicit`() {
        for (text in listOf("revisão", "previsão", "entrevista", "cartolina", "masterizar", "pixação", "cashback")) {
            assertTrue(ChatPayment.entries.none { ChatText.mentionsPayment(text, it) }, text)
        }
        for (text in listOf("Visa", "no cartão", "crédito", "Mastercard", "Master", "cart")) assertTrue(ChatText.mentionsPayment(text, ChatPayment.Card), text)
        assertTrue(ChatText.mentionsPayment("debitei 58 da conta Itaú", ChatPayment.Debit))
    }

    @Test fun `validator preserves a named person and refuses conflicting or ambiguous people`() {
        val ctx = ReducedBattery.context()
        val text = "Ana gastou 74 no cartão com tinta de parede"
        val p = Proposal.Register(description = "tinta de parede", amount = "74", payment = ChatPayment.Card)
        assertEquals("person-1", assertIs<ChatDecision.Register>(ChatValidator.evaluate(text, p, ctx)).entry.spentBy)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, p.copy(person = "m2"), ctx))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, p.copy(person = "m99"), ctx))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate("Ana e Bruno gastaram 74 no cartão", p.copy(description = null), ctx))
        assertNull(assertIs<ChatDecision.Register>(ChatValidator.evaluate("banana 74 no cartão", p.copy(description = "banana"), ctx)).entry.spentBy)
        val renamed = ctx.copy(members = ctx.members.map { it.copy(name = if (it.ref == "m1") "Carolina" else it.name) })
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, p, renamed))
        val fullNameOnly = ctx.copy(members = listOf(MemberItem("m1", "person-1", "Ana Paula")))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, p, fullNameOnly))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate("Carlos gastou 74 no cartão", p.copy(description = null), ctx))
    }

    @Test fun `current validator resolves both old oracle gaps while legacy grades remain unchanged`() {
        for (id in listOf("qr11", "qc04")) {
            val c = QualificationBattery.cases.single { it.id == id }
            assertFalse(ReducedBattery.grade(c, ChatValidator.evaluate(c.text, c.oracle, c.context, version = 4)).match, id)
            assertTrue(ReducedBattery.grade(c, ChatValidator.evaluate(c.text, c.oracle, c.context)).match, id)
        }
        val c = QualificationBattery.cases.single { it.id == "qr18" }
        val wrong = Proposal.Register(Nature.Expense, "texto", "640", payment = ChatPayment.Card)
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text, wrong, c.context, version = 4))
        assertEquals(QuestionField.Payment, assertIs<ChatDecision.Ask>(ChatValidator.evaluate(c.text, wrong, c.context, version = 5)).question.field)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text, wrong, c.context))
        val named = QualificationBattery.cases.single { it.id == "qr25" }
        val omitted = (named.oracle as Proposal.Register).copy(person = null)
        assertNull(assertIs<ChatDecision.Register>(ChatValidator.evaluate(named.text, omitted, named.context, version = 4)).entry.spentBy)
        assertEquals("person-1", assertIs<ChatDecision.Register>(ChatValidator.evaluate(named.text, omitted, named.context)).entry.spentBy)
    }

    @Test fun `new expense cannot consume an open question and wrong refs cannot answer`() {
        val base = ReducedBattery.context()
        for (field in QuestionField.entries) {
            val ctx = base.copy(question = OpenQuestion("q9", "pending-9", field, EntryCompleter.paymentOptions(base)))
            val text = "pizza 62 no cartão"
            val result = SharedReducedInterpreter(StepModel { _, _, _ -> StepOutput("\"responder\"") }, PromptStyle.ChatMl, profileVersion = 4)
                .interpret(InterpretRequest(text, ctx))
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, result.raw, ctx), field.name)
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate("cartão", Proposal.Answer("q1", choice = "card"), ctx))
        }
        val c = QualificationBattery.cases.single { it.id == "qa06" }
        val newEntry = c.copy(id = "new", text = "pizza 62 no cartão", oracle = Proposal.Register(description = "pizza", amount = "62", payment = ChatPayment.Card))
        val entry = assertIs<ChatDecision.Register>(scripted(newEntry, false)).entry
        assertEquals(6200, entry.amount.cents)
    }

    @Test fun `complete six-word descriptions retain internal prepositions and literal spacing`() {
        val text = "kit de reparo para bicicleta elétrica 49 no pix"
        val phrases = ReducedPreparation.descriptions(text, InterpreterGrammar.Spans(text))
        assertTrue("kit de reparo para bicicleta elétrica" in phrases)
        val spaced = "café  em grãos 34 no cartão"
        assertTrue("café  em grãos" in ReducedPreparation.descriptions(spaced, InterpreterGrammar.Spans(spaced)))
        assertTrue(phrases.all { text.contains(it) })
    }

    @Test fun `malformed or out of grammar model output never gains a financial effect`() {
        val ctx = ReducedBattery.context()
        for (raw in listOf("{", "[]", "\"inventado\"", "{\"a\":\"despesa\",\"d\":\"d9999\"}", "{\"a\":\"despesa\",\"d\":null,\"extra\":true}")) {
            val text = "pão 10 no pix"
            val result = SharedReducedInterpreter(StepModel { _, _, _ -> StepOutput(raw) }, PromptStyle.ChatMl, singleCall = true, profileVersion = 4)
                .interpret(InterpretRequest(text, ctx))
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, result.raw, ctx), raw)
        }
        val failure = IllegalStateException("engine failed")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            SharedReducedInterpreter(StepModel { _, _, _ -> throw failure }, PromptStyle.ChatMl, profileVersion = 4)
                .interpret(InterpretRequest("pão 10 no pix", ctx))
        })
    }

    @Test fun `strong control shares v4 guards without oracle and has no financial unsafe regression`() {
        val interpreter = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMl, profileVersion = 4)
        var complete = 0
        var answers = 0
        for (c in QualificationBattery.cases) {
            val result = interpreter.interpret(InterpretRequest(c.text, c.context))
            val decision = ChatValidator.evaluate(c.text, result.raw, c.context)
            val grade = ReducedBattery.grade(c, decision)
            assertFalse(grade.unsafe, "${c.id}: $decision")
            if (!c.clear) assertTrue(decision !is ChatDecision.Register && decision !is ChatDecision.Correct && decision !is ChatDecision.Answer, c.id)
            if (grade.match && grade.fields.values.all { it }) complete++
            if (c.group == "answer" && grade.match) answers++
        }
        assertEquals(24, answers)
        println("Deterministic regression only: rules-longest-v4 complete=$complete/120; answers=$answers/24; no model inference")
    }

    @Test fun `adversarial model cannot force effects through safety guards`() {
        val ctx = ReducedBattery.context()
        val inputs = QualificationBattery.cases.filter { it.expected == ReducedBattery.Expected.Safe }.map { it.text to it.context } + listOf(
            "não paguei 30 no pix", "café 12 no pix Safra", "café 12 no pix via Safra", "café 12 no pix da Safra",
            "café 12 na conta Inter pelo Nubank no pix", "Ana e Bruno gastaram 30 no cartão",
        ).map { it to ctx }
        for ((text, context) in inputs) for (action in listOf("despesa", "renda", "responder", "corrigir_valor", "corrigir_pagamento")) {
            val result = SharedReducedInterpreter(StepModel { _, _, _ -> StepOutput(JsonPrimitive(action).toString()) }, PromptStyle.ChatMl, profileVersion = 4)
                .interpret(InterpretRequest(text, context))
            val d = ChatValidator.evaluate(text, result.raw, context)
            assertTrue(d !is ChatDecision.Register && d !is ChatDecision.Correct && d !is ChatDecision.Answer, "$text: $action -> $d")
        }
    }
}
