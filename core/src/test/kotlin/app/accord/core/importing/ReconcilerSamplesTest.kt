package app.accord.core.importing

import org.junit.jupiter.api.Assumptions
import java.io.File
import kotlin.test.*

/** accord-c9m.2 on the user's private OFX samples (skipped when the folder is absent). Failure messages never carry statement content. */
class ReconcilerSamplesTest {
    private val folder: File? = (System.getenv("ACCORD_OFX_SAMPLES")?.let(::File) ?: File("../../accord-poc-private/ofx").takeIf { it.isDirectory })?.takeIf { it.isDirectory }
    private val decisions = Decisions(defaultCategoryId = "cat")

    private fun bytes(name: String): ByteArray {
        Assumptions.assumeTrue(folder != null, "OFX samples folder not available")
        return File(folder, "$name.ofx").readBytes()
    }

    private fun extract(raw: ByteArray) = assertIs<ReadOutcome.Extracted>(OfxReader.read(raw)).result

    private val samples = listOf("bradesco-conta-corrente" to "bradesco", "itau-conta-corrente" to "itau", "nubank-cartao-credito" to "nubank-cartao", "nubank-conta-corrente" to "nubank-conta")

    @Test fun `importing every sample twice finds everything known, creates nothing new and keeps the same doubts`() {
        for ((file, origin) in samples) {
            val e = extract(bytes(file))
            val first = Reconciler.preview("b1", origin, e, decisions = decisions)
            assertEquals(e.movements.size, first.counts.total, file)
            assertEquals(0, first.counts.alreadyKnown + first.counts.blocked, file)
            val known = KnownState(origins = Reconciler.originRecords(first, origin))
            val second = Reconciler.preview("b2", origin, e, known, decisions)
            assertEquals(first.items.map { it.key }, second.items.map { it.key }, "$file keys")
            assertEquals(0, second.counts.new + second.counts.safeLinks, "$file: nothing new on re-import")
            assertEquals(first.counts.new + first.counts.safeLinks, second.counts.alreadyKnown, "$file: known")
            assertEquals(first.counts.doubts, second.counts.doubts, "$file: same doubts, not doubled")
        }
    }

    @Test fun `a sample file has unique keys even when lines look alike`() {
        for ((file, origin) in samples) {
            val keys = Reconciler.preview("b1", origin, extract(bytes(file)), decisions = decisions).items.map { it.key }
            assertEquals(keys.size, keys.toSet().size, file)
        }
    }

    @Test fun `credits are doubts and debits are new in the Brazilian samples`() {
        val bradesco = Reconciler.preview("b1", "bradesco", extract(bytes("bradesco-conta-corrente")), decisions = decisions)
        val e = bradesco.items
        // Investment lines (decision of 02/11/2026): the two credits (redemption, yield) are income; the two debits are ordinary expenses.
        assertEquals(3, e.count { DoubtReason.CreditToClassify in it.doubts })
        assertEquals(2, e.count { it.effect == ProposedEffect.CreateIncome(IncomeBasis.InvestmentCredit, "investimento") })
        assertEquals(5, e.count { it.classification == Classification.New && (it.movement.amountCents ?: 0) < 0 })
        assertEquals(7, bradesco.counts.new)

        val card = Reconciler.preview("b1", "nubank-cartao", extract(bytes("nubank-cartao-credito")), decisions = decisions)
        assertEquals(101, card.counts.total)
        assertEquals(2, card.items.count { DoubtReason.ZeroAmount in it.doubts }, "zero lines: informational, not money")
        assertEquals(1, card.items.count { DoubtReason.CreditToClassify in it.doubts }, "the invoice payment is a credit to classify, never income")
        assertEquals(98, card.counts.new)
        assertEquals(BatchState.InReview, card.state)
    }

    @Test fun `an empty statement previews as zero movements`() {
        val p = Reconciler.preview("b1", "nubank-conta", extract(bytes("nubank-conta-corrente")), decisions = decisions)
        assertEquals(PreviewCounts(0, 0, 0, 0, 0), p.counts)
    }

    @Test fun `the same fitid with another amount in a later export is a divergence against the earlier record`() {
        val original = bytes("itau-conta-corrente")
        val text = String(original, Charsets.ISO_8859_1)
        assertTrue(text.contains("<TRNAMT>-30.00"), "fixture changed")
        val edited = text.replaceFirst("<TRNAMT>-30.00", "<TRNAMT>-31.00").toByteArray(Charsets.ISO_8859_1)
        val first = Reconciler.preview("b1", "itau", extract(original), decisions = decisions)
        val known = KnownState(origins = Reconciler.originRecords(first, "itau"))
        val second = Reconciler.preview("b2", "itau", extract(edited), known, decisions)
        assertEquals(1, second.items.count { DoubtReason.FitidDivergent in it.doubts })
        assertEquals(0, second.counts.new, "nothing is recreated")
        assertEquals(first.counts.new - 1, second.counts.alreadyKnown)
    }

    @Test fun `the invoice payment appears on both documents with the same amount`() {
        // Spec scenario 9: the account debit settles the card invoice (see the billing tests below), it is not an expense.
        val account = extract(bytes("itau-conta-corrente"))
        val card = extract(bytes("nubank-cartao-credito"))
        val debit = account.movements.filter { (it.amountCents ?: 0) < 0 }.map { -(it.amountCents!!) }.toSet()
        val credit = card.movements.filter { (it.amountCents ?: 0) > 0 }.map { it.amountCents!! }
        assertTrue(credit.all { it in debit }, "the card payment has the same value as a debit in the account")
    }

    // ---- billing rules (accord-c9m.7) on the same samples ----

    private val cycle = app.accord.core.CardCycle(closingDay = 2, dueDay = 2)

    @Test fun `the card payment debit in the account is a full invoice settlement when the invoice total is conferred`() {
        val account = extract(bytes("itau-conta-corrente"))
        val payment = account.movements.first { it.amountCents == -712116L }
        val billing = BillingContext(cycle, null, "tarifas", invoices = listOf(InvoiceRecord(java.time.YearMonth.of(2024, 1), java.time.LocalDate.of(2024, 1, 2), 712116, paid = false)))
        val p = Reconciler.preview("b1", "itau", account, decisions = decisions, billing = billing)
        val item = p.items.first { it.movement.ordinal == payment.ordinal }
        assertEquals(Classification.SafeLink, item.classification)
        assertEquals(ProposedEffect.PayInvoiceInFull(java.time.YearMonth.of(2024, 1)), item.effect)
        // Never an expense by category, and the rest of the account still previews: counts add up.
        assertEquals(account.movements.size, p.counts.total)
        // Re-importing after the settlement was recorded finds it known.
        val known = KnownState(origins = Reconciler.originRecords(p, "itau"))
        val again = Reconciler.preview("b2", "itau", account, known, decisions, billing = billing.copy(invoices = emptyList()))
        assertEquals(Classification.AlreadyKnown, again.items.first { it.movement.ordinal == payment.ordinal }.classification)
    }

    @Test fun `another title payment on the due date with a different amount is a divergent payment, never an expense`() {
        val account = extract(bytes("itau-conta-corrente"))
        val other = account.movements.first { it.amountCents == -49492L }
        val billing = BillingContext(cycle, null, "tarifas", invoices = listOf(InvoiceRecord(java.time.YearMonth.of(2024, 1), java.time.LocalDate.of(2024, 1, 2), 712116, paid = false)))
        val item = Reconciler.preview("b1", "itau", account, decisions = decisions, billing = billing).items.first { it.movement.ordinal == other.ordinal }
        assertEquals(setOf(DoubtReason.PaymentDivergent), item.doubts)
    }

    @Test fun `installment lines of the card sample never become new expenses until their purchase is registered`() {
        val card = extract(bytes("nubank-cartao-credito"))
        val billing = BillingContext(cycle, java.time.YearMonth.of(2024, 2), "tarifas")
        val p = Reconciler.preview("b1", "nubank-cartao", card, decisions = decisions, billing = billing)
        val hinted = p.items.filter { it.movement.installment != null }
        assertTrue(hinted.isNotEmpty())
        assertTrue(hinted.all { it.classification == Classification.Doubt && DoubtReason.InstallmentPurchaseNotRegistered in it.doubts })
        val plain = p.items.filter { it.classification == Classification.New }
        assertTrue(plain.all { (it.effect as ProposedEffect.CreateEntry).invoiceDueMonth == java.time.YearMonth.of(2024, 2) }, "new purchases take the invoice of the document")
        assertEquals(101, p.counts.total)
    }
}
