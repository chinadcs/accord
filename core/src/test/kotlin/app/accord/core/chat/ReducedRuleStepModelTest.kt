package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ReducedRuleStepModelTest {
    @Test fun `description baseline uses length then first literal position without oracle`() {
        val input = "Task: describe\nInput: " + """{"text":"copo verde e prato azul","descriptions":{"d0":"prato azul","d1":"copo verde","d2":"copo"}}"""
        assertEquals("\"d1\"", ReducedRuleStepModel(true).complete("", input, "").text)
        assertEquals("null", ReducedRuleStepModel(false).complete("", input, "").text)
    }

    @Test fun `historical first-action ablation remains available`() {
        val request = InterpretRequest("sabão líquido 22 no pix", ReducedBattery.context())
        val result = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMl, profileVersion=3).interpret(request)
        assertEquals(2, result.steps.size)
        assertEquals(Proposal.Register(Nature.Expense, "sabão líquido", "22", payment=ChatPayment.Pix),
            (ProposalCodec.decode(result.raw) as ProposalParse.Ok).proposal)
    }
}
