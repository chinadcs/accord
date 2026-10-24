package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CancelState
import app.accord.core.space.Classification
import app.accord.core.space.ClassifyEntry
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfirmNonCharge
import app.accord.core.space.ConfirmRefund
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.ErrorCode
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterRefund
import app.accord.core.space.RequestInstallmentCancellation
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RefundCancellationTest : SpaceTestBase() {
    private suspend fun Handle.withDefaultAccount() {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
    }

    private suspend fun Handle.pix(amount: Int, date: LocalDate, category: String = "compras", toClassify: Boolean = false): String {
        val classification = if (toClassify) Classification.ToClassify else Classification.Category(seed(category))
        return space.execute(
            env(), RegisterDirectPayment(DirectPaymentKind.Pix, "Compra", classification, date, brl(amount)),
        ).applied().changes.single().id
    }

    private suspend fun Handle.cardPurchase(
        amount: Int,
        date: LocalDate,
        installments: Int = 1,
        invoice: java.time.YearMonth? = null,
        category: String = "compras",
    ) = space.execute(
        env(),
        RegisterCardPurchase(
            description = "Compra", classification = Classification.Category(seed(category)), date = date,
            amount = brl(amount), amountMeaning = if (installments > 1) AmountMeaning.Total else null,
            installments = installments, invoice = invoice,
        ),
    ).applied().changes.single().id

    private fun confirmed(origin: String, amount: Int, date: LocalDate, invoice: java.time.YearMonth? = null) =
        RegisterRefund(origin, brl(amount), RefundStatus.Confirmed, date, invoice)

    // ---- estorno (cenário 10) ----

    @Test
    fun `compra de R$ 100 por Pix em outubro com devolução em novembro — outubro fica, novembro recebe o crédito, renda inalterada`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 20))
        h.space.execute(h.env(), confirmed(id, 100, d(2026, 11, 10))).applied()

        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(100), oct.categories.single { it.categoryId == seed("compras") }.net)
        val nov = h.space.budget(ym(2026, 11)).value()
        val compras = nov.categories.single { it.categoryId == seed("compras") }
        assertEquals(Money.ZERO, compras.gross)
        assertEquals(brl(100), compras.credits)
        assertEquals(Money(-10_000), compras.net) // crédito líquido, sem truncar em zero
        assertEquals(brl(100), nov.credits)
        assertEquals(Money.ZERO, nov.income.received)
        assertEquals(Money.ZERO, h.space.incomes(ym(2026, 11)).value().received)
    }

    @Test
    fun `estorno parcial de R$ 40 abate R$ 40`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 20))
        h.space.execute(h.env(), confirmed(id, 40, d(2026, 10, 25))).applied()
        val compras = h.space.budget(ym(2026, 10)).value().categories.single { it.categoryId == seed("compras") }
        assertEquals(brl(100), compras.gross)
        assertEquals(brl(40), compras.credits)
        assertEquals(brl(60), compras.net)
    }

    @Test
    fun `com R$ 70 já estornados um estorno de R$ 50 é rejeitado informando o restante de R$ 30 e depois R$ 30 é aceito`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 20))
        h.space.execute(h.env(), confirmed(id, 70, d(2026, 10, 25))).applied()

        val before = h.space.operations().value().size
        val over = h.space.execute(h.env(), confirmed(id, 50, d(2026, 10, 26)))
        val error = assertIs<CommandResult.Rejected>(over).errors.single()
        assertEquals(ErrorCode.ExceedsRemaining, error.code)
        assertEquals(brl(30), error.remaining)
        assertEquals(before, h.space.operations().value().size)

        h.space.execute(h.env(), confirmed(id, 30, d(2026, 10, 26))).applied()
        val none = h.space.execute(h.env(), confirmed(id, 1, d(2026, 10, 27)))
        assertEquals(Money.ZERO, assertIs<CommandResult.Rejected>(none).errors.single().remaining)
    }

    @Test
    fun `estorno solicitado não tem efeito nos totais mas entra na soma do limite`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 20))
        h.space.execute(h.env(), RegisterRefund(id, brl(80), RefundStatus.Requested)).applied()

        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(Money.ZERO, oct.credits)
        assertEquals(brl(100), oct.categories.single { it.categoryId == seed("compras") }.net)
        val over = h.space.execute(h.env(), confirmed(id, 30, d(2026, 10, 25)))
        assertEquals(brl(20), assertIs<CommandResult.Rejected>(over).errors.single().remaining)
        assertEquals(RefundStatus.Requested, h.space.refunds(id).value().single().status)
    }

    @Test
    fun `confirmar o solicitado pelo valor efetivo devolvido, sem presumir devolução integral`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 20))
        val refundId = h.space.execute(h.env(), RegisterRefund(id, brl(80), RefundStatus.Requested)).applied().changes.single().id

        h.space.execute(h.env(), ConfirmRefund(refundId, brl(50), d(2026, 11, 3))).applied()

        val nov = h.space.budget(ym(2026, 11)).value()
        assertEquals(brl(50), nov.credits)
        val refund = h.space.refunds(id).value().single()
        assertEquals(RefundStatus.Confirmed, refund.status)
        assertEquals(brl(50), refund.amount)
        assertEquals(ym(2026, 11), refund.creditMonth)
        // o restante conta o confirmado, não o solicitado
        val over = h.space.execute(h.env(), confirmed(id, 51, d(2026, 11, 4)))
        assertEquals(brl(50), assertIs<CommandResult.Rejected>(over).errors.single().remaining)
    }

    @Test
    fun `pago é líquido do estorno confirmado — R$ 1000 pagos com estorno de R$ 100 dão R$ 900`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(1000, d(2026, 10, 5))
        h.space.execute(h.env(), confirmed(id, 100, d(2026, 10, 20))).applied()
        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(900), oct.paid)
        assertEquals(brl(1000), oct.committed)
        assertEquals(brl(900), oct.categories.single { it.categoryId == seed("compras") }.paid)
    }

    @Test
    fun `mês só com crédito tem líquido negativo e pago negativo`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 5))
        h.space.execute(h.env(), confirmed(id, 100, d(2026, 11, 5))).applied()
        assertEquals(Money(-10_000), h.space.budget(ym(2026, 11)).value().paid)
    }

    @Test
    fun `estorno acima do valor ou de origem inexistente é rejeitado`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 5))
        val tooMuch = h.space.execute(h.env(), confirmed(id, 101, d(2026, 10, 6)))
        assertEquals(brl(100), assertIs<CommandResult.Rejected>(tooMuch).errors.single().remaining)
        val unknown = h.space.execute(h.env(), confirmed("nada", 10, d(2026, 10, 6)))
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(unknown).errors.single().code)
        val noDate = h.space.execute(h.env(), RegisterRefund(id, brl(10), RefundStatus.Confirmed))
        assertEquals("date", assertIs<CommandResult.Rejected>(noDate).errors.single().field)
        val zero = h.space.execute(h.env(), confirmed(id, 0, d(2026, 10, 6)))
        assertEquals("amount", assertIs<CommandResult.Rejected>(zero).errors.single().field)
    }

    @Test
    fun `estorno de compra parcelada vale até o total e o crédito cai na fatura informada`() = runBlocking<Unit> {
        val h = open()
        val id = h.cardPurchase(1200, d(2026, 9, 25), installments = 6, invoice = ym(2026, 10))
        h.space.execute(h.env(), confirmed(id, 1200, d(2026, 11, 10), invoice = ym(2026, 11))).applied()
        val over = h.space.execute(h.env(), confirmed(id, 1, d(2026, 11, 10), invoice = ym(2026, 11)))
        assertEquals(Money.ZERO, assertIs<CommandResult.Rejected>(over).errors.single().remaining)

        val nov = h.space.budget(ym(2026, 11)).value()
        val compras = nov.categories.single { it.categoryId == seed("compras") }
        assertEquals(brl(200), compras.gross) // parcela 2/6
        assertEquals(brl(1200), compras.credits)
        assertEquals(Money(-100_000), compras.net)
    }

    @Test
    fun `crédito no cartão sem fatura informada usa a fatura estimada pela data da devolução`() = runBlocking<Unit> {
        val h = open()
        val id = h.cardPurchase(100, d(2026, 10, 5))
        h.space.execute(h.env(), confirmed(id, 40, d(2026, 11, 20))).applied()
        // devolução em 20/11 com fechamento 8 → fatura que vence em 15/12
        assertEquals(ym(2026, 12), h.space.refunds(id).value().single().creditMonth)
        assertEquals(brl(40), h.space.budget(ym(2026, 12)).value().credits)
    }

    @Test
    fun `estorno acompanha a categoria quando o lançamento a classificar é classificado`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 5), toClassify = true)
        h.space.execute(h.env(), confirmed(id, 40, d(2026, 10, 10))).applied()

        val before = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(60), before.toClassify.amount)
        assertTrue(before.categories.all { it.credits == Money.ZERO })

        h.space.execute(h.env(), ClassifyEntry(id, Classification.Category(seed("pet")))).applied()
        val after = h.space.budget(ym(2026, 10)).value()
        assertEquals(Money.ZERO, after.toClassify.amount)
        val pet = after.categories.single { it.categoryId == seed("pet") }
        assertEquals(brl(100), pet.gross)
        assertEquals(brl(40), pet.credits)
    }

    @Test
    fun `desfazer o estorno devolve o valor ao líquido`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.pix(100, d(2026, 10, 5))
        val refund = h.space.execute(h.env(), confirmed(id, 40, d(2026, 10, 10))).applied()
        h.space.execute(h.env(), Undo(refund.operationId)).applied()
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().credits)
        assertTrue(h.space.refunds(id).value().isEmpty())
        // e o limite dos estornos volta a considerar só o que existe
        h.space.execute(h.env(), confirmed(id, 100, d(2026, 10, 11))).applied()
    }

    // ---- cancelamento de parcelas (cenário 11) ----

    private suspend fun Handle.sixInstallments() =
        cardPurchase(600, d(2026, 8, 20), installments = 6, invoice = ym(2026, 9)) // set..fev, R$ 100 cada

    @Test
    fun `pedido de cancelamento das parcelas 3 a 6 mantém tudo no comprometido`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3, 4, 5, 6))).applied()

        for (m in listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2))) {
            assertEquals(brl(100), h.space.budget(m).value().committed, "mês $m")
        }
        val states = h.space.purchase(id).value()!!.installments.map { it.cancel }
        assertEquals(List(2) { CancelState.Active } + List(4) { CancelState.CancelRequested }, states)
    }

    @Test
    fun `confirmar não cobrança das 4 futuras tira só elas, mantém as 2 anteriores e não cria crédito`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3, 4, 5, 6))).applied()
        h.space.execute(h.env(), ConfirmNonCharge(id, listOf(3, 4, 5, 6))).applied()

        assertEquals(brl(100), h.space.budget(ym(2026, 9)).value().committed)
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
        for (m in listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1), ym(2027, 2))) {
            assertEquals(Money.ZERO, h.space.budget(m).value().committed, "mês $m")
            assertEquals(Money.ZERO, h.space.budget(m).value().credits)
        }
        val detail = h.space.purchase(id).value()!!
        assertEquals(6, detail.installments.size) // cronograma preservado
        assertEquals(CancelState.Cancelled, detail.installments[5].cancel)
        assertEquals(brl(600), detail.total)
    }

    @Test
    fun `confirmar não cobrança só de algumas parcelas deixa as outras como estão`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3, 4, 5, 6))).applied()
        h.space.execute(h.env(), ConfirmNonCharge(id, listOf(3, 4))).applied()
        val states = h.space.purchase(id).value()!!.installments.map { it.cancel }
        assertEquals(
            listOf(CancelState.Active, CancelState.Active, CancelState.Cancelled, CancelState.Cancelled, CancelState.CancelRequested, CancelState.CancelRequested),
            states,
        )
    }

    @Test
    fun `só o desfazer anula o pedido e devolve as parcelas ao estado anterior`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        val request = h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3, 4))).applied()
        assertTrue(request.undoable)
        h.space.execute(h.env(), Undo(request.operationId)).applied()
        assertTrue(h.space.purchase(id).value()!!.installments.all { it.cancel == CancelState.Active })
    }

    @Test
    fun `desfazer a confirmação devolve as parcelas a cancelamento solicitado`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3, 4))).applied()
        val confirm = h.space.execute(h.env(), ConfirmNonCharge(id, listOf(3, 4))).applied()
        h.space.execute(h.env(), Undo(confirm.operationId)).applied()
        val states = h.space.purchase(id).value()!!.installments.map { it.cancel }
        assertEquals(CancelState.CancelRequested, states[2])
        assertEquals(brl(100), h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test
    fun `parcela inexistente, repetida ou já cancelada é rejeitada`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        val missing = h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(7)))
        assertEquals("installmentNumbers", assertIs<CommandResult.Rejected>(missing).errors.single().field)
        val empty = h.space.execute(h.env(), RequestInstallmentCancellation(id, emptyList()))
        assertEquals(ErrorCode.Required, assertIs<CommandResult.Rejected>(empty).errors.single().code)
        h.space.execute(h.env(), ConfirmNonCharge(id, listOf(6))).applied()
        val again = h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(6)))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(again).errors.single().code)
    }

    @Test
    fun `versão antiga no cancelamento devolve a atual`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        val r = h.space.execute(h.env(), RequestInstallmentCancellation(id, listOf(3), expectedRevision = 5))
        assertEquals(1L, assertIs<CommandResult.StaleVersion>(r).currentRevision)
    }

    @Test
    fun `parcelas canceladas bloqueiam o deslocamento da fatura da primeira parcela`() = runBlocking<Unit> {
        val h = open()
        val id = h.sixInstallments()
        h.space.execute(h.env(), ConfirmNonCharge(id, listOf(5, 6))).applied()
        val r = h.space.execute(h.env(), app.accord.core.space.CorrectInvoiceAssignment(id, 1, ym(2026, 10)))
        val errors = assertIs<CommandResult.Rejected>(r).errors
        assertEquals(setOf("installmentNumber"), errors.map { it.field }.toSet())
        assertEquals(ym(2026, 9), h.space.purchase(id).value()!!.installments.first().invoiceMonth)
    }
}
