package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.CardCycle
import app.accord.core.InvoicePhase
import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.InvoiceStatus
import app.accord.core.space.InvoiceTotalSource
import app.accord.core.space.InvoiceView
import app.accord.core.space.OccurrenceView
import app.accord.core.space.PaymentView
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import java.time.LocalDate
import java.time.YearMonth

/** Composição conhecida da fatura (compras, encargos e créditos separados) e a visão com conferência e pagamento. */
internal class InvoiceQueries(
    private val dao: SpaceDao,
    private val clock: BudgetClock,
    private val occurrences: suspend (YearMonth) -> List<OccurrenceView>,
    private val cycle: suspend () -> CardCycle,
) {
    data class Composition(val purchases: Money, val charges: Money, val credits: Money, val linksToConfirm: Int) {
        val known get() = purchases + charges - credits
    }

    /** Composição conhecida da fatura: compras e parcelas, encargos e créditos, separados. */
    suspend fun composition(month: YearMonth): Composition {
        val parts = dao.invoiceParts(month.toString())
        val recurring = occurrences(month).filter { it.kind == RecurrenceKind.Expense && it.form == RecurrenceForm.Card }
        return Composition(
            purchases = Money(parts.purchasesCents + recurring.sumOf { it.expectedAmount.cents }),
            charges = Money(parts.chargesCents),
            credits = Money(dao.invoiceCredits(month.toString())),
            linksToConfirm = dao.linksToConfirm(month.toString()) + recurring.count { it.assignment == AssignmentState.ToConfirm },
        )
    }

    suspend fun view(month: YearMonth): InvoiceView {
        val parts = composition(month)
        val row = dao.invoice(month.toString())
        val payments = dao.invoicePayments(month.toString())
        val checked = row?.checkedCents?.let(::Money)
        val conferred = checked != null && checked == parts.known
        val accepted = payments.firstOrNull { it.accepted }
        val phase = clock.today().let { today -> cycle().phase(month, today) }
        return InvoiceView(
            dueMonth = month,
            purchases = parts.purchases,
            charges = parts.charges,
            credits = parts.credits,
            knownTotal = parts.known,
            checkedTotal = checked,
            checkedSource = row?.checkedSource?.let(InvoiceTotalSource::valueOf),
            conferred = conferred,
            totalDifference = if (checked != null && !conferred) checked - parts.known else null,
            paid = accepted != null,
            paidDate = accepted?.let { LocalDate.ofEpochDay(it.epochDay) },
            status = when {
                checked != null -> InvoiceStatus.Closed
                phase == InvoicePhase.Open -> InvoiceStatus.Open
                else -> InvoiceStatus.ClosedEstimated
            },
            linksToConfirm = parts.linksToConfirm,
            divergentPayments = payments.filterNot { it.accepted }.map {
                PaymentView(it.id, Money(it.amountCents), LocalDate.ofEpochDay(it.epochDay), it.accountId, accepted = false)
            },
            revision = row?.revision ?: 0,
        )
    }
}
