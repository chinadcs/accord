package app.accord.core.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Perfil 19: sem candidata no vocabulário, o modelo escolhe entre todas as categorias, mas a escolha é só sugestão a confirmar. */
class RoutedVersion19Test {
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    private class Spy(val category: String?) : StepModel {
        val grammars = mutableListOf<String>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            grammars += grammar
            return StepOutput(if ("sem_acao" in grammar) "\"despesa\"" else category?.let { JsonPrimitive(it).toString() } ?: "null")
        }
    }

    private fun ref(id: String) = ctx.categories.single { it.id == id }.ref

    private fun run(text: String, version: Int, answer: String?): Pair<Spy, ChatDecision> {
        val spy = Spy(answer)
        val raw = RoutedInterpreter(spy, PromptStyle.ChatMlNoThink, RoutedOptions(version)).interpret(InterpretRequest(text, ctx)).raw
        return spy to ChatValidator.evaluate(text, raw, ctx)
    }

    private fun categoryOf(d: ChatDecision) = (d as ChatDecision.Register).entry.categoryId

    private fun hintsOf(d: ChatDecision) = (d as ChatDecision.Register).categoryHints

    @Test
    fun `palavra fora do vocabulario vai ao modelo com todas as categorias so no perfil 19`() {
        val text = "Zaffari 120 no pix"
        val (v18, d18) = run(text, 18, ref("alimentacao.mercado"))
        assertEquals(1, v18.grammars.size, "perfil 18 desiste sem chamar o modelo")
        assertEquals(null, categoryOf(d18))
        val (v19, d19) = run(text, 19, ref("alimentacao.mercado"))
        assertEquals(2, v19.grammars.size, "perfil 19 pergunta ao modelo")
        val offered = ctx.categories.count { "\\\"${it.ref}\\\"" in v19.grammars[1] || "\"${it.ref}\"" in v19.grammars[1] }
        assertEquals(ctx.categories.size, offered, "a gramática oferece todas as categorias")
        assertTrue(v19.grammars[1].contains("null"), "e permite null")
        assertEquals(null, categoryOf(d19), "a escolha do modelo não vira categoria do lançamento")
        assertEquals(listOf("alimentacao.mercado"), hintsOf(d19), "ela vai como sugestão da pergunta de categoria")
        assertEquals(emptyList(), hintsOf(d18))
    }

    @Test
    fun `resposta null do modelo continua sem categoria e a resposta dele e a que vale`() {
        val none = run("Zaffari 120 no pix", 19, null).second
        assertEquals(null, categoryOf(none))
        assertEquals(emptyList(), hintsOf(none), "null do modelo não gera sugestão")
        // O modelo pode sugerir uma categoria errada: ela só aparece como opção e nunca é aplicada sem o casal aceitar.
        val wrong = run("Zaffari 120 no pix", 19, ref("lazer.eventos")).second
        assertEquals(null, categoryOf(wrong))
        assertEquals(listOf("lazer.eventos"), hintsOf(wrong))
    }

    @Test
    fun `vocabulario conhecido e renda seguem como no perfil 18`() {
        for (text in listOf("camiseta 65 no cartão", "mercado 52 no pix", "pão 8,50 no pix")) {
            assertEquals(run(text, 18, ref("compras.roupas")).first.grammars.size, run(text, 19, ref("compras.roupas")).first.grammars.size, text)
        }
        assertTrue(run("freela 450 dia 15", 19, null).first.grammars.isEmpty(), "renda inequívoca continua sem modelo")
    }

    @Test
    fun `a sugestao viaja como proposta clarify no contrato estrito`() {
        val raw = RoutedInterpreter(Spy(ref("alimentacao.mercado")), PromptStyle.ChatMlNoThink, RoutedOptions(19))
            .interpret(InterpretRequest("Zaffari 120 no pix", ctx)).raw
        val proposal = (ProposalCodec.decode(raw) as ProposalParse.Ok).proposal as Proposal.Clarify
        assertEquals(QuestionField.Category, proposal.field)
        assertEquals(listOf(ref("alimentacao.mercado")), proposal.options)
        assertEquals(null, proposal.entry?.category, "o lançamento dentro da proposta não leva categoria")
    }

    /** Responde sempre a primeira categoria que a gramática ainda oferece, para provar que as já escolhidas saem das próximas. */
    private class FirstOffered : StepModel {
        val grammars = mutableListOf<String>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            grammars += grammar
            if ("sem_acao" in grammar) return StepOutput("\"despesa\"")
            val first = Regex("\"\\\\\"([a-z0-9_]+)\\\\\"\"").find(grammar.substringAfter("::=")) ?: Regex("\"([a-z0-9_]+)\"").find(grammar.substringAfter("::="))
            return StepOutput("\"" + first!!.groupValues[1] + "\"")
        }
    }

    @Test
    fun `varias sugestoes excluem as ja escolhidas e continuam sendo so opcoes`() {
        val model = FirstOffered()
        val raw = RoutedInterpreter(model, PromptStyle.ChatMlNoThink, RoutedOptions(19, suggestions = 3))
            .interpret(InterpretRequest("Zaffari 120 no pix", ctx)).raw
        val proposal = (ProposalCodec.decode(raw) as ProposalParse.Ok).proposal as Proposal.Clarify
        assertEquals(3, proposal.options.size)
        assertEquals(3, proposal.options.toSet().size, "sem repetir")
        assertEquals(4, model.grammars.size, "ação + três escolhas de categoria")
        assertTrue(model.grammars.drop(2).none { "null" in it.substringAfter("::=") }, "as escolhas extras não oferecem null")
        val decision = ChatValidator.evaluate("Zaffari 120 no pix", raw, ctx) as ChatDecision.Register
        assertEquals(null, decision.entry.categoryId)
        assertEquals(proposal.options.map { ref -> ctx.category(ref)!!.id }, decision.categoryHints)
    }
}
