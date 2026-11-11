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

    /** What the person decided for an installment line without a registered purchase: the confirmed total and the category (null while not chosen). */
    data class NewPurchase(val totalCents: Long, val categoryId: String?)

    /** Null when no billing rule applies and the generic matching must decide. Only called for clean debits that are not already known. */
    fun decide(m: ExtractedMovement, isCard: Boolean, b: BillingContext, shiftFirst: Boolean = false, newPurchase: NewPurchase? = null, series: InstallmentSeries? = null): Outcome? {
        val cents = m.amountCents ?: return null
        if (cents >= 0) return null
        val amount = -cents
        if (isCharge(m)) return Outcome(charge = true)
        if (isCard) return m.installment?.let { installment(m, it, b, amount, shiftFirst, newPurchase, series) }
        return invoicePayment(m, b, amount) ?: boleto(m, b, amount)
    }

    private fun isCharge(m: ExtractedMovement): Boolean {
        val type = m.originalFields.firstOrNull { it.first == "TRNTYPE" }?.second?.trim()?.uppercase()
        return type in setOf("FEE", "SRVCHG", "INT") || Regex("\\b(?:JUROS|MULTA|IOF|TARIFA|ENCARGOS?|ANUIDADE)\\b").containsMatchIn(upper(m.description))
    }

    // ---- rule 9: installments ----

    private fun installment(m: ExtractedMovement, hint: InstallmentHint, b: BillingContext, amount: Long, shiftFirst: Boolean, newPurchase: NewPurchase?, series: InstallmentSeries?): Outcome {
        val rows = b.purchases.flatMap { p -> p.installments.map { p to it } }
            .filter { (_, r) -> r.number == hint.number && r.total == hint.total && !r.cancelled }
        // O banco põe os centavos que sobram na 1ª parcela e o app na última: a linha pode diferir do cronograma por centavos de arredondamento (ADR 0076).
        val open = rows.filter { (_, r) -> !r.hasOrigin }
        val exact = open.filter { (_, r) -> r.cents == amount }.ifEmpty { open.filter { (_, r) -> abs(r.cents - amount) <= hint.total } }
        if (exact.size > 1) return Outcome(reasons = setOf(DoubtReason.PossibleMatch))
        if (exact.isEmpty()) {
            val related = rows.any { (p, _) -> sharesAWord(p.description, m.description, 2) }
            if (related) return Outcome(reasons = setOf(DoubtReason.InstallmentValueDiverges))
            return newPurchase?.let { createPurchase(m, hint, b, amount, it, series) } ?: Outcome(reasons = setOf(DoubtReason.InstallmentPurchaseNotRegistered))
        }
        val (purchase, row) = exact.single()
        // A fatura da própria linha (coluna `fatura` do CSV) vale mais que tudo; a data da linha é a da compra: sem fatura declarada, a parcela n cai n - 1 meses depois da fatura atribuída pela data (ADR 0043, 0076).
        val doc = m.invoice ?: b.documentInvoice ?: m.date?.let { b.cycle.assign(it).dueMonth.plusMonths((hint.number - 1).toLong()) }
            ?: return Outcome(reasons = setOf(DoubtReason.InstallmentInvoiceDiverges))
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

    /**
     * The total of a purchase the document shows by lines n of N is settled (by the person, or by the file when it shows all N): every installment is
     * created, the 1st on the document's invoice minus n - 1 months, or on the invoice the purchase date falls in when the document declares none (the
     * line's date is the purchase's). Nothing is guessed: a total that does not split into this series' values stays a doubt. A schedule that
     * reaches a paid invoice is created all the same (ADR 0090). [series] is the group the line belongs to; its leader is the line that creates the purchase.
     */
    fun createPurchase(m: ExtractedMovement, hint: InstallmentHint, b: BillingContext, amount: Long, plan: NewPurchase, series: InstallmentSeries? = null): Outcome {
        val notRegistered = Outcome(reasons = setOf(DoubtReason.InstallmentPurchaseNotRegistered))
        val leaderNumber = series?.leader?.number ?: hint.number
        val leaderInvoice = m.invoice ?: b.documentInvoice ?: m.date?.let { b.cycle.assign(it).dueMonth.plusMonths((leaderNumber - 1).toLong()) } ?: return notRegistered
        if (hint.number !in 1..hint.total || plan.totalCents < hint.total) return notRegistered
        val fits = series?.fits(plan.totalCents) ?: Installments.lineFits(plan.totalCents, hint.total, hint.number, amount)
        if (!fits) return notRegistered
        val categoryId = plan.categoryId ?: return Outcome(reasons = setOf(DoubtReason.CategoryUnknown))
        val first = leaderInvoice.minusMonths((leaderNumber - 1).toLong())
        // Parcela anterior numa fatura já quitada também é criada (ADR 0090): o mês passa a incluí-la e a divergência aparece na conferência.
        val months = Installments.schedule(first, hint.total)
        return Outcome(ProposedEffect.CreateInstallmentPurchase(categoryId, plan.totalCents, hint.number, hint.total, first, months, series?.let { exactSchedule(it, hint.total, plan.totalCents) }))
    }

    /**
     * O valor de cada parcela como o arquivo mostra (ADR 0081): a parcela com linha leva o valor da linha e as que o arquivo não mostra repartem o que sobra do total,
     * com a sobra de centavos na última delas. Nulo quando isso não fecha (total diferente da soma de uma série completa, ou parcela faltante com menos de 1 centavo):
     * aí o total é repartido como sempre.
     */
    internal fun exactSchedule(series: InstallmentSeries, count: Int, totalCents: Long): List<Long>? {
        val known = series.members.associate { it.number to it.cents }
        val missing = (1..count).filter { it !in known }
        val rest = totalCents - known.values.sum()
        if (missing.isEmpty()) return if (rest == 0L) (1..count).map { known.getValue(it) } else null
        if (rest < missing.size) return null
        val each = rest / missing.size
        val extra = rest - each * missing.size
        return (1..count).map { n -> known[n] ?: (each + if (n == missing.last()) extra else 0L) }
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

    /** The bank financing the unpaid invoice ("Parcela de refinanciamento"): a loan, not a purchase. It stays a doubt the person decides one by one (03/11/2026). */
    fun isInvoiceRefinancing(description: String): Boolean = Regex("\\bREFINANCIAMENTO\\b").containsMatchIn(upper(description))

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
