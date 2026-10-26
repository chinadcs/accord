package app.accord.core.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Perfil 18: banco citado depois do meio pergunta a conta e "conta da X" é conta a pagar de qualquer empresa. */
class RoutedVersion18Test {
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    private class Scripted(val category: String?) : StepModel {
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput =
            StepOutput(if ("sem_acao" in grammar) "\"despesa\"" else category?.let { JsonPrimitive(it).toString() } ?: "null")
    }

    private fun decide(text: String, version: Int, category: String? = null): ChatDecision {
        val raw = RoutedInterpreter(Scripted(category?.let { id -> ctx.categories.single { it.id == id }.ref }), PromptStyle.ChatMlNoThink, RoutedOptions(version))
            .interpret(InterpretRequest(text, ctx)).raw
        return ChatValidator.evaluate(text, raw, ctx)
    }

    private fun asksAccount(d: ChatDecision) = d is ChatDecision.Ask && d.question.field == QuestionField.Account

    @Test
    fun `banco novo logo depois do meio pergunta a conta no perfil 18 e era registrado em silencio no 17`() {
        for (text in listOf("gasolina 22,80 no pix Neon", "lanche 287 no débito Agibank", "consulta médica 48,10 no pix Agibank", "ração 71 no pix Daycoval",
            "restaurante 127 no pix Rico", "camiseta 308,86 no débito Modal", "mercado 121 no débito BS2", "mercado 40 no pix Banco Inter",
            "farmácia 30 no pix PagBank", "uber 18 no pix da conta Sofisa", "lanche 12 no débito Zeta", "lanche 12 no pix Zeta hoje")) {
            assertTrue(decide(text, 17) is ChatDecision.Register || decide(text, 17) is ChatDecision.Ask, "perfil 17: $text")
            assertTrue(asksAccount(decide(text, 18)), "perfil 18 pergunta a conta: $text -> ${decide(text, 18)}")
        }
        // Nome próprio seguido de valor é loja, não banco.
        for (text in listOf("pelo pix Hering 74,80", "no débito Riachuelo 90", "pix Zaffari 120")) {
            assertTrue(decide(text, 18) is ChatDecision.Register, "$text -> ${decide(text, 18)}")
        }
        // Os que o perfil 17 registrava sem avisar:
        for (text in listOf("gasolina 22,80 no pix Neon", "lanche 287 no débito Agibank", "ração 71 no pix Daycoval")) {
            assertTrue(decide(text, 17) is ChatDecision.Register, "perfil 17 registrava sem avisar: $text")
        }
    }

    @Test
    fun `conta conhecida, palavra parecida com banco e data depois do meio nao perguntam a conta`() {
        for (text in listOf("mercado 80 no pix Itaú", "mercado 80 no débito Nubank", "mercado 12 no pix hoje", "mercado 12 no pix ontem", "mercado 52 no pix",
            "mercado 52 no cartão Nubank")) {
            assertTrue(decide(text, 18, "alimentacao.mercado") is ChatDecision.Register, "$text -> ${decide(text, 18, "alimentacao.mercado")}")
        }
        // Palavras que são banco só fora do contexto de pagamento: seguem como descrição.
        assertTrue(decide("caixa de som 200 no pix", 18) is ChatDecision.Register)
        assertTrue(decide("camiseta original 80 no pix", 18) is ChatDecision.Register)
    }

    @Test
    fun `conta da empresa qualquer registra como conta a pagar e conta de banco continua sendo conta`() {
        for (text in listOf("conta da Equatorial 150 no pix", "conta da Celpe 98,40 no cartão", "paguei a conta do bar 80 no pix", "conta da Copasa 45 em dinheiro",
            "conta de luz 512 via pix", "conta da Sabesp 74,70 em dinheiro")) {
            val decision = decide(text, 18, "casa.contas-domesticas")
            assertTrue(decision is ChatDecision.Register, "$text -> $decision")
            // Com cartão ou dinheiro a conta nem importa; com pix o perfil 17 perguntava a conta por engano.
            if (text.contains("Equatorial") || text.contains("do bar")) {
                assertTrue(decide(text, 17, "casa.contas-domesticas") !is ChatDecision.Register, "perfil 17 não registrava: $text")
            }
        }
        for (text in listOf("recebi 500 na conta do Bradesco", "mercado 40 no pix da conta do Neon", "mercado 40 no débito da conta nova")) {
            assertTrue(decide(text, 18, "alimentacao.mercado") !is ChatDecision.Register, "continua conta: $text")
        }
    }

    @Test
    fun `os dois digitos de banco nao viram valor`() {
        val bs2 = decide("mercado 121 no débito BS2", 18, "alimentacao.mercado")
        assertTrue(asksAccount(bs2), "BS2: $bs2")
        val c6 = decide("mercado 121 no pix do C6", 18, "alimentacao.mercado")
        assertTrue(asksAccount(c6), "C6: $c6")
    }
}
