package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.RecurrenceScope
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommitmentDetail
import app.accord.core.space.CommitmentKind
import app.accord.core.space.CommitmentNature
import app.accord.core.space.ConfirmNonCharge
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DateCriterion
import app.accord.core.space.DeleteEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntryFilter
import app.accord.core.space.EntryKind
import app.accord.core.space.EntrySituation
import app.accord.core.space.ItemSync
import app.accord.core.space.PayInvoice
import app.accord.core.space.PaymentMethod
import app.accord.core.space.PendingItem
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.RequestInstallmentCancellation
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.space.ReceiveIncome
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueriesTest : SpaceTestBase() {
    private val sep = ym(2026, 9)
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Handle.withDefaultAccount(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private suspend fun Handle.pix(
        description: String,
        reais: Int,
        category: String = "alimentacao.mercado",
        date: LocalDate = d(2026, 10, 20),
        kind: DirectPaymentKind = DirectPaymentKind.Pix,
    ): String = space.execute(
        env(),
        RegisterDirectPayment(kind, description, Classification.Category(seed(category)), date, brl(reais)),
    ).applied().changes.single().id

    private suspend fun Handle.card(
        description: String,
        reais: Int,
        installments: Int = 1,
        category: String = "eletronicos",
        date: LocalDate = d(2026, 9, 25),
        invoice: YearMonth = oct,
        toClassify: Boolean = false,
    ): String = space.execute(
        env(),
        RegisterCardPurchase(
            description, if (toClassify) Classification.ToClassify else Classification.Category(seed(category)), date, brl(reais),
            if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
        ),
    ).applied().changes.single().id

    private fun filter(
        from: YearMonth = oct,
        to: YearMonth = oct,
        criterion: DateCriterion = DateCriterion.BudgetMonth,
    ) = EntryFilter(from, to, criterion)

    private suspend fun Handle.rows(filter: EntryFilter) = space.entries(filter).value().rows

    // ---- Lançamentos (tela 07) ----

    @Test
    fun `filtro de outubro no orçamento mostra R$ 200 exigíveis da compra de R$ 1200 em 6x, com a parcela identificada`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.card("Equipamento", 1200, 6)
        h.pix("Mercado", 50)
        val page = h.space.entries(filter()).value()

        val installment = page.rows.single { it.kind == EntryKind.CardInstallment }
        assertEquals(brl(200), installment.amount)
        assertEquals(1, installment.installmentNumber)
        assertEquals(6, installment.installmentCount)
        assertEquals(d(2026, 9, 25), installment.date)
        assertEquals(d(2026, 10, 15), installment.dueDate)
        assertEquals(PaymentMethod.Card, installment.method)
        assertEquals(brl(250), page.summary.expenses)
    }

    @Test
    fun `critério data da compra mostra a compra uma única vez pelo total e sem as parcelas`() = runBlocking<Unit> {
        val h = open()
        h.card("Equipamento", 1200, 6)
        val sepPage = h.space.entries(filter(sep, sep, DateCriterion.PurchaseDate)).value()
        val purchase = sepPage.rows.single()
        assertEquals(EntryKind.CardPurchase, purchase.kind)
        assertEquals(brl(1200), purchase.amount)
        assertEquals(brl(1200), sepPage.summary.expenses)
        // e nenhum mês seguinte repete o valor da compra
        assertEquals(emptyList(), h.rows(filter(oct, ym(2027, 3), DateCriterion.PurchaseDate)))
    }

    @Test
    fun `filtro Eletrônicos não mostra o Mercado e ao limpar os dois aparecem`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.card("Monitor", 800, category = "eletronicos.equipamentos")
        h.pix("Mercado", 50)
        val onlyElectronics = h.rows(filter().copy(categoryId = seed("eletronicos")))
        assertEquals(listOf("Monitor"), onlyElectronics.map { it.description })
        val onlySub = h.rows(filter().copy(categoryId = seed("eletronicos.equipamentos")))
        assertEquals(listOf("Monitor"), onlySub.map { it.description })
        assertEquals(emptyList(), h.rows(filter().copy(categoryId = seed("eletronicos.acessorios"))))
        assertEquals(setOf("Monitor", "Mercado"), h.rows(filter()).map { it.description }.toSet())
    }

    @Test
    fun `duas compras legítimas de mesmo valor e data permanecem como duas linhas`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.pix("mercado 80", 80)
        h.pix("mercado 80", 80)
        assertEquals(2, h.rows(filter()).size)
    }

    @Test
    fun `busca ignora maiúsculas e acentos`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.pix("Padaria Pão Quente", 12)
        h.pix("Mercado", 50)
        assertEquals(listOf("Padaria Pão Quente"), h.rows(filter().copy(search = "PAO quente")).map { it.description })
        assertEquals(emptyList(), h.rows(filter().copy(search = "xyz")))
    }

    @Test
    fun `filtro por forma de pagamento`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.pix("No Pix", 10)
        h.pix("No débito", 20, kind = DirectPaymentKind.Debit)
        h.card("No cartão", 30, category = "compras")
        assertEquals(listOf("No Pix"), h.rows(filter().copy(methods = setOf(PaymentMethod.Pix))).map { it.description })
        assertEquals(
            setOf("No Pix", "No cartão"),
            h.rows(filter().copy(methods = setOf(PaymentMethod.Pix, PaymentMethod.Card))).map { it.description }.toSet(),
        )
    }

    @Test
    fun `filtro por situação a classificar, a conferir e vencido`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.card("Amazon", 150, toClassify = true)
        h.space.execute(
            h.env(),
            RegisterCardPurchase("Janela", Classification.Category(seed("compras")), d(2026, 10, 7), brl(80)),
        ).applied()
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 10), brl(120)),
        ).applied()
        h.pix("Normal", 5)

        fun names(s: EntrySituation) = runBlocking { h.rows(filter().copy(situations = setOf(s))).map { it.description } }
        assertEquals(listOf("Amazon"), names(EntrySituation.ToClassify))
        assertEquals(listOf("Janela"), names(EntrySituation.InvoiceLinkToConfirm))
        assertEquals(listOf("Luz"), names(EntrySituation.Overdue)) // vence em 10/10, hoje é 24/10
        assertTrue("Luz" in names(EntrySituation.NotPaid))
    }

    @Test
    fun `filtro por pessoa considera quem registrou`() = runBlocking<Unit> {
        val db = newDatabase()
        val ana = open("ana", db)
        val bia = open("bia", db)
        ana.withDefaultAccount()
        bia.space.execute(bia.env(), SetMyDefaultAccount(ana.space.accounts().value().accounts.single().id)).applied()
        ana.pix("Da Ana", 10)
        bia.pix("Da Bia", 20)
        assertEquals(listOf("Da Bia"), ana.rows(filter().copy(person = "bia")).map { it.description })
        assertEquals(2, ana.rows(filter()).size)
    }

    @Test
    fun `paginação estável não duplica nem perde linhas`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        repeat(5) { h.pix("Item $it", 10 + it, date = d(2026, 10, 1 + it)) }
        val full = h.space.entries(filter(), pageSize = 100).value().rows.map { it.id }
        val first = h.space.entries(filter(), cursor = 0, pageSize = 2).value()
        val second = h.space.entries(filter(), cursor = first.nextCursor!!, pageSize = 2).value()
        val third = h.space.entries(filter(), cursor = second.nextCursor!!, pageSize = 2).value()
        assertNull(third.nextCursor)
        assertEquals(5, first.total)
        assertEquals(full, (first.rows + second.rows + third.rows).map { it.id })
        assertEquals(full.size, full.toSet().size)
    }

    @Test
    fun `resumo separa despesas, rendas, créditos e quitações`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val spent = h.pix("Compra", 100)
        h.space.execute(h.env(), RegisterRefund(spent, brl(40), RefundStatus.Confirmed, d(2026, 10, 25))).applied()
        val income = h.space.execute(h.env(), RegisterIncome("Salário", brl(500), d(2026, 10, 5))).applied().changes.single().id
        h.space.execute(h.env(), ReceiveIncome(income, Settlement(d(2026, 10, 6)))).applied()
        h.card("Fatura", 70, category = "compras")
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(70))).applied()
        h.space.execute(h.env(), PayInvoice(oct, brl(70), d(2026, 10, 15))).applied()

        val page = h.space.entries(filter()).value()
        assertEquals(brl(170), page.summary.expenses)
        assertEquals(brl(500), page.summary.incomes)
        assertEquals(brl(40), page.summary.credits)
        assertEquals(brl(70), page.summary.invoicePayments)
        assertEquals(
            setOf(EntryKind.DirectPayment, EntryKind.Refund, EntryKind.Income, EntryKind.CardInstallment, EntryKind.InvoicePayment),
            page.rows.map { it.kind }.toSet(),
        )
        assertTrue(page.rows.single { it.kind == EntryKind.InvoicePayment }.situations.contains(EntrySituation.Paid))
    }

    @Test
    fun `lançamentos desfeitos ou excluídos não aparecem`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix("Some", 10)
        h.pix("Fica", 10)
        h.space.execute(h.env(), DeleteEntry(id)).applied()
        assertEquals(listOf("Fica"), h.rows(filter()).map { it.description })
    }

    @Test
    fun `boleto pago aparece no mês do pagamento e recorrências entram na lista`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val boleto = h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        h.space.execute(h.env(), app.accord.core.space.PayBoleto(boleto, Settlement(d(2026, 11, 3)))).applied()
        h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Streaming", seed("assinaturas.streaming"), brl(40), frequency = Frequency.Monthly, firstDate = d(2026, 11, 5)),
        ).applied()

        assertEquals(emptyList(), h.rows(filter(oct, oct)))
        val novRows = h.rows(filter(nov, nov))
        assertEquals(setOf("Luz", "Streaming"), novRows.map { it.description }.toSet())
        assertTrue(novRows.single { it.description == "Luz" }.situations.contains(EntrySituation.Paid))
        assertEquals(EntryKind.RecurrenceOccurrence, novRows.single { it.description == "Streaming" }.kind)
    }

    // ---- Compromissos (telas 10 e 11) ----

    @Test
    fun `novembro mostra a parcela 2 de 6 e março a 6 de 6 sem sétima em abril`() = runBlocking<Unit> {
        val h = open()
        h.card("Equipamento", 1200, 6)
        val novItem = h.space.commitments(nov).value().items.single()
        assertEquals(2, novItem.number)
        assertEquals(6, novItem.count)
        assertEquals(CommitmentNature.Finite, novItem.nature)
        assertEquals(d(2026, 11, 15), novItem.dueDate)
        assertEquals(brl(200), novItem.amount)
        val mar = h.space.commitments(ym(2027, 3)).value().items.single()
        assertEquals(6, mar.number)
        assertEquals(emptyList(), h.space.commitments(ym(2027, 4)).value().items)
    }

    @Test
    fun `total de conhecidos soma parcelas, recorrências e contas previstas sem rendas nem intenções`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.card("Equipamento", 1200, 6)
        h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Streaming", seed("assinaturas.streaming"), Money(3_990), frequency = Frequency.Monthly, firstDate = d(2026, 11, 5)),
        ).applied()
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 11, 10), brl(100)),
        ).applied()
        h.space.execute(h.env(), RegisterIncome("Salário", brl(6000), d(2026, 11, 5))).applied()
        h.pix("Mercado", 50, date = d(2026, 11, 2))

        val month = h.space.commitments(nov).value()
        assertEquals(Money(20_000 + 3_990 + 10_000), month.knownTotal)
        assertEquals(setOf(CommitmentNature.Finite, CommitmentNature.Recurring, CommitmentNature.Bill), month.items.map { it.nature }.toSet())
        val streaming = month.items.single { it.nature == CommitmentNature.Recurring }
        assertNull(streaming.count) // recorrência não inventa total de parcelas
    }

    @Test
    fun `filtros de compromissos`() = runBlocking<Unit> {
        val h = open()
        h.card("Equipamento", 1200, 6)
        h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Streaming", seed("assinaturas.streaming"), brl(40), frequency = Frequency.Monthly, firstDate = d(2026, 11, 5)),
        ).applied()
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 11, 10), brl(100)),
        ).applied()
        suspend fun names(kind: CommitmentKind) = h.space.commitments(nov, kind).value().items.map { it.description }
        assertEquals(listOf("Equipamento"), names(CommitmentKind.Installments))
        assertEquals(listOf("Streaming"), names(CommitmentKind.Recurrences))
        assertEquals(listOf("Luz"), names(CommitmentKind.Bills))
        assertEquals(3, names(CommitmentKind.All).size)
    }

    @Test
    fun `boleto vencido continua no mês original marcado como vencido`() = runBlocking<Unit> {
        val h = open()
        h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 9, 28), brl(100)),
        ).applied()
        val item = h.space.commitments(sep).value().items.single()
        assertEquals(d(2026, 9, 28), item.dueDate)
        assertTrue(EntrySituation.Overdue in item.situations)
        assertEquals(emptyList(), h.space.commitments(oct).value().items)
    }

    @Test
    fun `parcelas com cancelamento solicitado continuam e as canceladas saem, com a situação visível`() = runBlocking<Unit> {
        val h = open()
        val id = h.card("Equipamento", 600, 6, invoice = sep)
        h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3, 4))).applied()
        val request = h.space.commitments(nov).value().items.single()
        assertTrue(EntrySituation.CancelRequested in request.situations)
        assertEquals(brl(100), request.amount)

        h.space.execute(h.env(), ConfirmNonCharge(id, listOf(3, 4))).applied()
        assertEquals(emptyList(), h.space.commitments(nov).value().items)
        assertEquals(Money.ZERO, h.space.commitments(nov).value().knownTotal)
    }

    @Test
    fun `detalhe do compromisso parcelado traz o cronograma de 1 a 6 com canceladas`() = runBlocking<Unit> {
        val h = open()
        val id = h.card("Equipamento", 1200, 6)
        h.space.execute(h.env(), ConfirmNonCharge(id, listOf(5, 6))).applied()
        val detail = assertIs<CommitmentDetail.Installments>(h.space.commitment("$id#2").value())
        assertEquals(listOf(1, 2, 3, 4, 5, 6), detail.schedule.map { it.number })
        assertEquals(d(2026, 11, 15), detail.schedule[1].dueDate)
        assertTrue(EntrySituation.Cancelled in detail.schedule[5].situations)
        assertEquals(brl(1200), detail.purchase.total)
    }

    @Test
    fun `detalhe de recorrência mostra frequência e próxima cobrança sem total`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Streaming", seed("assinaturas.streaming"), brl(40), frequency = Frequency.Monthly, firstDate = d(2026, 9, 5)),
        ).applied().changes.single().id
        val detail = assertIs<CommitmentDetail.Recurring>(h.space.commitment("$id:2").value())
        assertEquals(Frequency.Monthly, detail.recurrence.frequency)
        assertEquals(2, detail.current.number)
        assertEquals(3, detail.next!!.number)
        assertEquals(d(2026, 11, 5), detail.next!!.scheduledDate)
    }

    @Test
    fun `detalhe de recorrência encerrada não tem próxima`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Curso", seed("lazer"), brl(40), frequency = Frequency.Monthly, firstDate = d(2026, 9, 5)),
        ).applied().changes.single().id
        h.space.execute(h.env(), app.accord.core.space.DeleteRecurrence(id, 3, RecurrenceScope.ThisAndNext)).applied()
        val detail = assertIs<CommitmentDetail.Recurring>(h.space.commitment("$id:2").value())
        assertNull(detail.next)
        assertNull(h.space.commitment("$id:3").value())
    }

    @Test
    fun `detalhe do boleto como compromisso e id desconhecido`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 11, 10), brl(100)),
        ).applied().changes.single().id
        assertIs<CommitmentDetail.Bill>(h.space.commitment(id).value())
        assertNull(h.space.commitment("nada").value())
    }

    // ---- Detalhe do lançamento (tela 08) ----

    @Test
    fun `detalhe de Pix traz autor, origem, conta, estornos e situação de persistência`() = runBlocking<Unit> {
        val h = open()
        val account = h.withDefaultAccount()
        val id = h.pix("Mercado", 100)
        h.space.execute(h.env(), RegisterRefund(id, brl(30), RefundStatus.Confirmed, d(2026, 10, 25))).applied()

        val detail = assertNotNull(h.space.entry(id).value())
        assertEquals(EntryKind.DirectPayment, detail.kind)
        assertEquals(brl(100), detail.amount)
        assertEquals(PaymentMethod.Pix, detail.method)
        assertEquals("ana", detail.authorId)
        assertEquals(account, detail.accountId)
        assertEquals(app.accord.core.space.Origin.Manual, detail.origin)
        assertEquals(1, detail.refunds.size)
        assertEquals(ItemSync.AwaitingSend, detail.sync) // salvo no aparelho, com operação ainda não reconhecida
        assertFalse(detail.voided)
    }

    @Test
    fun `detalhe de parcela traz a compra com total e parcelas em posições distintas`() = runBlocking<Unit> {
        val h = open()
        val id = h.card("Equipamento", 1200, 6)
        val detail = assertNotNull(h.space.entry("$id#3").value())
        assertEquals(EntryKind.CardInstallment, detail.kind)
        assertEquals(brl(200), detail.amount) // a parcela
        assertEquals(brl(1200), detail.purchase!!.total) // a compra
        assertEquals(d(2026, 9, 25), detail.purchaseDate)
        assertEquals(6, detail.purchase!!.installments.size)
        assertEquals(PaymentMethod.Card, detail.method)
    }

    @Test
    fun `detalhe do boleto mostra vencimento, pagamento e mês usado`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        h.space.execute(h.env(), app.accord.core.space.PayBoleto(id, Settlement(d(2026, 11, 3), brl(104)))).applied()
        val detail = assertNotNull(h.space.entry(id).value())
        assertEquals(d(2026, 10, 30), detail.dueDate)
        assertEquals(d(2026, 11, 3), detail.settledDate)
        assertEquals(nov, detail.boleto!!.budgetMonth)
        assertEquals(brl(100), detail.boleto!!.expectedAmount)
        assertEquals(brl(104), detail.boleto!!.paidAmount)
    }

    @Test
    fun `detalhe de ocorrência de recorrência e lançamento desconhecido`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(
            h.env(),
            CreateRecurrence(RecurrenceKind.Expense, "Streaming", seed("assinaturas.streaming"), brl(40), RecurrenceForm.OffCard, frequency = Frequency.Monthly, firstDate = d(2026, 9, 5)),
        ).applied().changes.single().id
        val detail = assertNotNull(h.space.entry("$id:2").value())
        assertEquals(EntryKind.RecurrenceOccurrence, detail.kind)
        assertEquals(2, detail.occurrence!!.number)
        assertNull(h.space.entry("nada").value())
    }

    @Test
    fun `detalhe de lançamento excluído mantém a identidade marcada como excluída`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix("Some", 10)
        h.space.execute(h.env(), DeleteEntry(id)).applied()
        assertTrue(h.space.entry(id).value()!!.voided)
    }

    // ---- Pendências (tela 18) ----

    @Test
    fun `pendências derivam do estado — a classificar, a conferir, pagamento divergente e diferença de total`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.card("Amazon", 150, toClassify = true, invoice = nov)
        h.space.execute(
            h.env(),
            RegisterCardPurchase("Janela", Classification.Category(seed("compras")), d(2026, 10, 7), brl(80)),
        ).applied()
        h.card("Compra", 100, category = "compras", invoice = oct)
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(120))).applied() // composição 180 (Compra + Janela) → diferença −60
        h.space.execute(h.env(), PayInvoice(oct, brl(120), d(2026, 10, 15))).applied() // não aceita → divergente

        val pendings = h.space.pendings().value()
        assertEquals(1, pendings.filterIsInstance<PendingItem.CategoryToClarify>().size)
        assertEquals(brl(150), pendings.filterIsInstance<PendingItem.CategoryToClarify>().single().amount)
        assertEquals("Janela", pendings.filterIsInstance<PendingItem.InvoiceLink>().single().description)
        assertEquals(brl(120), pendings.filterIsInstance<PendingItem.DivergentInvoicePayment>().single().amount)
        assertEquals(Money(-6_000), pendings.filterIsInstance<PendingItem.InvoiceTotalDifference>().single().difference)
        assertEquals(pendings.map { it.month }.sorted(), pendings.map { it.month })
    }

    @Test
    fun `classificar fecha a pendência de categoria`() = runBlocking<Unit> {
        val h = open()
        val id = h.card("Amazon", 150, toClassify = true)
        assertEquals(1, h.space.pendings().value().size)
        h.space.execute(h.env(), app.accord.core.space.ClassifyEntry(id, Classification.Category(seed("pet")))).applied()
        assertEquals(emptyList(), h.space.pendings().value())
    }

    @Test
    fun `corrigir o vínculo da compra a conferir fecha a pendência`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(
            h.env(),
            RegisterCardPurchase("Janela", Classification.Category(seed("compras")), d(2026, 10, 8), brl(80)),
        ).applied().changes.single().id
        assertEquals(1, h.space.pendings().value().size)
        h.space.execute(h.env(), app.accord.core.space.CorrectInvoiceAssignment(id, 1, oct)).applied()
        assertEquals(emptyList(), h.space.pendings().value())
    }
}
