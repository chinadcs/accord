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

    // ---- mensagem mista (decisão 9) ----

    @Test
    fun `registro com pergunta na mesma mensagem vira esclarecimento e nao grava`() = runBlocking<Unit> {
        val interpreter = RuleInterpreter()
        for (text in listOf("gastei 50 no mercado, quanto sobrou?", "recebi 1000, quanto sobra?", "paguei 30 de lanche e quanto gastei com alimentação este mês?", "mercado 50 no pix. Qual o total?")) {
            val raw = interpreter.interpret(InterpretRequest(text, p1))
            assertEquals(ChatDecision.Reply(StandardReply.MixedRequest), ChatValidator.evaluate(text, raw, p1), text)
        }
    }

    @Test
    fun `o esclarecimento tambem vale para propostas forjadas e so na versao 22`() {
        val text = "recebi 1000, quanto sobra?"
        val forged = Proposal.Register(nature = Nature.Income, description = "salario", amount = "1000")
        assertEquals(ChatDecision.Reply(StandardReply.MixedRequest), ChatValidator.evaluate(text, forged, p1))
        assertTrue(ChatValidator.evaluate(text, forged, p1, version = 21) !is ChatDecision.Reply)
    }

    @Test
    fun `palavra interrogativa dentro da descricao nao e pergunta`() {
        for (text in listOf("mercado 50 no pix", "paguei 50 por quanto tempo", "gastei 30, falta 20", "comprei 3 por 20 cada")) {
            assertTrue(!ChatText.hasQuestionClause(text), text)
        }
        assertTrue(ChatText.hasQuestionClause("gastei 50 no mercado, quanto sobrou?"))
    }

    // ---- formas de perguntar vistas nas frases do casal (accord-a8kf) ----

    @Test
    fun `le parcelas e compromissos em formas sem quanto`() {
        assertEquals(ConsultType.PurchaseSchedule, read("parcelas do celular")?.type)
        assertEquals("celular", read("parcelas do celular")?.target)
        assertEquals("sofa", read("quando termina o sofá")?.target)
        assertEquals("notebook", read("quantas parcelas do notebook ainda faltam")?.target)
        assertEquals(ConsultType.PurchaseSchedule, read("qual a última parcela da geladeira")?.type)
        assertEquals(ConsultType.PurchaseSchedule, read("quanto ficou cada parcela do notebook")?.type)
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments, month = "dezembro"), read("como estão os compromissos de dezembro"))
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments), read("compromissos do mês"))
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments, month = "mes que vem"), read("quais as contas do mês que vem"))
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments, month = "esse mes"), read("o que tenho pra pagar esse mês"))
        assertEquals(Proposal.Consult(ConsultType.MonthCommitments, month = "dezembro"), read("quanto tenho de compromisso em dezembro"))
    }

    @Test
    fun `gasto sem a palavra quanto e fatura com pesar`() {
        assertEquals(ConsultType.CategorySpend, read("gasto com lazer em outubro")?.type)
        val heavy = read("quanto vai pesar na fatura de novembro de lazer")
        assertEquals(ConsultType.CategorySpend, heavy?.type)
        assertEquals(ConsultCriterion.Budget, heavy?.criterion)
    }

    @Test
    fun `intencao e conselho nao viram consulta`() {
        assertNull(read("devo comprar notebook agora"))
        assertNull(read("vale a pena trocar o celular"))
        assertNull(read("quero comprar uma tv"))
    }

    @Test
    fun `pergunta sem numero lida como registro vira consulta ou orientacao, e registro continua registro`() = runBlocking<Unit> {
        val interpreter = RuleInterpreter()
        suspend fun proposal(text: String) = (ProposalCodec.decode(interpreter.interpret(InterpretRequest(text, p1))) as ProposalParse.Ok).proposal
        assertEquals(ConsultType.CategorySpend, assertIs<Proposal.Consult>(proposal("gastei quanto de mercado em setembro")).type)
        assertEquals(Proposal.Query, proposal("gastei mais com mercado do que mês passado"))
        assertEquals(Proposal.Query, proposal("em qual estabelecimento eu mais gastei"))
        // Registro de verdade, com ou sem valor, não pode virar consulta nem orientação.
        for (text in listOf("gastei com mercado", "gastei 50 no mercado", "paguei a luz", "mercado 50 no pix")) {
            val p = proposal(text)
            assertTrue(p !is Proposal.Consult && p != Proposal.Query, "$text -> $p")
        }
    }

    @Test
    fun `no app o id da categoria e um UUID e o vocabulario ainda acha a categoria`() {
        val uuid = mapOf("alimentacao.delivery" to "u-2", "alimentacao" to "u-1", "transporte" to "u-8", "transporte.aplicativos" to "u-9")
        val categories = listOf(
            CategoryItem("c1", "u-1", "Alimentação"), CategoryItem("c2", "u-2", "Delivery", "u-1"),
            CategoryItem("c3", "u-8", "Transporte"), CategoryItem("c4", "u-9", "Aplicativos", "u-8"),
        )
        val context = p1.copy(categories = categories, seedKeys = uuid.entries.associate { (key, id) -> id to key })
        assertEquals("c2", ConsultReader.read("quanto foi de ifood mês passado", context)?.category)
        assertEquals("c4", ConsultReader.read("quanto foi de uber esse mês", context)?.category)
        // Sem a tradução (contexto sem seedKeys), o mesmo texto não acha categoria e vira pergunta de complemento.
        assertNull(ConsultReader.read("quanto foi de ifood mês passado", context.copy(seedKeys = emptyMap()))?.category)
    }
}
