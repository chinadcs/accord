package app.accord.ui

import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.BoletoView
import app.accord.core.space.CancelState
import app.accord.core.space.Classification
import app.accord.core.space.ConfirmNonCharge
import app.accord.core.space.ConfirmRefund
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.OccurrenceView
import app.accord.core.space.CorrectOccurrenceInvoice
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.InstallmentView
import app.accord.core.space.IncomeView
import app.accord.core.space.ItemSync
import app.accord.core.space.PayBoleto
import app.accord.core.space.PurchaseDetail
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RefundStatus
import app.accord.core.space.RefundView
import app.accord.core.space.RegisterRefund
import app.accord.core.space.RequestInstallmentCancellation
import app.accord.ui.detail.LinkedAction
import app.accord.ui.detail.SettleSwitch
import app.accord.ui.detail.settleSwitch
import app.accord.ui.detail.LinkedBuilt
import app.accord.ui.detail.LinkedError
import app.accord.ui.detail.LinkedField
import app.accord.ui.detail.LinkedForm
import app.accord.ui.detail.availableActions
import app.accord.ui.detail.build
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import app.accord.core.space.UndoBoletoPayment
import app.accord.core.space.UndoIncomeReceipt
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import app.accord.core.space.Settlement
import kotlin.test.assertTrue

class LinkedActionsTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val form = LinkedForm(date = today)

    private fun detail(
        kind: EntryKind,
        boleto: BoletoView? = null,
        income: IncomeView? = null,
        purchase: PurchaseDetail? = null,
        refunds: List<RefundView> = emptyList(),
        voided: Boolean = false,
    ) = EntryDetail(
        id = "id", kind = kind, description = "x", classification = Classification.ToClassify, amount = Money(10_000), purchaseDate = today,
        dueDate = today, settledDate = null, method = null, accountId = null, authorId = null, spentBy = null, origin = null, revision = 4,
        voided = voided, sync = ItemSync.SavedOnDevice, purchase = purchase, boleto = boleto, income = income, occurrence = null, refunds = refunds,
    )

    private fun boleto(paid: Boolean) = BoletoView(
        "b", "Luz", Classification.ToClassify, today, Money(10_000), today.takeIf { paid }, Money(10_000).takeIf { paid }, null,
        YearMonth.from(today), false, 7, false,
    )

    private fun purchase(vararg states: CancelState) = PurchaseDetail(
        "p", "Compra", Classification.ToClassify, today, Money(30_000), false, 5, false,
        states.mapIndexed { i, c -> InstallmentView(i + 1, Money(10_000), YearMonth.of(2026, 11 + i % 2), AssignmentState.Estimated, c) },
    )

    @Test
    fun boletoNaoPagoOfereceRegistrarPagamentoEPagoNao() {
        val unpaid = availableActions(detail(EntryKind.Boleto, boleto = boleto(paid = false)))
        assertIs<LinkedAction.Settle>(unpaid.first())
        assertTrue(unpaid.none { it is LinkedAction.Settle && it.receiving })
        assertTrue(availableActions(detail(EntryKind.Boleto, boleto = boleto(paid = true))).none { it is LinkedAction.Settle })
    }

    @Test
    fun interruptorSoExisteParaBoletoERendaVivosEReflitaPagoERecebido() {
        val off = assertNotNull(settleSwitch(detail(EntryKind.Boleto, boleto = boleto(paid = false))))
        assertFalse(off.on)
        assertFalse(off.receiving)
        assertEquals(Money(10_000), off.expectedAmount)
        val on = assertNotNull(settleSwitch(detail(EntryKind.Boleto, boleto = boleto(paid = true))))
        assertTrue(on.on)
        assertEquals(today, on.doneDate)
        assertNull(settleSwitch(detail(EntryKind.Boleto, boleto = boleto(false), voided = true)))
        assertNull(settleSwitch(detail(EntryKind.DirectPayment)))
    }

    @Test
    fun ligarUsaHojeEValorPrevistoEDesligarDesfazComARevisaoVista() {
        val boletoOff = assertNotNull(settleSwitch(detail(EntryKind.Boleto, boleto = boleto(paid = false))))
        val pay = assertIs<PayBoleto>(boletoOff.command(turnOn = true, today))
        assertEquals(Settlement(today, null), pay.payment)
        assertEquals(7L, pay.expectedRevision)
        assertNull(pay.accountId)
        val unpay = assertIs<UndoBoletoPayment>(boletoOff.command(turnOn = false, today))
        assertEquals("b", unpay.boletoId)

        val incomeOn = SettleSwitch(LinkedAction.Settle.Kind.Income, "i", true, Money(500), today, Money(500), 3)
        assertIs<ReceiveIncome>(incomeOn.command(turnOn = true, today))
        assertEquals("i", assertIs<UndoIncomeReceipt>(incomeOn.command(turnOn = false, today)).incomeId)
        assertEquals(LinkedAction.Settle.Kind.Income, incomeOn.adjustAction().kind)
    }

    @Test
    fun excluidoOuEstornoNaoOferecemNada() {
        assertTrue(availableActions(detail(EntryKind.Boleto, boleto = boleto(false), voided = true)).isEmpty())
        assertTrue(availableActions(detail(EntryKind.Refund)).isEmpty())
        assertTrue(availableActions(detail(EntryKind.InvoicePayment)).isEmpty())
    }

    @Test
    fun rendaNaoRecebidaOfereceRecebimentoEnaoEstorno() {
        val income = IncomeView("i", "Salário", Money(600_000), today, null, null, null, YearMonth.from(today), 2, false)
        val actions = availableActions(detail(EntryKind.Income, income = income))
        val settle = assertIs<LinkedAction.Settle>(actions.single())
        assertTrue(settle.receiving)
    }

    @Test
    fun compraParceladaOfereceEstornoECancelamentoESoOQueFoiPedidoPodeSerConfirmado() {
        val actions = availableActions(detail(EntryKind.CardInstallment, purchase = purchase(CancelState.Active, CancelState.CancelRequested, CancelState.Active)))
        val refund = assertIs<LinkedAction.NewRefund>(actions.first())
        assertEquals("p", refund.originId) // o estorno é da compra, nunca de uma parcela
        val cancels = actions.filterIsInstance<LinkedAction.Cancellation>()
        assertEquals(listOf(1, 3), cancels.single { !it.nonCharge }.candidates)
        assertEquals(listOf(2), cancels.single { it.nonCharge }.candidates)
    }

    @Test
    fun estornoSolicitadoOfereceConfirmarEConfirmadoNao() {
        val requested = RefundView("r1", "p", Money(5_000), RefundStatus.Requested, null, null, 1, false)
        val confirmed = RefundView("r2", "p", Money(2_000), RefundStatus.Confirmed, today, YearMonth.from(today), 1, false)
        val actions = availableActions(detail(EntryKind.DirectPayment, refunds = listOf(requested, confirmed)))
        assertEquals(listOf("r1"), actions.filterIsInstance<LinkedAction.ConfirmRefundOf>().map { it.refund.id })
    }

    @Test
    fun pagamentoSemValorConfirmaOPrevistoEComValorLevaOEfetivo() {
        val settle = LinkedAction.Settle(LinkedAction.Settle.Kind.Boleto, "b", Money(10_000), revision = 7)
        val same = assertIs<PayBoleto>(assertIs<LinkedBuilt.Ok>(settle.build(form)).command)
        assertNull(same.payment.amount)
        assertEquals(7, same.expectedRevision)
        val other = assertIs<PayBoleto>(assertIs<LinkedBuilt.Ok>(settle.build(form.copy(amountText = "98,40"))).command)
        assertEquals(Money(9_840), other.payment.amount)
        assertEquals(today, other.payment.date)
    }

    @Test
    fun recebimentoDeRendaUsaReceiveIncome() {
        val settle = LinkedAction.Settle(LinkedAction.Settle.Kind.Income, "i", Money(600_000), revision = 2)
        assertIs<ReceiveIncome>(assertIs<LinkedBuilt.Ok>(settle.build(form)).command)
    }

    @Test
    fun valorAmbiguoNoPagamentoEErroDoCampo() {
        val settle = LinkedAction.Settle(LinkedAction.Settle.Kind.Boleto, "b", Money(10_000), revision = 7)
        val invalid = assertIs<LinkedBuilt.Invalid>(settle.build(form.copy(amountText = "1.20")))
        assertEquals(LinkedError.AmountAmbiguous, invalid.errors[LinkedField.Amount])
    }

    @Test
    fun estornoExigeValorEEscolhaDeStatusSemPresumirDevolucao() {
        val refund = LinkedAction.NewRefund("p", Money(30_000), 5)
        val invalid = assertIs<LinkedBuilt.Invalid>(refund.build(form))
        assertEquals(LinkedError.AmountEmpty, invalid.errors[LinkedField.Amount])
        assertEquals(LinkedError.StatusRequired, invalid.errors[LinkedField.Status])
    }

    @Test
    fun estornoSolicitadoNaoLevaDataEConfirmadoLeva() {
        val refund = LinkedAction.NewRefund("p", Money(30_000), 5)
        val requested = assertIs<RegisterRefund>(assertIs<LinkedBuilt.Ok>(refund.build(form.copy(amountText = "100", refundStatus = RefundStatus.Requested))).command)
        assertNull(requested.date)
        val confirmed = assertIs<RegisterRefund>(assertIs<LinkedBuilt.Ok>(refund.build(form.copy(amountText = "100", refundStatus = RefundStatus.Confirmed))).command)
        assertEquals(today, confirmed.date)
        assertEquals(Money(10_000), confirmed.amount)
    }

    @Test
    fun confirmarEstornoLevaOValorEfetivamenteDevolvidoEAVersaoDele() {
        val view = RefundView("r1", "p", Money(5_000), RefundStatus.Requested, null, null, 3, false)
        val command = assertIs<ConfirmRefund>(assertIs<LinkedBuilt.Ok>(LinkedAction.ConfirmRefundOf(view).build(form.copy(amountText = "30,00"))).command)
        assertEquals(Money(3_000), command.amount) // devolução parcial: não presume integral
        assertEquals(3, command.expectedRevision)
    }

    @Test
    fun cancelamentoExigeParcelaMarcadaEPedirDifereDeConfirmarNaoCobranca() {
        val request = LinkedAction.Cancellation("p", listOf(1, 3), nonCharge = false, revision = 5)
        val invalid = assertIs<LinkedBuilt.Invalid>(request.build(form))
        assertEquals(LinkedError.NothingSelected, invalid.errors[LinkedField.Installments])
        val asked = assertIs<RequestInstallmentCancellation>(assertIs<LinkedBuilt.Ok>(request.build(form.copy(selected = setOf(3, 1)))).command)
        assertEquals(listOf(1, 3), asked.installmentNumbers)
        val confirmed = assertIs<ConfirmNonCharge>(
            assertIs<LinkedBuilt.Ok>(LinkedAction.Cancellation("p", listOf(2), nonCharge = true, revision = 5).build(form.copy(selected = setOf(2)))).command,
        )
        assertEquals(listOf(2), confirmed.installmentNumbers)
    }
    // ---- fatura da ocorrência no cartão (ADR 0049) ----

    private fun occurrence(
        form: RecurrenceForm = RecurrenceForm.Card,
        kind: RecurrenceKind = RecurrenceKind.Expense,
        assignment: AssignmentState? = AssignmentState.ToConfirm,
        estimated: YearMonth? = YearMonth.of(2026, 11),
        budgetMonth: YearMonth = YearMonth.of(2026, 11),
    ) = OccurrenceView(
        recurrenceId = "rec", number = 3, kind = kind, form = form, scheduledDate = today, description = "Academia", categoryId = null,
        expectedAmount = Money(10_000), accountId = null, settledDate = null, settledAmount = null, budgetMonth = budgetMonth,
        paid = false, assignment = assignment, estimatedInvoice = estimated,
    )

    private fun occurrenceDetail(o: OccurrenceView) = detail(EntryKind.RecurrenceOccurrence).copy(occurrence = o)

    @Test
    fun ocorrenciaNoCartaoOfereConferirFaturaComAEstimadaEAsVizinhas() {
        val action = availableActions(occurrenceDetail(occurrence())).single()
        assertIs<LinkedAction.OccurrenceInvoice>(action)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), action.options)
        assertEquals(false, action.confirmed)
        assertEquals(4L, action.revision)
    }

    @Test
    fun faturaJaEscolhidaViraCorrigirESemEstimativaOuForaDoCartaoNaoHaAcao() {
        val chosen = availableActions(occurrenceDetail(occurrence(assignment = AssignmentState.Confirmed, budgetMonth = YearMonth.of(2026, 12)))).single()
        assertEquals(true, (chosen as LinkedAction.OccurrenceInvoice).confirmed)
        assertEquals(YearMonth.of(2026, 12), chosen.current)
        assertTrue(availableActions(occurrenceDetail(occurrence(estimated = null))).isEmpty())
        // fora do cartão a ação é a de pagar, nunca a de fatura
        val off = availableActions(occurrenceDetail(occurrence(form = RecurrenceForm.OffCard, assignment = null, estimated = null)))
        assertTrue(off.none { it is LinkedAction.OccurrenceInvoice })
        // renda recorrente não tem fatura
        assertTrue(availableActions(occurrenceDetail(occurrence(kind = RecurrenceKind.Income))).none { it is LinkedAction.OccurrenceInvoice })
    }

    @Test
    fun faturaDaOcorrenciaExigeEscolhaEMontaOComandoComAVersao() {
        val action = availableActions(occurrenceDetail(occurrence())).single()
        val missing = assertIs<LinkedBuilt.Invalid>(action.build(form))
        assertEquals(LinkedError.InvoiceRequired, missing.errors[LinkedField.Invoice])

        val ok = assertIs<LinkedBuilt.Ok>(action.build(form.copy(invoice = YearMonth.of(2026, 12))))
        assertEquals(CorrectOccurrenceInvoice("rec", 3, YearMonth.of(2026, 12), expectedRevision = 4), ok.command)
    }
}
