package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducedInterpreterTest {
    private val context = ChatBattery.context()
    private class Script(vararg outputs: String) : StepModel {
        private val replies = outputs.toList().iterator()
        val grammars = mutableListOf<String>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            grammars += grammar
            return StepOutput(replies.next())
        }
    }
    @Test fun `binds a description after the number without any category inference`() {
        val model = Script("\"despesa\"", """{"action":"register","description":"café","amount":"35,90","payment":"pix","date":{"kind":"yesterday","text":"ontem"}}""")
        val result = ReducedInterpreter(model, PromptStyle.ChatMl).interpret(InterpretRequest("paguei 35,90 no café ontem no pix", context))
        val decision = ChatValidator.evaluate("paguei 35,90 no café ontem no pix", result.raw, context) as ChatDecision.Register
        assertEquals(3590, decision.entry.amount.cents)
        assertEquals("café", decision.entry.description)
        assertEquals(null, decision.entry.categoryId)
        assertEquals(2, result.steps.size)
        assertTrue(model.grammars.last().startsWith("root ::= register-expense\n"))
        assertFalse(model.grammars.last().contains(",\\\"category\\\":"))
    }
    @Test fun `unresolved named account and multiple amounts never fall back to default or first value`() {
        for (text in listOf("paguei 50 no pix da conta Bradesco", "café 20 e bolo 30 no pix")) {
            val model = Script("\"despesa\"")
            val result = ReducedInterpreter(model, PromptStyle.ChatMl).interpret(InterpretRequest(text, context))
            assertEquals(Proposal.Uninterpretable, (ProposalCodec.decode(result.raw) as ProposalParse.Ok).proposal)
            assertEquals(1, result.steps.size)
        }
    }
    @Test fun `second stage cannot change nature or correction field`() {
        val model = Script("\"corrigir_valor\"", """{"action":"correct","target":"r1","field":"payment","payment":"pix"}""")
        val result = ReducedInterpreter(model, PromptStyle.ChatMl).interpret(InterpretRequest("corrige o valor para 19", context))
        assertEquals("binding_action", result.guard)
    }
    @Test fun `engine errors propagate and long input is never silently truncated`() {
        val broken = ReducedInterpreter(StepModel { _, _, _ -> throw InterpreterFailure(InterpreterFailure.Kind.Memory) }, PromptStyle.ChatMl)
        assertFailsWith<InterpreterFailure> { broken.interpret(InterpretRequest("café 20 no pix", context)) }
        assertEquals("input", broken.interpret(InterpretRequest("x".repeat(601), context)).guard)
    }
    @Test fun `query does not bind financial fields`() {
        val model = Script("\"consulta\"")
        val result = ReducedInterpreter(model, PromptStyle.ChatMl).interpret(InterpretRequest("quanto gastei?", context))
        assertEquals(1, result.steps.size)
        assertEquals(Proposal.Query, (ProposalCodec.decode(result.raw) as ProposalParse.Ok).proposal)
    }
    @Test fun `engine argument exceptions and cancellation are not mistaken for invalid JSON`() {
        for (error in listOf(IllegalArgumentException("engine"), java.util.concurrent.CancellationException("stop"))) {
            val interpreter = ReducedInterpreter(StepModel { _, _, _ -> throw error }, PromptStyle.ChatMl)
            val caught = assertFailsWith<Exception> { interpreter.interpret(InterpretRequest("café 20 no pix",context)) }
            assertTrue(caught === error)
        }
    }
}
