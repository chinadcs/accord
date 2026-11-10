package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectInvoiceAssignment
import app.accord.core.space.ErrorCode
import app.accord.core.space.InvoiceStatus
import app.accord.core.space.InvoiceTotalSource
import app.accord.core.space.PayInvoice
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceTest : SpaceTestBase() {
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Handle.withDefaultAccount() {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
    }

    private suspend fun Handle.buy(
        amount: Int,
        invoice: YearMonth = oct,
        category: String = "compras",
        installments: Int = 1,
        isCharge: Boolean = false,
        toClassify: Boolean = false,
    ): String = space.execute(
        env(),
        RegisterCardPurchase(
            description = if (isCharge) "Anuidade" else "Compra",
            classification = if (toClassify) Classification.ToClassify else Classification.Category(seed(category)),
            date = d(2026, 9, 25),
            amount = brl(amount),
            amountMeaning = if (installments > 1) AmountMeaning.Total else null,
            installments = installments,
            isCharge = isCharge,
            invoice = invoice,
        ),
    ).applied().changes.single().id

    private suspend fun Handle.invoice(month: YearMonth) = space.invoice(month).value()

    private suspend fun Handle.registerTotal(month: YearMonth, reais: Int) =
        space.execute(env(), RegisterInvoiceTotal(month, brl(reais))).applied()

    private suspend fun Handle.pay(month: YearMonth, reais: Int) =
        space.execute(env(), PayInvoice(month, brl(reais), d(2026, 10, 15)))

    // ---- composição (cenário 9) ----

    @Test
    fun `R$ 1000 em compras mais R$ 15 de tarifa totalizam R$ 1015 e a tarifa entra uma vez em Serviços`() = runBlocking<Unit> {
        val h = open()
        h.buy(1000)
        h.buy(15, isCharge = true, toClassify = true)
        val inv = h.invoice(oct)
        assertEquals(brl(1000), inv.purchases)
        assertEquals(brl(15), inv.charges)
        assertEquals(brl(1015), inv.knownTotal)
        val b = h.space.budget(oct).value()
        assertEquals(brl(15), b.categories.single { it.categoryId == seed("servicos") }.gross)
        assertEquals(brl(1015), b.committed)
    }

    @Test
    fun `R$ 1300 em despesas menos R$ 100 de estorno são R$ 1200 líquidos, separados na composição`() = runBlocking<Unit> {
        val h = open()
        val id = h.buy(1300)
        h.space.execute(h.env(), RegisterRefund(id, brl(100), RefundStatus.Confirmed, d(2026, 10, 2), invoice = oct)).applied()
        val inv = h.invoice(oct)
        assertEquals(brl(1300), inv.purchases)
        assertEquals(brl(100), inv.credits)
        assertEquals(brl(1200), inv.knownTotal)
    }

    @Test
    fun `fatura sem nada ainda existe com tudo zerado e aberta`() = runBlocking<Unit> {
        val h = open()
        val inv = h.invoice(ym(2027, 5))
        assertEquals(Money.ZERO, inv.knownTotal)
        assertNull(inv.checkedTotal)
        assertFalse(inv.paid)
        assertEquals(InvoiceStatus.Open, inv.status)
    }

    // ---- conferência sem arquivo ----

    @Test
    fun `digitar R$ 1300 numa fatura de R$ 1300 deixa conferida e R$ 1320 vira pendência de R$ 20 sem ajustar nada`() = runBlocking<Unit> {
        val h = open()
        h.buy(1300)
        h.registerTotal(oct, 1300)
        assertTrue(h.invoice(oct).conferred)
        assertNull(h.invoice(oct).totalDifference)

        h.registerTotal(oct, 1320)
        val inv = h.invoice(oct)
        assertFalse(inv.conferred)
        assertEquals(brl(20), inv.totalDifference)
        assertEquals(brl(1300), inv.knownTotal) // nada ajustado
        assertEquals(brl(1300), h.space.budget(oct).value().committed)
    }

    @Test
    fun `fechamento 8 e relógio em 08 10 deixa a fatura em aberto, em 09 10 fechada estimada e com total registrado fechada`() = runBlocking<Unit> {
        val before = open(now = Instant.parse("2026-10-08T15:00:00Z"))
        assertEquals(InvoiceStatus.Open, before.invoice(oct).status, "no dia do fechamento a fatura ainda recebe compras (ADR 0079)")

        val closing = open(now = Instant.parse("2026-10-09T15:00:00Z"))
        assertEquals(InvoiceStatus.ClosedEstimated, closing.invoice(oct).status)
        closing.buy(100)
        closing.registerTotal(oct, 100)
        assertEquals(InvoiceStatus.Closed, closing.invoice(oct).status)
    }

    @Test
    fun `corrigir vínculo de compra a conferir continua permitido enquanto a fatura é só estimada`() = runBlocking<Unit> {
        val h = open(now = Instant.parse("2026-10-09T15:00:00Z"))
        val id = h.space.execute(
            h.env(),
            RegisterCardPurchase("Compra", Classification.Category(seed("compras")), d(2026, 10, 9), brl(100)),
        ).applied().changes.single().id
        assertEquals(InvoiceStatus.Open, h.invoice(nov).status)
        h.space.execute(h.env(), CorrectInvoiceAssignment(id, 1, oct)).applied()
        assertEquals(brl(100), h.invoice(oct).knownTotal)
    }

    @Test
    fun `total registrado entra na lista de faturas e a fatura vira conferida de novo se a composição voltar a coincidir`() = runBlocking<Unit> {
        val h = open()
        h.buy(1000)
        h.registerTotal(oct, 1015)
        assertFalse(h.invoice(oct).conferred)
        h.buy(15, isCharge = true, toClassify = true)
        assertTrue(h.invoice(oct).conferred)
        assertEquals(listOf(oct), h.space.invoices().value().map { it.dueMonth })
    }

    // ---- quitação (cenário 8) ----

    @Test
    fun `fatura conferida em R$ 1300 e pagamento de R$ 1300 — paga, comprometido inalterado, nenhuma despesa nova`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.buy(1300)
        h.registerTotal(oct, 1300)
        val before = h.space.budget(oct).value()
        assertEquals(Money.ZERO, before.paid)

        h.pay(oct, 1300).let { assertIs<CommandResult.Applied>(it) }

        val after = h.space.budget(oct).value()
        assertEquals(before.committed, after.committed)
        assertEquals(brl(1300), after.paid)
        assertEquals(before.categories.map { it.gross }, after.categories.map { it.gross })
        assertTrue(h.invoice(oct).paid)
        assertTrue(h.invoice(oct).divergentPayments.isEmpty())
        assertEquals(emptyList(), h.space.movements(oct).value()) // sem despesa nova de quitação
    }

    @Test
    fun `pagamento de R$ 500 numa fatura de R$ 1300 fica preservado em revisão, sem pagar a fatura nem criar despesa de R$ 800`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.buy(1300)
        h.registerTotal(oct, 1300)
        h.pay(oct, 500).let { assertIs<CommandResult.Applied>(it) }

        val inv = h.invoice(oct)
        assertFalse(inv.paid)
        assertEquals(listOf(brl(500)), inv.divergentPayments.map { it.amount })
        val b = h.space.budget(oct).value()
        assertEquals(brl(1300), b.committed)
        assertEquals(Money.ZERO, b.paid)
        assertEquals(emptyList(), h.space.movements(oct).value())
    }

    @Test
    fun `quitação sem total conferido vira pendência de pagamento divergente`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.buy(1300)
        h.pay(oct, 1300)
        val inv = h.invoice(oct)
        assertFalse(inv.paid)
        assertEquals(1, inv.divergentPayments.size)
    }

    @Test
    fun `conferida em R$ 1320 com composição de R$ 1300 não aceita quitação`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.buy(1300)
        h.registerTotal(oct, 1320)
        h.pay(oct, 1320)
        assertFalse(h.invoice(oct).paid)
        assertEquals(1, h.invoice(oct).divergentPayments.size)
    }

    @Test
    fun `parcelas de fatura quitada contam como pagas, as de outra fatura não`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.buy(400, installments = 2) // R$ 200 em outubro e novembro
        h.registerTotal(oct, 200)
        h.pay(oct, 200)
        assertEquals(brl(200), h.space.budget(oct).value().paid)
        assertEquals(Money.ZERO, h.space.budget(nov).value().paid)
    }

    @Test
    fun `crédito de estorno só desconta do pago quando a fatura que o recebe está quitada`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.buy(1300)
        h.space.execute(h.env(), RegisterRefund(id, brl(100), RefundStatus.Confirmed, d(2026, 10, 2), invoice = oct)).applied()
        assertEquals(Money.ZERO, h.space.budget(oct).value().paid)

        h.registerTotal(oct, 1200)
        h.pay(oct, 1200).let { assertIs<CommandResult.Applied>(it) }
        assertEquals(brl(1200), h.space.budget(oct).value().paid) // 1.300 de itens − 100 de crédito
    }

    @Test
    fun `pagar fatura já paga ou sem conta padrão é rejeitado`() = runBlocking<Unit> {
        val h = open()
        h.buy(100)
        h.registerTotal(oct, 100)
        val noAccount = h.pay(oct, 100)
        assertEquals("accountId", assertIs<CommandResult.Rejected>(noAccount).errors.single().field)
        h.withDefaultAccount()
        h.pay(oct, 100).let { assertIs<CommandResult.Applied>(it) }
        val twice = h.pay(oct, 100)
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(twice).errors.single().code)
    }

    @Test
    fun `valor de quitação zero é rejeitado`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val r = h.pay(oct, 0)
        assertEquals("amount", assertIs<CommandResult.Rejected>(r).errors.single().field)
    }

    @Test
    fun `desfazer a quitação devolve itens a não pagos`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.buy(100)
        h.registerTotal(oct, 100)
        val paid = assertIs<CommandResult.Applied>(h.pay(oct, 100)).receipt
        assertTrue(paid.undoable)
        assertEquals(2, paid.changes.size) // a fatura e o pagamento, numa só operação
        h.space.execute(h.env(), Undo(paid.operationId)).applied()
        assertFalse(h.invoice(oct).paid)
        assertEquals(Money.ZERO, h.space.budget(oct).value().paid)
    }

    @Test
    fun `desfazer o total registrado volta ao total anterior`() = runBlocking<Unit> {
        val h = open()
        h.buy(100)
        h.registerTotal(oct, 100)
        val second = h.registerTotal(oct, 130)
        assertEquals(brl(130), h.invoice(oct).checkedTotal)
        h.space.execute(h.env(), Undo(second.operationId)).applied()
        assertEquals(brl(100), h.invoice(oct).checkedTotal)
    }

    @Test
    fun `total de origem importação fica registrado como tal`() = runBlocking<Unit> {
        val h = open()
        h.buy(100)
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(100), InvoiceTotalSource.Import)).applied()
        assertEquals(InvoiceTotalSource.Import, h.invoice(oct).checkedSource)
    }

    // ---- deslocamento bloqueado por fatura quitada (25d) ----

    @Test
    fun `corrigir a 1ª parcela para novembro com outubro e novembro quitados é rejeitado listando as duas e nada é movido`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.buy(1200, installments = 6)
        h.registerTotal(oct, 200)
        h.pay(oct, 200).let { assertIs<CommandResult.Applied>(it) }
        h.registerTotal(nov, 200)
        h.pay(nov, 200).let { assertIs<CommandResult.Applied>(it) }

        val r = h.space.execute(h.env(), CorrectInvoiceAssignment(id, 1, nov))
        val errors = assertIs<CommandResult.Rejected>(r).errors
        assertEquals(2, errors.count { it.field == "invoice" })
        assertTrue(errors.any { "2026-10" in it.message } && errors.any { "2026-11" in it.message })
        assertEquals(oct, h.space.purchase(id).value()!!.installments.first().invoiceMonth)
    }

    @Test
    fun `sem fatura quitada no intervalo o deslocamento acontece`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.buy(1200, installments = 6)
        h.space.execute(h.env(), CorrectInvoiceAssignment(id, 1, nov)).applied()
        assertEquals(nov, h.space.purchase(id).value()!!.installments.first().invoiceMonth)
    }
}
