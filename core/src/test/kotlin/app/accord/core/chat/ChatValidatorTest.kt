package app.accord.core.chat

import app.accord.core.Money
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatValidatorTest {
    private val ctx = ChatBattery.context()

    private fun reg(
        description: String? = null,
        amount: String? = null,
        role: AmountRole? = null,
        installments: String? = null,
        date: DateExpression? = null,
        category: String? = null,
        payment: ChatPayment? = null,
        account: String? = null,
        nature: Nature? = null,
    ) = Proposal.Register(nature, description, amount, role, installments, date, category, payment, account)

    private fun decide(text: String, proposal: Proposal, context: ChatContext = ctx) = ChatValidator.evaluate(text, proposal, context)

    private fun registered(decision: ChatDecision) = assertIs<ChatDecision.Register>(decision).entry

    @Test
    fun `a bateria ficticia inteira bate com o gabarito usando a saida ideal`() = runBlocking<Unit> {
        val results = ChatBattery.run(ChatBattery.idealInterpreter())
        val failures = results.filter { it.outcome != ChatBattery.Outcome.Match }
        assertTrue(failures.isEmpty(), failures.joinToString { "${it.case.id}: ${it.decision}" })
        assertEquals(ChatBattery.cases.size, results.size)
        assertEquals(ChatBattery.cases.size, ChatBattery.cases.map { it.text }.toSet().size, "textos repetidos escondem casos na tabela do fake")
    }

    @Test
    fun `proposta aceita porem errada e medida como AcceptedWrong`() = runBlocking<Unit> {
        // O modelo presume Eletrônicos pelo estabelecimento; o gabarito é "a classificar". O validador não prova que está errado.
        val fake = FakeInterpreter().on("amazon 150 no cartão", reg("amazon", "150", category = "c7", payment = ChatPayment.Card))
        val case = ChatBattery.cases.first { it.id == "categoria-2" }
        val decision = ChatValidator.evaluate(case.text, fake.interpret(InterpretRequest(case.text, ctx)), ctx)
        assertEquals(ChatBattery.Outcome.AcceptedWrong, ChatBattery.grade(case.expected, decision))
    }

    // ---- cenário 3: ancoragem ----

    @Test
    fun `trecho que nao existe no texto e recusado`() {
        val decision = decide("Amazon 150", reg("Amazon", "1500", payment = ChatPayment.Card))
        assertEquals(RefuseReason.Unanchored, assertIs<ChatDecision.Refuse>(decision).reason)
    }

    @Test
    fun `numero cru no lugar do trecho e recusado no formato`() {
        val decision = ChatValidator.evaluate("Amazon 150", """{"action":"register","amount":150}""", ctx)
        assertEquals(RefuseReason.BadFormat, assertIs<ChatDecision.Refuse>(decision).reason)
    }

    @Test
    fun `trecho numerico respeita as bordas do numero`() {
        assertTrue(ChatText.anchored("Amazon 150", "150"))
        assertTrue(!ChatText.anchored("Amazon 1500", "150"))
        assertTrue(!ChatText.anchored("Amazon 150,50", "150"))
        assertTrue(!ChatText.anchored("tv 1.200", "200"))
        assertTrue(ChatText.anchored("TV 1.200 em 6X", "1.200"))
    }

    // ---- cenário 4: leitura de valores ----

    @Test
    fun `leitura de valores em centavos`() {
        fun read(text: String) = ChatText.readAmount(text)
        assertEquals(AmountRead.Ok(Money(3500)), read("35"))
        assertEquals(AmountRead.Ok(Money(3590)), read("35,90"))
        assertEquals(AmountRead.Ok(Money(120_000)), read("1.200"))
        assertEquals(AmountRead.Ok(Money(120_050)), read("1.200,50"))
        assertEquals(AmountRead.Ok(Money(6800)), read("R$ 68,00"))
        assertEquals(AmountRead.Ambiguous, read("39.90"))
        assertEquals(AmountRead.Invalid, read("0"))
        assertEquals(AmountRead.Invalid, read("-5"))
        assertEquals(AmountRead.Invalid, read("trinta"))
    }

    @Test
    fun `valor ambiguo ou zero vira pergunta de valor e rascunho`() {
        for (amount in listOf("39.90", "0")) {
            val decision = decide("livro $amount no pix", reg("livro", amount, payment = ChatPayment.Pix))
            val ask = assertIs<ChatDecision.Ask>(decision)
            assertEquals(QuestionField.Amount, ask.question.field)
            assertNull(ask.draft.amount)
        }
    }

    // ---- cenário 5 e 6: parcelas ----

    @Test
    fun `X em Nx e total sem pergunta mesmo se o modelo disser parcela`() {
        val entry = registered(decide("equipamento 1.200 em 6x no cartão", reg("equipamento", "1.200", AmountRole.PerInstallment, "6x", payment = ChatPayment.Card)))
        assertEquals(Money(120_000), entry.amount)
        assertEquals(AmountRole.Total, entry.role)
        assertEquals(6, entry.installments)
    }

    @Test
    fun `N parcelas de X e parcela e a divergencia vira pergunta`() {
        val entry = registered(decide("tv 6 parcelas de 200 no cartão", reg("tv", "200", AmountRole.PerInstallment, "6 parcelas", payment = ChatPayment.Card)))
        assertEquals(AmountRole.PerInstallment, entry.role)
        val ask = assertIs<ChatDecision.Ask>(decide("tv 6 parcelas de 200 no cartão", reg("tv", "200", AmountRole.Total, "6 parcelas", payment = ChatPayment.Card)))
        assertEquals(QuestionField.AmountRole, ask.question.field)
        assertEquals(ChatQuestion.Reason.Conflict, ask.question.reason)
    }

    @Test
    fun `parcelas sem marcador perguntam o papel do valor`() {
        val ask = assertIs<ChatDecision.Ask>(decide("tv 1.200 6x no cartão", reg("tv", "1.200", AmountRole.Total, "6x", payment = ChatPayment.Card)))
        assertEquals(QuestionField.AmountRole, ask.question.field)
    }

    @Test
    fun `parcelamento fora do cartao e recusado`() {
        val decision = decide("tv 1.200 em 6x no pix", reg("tv", "1.200", installments = "6x", payment = ChatPayment.Pix))
        assertEquals(RefuseReason.InstallmentsNeedCard, assertIs<ChatDecision.Refuse>(decision).reason)
    }

    @Test
    fun `parcelas do marcador do texto valem mesmo se o modelo esquecer`() {
        val entry = registered(decide("fone 100 em 3x no cartão", reg("fone", "100", payment = ChatPayment.Card)))
        assertEquals(3, entry.installments)
        assertEquals(AmountRole.Total, entry.role)
    }

    // ---- cenários 7 e 7a: datas ----

    @Test
    fun `datas relativas usam o dia local de Sao Paulo`() {
        val today = LocalDate.of(2026, 10, 22)
        fun resolve(kind: DateKind, text: String, on: LocalDate = today) = ChatText.resolveDate(DateExpression(kind, text), on)
        assertEquals(DateRead.Ok(today), resolve(DateKind.Today, "hoje"))
        assertEquals(DateRead.Ok(LocalDate.of(2026, 10, 21)), resolve(DateKind.Yesterday, "ontem"))
        assertEquals(DateRead.Ok(LocalDate.of(2026, 10, 7)), resolve(DateKind.DayOfMonth, "dia 7"))
        assertEquals(DateRead.Ok(LocalDate.of(2026, 9, 25)), resolve(DateKind.DayOfMonth, "dia 25"))
        assertEquals(DateRead.Ok(today), resolve(DateKind.DayOfMonth, "dia 22"))
        assertEquals(DateRead.Invalid, resolve(DateKind.DayOfMonth, "dia 31"))
        assertEquals(DateRead.Invalid, resolve(DateKind.DayOfMonth, "dia 40"))
        assertEquals(DateRead.Ok(LocalDate.of(2026, 11, 22)), resolve(DateKind.NextMonth, "mês que vem"))
        assertEquals(DateRead.Ok(LocalDate.of(2027, 2, 28)), resolve(DateKind.NextMonth, "mês que vem", LocalDate.of(2027, 1, 31)))
        assertEquals(DateRead.Ok(LocalDate.of(2026, 10, 3)), resolve(DateKind.Explicit, "3/10"))
        assertEquals(DateRead.Ok(LocalDate.of(2025, 12, 25)), resolve(DateKind.Explicit, "25/12"))
        assertEquals(DateRead.Invalid, resolve(DateKind.Explicit, "31/02"))
    }

    @Test
    fun `sem data usa hoje sem pergunta e dia 31 sem o dia pergunta`() {
        val entry = registered(decide("mercado 50 no pix", reg("mercado", "50", payment = ChatPayment.Pix)))
        assertEquals(ctx.today, entry.date)
        val ask = assertIs<ChatDecision.Ask>(decide("mercado 50 dia 31 no pix", reg("mercado", "50", date = DateExpression(DateKind.DayOfMonth, "dia 31"), payment = ChatPayment.Pix)))
        assertEquals(QuestionField.Date, ask.question.field)
    }

    @Test
    fun `renda com mes que vem e prevista e despesa com data futura pergunta`() {
        val income = registered(decide("salário 6.000 mês que vem", reg("salário", "6.000", date = DateExpression(DateKind.NextMonth, "mês que vem"), nature = Nature.Income)))
        assertEquals(LocalDate.of(2026, 11, 22), income.date)
        assertEquals(Nature.Income, income.nature)
        assertNull(income.payment)
        val ask = assertIs<ChatDecision.Ask>(decide("mercado 50 mês que vem no pix", reg("mercado", "50", date = DateExpression(DateKind.NextMonth, "mês que vem"), payment = ChatPayment.Pix)))
        assertEquals(ChatQuestion.Reason.FutureExpense, ask.question.reason)
    }

    // ---- cenário 8 e 9: intenção, consulta e fora do Chat ----

    @Test
    fun `intencao e consulta so recebem resposta padrao`() {
        assertEquals(StandardReply.Intent, assertIs<ChatDecision.Reply>(decide("quero comprar uma TV de 1.200 em 6x", Proposal.Intent)).reply)
        assertEquals(StandardReply.Query, assertIs<ChatDecision.Reply>(decide("quanto gastei com delivery?", Proposal.Query)).reply)
    }

    @Test
    fun `marca de recorrencia impede registrar como despesa comum e leva o que foi entendido`() {
        val reply = assertIs<ChatDecision.Reply>(decide("netflix 39,90 todo mês", reg("netflix", "39,90", payment = null)))
        assertEquals(StandardReply.UnsupportedRecurrence, reply.reply)
        assertEquals(Money(3990), reply.prefill?.amount)
        assertEquals("netflix", reply.prefill?.description)
    }

    @Test
    fun `marcas de boleto estorno fatura e cancelamento`() {
        val cases = mapOf(
            "paguei o boleto da luz 120" to StandardReply.UnsupportedBoleto,
            "estorno de 40 da amazon" to StandardReply.UnsupportedRefund,
            "paguei a fatura" to StandardReply.UnsupportedInvoicePayment,
            "cancelaram as parcelas do fone" to StandardReply.UnsupportedCancellation,
        )
        cases.forEach { (text, expected) ->
            assertEquals(expected, assertIs<ChatDecision.Reply>(decide(text, reg("x", "120"))).reply, text)
        }
    }

    @Test
    fun `desfazer por texto nao desfaz nada`() {
        for (text in listOf("desfaz", "Apaga esse", "desfazer isso", "exclui esse.")) {
            assertEquals(StandardReply.UndoByText, assertIs<ChatDecision.Reply>(ChatValidator.prefilter(text)).reply, text)
        }
        assertNull(ChatValidator.prefilter("delivery 68 no cartão"))
    }

    // ---- cenário 10: referências inventadas ----

    @Test
    fun `categoria inventada vira a classificar e conta inexistente vira pergunta`() {
        val entry = registered(decide("viagem 300 no cartão", reg("viagem", "300", category = "c99", payment = ChatPayment.Card)))
        assertNull(entry.categoryId)
        val ask = assertIs<ChatDecision.Ask>(decide("mercado 35 no pix", reg("mercado", "35", payment = ChatPayment.Pix, account = "a9")))
        assertEquals(QuestionField.Account, ask.question.field)
        assertEquals(listOf("account-1", "account-2"), ask.question.options.map { (it.value as OptionValue.Account).id })
    }

    @Test
    fun `recibo alvo inexistente pergunta o alvo e sem recibos leva a lancamentos`() {
        val two = ChatBattery.context(
            listOf(ReceiptItem("r1", "rec-1", "e-1", "Delivery"), ReceiptItem("r2", "rec-2", "e-2", "Mercado")),
        )
        val ask = assertIs<ChatDecision.AskCorrection>(
            decide("troca para casa", Proposal.Correct(target = "r9", field = CorrectionField.Category, category = "c5"), two),
        )
        assertEquals(QuestionField.Target, ask.question.field)
        assertEquals(listOf("rec-1", "rec-2"), ask.question.options.map { (it.value as OptionValue.Receipt).receiptId })

        val none = ChatBattery.context(emptyList())
        val reply = assertIs<ChatDecision.Reply>(decide("corrige o mercado de semana passada", Proposal.Correct(field = CorrectionField.Amount, text = "10"), none))
        assertEquals(StandardReply.NoRecentReceipt, reply.reply)
    }

    // ---- cenário 11: saída fora do esquema ----

    @Test
    fun `saida fora do esquema e recusada`() {
        val outputs = listOf(
            "'; DROP TABLE movement; --",
            """{"action":"register","amount":"150","sql":"delete from movement"}""",
            """{"action":"drop"}""",
            """{"action":"register","amount":["150"]}""",
            """{"action":"register","payment":"boleto"}""",
            """{"action":"query","amount":"1"}""",
            """[1,2,3]""",
            "{" + "\"action\":\"register\",\"description\":\"" + "x".repeat(200) + "\"}",
        )
        outputs.forEach { raw ->
            val decision = ChatValidator.evaluate("mercado 150 no pix", raw, ctx)
            assertEquals(RefuseReason.BadFormat, assertIs<ChatDecision.Refuse>(decision).reason, raw)
        }
    }

    @Test
    fun `descricao e termo curto e nunca a mensagem inteira`() {
        val text = "comprei um monte de coisas diferentes no mercado hoje 150 no pix"
        val decision = decide(text, reg(text, "150", payment = ChatPayment.Pix))
        assertEquals(RefuseReason.BadFormat, assertIs<ChatDecision.Refuse>(decision).reason)
    }

    // ---- categoria, pagamento e conta ----

    @Test
    fun `falta so a categoria salva a classificar e sugestoes do modelo so ordenam as opcoes`() {
        val decision = ChatValidator.evaluate(
            "Amazon 150 no cartão",
            Proposal.Clarify(QuestionField.Category, listOf("c4", "c7", "c99"), reg("Amazon", "150", payment = ChatPayment.Card)),
            ctx,
        )
        val register = assertIs<ChatDecision.Register>(decision)
        assertNull(register.entry.categoryId)
        assertEquals(listOf("compras", "eletronicos"), register.categoryHints)
        val options = EntryCompleter.categoryOptions(ctx, listOf("c7", "c5"))
        assertEquals(listOf("Eletrônicos", "Casa"), options.take(2).map { it.label })
    }

    @Test
    fun `pagamento ausente pergunta com cartao contas e dinheiro e nunca presume o cartao`() {
        val ask = assertIs<ChatDecision.Ask>(decide("mercado 35", reg("mercado", "35", payment = ChatPayment.Card)))
        assertEquals(QuestionField.Payment, ask.question.field)
        assertEquals(listOf("card", "a1", "a2", "cash"), ask.question.options.map { it.ref })
    }

    @Test
    fun `tocar numa conta registra pix e foi debito corrige depois`() {
        val ask = assertIs<ChatDecision.Ask>(decide("mercado 35", reg("mercado", "35")))
        val answered = EntryCompleter.apply(ask.draft, QuestionField.Payment, AnswerValue.Option(OptionValue.Account("account-2")))
        val entry = registered(EntryCompleter.complete(answered, ctx))
        assertEquals(ChatPayment.Pix, entry.payment)
        assertEquals("account-2", entry.accountId)
        assertEquals(Money(3500), entry.amount)
    }

    @Test
    fun `pix sem conta usa o padrao e sem padrao pergunta a conta`() {
        val withDefault = registered(decide("mercado 35 no pix", reg("mercado", "35", payment = ChatPayment.Pix)))
        assertEquals("account-1", withDefault.accountId)
        val noDefault = ctx.copy(myDefaultAccountId = null)
        val ask = assertIs<ChatDecision.Ask>(decide("mercado 35 no pix", reg("mercado", "35", payment = ChatPayment.Pix), noDefault))
        assertEquals(QuestionField.Account, ask.question.field)
        val noAccounts = ctx.copy(accounts = emptyList(), myDefaultAccountId = null)
        assertEquals(RefuseReason.NoAccounts, assertIs<ChatDecision.Refuse>(decide("mercado 35 no pix", reg("mercado", "35", payment = ChatPayment.Pix), noAccounts)).reason)
    }

    @Test
    fun `ordem das perguntas e valor primeiro depois pagamento`() {
        val ask = assertIs<ChatDecision.Ask>(decide("comprei ração", reg("ração")))
        assertEquals(QuestionField.Amount, ask.question.field)
        val next = EntryCompleter.complete(EntryCompleter.apply(ask.draft, QuestionField.Amount, AnswerValue.Amount(Money(8990))), ctx)
        assertEquals(QuestionField.Payment, assertIs<ChatDecision.Ask>(next).question.field)
    }

    // ---- correções ----

    @Test
    fun `correcao de pix sem conta padrao pergunta a conta e mantem o lancamento`() {
        val noDefault = ctx.copy(myDefaultAccountId = null)
        val ask = assertIs<ChatDecision.AskCorrection>(
            decide("foi pix", Proposal.Correct(target = "r1", field = CorrectionField.Payment, payment = ChatPayment.Pix), noDefault),
        )
        assertEquals(QuestionField.Account, ask.question.field)
        val resolved = CorrectionCompleter.apply(ask.request, noDefault, OptionValue.Account("account-2"))
        val plan = assertIs<ChatDecision.Correct>(CorrectionCompleter.complete(resolved, noDefault)).plan
        assertEquals(EntryChange.Payment(ChatPayment.Pix, "account-2"), plan.change)
        assertEquals("entry-1", plan.entryId)
    }

    @Test
    fun `parcelada abre a tela 09 para valor data e pagamento mas aceita categoria e descricao`() {
        val installments = ChatBattery.context(listOf(ReceiptItem("r1", "rec-1", "purchase-1", "6x R$ 200,00", installments = 6)))
        assertEquals("purchase-1", assertIs<ChatDecision.OpenEdit>(decide("corrige o valor para 39,90", Proposal.Correct(target = "r1", field = CorrectionField.Amount, text = "39,90"), installments)).entryId)
        assertIs<ChatDecision.OpenEdit>(decide("foi pix", Proposal.Correct(target = "r1", field = CorrectionField.Payment, payment = ChatPayment.Pix), installments))
        assertIs<ChatDecision.Correct>(decide("troca para casa", Proposal.Correct(target = "r1", field = CorrectionField.Category, category = "c5"), installments))
    }

    @Test
    fun `correcao sem alvo com um unico recibo usa esse recibo`() {
        val plan = assertIs<ChatDecision.Correct>(decide("troca para casa", Proposal.Correct(field = CorrectionField.Category, category = "c5"))).plan
        assertEquals("receipt-1", plan.receiptId)
        assertEquals(EntryChange.Category("casa"), plan.change)
    }

    // ---- respostas ----

    @Test
    fun `resposta digitada precisa citar a pergunta aberta`() {
        val open = ctx.copy(
            question = OpenQuestion(
                "q1", "question-1", QuestionField.Category,
                EntryCompleter.categoryOptions(ctx, emptyList()),
            ),
        )
        val answer = assertIs<ChatDecision.Answer>(decide("pet", Proposal.Answer(question = "q1", choice = "c6"), open))
        assertEquals(AnswerValue.Option(OptionValue.Category("pet")), answer.value)
        // Subcategoria que não está entre as opções mostradas, mas existe no contexto, também vale.
        assertIs<ChatDecision.Answer>(decide("delivery", Proposal.Answer(question = "q1", choice = "c2"), open))
        assertEquals(RefuseReason.InvalidReference, assertIs<ChatDecision.Refuse>(decide("pet", Proposal.Answer(question = "q9", choice = "c6"), open)).reason)
        assertEquals(RefuseReason.InvalidReference, assertIs<ChatDecision.Refuse>(decide("pet", Proposal.Answer(question = "q1", choice = "c99"), open)).reason)
        assertEquals(RefuseReason.InvalidReference, assertIs<ChatDecision.Refuse>(decide("pet", Proposal.Answer(question = "q1", choice = "c6"))).reason)
    }

    @Test
    fun `resposta de valor e lida do texto`() {
        val open = ctx.copy(question = OpenQuestion("q1", "question-1", QuestionField.Amount, emptyList()))
        val answer = assertIs<ChatDecision.Answer>(decide("89,90", Proposal.Answer(question = "q1", text = "89,90"), open))
        assertEquals(AnswerValue.Amount(Money(8990)), answer.value)
        assertEquals(RefuseReason.Unanchored, assertIs<ChatDecision.Refuse>(decide("89,90", Proposal.Answer(question = "q1", text = "899"), open)).reason)
    }

    // ---- codec ----

    @Test
    fun `codec faz ida e volta de todas as propostas`() {
        val proposals = listOf(
            reg("café", "35", AmountRole.Single, "2x", DateExpression(DateKind.Explicit, "3/10"), "c1", ChatPayment.Pix, "a1", Nature.Expense),
            Proposal.Correct("r1", CorrectionField.Date, "x", DateExpression(DateKind.Today, "hoje"), "c1", ChatPayment.Cash, "a1"),
            Proposal.Answer("q1", "c1", "x", DateExpression(DateKind.Yesterday, "ontem")),
            Proposal.Clarify(QuestionField.Category, listOf("c1", "c2"), reg("a", "1")),
            Proposal.Query, Proposal.Intent, Proposal.Unsupported(UnsupportedType.Boleto), Proposal.Uninterpretable,
        )
        proposals.forEach { proposal ->
            assertEquals(ProposalParse.Ok(proposal), ProposalCodec.decode(ProposalCodec.encode(proposal)), proposal.toString())
        }
    }

    @Test
    fun `o esquema nao tem campo para mes fatura parcela calculada nem confianca`() {
        for (field in listOf("month", "invoice", "installmentAmount", "total", "balance", "confidence")) {
            val raw = """{"action":"register","amount":"150","$field":"x"}"""
            assertTrue(ProposalCodec.decode(raw) is ProposalParse.Invalid, field)
        }
    }

    @Test
    fun `fake interpretador registra apenas texto e contexto curto`() = runBlocking<Unit> {
        val fake = FakeInterpreter().on("delivery 68 no cartão", reg("delivery", "68", payment = ChatPayment.Card))
        fake.interpret(InterpretRequest("delivery 68 no cartão", ctx))
        assertEquals(1, fake.requests.size)
        assertEquals("""{"action":"none"}""", fake.interpret(InterpretRequest("desconhecido", ctx)))
    }
}
