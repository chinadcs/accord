package app.accord.core.chat

import kotlinx.serialization.json.*
import kotlin.test.*

/** accord-nsu.21.7: a place introduced by no/na is context, never the item; 'caiu' is an income verb. Profile 13 / validator 16. */
class PlaceAndIncomeVerbDescriptionTest {
    private val ctx = IndependentFinancialBattery.context

    private class Run(val offered: List<List<String>>, val decision: ChatDecision)

    private fun run(text: String, profile: Int, choose: (List<String>) -> String? = { null }): Run {
        val offered = mutableListOf<List<String>>()
        val model = StepModel { _, suffix, _ ->
            val data = Json.parseToJsonElement(suffix.substring(suffix.indexOf('{'), suffix.lastIndexOf('}') + 1)).jsonObject
            val options = data.getValue("descriptions").jsonObject
            offered += options.values.map { it.jsonPrimitive.content }
            val pick = choose(offered.last())?.let { wanted -> options.entries.single { it.value.jsonPrimitive.content == wanted }.key }
            StepOutput(pick?.let { JsonPrimitive(it).toString() } ?: "null")
        }
        val validator = when { profile >= 13 -> 16; profile >= 12 -> 15; else -> 14 }
        val result = SharedReducedInterpreter(model, PromptStyle.ChatMlNoThink, profileVersion = profile).interpret(InterpretRequest(text, ctx))
        return Run(offered, ChatValidator.evaluate(text, result.raw, ctx, version = validator))
    }

    private fun description(d: ChatDecision) = assertIs<ChatDecision.Register>(d).entry.description

    @Test fun `profile twelve picks the longer place and profile thirteen keeps the item`() {
        val text = "pão de queijo 18 no débito na padaria da esquina"
        assertEquals("padaria da esquina", description(run(text, 12).decision))
        val fixed = run(text, 13)
        assertEquals("pão de queijo", description(fixed.decision))
        assertTrue(fixed.offered.isEmpty(), "Só há uma opção legítima: ${fixed.offered}")
    }

    @Test fun `places with a qualifier are context in every position and the item survives`() {
        for ((text, item) in listOf(
            "mamão 9 no débito no mercado do bairro" to "mamão",
            "remédio de pressão 31 no pix na farmácia São João" to "remédio de pressão",
            "pão francês 8 no pix na Padaria Estrela" to "pão francês",
            "bolo de fubá 15 em dinheiro na feira" to "bolo de fubá",
            "gasolina 180 no pix no posto" to "gasolina",
            "corte de cabelo 45 no pix na barbearia" to "corte de cabelo",
        )) {
            val r = run(text, 13)
            assertEquals(item, description(r.decision), text)
            assertTrue(r.offered.isEmpty(), "$text -> ${r.offered}")
        }
    }

    @Test fun `a forged place description is rejected only from validator sixteen`() {
        val text = "pão de queijo 18 no débito na padaria da esquina"
        val forged = Proposal.Register(description = "padaria da esquina", amount = "18", payment = ChatPayment.Debit)
        assertEquals("padaria da esquina", description(ChatValidator.evaluate(text, forged, ctx, version = 15)))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, forged, ctx, version = 16))
    }

    @Test fun `a place that is part of the item or without the locative stays a description`() {
        assertEquals("livro sobre padaria", description(run("livro sobre padaria 40 no pix", 13).decision))
        assertEquals("pão na padaria", description(run("pão na padaria 12 no pix", 13).decision))
        assertEquals("padaria", description(run("padaria 22 no pix", 13).decision))
    }

    @Test fun `caiu is an income verb and the salary stays the description`() {
        val text = "caiu o salário 4200 na conta Inter"
        assertEquals("caiu o salário", description(run(text, 12).decision))
        val entry = assertIs<ChatDecision.Register>(run(text, 13).decision).entry
        assertEquals("salário", entry.description)
        assertEquals(420000, entry.amount.cents)
        assertEquals("account-1", entry.accountId)
        assertEquals(Nature.Income, entry.nature)
    }
}
