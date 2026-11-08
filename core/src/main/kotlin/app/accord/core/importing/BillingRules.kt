package app.accord.core.importing

import app.accord.core.Installments
import java.text.Normalizer
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Billing rules of the reconciliation (S4 rules 8 to 11 and 13), pure: the caller reads invoices, installment schedules and boletos from the
 * facade (S1) into a [BillingContext]. Heuristic thresholds (hints, payment window) are proposals to calibrate with real samples.
 */
internal object BillingRules {
    /** [charge] marks interest, fees and fines identified by the layout: they become new entries in the charges category. */
    data class Outcome(
        val effect: ProposedEffect? = null,
        val reasons: Set<DoubtReason> = emptySet(),
        val suggestedFirst: YearMonth? = null,
        val charge: Boolean = false,
    )

    /** Null when no billing rule applies and the generic matching must decide. Only called for clean debits that are not already known. */
    fun decide(m: ExtractedMovement, isCard: Boolean, b: BillingContext, shiftFirst: Boolean = false): Outcome? {
        val cents = m.amountCents ?: return null
        if (cents >= 0) return null
        val amount = -cents
        if (isCharge(m)) return Outcome(charge = true)
        if (isCard) return m.installment?.let { installment(m, it, b, amount, shiftFirst) }
        return invoicePayment(m, b, amount) ?: boleto(m, b, amount)
    }

    private fun isCharge(m: ExtractedMovement): Boolean {
        val type = m.originalFields.firstOrNull { it.first == "TRNTYPE" }?.second?.trim()?.uppercase()
        return type in setOf("FEE", "SRVCHG", "INT") || Regex("\\b(?:JUROS|MULTA|IOF|TARIFA|ENCARGOS?|ANUIDADE)\\b").containsMatchIn(upper(m.description))
    }

    // ---- rule 9: installments ----

    private fun installment(m: ExtractedMovement, hint: InstallmentHint, b: BillingContext, amount: Long, shiftFirst: Boolean): Outcome {
        val rows = b.purchases.flatMap { p -> p.installments.map { p to it } }
            .filter { (_, r) -> r.number == hint.number && r.total == hint.total && !r.cancelled }
        val exact = rows.filter { (_, r) -> r.cents == amount && !r.hasOrigin }
        if (exact.size > 1) return Outcome(reasons = setOf(DoubtReason.PossibleMatch))
        if (exact.isEmpty()) {
            val related = rows.any { (p, _) -> sharesAWord(p.description, m.description, 2) }
            return Outcome(reasons = setOf(if (related) DoubtReason.InstallmentValueDiverges else DoubtReason.InstallmentPurchaseNotRegistered))
        }
        val (purchase, row) = exact.single()
        val doc = b.documentInvoice ?: return Outcome(reasons = setOf(DoubtReason.InstallmentInvoiceDiverges))
        if (row.dueMonth == doc) return Outcome(ProposedEffect.LinkInstallment(purchase.purchaseId, row.number, row.entryId))
        // Only the first installment changes invoice: a later line suggests the first one it implies, applied only when the person accepts it.
        val newFirst = doc.minusMonths((row.number - 1).toLong())
        if (row.number != 1 && !shiftFirst) return Outcome(reasons = setOf(DoubtReason.InstallmentInvoiceDiverges), suggestedFirst = newFirst)
        val oldFirst = purchase.installments.minOf { it.dueMonth }
        val paid = b.invoices.filter { it.paid }.map { it.dueMonth }.toSet()
        val cancelled = purchase.installments.filter { it.cancelled }.map { it.number }.toSet()
        return when (val shift = Installments.shiftFirst(oldFirst, row.total, newFirst, paid, cancelled)) {
            is app.accord.core.ShiftResult.Shifted -> Outcome(ProposedEffect.LinkInstallment(purchase.purchaseId, row.number, row.entryId, InstallmentShift(shift.before, shift.after)))
            is app.accord.core.ShiftResult.Blocked -> Outcome(reasons = setOf(DoubtReason.InstallmentShiftBlocked))
        }
    }

    // ---- rule 10: invoice payment (full only) ----

    private fun invoicePayment(m: ExtractedMovement, b: BillingContext, amount: Long): Outcome? {
        val date = m.date ?: return null
        val unpaid = b.invoices.filter { !it.paid }
        if (unpaid.isEmpty()) return null
        val hinted = b.invoiceHints.any { upper(m.description).contains(it) }
        val equal = unpaid.filter { it.confirmedTotalCents == amount }
        if (!hinted) return if (equal.size == 1) Outcome(reasons = setOf(DoubtReason.PossibleInvoicePayment)) else null
        val near = unpaid.filter { abs(ChronoUnit.DAYS.between(it.dueDate, date)) <= b.paymentWindowDays }
        return when {
            equal.size == 1 -> Outcome(ProposedEffect.PayInvoiceInFull(equal.single().dueMonth, equal.single().cardId))
            equal.size > 1 -> Outcome(reasons = setOf(DoubtReason.PossibleMatch))
            near.isEmpty() -> null
            near.any { it.confirmedTotalCents == null } -> Outcome(reasons = setOf(DoubtReason.InvoiceTotalNotConfirmed))
            else -> Outcome(reasons = setOf(DoubtReason.PaymentDivergent))
        }
    }

    // ---- boleto ----

    private fun boleto(m: ExtractedMovement, b: BillingContext, amount: Long): Outcome? {
        val unpaid = b.boletos.filter { !it.paid }
        if (unpaid.isEmpty()) return null
        val equal = unpaid.filter { it.cents == amount }
        val hinted = Regex("\\b(?:BOLETO|TITULO|TIT)\\b").containsMatchIn(upper(m.description))
        return when {
            equal.size == 1 -> Outcome(ProposedEffect.PayBoleto(equal.single().id))
            equal.size > 1 || hinted -> Outcome(reasons = setOf(DoubtReason.PossibleBoleto))
            else -> null
        }
    }

    /** Investment lines by their words (aplicação, resgate, rendimento, CDB, Tesouro…), as whole tokens so "aplicativo" does not match. */
    fun isInvestment(description: String): Boolean =
        Regex("(?:^|[^A-Z])(?:APL|APLIC|APLICACAO|RESGATE|RENT|RENDIMENTO|RENDIMENTOS|INVEST[A-Z]*|CDB|LCI|LCA|TESOURO)(?:[^A-Z]|$)").containsMatchIn(upper(description))

    // ---- text helpers shared with the reconciler ----

    fun upper(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim().uppercase()

    fun sharesAWord(a: String, b: String, minLength: Int = 4): Boolean {
        fun words(s: String) = Regex("\\p{L}{$minLength,}").findAll(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")).map { it.value }.toSet()
        return words(a).intersect(words(b)).isNotEmpty()
    }
}
