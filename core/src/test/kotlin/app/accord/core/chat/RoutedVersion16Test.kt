package app.accord.core.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Perfil 16: "conta de luz" e "tarifa do banco" deixam de ser menção a conta, mais rendas e "por mês" como recorrência. */
class RoutedVersion16Test {
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    private class Scripted(val action: String, val category: String?) : StepModel {
        val calls = mutableListOf<String>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            calls += grammar
            return StepOutput(if ("sem_acao" in grammar) "\"$action\"" else category?.let { JsonPrimitive(it).toString() } ?: "null")
        }
    }

    private fun run(text: String, version: Int, action: String = "despesa", category: String? = null): Pair<Scripted, ChatDecision> {
        val model = Scripted(action, category)
        val raw = RoutedInterpreter(model, PromptStyle.ChatMlNoThink, RoutedOptions(version)).interpret(InterpretRequest(text, ctx)).raw
        return model to ChatValidator.evaluate(text, raw, ctx)
    }

    private fun ref(id: String) = ctx.categories.single { it.id == id }.ref

    @Test
    fun `conta de luz e tarifa do banco registram no perfil 16 e seguem recusadas no 15`() {
        val cases = mapOf(
            "conta de luz 512 via pix" to "casa.contas-domesticas",
            "paguei conta da Sabesp 74,70 em dinheiro" to "casa.contas-domesticas",
            "conta da Cemig 45,80 no cartão" to "casa.contas-domesticas",
            "gastei 95 em tarifa do banco no cartão" to "servicos.tarifas-e-juros",
            "anuidade do banco 210 no pix" to null,
        )
        for ((text, category) in cases) {
            val (_, old) = run(text, 15, category = category?.let(::ref))
            assertTrue(old is ChatDecision.Refuse, "perfil 15 recusa: $text -> $old")
            val (_, new) = run(text, 16, category = category?.let(::ref))
            assertTrue(new is ChatDecision.Register, "perfil 16 registra: $text -> $new")
            assertEquals(category, new.entry.categoryId, text)
        }
    }

    @Test
    fun `menção real a conta ou banco continua sendo tratada como conta`() {
        for (text in listOf("mercado 57 no pix da conta desconhecida", "mercado 80 no pix do bradesco", "recebi 500 na conta do Bradesco",
            "mercado 40 no débito da conta nova", "paguei 30 no banco")) {
            val (_, decision) = run(text, 16, category = ref("alimentacao.mercado"))
            assertFalse(decision is ChatDecision.Register, "$text deve continuar recusado ou perguntando: $decision")
        }
    }

    @Test
    fun `entrada de dinheiro fora de recebi e salario oferece renda e dispensa o modelo quando inequivoca`() {
        for (text in listOf("caiu 1.357,10 de bônus", "entrou 4.054,23 de freela anteontem", "dividendos de 540 hoje", "mesada de 215,50 na conta Itaú hoje",
            "rendimento do CDB 82,10", "depositaram 900 de comissão", "vendi o notebook usado por 1.200")) {
            val offered = GroundedCandidates.actions(text, ctx, 16) - "sem_acao"
            assertEquals(setOf("renda"), offered, text)
            val old = GroundedCandidates.actions(text, ctx, 15) - "sem_acao"
            assertEquals(setOf("despesa"), old, "perfil 15 não conhece: $text")
        }
        for (text in listOf("caiu 1.357,10 de bônus", "mesada de 215,50 na conta Itaú hoje", "dividendos de 540 hoje")) {
            val (model, decision) = run(text, 16, action = "sem_acao")
            assertTrue(model.calls.isEmpty(), "$text não deve chamar o modelo")
            assertEquals(Nature.Income, (decision as ChatDecision.Register).entry.nature, text)
        }
    }

    @Test
    fun `caiu ou entrou em gasto nao vira renda`() {
        for (text in listOf("caiu o vidro do carro, conserto 200 no pix", "mercado 52 no pix", "entrou no cinema 30 no cartão", "paguei o salário da diarista 900 no pix")) {
            assertFalse(GroundedCandidates.incomeOnly(text, ctx, 16), text)
        }
        assertFalse("renda" in GroundedCandidates.actions("quanto caiu de dividendos este mês?", ctx, 16), "pergunta não é renda")
    }

    @Test
    fun `por mes ao mes e a cada mes sao recorrencia no perfil 16 e consulta continua consulta`() {
        for (text in listOf("aluguel 531 por mês", "academia 129 ao mês", "plano de celular 79 a cada mês", "seguro do carro 300 por ano")) {
            assertEquals(setOf("assinatura_recorrente"), GroundedCandidates.actions(text, ctx, 16) - "sem_acao", text)
            assertEquals(setOf("despesa"), GroundedCandidates.actions(text, ctx, 15) - "sem_acao", "perfil 15 não conhece: $text")
            val (_, decision) = run(text, 16, action = "assinatura_recorrente")
            assertEquals(ChatDecision.Reply(StandardReply.UnsupportedRecurrence), decision, text)
        }
        assertEquals(setOf("consulta"), GroundedCandidates.actions("quanto gasto por mês com mercado?", ctx, 16) - "sem_acao")
    }

    @Test
    fun `bateria de marcas continua perfeita no perfil 16`() {
        BrandBattery.cases.forEach { case ->
            val expected = case.expected as ChatBattery.Expected.Registers
            val (model, decision) = run(case.text, 16, if (expected.nature == Nature.Income) "renda" else "despesa", expected.categoryId?.let(::ref))
            assertEquals(ChatBattery.Outcome.Match, ChatGrading.grade(case, decision).outcome, "${case.id}: $decision (${model.calls.size} chamadas)")
        }
    }
}
