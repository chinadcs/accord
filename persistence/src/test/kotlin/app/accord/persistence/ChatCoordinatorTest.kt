package app.accord.persistence

import app.accord.core.Money
import app.accord.core.chat.AmountRole
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.CorrectionField
import app.accord.core.chat.DateExpression
import app.accord.core.chat.DateKind
import app.accord.core.chat.InterpreterFailure
import app.accord.core.chat.MessageState
import app.accord.core.chat.Nature
import app.accord.core.chat.OptionValue
import app.accord.core.chat.Proposal
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionState
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.RefuseReason
import app.accord.core.chat.StandardReply
import app.accord.core.chat.UndoResult
import app.accord.core.chat.UnsupportedType
import app.accord.core.chat.undoableReceipt
import app.accord.core.space.Classification
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DraftSource
import app.accord.core.space.EntityType
import app.accord.core.space.EntryKind
import app.accord.core.space.PaymentMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatCoordinatorTest : ChatTestBase() {
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Env.committed(month: java.time.YearMonth) = space.budget(month).value().committed
    private suspend fun Env.toClassifyTotal(month: java.time.YearMonth) = space.budget(month).value().toClassify.amount
    private suspend fun Env.entries(month: java.time.YearMonth) = space.entries(
        app.accord.core.space.EntryFilter(from = month, to = month),
    ).value().rows

    private fun purchaseId(rowId: String) = rowId.substringBefore('#')

    private suspend fun Env.deliveryRegister(text: String = "delivery 68 no cartão") {
        fake.on(text, reg("delivery", "68", category = categoryRef("alimentacao.delivery"), payment = ChatPayment.Card))
    }

    // ---- cenário 1 ----

    @Test
    fun `delivery 68 no cartao salva direto com recibo calculado pela fachada`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        val message = e.say("delivery 68 no cartão")
        assertEquals(MessageState.Applied, message.state)

        val rows = e.entries(nov)
        val row = rows.single()
        assertEquals(EntryKind.CardInstallment, row.kind)
        assertEquals(Money(6800), row.amount)
        val detail = e.space.entry(purchaseId(row.id)).value()!!
        assertEquals(Classification.Category(seed("alimentacao.delivery")), detail.classification)
        assertEquals(app.accord.core.space.Origin.Chat, detail.origin)
        assertEquals("ana", detail.authorId)

        val receipt = e.store.snapshot().receipts.single()
        assertEquals(purchaseId(row.id), receipt.entryId)
        // Mês e fatura do recibo vêm da fachada, nunca da proposta.
        assertEquals("2026-11", receipt.summary.invoiceMonth)
        assertEquals(6800, receipt.summary.totalCents)
        assertEquals(ChatPayment.Card, receipt.summary.payment)
        assertEquals("Delivery", receipt.summary.categoryName)
        assertEquals("Alimentação", receipt.summary.parentName)
        // Salvo no aparelho e ainda não reconhecido pelo serviço: o estado de sincronização é separado do recibo.
        assertTrue(detail.sync != app.accord.core.space.ItemSync.Acknowledged)
        assertTrue(e.financeChanges > 0)
    }

    // ---- cenário 2: regra híbrida ----

    @Test
    fun `amazon 150 no cartao sem categoria salva a classificar e a resposta classifica o mesmo lancamento`() = runBlocking<Unit> {
        val e = env()
        val before = e.committed(nov)
        e.fake.on(
            "Amazon 150 no cartão",
            Proposal.Clarify(QuestionField.Category, emptyList(), reg("Amazon", "150", payment = ChatPayment.Card)),
        )
        val message = e.say("Amazon 150 no cartão")
        assertEquals(MessageState.ToClassify, message.state)

        val detail = e.space.entry(purchaseId(e.entries(nov).single().id)).value()!!
        assertEquals(Classification.ToClassify, detail.classification)
        assertEquals(Money(15_000), detail.amount)
        assertEquals(before + Money(15_000), e.committed(nov))
        assertEquals(Money(15_000), e.toClassifyTotal(nov))

        // Vai para a fila de sincronização só como lançamento estruturado, sem o texto do Chat.
        val ops = e.database.db.dao().operationsAfter(0, 100)
        val states = e.database.db.dao().changesOf(ops.map { it.operationId }).joinToString { it.state }
        assertTrue("Amazon 150 no cartão" !in states)

        val question = e.store.openQuestions().single()
        assertEquals(QuestionField.Category, question.field)
        val labels = question.options.map { it.label }
        assertTrue(labels.containsAll(listOf("Casa", "Pet", "Eletrônicos")))
        assertTrue(e.store.snapshot().receipts.single().summary.toClassify)

        // Reinício do processo: a pergunta continua na consulta e a resposta usa o mesmo lançamento.
        val restarted = e.newCoordinator()
        restarted.recover()
        val reopened = e.store.openQuestions().single()
        val pet = reopened.options.first { it.label == "Pet" }
        restarted.answer(reopened.id, pet)

        val classified = e.space.entry(detail.id).value()!!
        assertEquals(Classification.Category(seed("pet")), classified.classification)
        assertEquals(Money(15_000), classified.amount)
        assertEquals(1, e.entries(nov).size)
        assertEquals(before + Money(15_000), e.committed(nov))
        assertEquals(Money(0), e.toClassifyTotal(nov))
        assertEquals(QuestionState.Answered, e.store.question(question.id)!!.state)
        val receipts = e.store.snapshot().receipts
        assertEquals(2, receipts.size)
        assertEquals(ReceiptState.ChangedLater, receipts.first { it.entryId == detail.id && it.summary.toClassify }.state)
    }

    @Test
    fun `desfazer no recibo a classificar devolve o total e descarta a pergunta`() = runBlocking<Unit> {
        val e = env()
        val before = e.committed(nov)
        e.fake.on("Amazon 150 no cartão", Proposal.Clarify(QuestionField.Category, emptyList(), reg("Amazon", "150", payment = ChatPayment.Card)))
        e.say("Amazon 150 no cartão")
        val receipt = e.store.snapshot().undoableReceipt()!!
        assertEquals(UndoResult.Done, e.coordinator.undo(receipt.id))
        assertEquals(before, e.committed(nov))
        assertTrue(e.store.openQuestions().isEmpty())
        assertEquals(UndoResult.NotAvailable, e.coordinator.undo(receipt.id))
    }

    @Test
    fun `classificado em outra tela antes da resposta a pergunta fica resolvida em outro lugar`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("Amazon 150 no cartão", Proposal.Clarify(QuestionField.Category, emptyList(), reg("Amazon", "150", payment = ChatPayment.Card)))
        e.say("Amazon 150 no cartão")
        val id = purchaseId(e.entries(nov).single().id)
        val revision = e.space.entry(id).value()!!.revision
        e.space.execute(e.handle.env(), app.accord.core.space.ClassifyEntry(id, Classification.Category(seed("casa")), revision)).applied()

        val question = e.store.openQuestions().single()
        e.coordinator.answer(question.id, question.options.first { it.label == "Pet" })
        assertEquals(Classification.Category(seed("casa")), e.space.entry(id).value()!!.classification)
        assertEquals(QuestionState.ResolvedElsewhere, e.store.question(question.id)!!.state)
    }

    @Test
    fun `sem valor nao entra nada no orcamento e o rascunho local espera a resposta`() = runBlocking<Unit> {
        val e = env()
        val operationsBefore = e.space.operations().value().size
        e.fake.on("comprei ração", reg("ração"))
        val message = e.say("comprei ração")
        assertEquals(MessageState.Asked, message.state)
        assertEquals(operationsBefore, e.space.operations().value().size)
        assertEquals(Money(0), e.committed(oct))
        val draft = e.space.drafts().value().single()
        assertEquals(DraftSource.Chat, draft.source)
        assertTrue(draft.fields.values.none { "comprei" in it })
        val question = e.store.openQuestions().single()
        assertEquals(QuestionField.Amount, question.field)

        assertTrue(e.coordinator.answerAmount(question.id, "89,90"))
        // Falta onde foi pago: nova pergunta, ainda sem lançamento.
        val payment = e.store.openQuestions().single()
        assertEquals(QuestionField.Payment, payment.field)
        assertEquals(operationsBefore, e.space.operations().value().size)
        e.coordinator.answer(payment.id, payment.options.first { it.value is OptionValue.Card })

        val entry = e.space.entry(purchaseId(e.entries(nov).single().id)).value()!!
        assertEquals(Money(8_990), entry.amount)
        assertEquals(Classification.ToClassify, entry.classification)
        assertTrue(e.space.drafts().value().isEmpty())
        assertEquals(MessageState.ToClassify, e.message(message.id).state)
    }

    @Test
    fun `sem meio de pagamento pergunta com cartao contas e dinheiro e conta tocada vira pix`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("mercado 35", reg("mercado", "35", category = e.categoryRef("alimentacao.mercado")))
        e.say("mercado 35", MessageState.Asked)
        val question = e.store.openQuestions().single()
        assertEquals(QuestionField.Payment, question.field)
        assertEquals(listOf("Cartão", "Nubank · Conta", "Itaú · Salário", "Dinheiro").size, question.options.size)
        assertTrue(e.entries(oct).isEmpty())

        val itau = question.options.first { it.label.startsWith("Itaú") }
        e.coordinator.answer(question.id, itau)
        val row = e.entries(oct).single()
        assertEquals(Money(3_500), row.amount)
        val detail = e.space.entry(row.id).value()!!
        assertEquals(PaymentMethod.Pix, detail.method)
        assertEquals((itau.value as OptionValue.Account).id, detail.accountId)

        // "foi débito" corrige o mesmo lançamento, mesma conta e mesmo mês.
        e.fake.on("foi débito", Proposal.Correct(target = "r1", field = CorrectionField.Payment, payment = ChatPayment.Debit))
        e.say("foi débito")
        val corrected = e.space.entry(row.id).value()!!
        assertEquals(PaymentMethod.Debit, corrected.method)
        assertEquals(detail.accountId, corrected.accountId)
        assertEquals(1, e.entries(oct).size)
    }

    @Test
    fun `dinheiro e debito sem pergunta quando a frase indica`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("mercado 35 em dinheiro", reg("mercado", "35", payment = ChatPayment.Cash))
        e.fake.on("mercado 36 no débito", reg("mercado", "36", payment = ChatPayment.Debit))
        e.say("mercado 35 em dinheiro")
        e.say("mercado 36 no débito")
        val methods = e.entries(oct).map { e.space.entry(it.id).value()!!.method }
        assertEquals(setOf(PaymentMethod.Cash, PaymentMethod.Debit), methods.toSet())
    }

    // ---- cenário 3, 4, 11: validação ----

    @Test
    fun `valor sem trecho no texto e recusado sem tocar na fachada e a mensagem fica`() = runBlocking<Unit> {
        val e = env()
        val operations = e.space.operations().value().size
        e.fake.on("Amazon 150", reg("Amazon", "1500", payment = ChatPayment.Card))
        val message = e.say("Amazon 150")
        assertEquals(MessageState.Refused, message.state)
        assertEquals(RefuseReason.Unanchored, message.refuse)
        assertEquals("Amazon 150", message.text)
        assertEquals(operations, e.space.operations().value().size)
        // O registro manual abre com o que foi entendido, sem salvar nada.
        val prefill = e.coordinator.manualPrefill(message.id)
        assertEquals("Amazon", prefill?.description)
        assertNull(prefill?.amount)
    }

    @Test
    fun `saida fora do esquema e recusada e a mensagem continua preservada`() = runBlocking<Unit> {
        val e = env()
        val operations = e.space.operations().value().size
        e.fake.onRaw("café 5 no pix", """{"action":"register","amount":"5","sql":"delete from movement"}""")
        val message = e.say("café 5 no pix")
        assertEquals(RefuseReason.BadFormat, message.refuse)
        assertEquals("café 5 no pix", message.text)
        assertEquals(operations, e.space.operations().value().size)
    }

    // ---- cenários 5 e 6: parcelas ----

    @Test
    fun `X em Nx vira total e seis parcelas de outubro a marco`() = runBlocking<Unit> {
        val e = env(Instant.parse("2026-09-25T15:00:00Z"))
        e.fake.on(
            "equipamento 1.200 em 6x no cartão",
            reg("equipamento", "1.200", category = e.categoryRef("compras"), installments = "6x", payment = ChatPayment.Card),
        )
        e.say("equipamento 1.200 em 6x no cartão")
        val receipt = e.store.snapshot().receipts.single()
        assertEquals(120_000, receipt.summary.totalCents)
        assertEquals(6, receipt.summary.installments)
        assertEquals(20_000, receipt.summary.installmentCents)
        val purchase = e.space.purchase(receipt.entryId).value()!!
        assertEquals(listOf(ym(2026, 10), ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3)), purchase.installments.map { it.invoiceMonth })
        // O orçamento não soma R$ 1.200 à vista.
        assertEquals(Money(20_000), e.committed(oct))
    }

    @Test
    fun `arredondamento fica na ultima parcela e aparece no recibo`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("fone 100 em 3x no cartão", reg("fone", "100", category = e.categoryRef("eletronicos.acessorios"), installments = "3x", payment = ChatPayment.Card))
        e.say("fone 100 em 3x no cartão")
        val summary = e.store.snapshot().receipts.single().summary
        assertEquals(3, summary.installments)
        assertEquals(3_333, summary.installmentCents)
        assertEquals(3_334, summary.lastInstallmentCents)
        val amounts = e.space.purchase(e.store.snapshot().receipts.single().entryId).value()!!.installments.map { it.amount.cents }
        assertEquals(listOf(3_333L, 3_333L, 3_334L), amounts)
    }

    @Test
    fun `6 parcelas de 200 com papel divergente pergunta e nao grava`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("tv 6 parcelas de 200 no cartão", reg("tv", "200", role = AmountRole.Total, installments = "6 parcelas", payment = ChatPayment.Card))
        val message = e.say("tv 6 parcelas de 200 no cartão")
        assertEquals(MessageState.Asked, message.state)
        assertEquals(QuestionField.AmountRole, e.store.openQuestions().single().field)
        assertTrue(e.entries(oct).isEmpty() && e.entries(nov).isEmpty())
        val question = e.store.openQuestions().single()
        e.coordinator.answer(question.id, question.options.first { (it.value as OptionValue.Role).role == AmountRole.PerInstallment })
        assertEquals(Money(20_000), e.committed(nov))
    }

    // ---- cenário 7: fuso e datas ----

    @Test
    fun `ontem e hoje usam o dia local de Sao Paulo`() = runBlocking<Unit> {
        // 02h30 de 23/10 em UTC ainda é 22/10 às 23h30 em São Paulo.
        val e = env(Instant.parse("2026-10-23T02:30:00Z"))
        e.fake.on("mercado 50 ontem no pix", reg("mercado", "50", date = DateExpression(DateKind.Yesterday, "ontem"), payment = ChatPayment.Pix))
        e.fake.on("mercado 51 hoje no pix", reg("mercado", "51", date = DateExpression(DateKind.Today, "hoje"), payment = ChatPayment.Pix))
        e.say("mercado 50 ontem no pix")
        e.say("mercado 51 hoje no pix")
        val dates = e.entries(oct).associate { it.amount.cents to it.date }
        assertEquals(LocalDate.of(2026, 10, 21), dates[5_000])
        assertEquals(LocalDate.of(2026, 10, 22), dates[5_100])
    }

    @Test
    fun `dia N sem mes sem data e renda no mes que vem`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("mercado 50 dia 7 no pix", reg("mercado", "50", date = DateExpression(DateKind.DayOfMonth, "dia 7"), payment = ChatPayment.Pix))
        e.fake.on("mercado 51 dia 25 no pix", reg("mercado", "51", date = DateExpression(DateKind.DayOfMonth, "dia 25"), payment = ChatPayment.Pix))
        e.fake.on("mercado 52 no pix", reg("mercado", "52", payment = ChatPayment.Pix))
        e.fake.on("salário 6.000 mês que vem", reg("salário", "6.000", date = DateExpression(DateKind.NextMonth, "mês que vem"), nature = Nature.Income))
        listOf("mercado 50 dia 7 no pix", "mercado 51 dia 25 no pix", "mercado 52 no pix", "salário 6.000 mês que vem").forEach { e.say(it) }

        val octDates = e.entries(oct).associate { it.amount.cents to it.date }
        assertEquals(LocalDate.of(2026, 10, 7), octDates[5_000])
        assertEquals(LocalDate.of(2026, 10, 22), octDates[5_200])
        assertEquals(LocalDate.of(2026, 9, 25), e.entries(ym(2026, 9)).single().date)
        val income = e.space.incomes(nov).value()
        assertEquals(Money(600_000), income.toReceive)
        assertEquals(LocalDate.of(2026, 11, 22), income.items.single().expectedDate)
        assertEquals(Money(0), e.space.incomes(oct).value().received)
        assertTrue(e.store.snapshot().receipts.last().summary.expectedIncome)
    }

    @Test
    fun `renda com mes que vem em 31 de janeiro cai no ultimo dia de fevereiro`() = runBlocking<Unit> {
        val e = env(Instant.parse("2027-01-31T15:00:00Z"))
        e.fake.on("salário 6.000 mês que vem", reg("salário", "6.000", date = DateExpression(DateKind.NextMonth, "mês que vem"), nature = Nature.Income))
        e.say("salário 6.000 mês que vem")
        assertEquals(LocalDate.of(2027, 2, 28), e.space.incomes(ym(2027, 2)).value().items.single().expectedDate)
    }

    @Test
    fun `despesa realizada no futuro pergunta e intencao nao vira gasto`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("mercado 50 mês que vem no pix", reg("mercado", "50", date = DateExpression(DateKind.NextMonth, "mês que vem"), payment = ChatPayment.Pix))
        e.fake.on("quero comprar uma TV de 1.200 em 6x", Proposal.Intent)
        val total = e.committed(oct) + e.committed(nov)
        assertEquals(MessageState.Asked, e.say("mercado 50 mês que vem no pix").state)
        assertEquals(StandardReply.Intent, e.say("quero comprar uma TV de 1.200 em 6x").reply)
        assertEquals(total, e.committed(oct) + e.committed(nov))
        assertTrue(e.entries(oct).isEmpty())
    }

    // ---- cenário 9: consulta e ações fora do Chat ----

    @Test
    fun `consulta e acoes fora do chat respondem com texto padrao e nao registram`() = runBlocking<Unit> {
        val e = env()
        val cases = mapOf(
            "quanto gastei com delivery?" to (Proposal.Query to StandardReply.Query),
            "paguei o boleto da luz 120" to (Proposal.Unsupported(UnsupportedType.Boleto) to StandardReply.UnsupportedBoleto),
            "estorno de 40 da amazon" to (Proposal.Unsupported(UnsupportedType.Refund) to StandardReply.UnsupportedRefund),
            "paguei a fatura" to (Proposal.Unsupported(UnsupportedType.InvoicePayment) to StandardReply.UnsupportedInvoicePayment),
            "cancelaram as parcelas do fone" to (Proposal.Unsupported(UnsupportedType.Cancellation) to StandardReply.UnsupportedCancellation),
        )
        cases.forEach { (text, pair) -> e.fake.on(text, pair.first) }
        e.fake.on("netflix 39,90 todo mês", reg("netflix", "39,90", payment = ChatPayment.Card))
        val operations = e.space.operations().value().size
        cases.forEach { (text, pair) -> assertEquals(pair.second, e.say(text).reply, text) }
        val recurrence = e.say("netflix 39,90 todo mês")
        assertEquals(StandardReply.UnsupportedRecurrence, recurrence.reply)
        // Os campos entendidos vão pré-preenchidos para o registro manual.
        assertEquals(Money(3_990), e.coordinator.manualPrefill(recurrence.id)?.amount)
        assertEquals(operations, e.space.operations().value().size)
    }

    @Test
    fun `desfaz por texto responde sem desfazer e mantem o botao do recibo`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        assertEquals(StandardReply.UndoByText, e.say("desfaz").reply)
        assertNotNull(e.store.snapshot().undoableReceipt())
        assertEquals(1, e.entries(nov).size)
    }

    // ---- cenário 10: referências inventadas ----

    @Test
    fun `categoria inventada vira a classificar com opcoes validas e conta inexistente vira pergunta`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("viagem 300 no cartão", reg("viagem", "300", category = "c999", payment = ChatPayment.Card))
        e.fake.on("mercado 35 no pix", reg("mercado", "35", payment = ChatPayment.Pix, account = "a9"))
        assertEquals(MessageState.ToClassify, e.say("viagem 300 no cartão").state)
        val question = e.store.openQuestions().single()
        assertTrue(question.options.none { it.label == "Viagens" })
        assertEquals(Classification.ToClassify, e.space.entry(purchaseId(e.entries(nov).single().id)).value()!!.classification)

        e.say("mercado 35 no pix", MessageState.Asked)
        assertEquals(QuestionField.Account, e.store.openQuestions().maxBy { it.createdAtMillis }.field)
    }

    // ---- cenário 12: janela "a conferir" ----

    @Test
    fun `janela a conferir em torno do fechamento dia 8`() = runBlocking<Unit> {
        val expected = listOf(
            "2026-10-06" to Triple("2026-10", false, "véspera-1"),
            "2026-10-07" to Triple("2026-10", true, "véspera"),
            "2026-10-08" to Triple("2026-11", true, "dia"),
            "2026-10-09" to Triple("2026-11", true, "seguinte"),
            "2026-10-10" to Triple("2026-11", false, "depois"),
        )
        expected.forEach { (day, result) ->
            val e = env(Instant.parse("${day}T15:00:00Z"))
            e.fake.on("mercado 50 no cartão", reg("mercado", "50", category = e.categoryRef("alimentacao.mercado"), payment = ChatPayment.Card))
            assertEquals(MessageState.Applied, e.say("mercado 50 no cartão").state)
            val summary = e.store.snapshot().receipts.single().summary
            assertEquals(result.first, summary.invoiceMonth, day)
            assertEquals(result.second, summary.invoiceToConfirm, day)
            // Sem pergunta do Chat: a pendência de vínculo é da fachada.
            assertTrue(e.store.openQuestions().isEmpty(), day)
            assertEquals(result.second, e.space.pendings().value().any { it is app.accord.core.space.PendingItem.InvoiceLink }, day)
        }
    }

    @Test
    fun `fatura incerta com categoria ausente gera um unico lancamento a conferir e a classificar`() = runBlocking<Unit> {
        val e = env(Instant.parse("2026-10-07T15:00:00Z"))
        e.fake.on("mercado 50 no cartão", reg("mercado", "50", payment = ChatPayment.Card))
        assertEquals(MessageState.ToClassify, e.say("mercado 50 no cartão").state)
        val summary = e.store.snapshot().receipts.single().summary
        assertTrue(summary.toClassify && summary.invoiceToConfirm)
        assertEquals(1, e.entries(oct).size + e.entries(nov).size)
    }

    // ---- cenário 13 a 15: correções ----

    @Test
    fun `foi pix troca o cartao pelo pix na conta padrao e mantem um unico lancamento`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        val first = e.store.snapshot().receipts.single()
        e.fake.on("foi pix", Proposal.Correct(target = "r1", field = CorrectionField.Payment, payment = ChatPayment.Pix))
        e.say("foi pix")

        assertEquals(Money(0), e.committed(nov))
        val row = e.entries(oct).single()
        val detail = e.space.entry(row.id).value()!!
        assertEquals(PaymentMethod.Pix, detail.method)
        assertEquals(Money(6_800), detail.amount)
        assertEquals("2026-10", e.store.snapshot().receipts.maxBy { it.createdAtMillis }.summary.budgetMonth)
        assertEquals(ReceiptState.ChangedLater, e.store.receipt(first.id)!!.state)
        assertTrue(e.space.entry(first.entryId).value()!!.voided)
        // Desfazer agora é o da correção: o cartão volta.
        val latest = e.store.snapshot().undoableReceipt()!!
        assertEquals(UndoResult.Done, e.coordinator.undo(latest.id))
        assertEquals(Money(6_800), e.committed(nov))
        assertEquals(ReceiptState.Active, e.store.receipt(first.id)!!.state)
    }

    @Test
    fun `foi pix sem conta padrao pergunta a conta e mantem o lancamento ate a resposta`() = runBlocking<Unit> {
        val e = env(defaultAccount = false)
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        e.fake.on("foi pix", Proposal.Correct(target = "r1", field = CorrectionField.Payment, payment = ChatPayment.Pix))
        e.say("foi pix", MessageState.Asked)
        assertEquals(1, e.entries(nov).size)
        assertEquals(PaymentMethod.Card, e.space.entry(e.entries(nov).single().id).value()!!.method)
        val question = e.store.openQuestions().single()
        assertEquals(QuestionField.Account, question.field)
        e.coordinator.answer(question.id, question.options.first())
        assertEquals(PaymentMethod.Pix, e.space.entry(e.entries(oct).single().id).value()!!.method)
    }

    @Test
    fun `troca para casa muda as seis parcelas e informa o alcance`() = runBlocking<Unit> {
        val e = env(Instant.parse("2026-09-25T15:00:00Z"))
        e.fake.on("equipamento 1.200 em 6x no cartão", reg("equipamento", "1.200", category = e.categoryRef("compras"), installments = "6x", payment = ChatPayment.Card))
        e.say("equipamento 1.200 em 6x no cartão")
        val purchaseId = e.store.snapshot().receipts.single().entryId
        val before = e.space.purchase(purchaseId).value()!!.installments
        e.fake.on("troca para casa", Proposal.Correct(target = "r1", field = CorrectionField.Category, category = e.categoryRef("casa")))
        e.say("troca para casa")

        val after = e.space.purchase(purchaseId).value()!!
        assertEquals(Classification.Category(seed("casa")), after.classification)
        assertEquals(before, after.installments)
        assertEquals(6, e.store.snapshot().receipts.maxBy { it.createdAtMillis }.summary.scopeInstallments)
    }

    @Test
    fun `corrige o valor muda a despesa simples e abre a tela 09 para parcelada`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("almoço 35,90 no pix", reg("almoço", "35,90", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        e.say("almoço 35,90 no pix")
        val id = e.entries(oct).single().id
        e.fake.on("corrige o valor para 39,90", Proposal.Correct(target = "r1", field = CorrectionField.Amount, text = "39,90"))
        e.say("corrige o valor para 39,90")
        assertEquals(Money(3_990), e.space.entry(id).value()!!.amount)
        assertEquals(1, e.entries(oct).size)

        e.fake.on("tv 1.200 em 6x no cartão", reg("tv", "1.200", category = e.categoryRef("eletronicos"), installments = "6x", payment = ChatPayment.Card))
        e.say("tv 1.200 em 6x no cartão")
        val purchaseId = e.store.snapshot().receipts.maxBy { it.createdAtMillis }.entryId
        e.fake.on("corrige o valor para 100", Proposal.Correct(target = "r1", field = CorrectionField.Amount, text = "100"))
        val message = e.say("corrige o valor para 100")
        assertEquals(purchaseId, message.editEntryId)
        assertEquals(Money(120_000), e.space.entry(purchaseId).value()!!.amount)
    }

    @Test
    fun `correcao sem recibo nesta conversa leva a lista de lancamentos`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("corrige o mercado de semana passada", Proposal.Correct(field = CorrectionField.Amount, text = "10"))
        val operations = e.space.operations().value().size
        assertEquals(StandardReply.NoRecentReceipt, e.say("corrige o mercado de semana passada").reply)
        assertEquals(operations, e.space.operations().value().size)
    }

    // ---- cenário 16: desfazer ----

    @Test
    fun `desfazer reverte uma vez e o segundo nao muda nada`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        val receipt = e.store.snapshot().undoableReceipt()!!
        assertEquals(UndoResult.Done, e.coordinator.undo(receipt.id))
        assertEquals(Money(0), e.committed(nov))
        assertEquals(UndoResult.NotAvailable, e.coordinator.undo(receipt.id))
        assertEquals(Money(0), e.committed(nov))
        assertEquals(ReceiptState.Undone, e.store.receipt(receipt.id)!!.state)
    }

    @Test
    fun `desfazer depois de edicao em outra tela recusa e nao apaga a edicao`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        val receipt = e.store.snapshot().undoableReceipt()!!
        e.space.execute(e.handle.env(), CorrectEntry(receipt.entryId, description = "Jantar")).applied()
        assertEquals(UndoResult.ChangedAfter, e.coordinator.undo(receipt.id))
        assertEquals("Jantar", e.space.entry(receipt.entryId).value()!!.description)
        assertEquals(ReceiptState.ChangedLater, e.store.receipt(receipt.id)!!.state)
    }

    @Test
    fun `a proxima mensagem fecha o desfazer do recibo anterior mas a correcao nao`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("café 5 no pix", reg("café", "5", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        e.fake.on("pão 7 no pix", reg("pão", "7", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        e.say("café 5 no pix")
        val first = e.store.snapshot().receipts.single()
        e.fake.on("troca para casa", Proposal.Correct(target = "r1", field = CorrectionField.Category, category = e.categoryRef("casa")))
        e.say("troca para casa")
        assertNotNull(e.store.snapshot().undoableReceipt())
        e.say("pão 7 no pix")
        val receipts = e.store.snapshot().receipts
        assertEquals(setOf(first.id), receipts.filter { it.state == ReceiptState.ChangedLater }.map { it.id }.toSet())
        assertTrue(receipts.filter { it.id != receipts.maxBy { r -> r.createdAtMillis }.id }.all { it.undoClosed })
        assertEquals(e.store.snapshot().undoableReceipt()?.id, receipts.maxBy { it.createdAtMillis }.id)
    }

    // ---- cenário 17: reinício durante Aplicando ----

    @Test
    fun `reinicio em Aplicando apos a gravacao gera um lancamento e um recibo`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        val receipt = e.store.snapshot().receipts.single()
        // Simula a morte do processo depois da gravação em S1 e antes do histórico: volta a Aplicando sem recibo.
        val message = e.message(receipt.messageId)
        e.database.db.chatDao().clearReceipts()
        e.store.saveMessage(message.copy(state = MessageState.Applying, draftId = null))

        val restarted = e.newCoordinator()
        restarted.recover()
        assertEquals(1, e.entries(nov).size)
        val receipts = e.store.snapshot().receipts
        assertEquals(1, receipts.size)
        assertEquals(receipt.entryId, receipts.single().entryId)
        assertEquals(MessageState.Applied, e.message(message.id).state)
    }

    @Test
    fun `reinicio em Aplicando antes da gravacao aplica uma unica vez`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        val partial = app.accord.core.chat.PartialEntry(
            description = "delivery", amount = Money(6_800), role = AmountRole.Single, date = LocalDate.of(2026, 10, 22),
            categoryId = seed("alimentacao.delivery"), payment = ChatPayment.Card,
        )
        e.store.saveMessage(
            app.accord.core.chat.ChatMessage(
                id = "m-1", text = "delivery 68 no cartão", createdAtMillis = 1, state = MessageState.Applying,
                operationId = "op-fixa", fields = app.accord.core.chat.DraftCodec.toFields(partial),
            ),
        )
        val restarted = e.newCoordinator()
        restarted.recover()
        restarted.recover()
        assertEquals(1, e.entries(nov).size)
        assertEquals(1, e.store.snapshot().receipts.size)
        assertEquals("op-fixa", e.store.snapshot().receipts.single().operationId)
    }

    @Test
    fun `interpretando ao reabrir vira interrompida sem nova tentativa automatica`() = runBlocking<Unit> {
        val e = env()
        e.store.saveMessage(app.accord.core.chat.ChatMessage(id = "m-1", text = "café 5 no pix", createdAtMillis = 1, state = MessageState.Interpreting))
        e.newCoordinator().recover()
        delay(200)
        assertEquals(MessageState.Interrupted, e.message("m-1").state)
        assertTrue(e.fake.requests.isEmpty())
        e.fake.on("café 5 no pix", reg("café", "5", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        e.coordinator.retry("m-1")
        assertEquals(MessageState.Applied, e.wait("m-1").state)
    }

    // ---- cenários 18 a 23 ----

    @Test
    fun `duas mensagens iguais sao duas intencoes e geram dois lancamentos`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("café 5 no pix", reg("café", "5", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        e.say("café 5 no pix")
        e.say("café 5 no pix")
        assertEquals(listOf(500L, 500L), e.entries(oct).map { it.amount.cents })
        assertNull(e.coordinator.send("   "))
    }

    @Test
    fun `cancelar durante a interpretacao preserva o texto e descarta o resultado tardio`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("café 5 no pix", reg("café", "5", payment = ChatPayment.Pix), delayMillis = 600)
        val id = checkNotNull(e.coordinator.send("café 5 no pix"))
        e.wait(id, MessageState.Interpreting)
        e.coordinator.cancel(id)
        val message = e.wait(id, MessageState.Canceled)
        assertEquals("café 5 no pix", message.text)
        delay(800)
        assertEquals(MessageState.Canceled, e.message(id).state)
        assertTrue(e.entries(oct).isEmpty())
    }

    @Test
    fun `demora nao cancela sozinha e o resultado e aplicado normalmente`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("café 5 no pix", reg("café", "5", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix), delayMillis = 700)
        val id = checkNotNull(e.coordinator.send("café 5 no pix"))
        e.wait(id, MessageState.Interpreting)
        delay(350)
        assertEquals(MessageState.Interpreting, e.message(id).state)
        assertEquals(MessageState.Applied, e.wait(id).state)
        assertEquals(1, e.entries(oct).size)
    }

    @Test
    fun `falha do interpretador preserva a mensagem permite tentar de novo e a resolucao manual encerra`() = runBlocking<Unit> {
        val e = env()
        e.fake.onFailure("café 5 no pix", InterpreterFailure(InterpreterFailure.Kind.Memory))
        val failed = e.say("café 5 no pix")
        assertEquals(MessageState.Failed, failed.state)
        assertEquals("Memory", failed.failure)
        assertEquals("café 5 no pix", failed.text)
        assertTrue(e.entries(oct).isEmpty())

        e.fake.on("café 5 no pix", reg("café", "5", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        e.coordinator.retry(failed.id)
        assertEquals(MessageState.Applied, e.wait(failed.id).state)

        val other = e.say("xyz", MessageState.Refused)
        e.coordinator.resolveManually(other.id)
        assertEquals(MessageState.ResolvedManually, e.message(other.id).state)
        e.coordinator.retry(other.id)
        assertEquals(MessageState.ResolvedManually, e.message(other.id).state)
    }

    @Test
    fun `sem modelo guarda a mensagem sem chamar o interpretador e interpreta agora so por escolha`() = runBlocking<Unit> {
        val e = env()
        e.modelInstalled = false
        e.fake.on("café 5 no pix", reg("café", "5", category = e.categoryRef("alimentacao.restaurantes"), payment = ChatPayment.Pix))
        val message = e.say("café 5 no pix")
        assertEquals(MessageState.NoModel, message.state)
        assertTrue(e.fake.requests.isEmpty())
        e.modelInstalled = true
        delay(200)
        assertEquals(MessageState.NoModel, e.message(message.id).state)
        e.coordinator.retry(message.id)
        assertEquals(MessageState.Applied, e.wait(message.id).state)
    }

    @Test
    fun `mensagem nova durante pergunta aberta sem responder e um registro novo`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("Amazon 150 no cartão", Proposal.Clarify(QuestionField.Category, emptyList(), reg("Amazon", "150", payment = ChatPayment.Card)))
        e.fake.on("café 5 no pix", reg("café", "5", payment = ChatPayment.Pix))
        e.say("Amazon 150 no cartão")
        e.say("café 5 no pix")
        assertEquals(1, e.entries(nov).size)
        assertEquals(1, e.entries(oct).size)
        val open = e.store.openQuestions()
        assertTrue(open.any { it.field == QuestionField.Category && it.state == QuestionState.Open })
        assertEquals(Classification.ToClassify, e.space.entry(purchaseId(e.entries(nov).single().id)).value()!!.classification)
    }

    @Test
    fun `resposta digitada de categoria usa a pergunta aberta`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("Amazon 150 no cartão", Proposal.Clarify(QuestionField.Category, emptyList(), reg("Amazon", "150", payment = ChatPayment.Card)))
        e.say("Amazon 150 no cartão")
        e.fake.on("é pet", Proposal.Answer(question = "q1", choice = e.categoryRef("pet")))
        e.say("é pet")
        val id = purchaseId(e.entries(nov).single().id)
        assertEquals(Classification.Category(seed("pet")), e.space.entry(id).value()!!.classification)
        assertTrue(e.store.openQuestions().isEmpty())
    }

    // ---- cenário 24: privacidade ----

    @Test
    fun `o texto da mensagem nunca chega a fachada nem a fila nem ao diagnostico`() = runBlocking<Unit> {
        val e = env()
        val marker = "ZQXMARCADORUNICO"
        val text = "delivery 68 no cartão $marker"
        e.fake.on(text, reg("delivery", "68", category = e.categoryRef("alimentacao.delivery"), payment = ChatPayment.Card))
        val markerAsk = "Amazon 150 $marker"
        e.fake.on(markerAsk, reg("Amazon", "150"))
        e.say(text)
        e.say(markerAsk, MessageState.Asked)

        val dao = e.database.db.dao()
        val ops = dao.operationsAfter(0, 100)
        val serialized = dao.changesOf(ops.map { it.operationId }).joinToString("|") { it.state } +
            ops.joinToString("|") { "${it.commandType}${it.detail}${it.lastFailure}" }
        assertTrue(marker !in serialized)
        assertTrue(e.space.drafts().value().none { marker in it.fields.toString() + it.reason })
        assertTrue(e.store.attempts().none { marker in it.toString() })
        val entry = e.space.entry(purchaseId(e.entries(nov).single().id)).value()!!
        assertEquals("delivery", entry.description)
        assertTrue(e.store.snapshot().receipts.none { marker in it.summary.toString() })
        // O histórico guarda o texto, mas só neste aparelho: nenhuma operação é do Chat.
        assertTrue(ops.none { it.commandType.contains("Chat") })
    }

    // ---- diagnóstico e histórico ----

    @Test
    fun `tentativas registram duracao e resultado sem texto`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("café 5 no pix", reg("café", "5", payment = ChatPayment.Pix))
        e.say("café 5 no pix")
        e.say("xyz", MessageState.Refused)
        val attempts = e.store.attempts()
        assertEquals(listOf("applied", "refused"), attempts.map { it.outcome })
        assertTrue(attempts.all { it.modelVersion == "fake-1" && it.durationMillis >= 0 })
    }

    @Test
    fun `exportar limpar e importar restaura o historico sem duplicar nem reprocessar`() = runBlocking<Unit> {
        val e = env()
        e.deliveryRegister()
        e.say("delivery 68 no cartão")
        e.store.saveMessage(app.accord.core.chat.ChatMessage(id = "m-sent", text = "pendente", createdAtMillis = 5_000_000_000_000, state = MessageState.Sent))
        val archive = e.store.export()
        val entriesBefore = e.entries(nov)

        e.store.clear()
        assertTrue(e.store.snapshot().messages.isEmpty())
        assertEquals(entriesBefore, e.entries(nov))

        assertEquals(2, e.store.import(archive))
        assertEquals(0, e.store.import(archive))
        val snapshot = e.store.snapshot()
        assertEquals(2, snapshot.messages.size)
        assertEquals(MessageState.Interrupted, snapshot.messages.first { it.id == "m-sent" }.state)
        assertEquals(1, snapshot.receipts.size)
        assertEquals(entriesBefore, e.entries(nov))
    }

    @Test
    fun `limpar historico nao altera lancamentos e a classificar continua`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("Amazon 150 no cartão", Proposal.Clarify(QuestionField.Category, emptyList(), reg("Amazon", "150", payment = ChatPayment.Card)))
        e.say("Amazon 150 no cartão")
        e.store.clear()
        assertTrue(e.store.snapshot().messages.isEmpty() && e.store.openQuestions().isEmpty())
        assertEquals(Money(15_000), e.toClassifyTotal(nov))
        assertTrue(e.space.pendings().value().any { it is app.accord.core.space.PendingItem.CategoryToClarify })
    }

    @Test
    fun `historico de uma conta nao aparece em outra conta do aparelho`() = runBlocking<Unit> {
        val a = env(person = "ana")
        val b = env(person = "bruno")
        a.deliveryRegister()
        a.say("delivery 68 no cartão")
        assertTrue(b.store.snapshot().messages.isEmpty())
    }
}
