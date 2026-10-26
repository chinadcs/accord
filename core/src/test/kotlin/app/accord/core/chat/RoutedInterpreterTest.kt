package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** O perfil em etapas roda no PC e no celular; aqui só a porta [StepModel], sem motor. */
class RoutedInterpreterTest {
    private class Recorder(private val reply: (String) -> String) : StepModel {
        val grammars = mutableListOf<String>()
        val prompts = mutableListOf<Pair<String, String>>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            prompts += prefix to suffix
            grammars += grammar
            return StepOutput(reply(grammar), detail = grammars.size)
        }
    }

    private val context = SemanticRefs.apply(ChatBattery.realisticContext())

    @Test
    fun `perfil 14 pede a acao com espaco inicial e devolve cada etapa na ordem`() {
        val model = Recorder { "\"consulta\"" }
        val result = RoutedInterpreter(model, PromptStyle.ChatMlNoThink, RoutedOptions(RoutedInterpreter.VERSION))
            .interpret(InterpretRequest("quanto gastei em mercado?", context))
        assertTrue(model.grammars.isNotEmpty(), "a primeira etapa classifica a ação")
        assertTrue(model.grammars.first().startsWith("root ::= \" \" ("), "versões 6+ exigem o espaço inicial na gramática")
        assertEquals(model.grammars.size, result.steps.size)
        assertEquals(result.steps.indices.map { it + 1 }, result.steps.map { it.detail }, "o detalhe do motor acompanha a etapa")
        assertTrue(result.raw.isNotBlank())
    }

    @Test
    fun `falha do motor numa etapa interrompe a mensagem sem inventar proposta`() {
        val broken = StepModel { _, _, _ -> throw InterpreterFailure(InterpreterFailure.Kind.Memory) }
        val interpreter = RoutedInterpreter(broken, PromptStyle.ChatMlNoThink, RoutedOptions(RoutedInterpreter.VERSION))
        assertEquals(
            InterpreterFailure.Kind.Memory,
            assertFailsWith<InterpreterFailure> { interpreter.interpret(InterpretRequest("café 35 no pix", context)) }.kind,
        )
    }

    @Test
    fun `resultado de uma mensagem nao herda etapas da anterior`() {
        val model = Recorder { "\"consulta\"" }
        val interpreter = RoutedInterpreter(model, PromptStyle.ChatMlNoThink, RoutedOptions(RoutedInterpreter.VERSION))
        val first = interpreter.interpret(InterpretRequest("quanto gastei em mercado?", context)).steps.size
        val second = interpreter.interpret(InterpretRequest("quanto gastei em mercado?", context)).steps.size
        assertEquals(first, second)
    }
}
