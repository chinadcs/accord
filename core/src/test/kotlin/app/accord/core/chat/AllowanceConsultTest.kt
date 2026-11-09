package app.accord.core.chat

import app.accord.core.analysis.ProductPhase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AllowanceConsultTest {
    private val context = ChatBattery.context().copy(phase = ProductPhase.P1)

    @Test fun `interpretador codec e validador reconhecem so as duas consultas fechadas`() = runBlocking<Unit> {
        for ((text, type) in listOf("quanto posso gastar hoje?" to ConsultType.TodayAllowance, "quanto posso gastar esta semana?" to ConsultType.WeekAllowance)) {
            val raw = RuleInterpreter().interpret(InterpretRequest(text, context))
            val decision = assertIs<ChatDecision.Consult>(ChatValidator.evaluate(text, raw, context))
            assertEquals(type, decision.request.type)
            val proposal = Proposal.Consult(type)
            assertEquals(proposal, assertIs<ProposalParse.Ok>(ProposalCodec.decode(ProposalCodec.encode(proposal))).proposal)
            assertIs<ChatDecision.Reply>(ChatValidator.evaluate(text, proposal, context.copy(phase = ProductPhase.P0)))
        }
    }

    @Test fun `saida forjada com periodo categoria conselho negacao ou registro misto nao executa consulta`() {
        val p = Proposal.Consult(ConsultType.TodayAllowance)
        for (text in listOf("posso comprar um celular hoje?", "não quero saber quanto posso gastar hoje", "gastei 50, quanto posso gastar hoje?", "quanto posso gastar amanhã?")) {
            assertNull(ConsultReader.allowanceType(text))
            assertFalse(ChatValidator.evaluate(text, p, context) is ChatDecision.Consult)
        }
        assertFalse(ChatValidator.evaluate("quanto posso gastar hoje?", p.copy(month = "hoje"), context) is ChatDecision.Consult)
        assertFalse(ChatValidator.evaluate("quanto posso gastar hoje?", p.copy(category = "c1"), context) is ChatDecision.Consult)
        assertFalse(ChatValidator.evaluate("quanto posso gastar hoje?", Proposal.Consult(ConsultType.WeekAllowance), context) is ChatDecision.Consult)
    }
}
