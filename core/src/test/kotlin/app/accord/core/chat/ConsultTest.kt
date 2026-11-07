package app.accord.core.chat

import app.accord.core.analysis.ProductPhase
import app.accord.core.space.DateCriterion
import kotlinx.coroutines.runBlocking
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S6, cenários 15 e 17: catálogo fechado, validação, leitura por regras e o limite entre registrar e consultar. */
class ConsultTest {
    private val p0 = ChatBattery.context()
    private val p1 = p0.copy(phase = ProductPhase.P1)

    private fun decide(text: String, proposal: Proposal, context: ChatContext = p1) = ChatValidator.evaluate(text, proposal, context)

    private fun consulted(decision: ChatDecision) = assertIs<ChatDecision.Consult>(decision).request

    // ---- validador ----

    @Test
    fun `gasto de uma categoria em um mes vira consulta normalizada pelo app`() {
        val request = consulted(
            decide(
                "quanto gastei com alimentação em outubro?",
                Proposal.Consult(ConsultType.CategorySpend, month = "outubro", category = "c1"),
            ),
        )
        assertEquals(YearMonth.of(2026, 10), request.month)
        assertEquals("alimentacao", request.categoryId)
        assertEquals(DateCriterion.BudgetMonth, request.criterion)
        assertTrue(request.criterionAssumed && !request.periodAssumed)
    }

    @Test
    fun `sem periodo assume o mes corrente e diz que assumiu`() {
        val request = consulted(decide("quanto gastei com alimentação?", Proposal.Consult(ConsultType.CategorySpend, category = "c1")))
        assertEquals(YearMonth.from(p1.today), request.month)
        assertTrue(request.periodAssumed)
    }

    @Test
    fun `criterio de data da compra so vale quando a mensagem o pede`() {
        val request = consulted(
            decide(
                "quanto comprei de alimentação em setembro pela data da compra?",
                Proposal.Consult(ConsultType.CategorySpend, month = "setembro", criterion = ConsultCriterion.PurchaseDate, category = "c1"),
            ),
        )
        assertEquals(DateCriterion.PurchaseDate, request.criterion)
        assertTrue(!request.criterionAssumed)
    }

    @Test
    fun `fora do P1 o validador recusa a consulta com orientacao e sem numero`() {
        val decision = decide("quanto gastei com alimentação?", Proposal.Consult(ConsultType.CategorySpend, category = "c1"), p0)
        assertEquals(ChatDecision.Reply(StandardReply.ConsultNeedsP1), decision)
    }

    @Test
    fun `categoria inexistente pergunta com opcoes e nunca devolve numero`() {
        val decision = decide("quanto gastei com viagens?", Proposal.Consult(ConsultType.CategorySpend, category = "c99"))
        val ask = assertIs<ChatDecision.ConsultAsk>(decision).ask
        assertEquals(ConsultAskNotice.Reason.UnknownCategory, ask.reason)
        assertTrue(ask.options.isNotEmpty() && ask.options.all { it.text.startsWith("quanto gastei com ") })
    }

    @Test
    fun `categoria ambigua oferece so os candidatos`() {
        val decision = decide("quanto gastei com compras?", Proposal.Consult(ConsultType.CategorySpend, category = "c4,c7"))
        val ask = assertIs<ChatDecision.ConsultAsk>(decision).ask
        assertEquals(ConsultAskNotice.Reason.AmbiguousCategory, ask.reason)
        assertEquals(listOf("Compras", "Eletrônicos"), ask.options.map { it.label })
    }

    @Test
    fun `gasto sem categoria pergunta qual`() {
        val ask = assertIs<ChatDecision.ConsultAsk>(decide("quanto gastei este mês?", Proposal.Consult(ConsultType.CategorySpend, month = "este mês"))).ask
        assertEquals(ConsultAskNotice.Reason.MissingCategory, ask.reason)
    }

    @Test
    fun `periodo que nao e mes calendario e recusado sem numero`() {
        val decision = decide("quanto gastei com alimentação na semana passada?", Proposal.Consult(ConsultType.CategorySpend, month = "semana passada", category = "c1"))
        assertEquals(ChatDecision.Reply(StandardReply.ConsultOutOfCatalog), decision)
    }

    @Test
    fun `trecho que nao esta na mensagem e recusado`() {
        val decision = decide("quanto gastei com alimentação?", Proposal.Consult(ConsultType.CategorySpend, month = "novembro", category = "c1"))
        assertEquals(ChatDecision.Refuse(RefuseReason.Unanchored), decision)
    }

    @Test
    fun `parametros incompativeis com o tipo sao recusados`() {
        val decision = decide("parcelas do notebook", Proposal.Consult(ConsultType.PurchaseSchedule, target = "notebook", category = "c1"))
        assertEquals(ChatDecision.Reply(StandardReply.ConsultOutOfCatalog), decision)
        val commitments = decide("o que vence este mês?", Proposal.Consult(ConsultType.MonthCommitments, month = "este mês", criterion = ConsultCriterion.Budget))
        assertEquals(ChatDecision.Reply(StandardReply.ConsultOutOfCatalog), commitments)
    }

    @Test
    fun `cronograma sem compra nomeada pede a compra`() {
        val ask = assertIs<ChatDecision.ConsultAsk>(decide("quanto falta?", Proposal.Consult(ConsultType.PurchaseSchedule))).ask
        assertEquals(ConsultAskNotice.Reason.MissingPurchase, ask.reason)
    }

    // ---- codec: catálogo fechado, campo extra, SQL ----

    @Test
    fun `tipo fora do catalogo, campo extra e SQL sao saida invalida`() {
        listOf(
            """{"action":"consult","type":"drop_table"}""",
            """{"action":"consult","type":"category_spend","sql":"select * from lancamento"}""",
            """{"action":"consult","type":"category_spend","category":"c1; drop table entry"}""".replace("c1; drop table entry", "x".repeat(81)),
            """{"action":"consult"}""",
            """{"action":"consult","type":"category_spend","month":7}""",
        ).forEach { raw ->
            assertTrue(ProposalCodec.decode(raw) is ProposalParse.Invalid, raw)
            assertEquals(ChatDecision.Refuse(RefuseReason.BadFormat), ChatValidator.evaluate("qualquer", raw, p1), raw)
        }
    }

    @Test
    fun `a proposta de consulta faz ida e volta no contrato`() {
        val proposal = Proposal.Consult(
            ConsultType.PurchaseSchedule, target = "equipamento", date = DateExpression(DateKind.Explicit, "25/09/2026"),
        )
        assertEquals(ProposalParse.Ok(proposal), ProposalCodec.decode(ProposalCodec.encode(proposal)))
    }

    // ---- leitor por regras ----

    private fun read(text: String) = ConsultReader.read(text, p1)

    @Test
    fun `le gasto de categoria com mes citado`() {
        assertEquals(
            Proposal.Consult(ConsultType.CategorySpend, month = "outubro", category = "c1"),
            read("quanto gastei com alimentação em outubro?"),
        )
        assertEquals("c1", read("quanto gastei com alimentação este mês?")?.category)
    }

    @Test
    fun `subcategoria vale mais que a categoria principal citada junto`() {
        assertEquals("c2", read("quanto gastei com delivery de alimentação este mês?")?.category)
    }

    @Test
    fun `le o cronograma de uma compra e o mes de compromissos`() {
        val schedule = read("quanto falta do equipamento?")
        assertEquals(ConsultType.PurchaseSchedule, schedule?.type)
        assertEquals("equipamento", schedule?.target)
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments, month = "mes que vem"), read("como estão as parcelas do mês que vem?"))
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments), read("quanto vou pagar?"))
    }

    @Test
    fun `periodo que nao e mes vira trecho que o validador recusa`() {
        assertEquals("semana passada", read("quanto gastei com alimentação na semana passada?")?.month)
    }

    @Test
    fun `comparacao, numero solto e frase fora do catalogo nao viram consulta`() {
        assertNull(read("gastei mais com alimentação do que no mês passado?"))
        assertNull(read("gastei 50 no mercado, quanto sobrou?"))
        assertNull(read("qual o melhor cartão?"))
        assertNull(read("mercado 50"))
    }

    // ---- interpretador por regras ----

    @Test
    fun `o interpretador por regras so emite consulta no P1`() = runBlocking<Unit> {
        val interpreter = RuleInterpreter()
        val text = "quanto gastei com alimentação em outubro?"
        val raw1 = interpreter.interpret(InterpretRequest(text, p1))
        assertIs<Proposal.Consult>((ProposalCodec.decode(raw1) as ProposalParse.Ok).proposal)
        val raw0 = interpreter.interpret(InterpretRequest(text, p0))
        assertEquals(Proposal.Query, (ProposalCodec.decode(raw0) as ProposalParse.Ok).proposal)
    }

    @Test
    fun `registrar nunca vira consulta nem a pergunta vira registro`() = runBlocking<Unit> {
        val interpreter = RuleInterpreter()
        for (text in listOf("mercado 50 no pix", "gastei 30 com delivery ontem", "paguei 120 de luz")) {
            val raw = interpreter.interpret(InterpretRequest(text, p1))
            assertTrue((ProposalCodec.decode(raw) as ProposalParse.Ok).proposal !is Proposal.Consult, text)
        }
        for (text in listOf("quanto gastei com alimentação em outubro?", "quanto vou pagar mês que vem?")) {
            val raw = interpreter.interpret(InterpretRequest(text, p1))
            val decision = ChatValidator.evaluate(text, raw, p1)
            assertTrue(decision !is ChatDecision.Register && decision !is ChatDecision.Ask, text)
        }
    }
}
