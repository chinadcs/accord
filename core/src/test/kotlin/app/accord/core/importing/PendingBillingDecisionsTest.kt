package app.accord.core.importing

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterRefund
import app.accord.core.space.CorrectInvoiceAssignment
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * accord-c9m.12: what the person can decide for the billing doubts of a confirmed batch: correct the invoice of a purchase, accept the suggested
 * first installment, and say that a credit refunds a purchase. Each one is explicit and none changes what stays a doubt without it.
 */
class PendingBillingDecisionsTest {
    private val card = "card"
    private val account = "acc"
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, installment: InstallmentHint? = null) =
        ExtractedMovement(ordinal, listOf("TRNAMT" to cents.toString()), date, cents, "BRL", description, installment, emptySet())

    private fun doc(kind: DocumentKind, vararg m: ExtractedMovement) =
        ExtractionResult("sha", DeclaredMetadata(SourceFormat.Pdf, "layout", "BRL", null, null, kind, null, null, null), m.toList(), 0, emptyList())

    private fun billing(docInvoice: YearMonth?, purchases: List<CardPurchaseRecord> = emptyList(), invoices: List<InvoiceRecord> = emptyList()) =
        BillingContext(CardCycle(8, 15), docInvoice, "fees", purchases, invoices)

    private fun run(origin: String, e: ExtractionResult, b: BillingContext, known: KnownState = KnownState(), decisions: Decisions = Decisions(defaultCategoryId = "cat")) =
        Reconciler.preview("b1", origin, e, known, decisions, billing = b)

    private fun key(origin: String, e: ExtractionResult, index: Int = 0) = Reconciler.key(origin, e.movements[index], e.movements)

    // ---- corrigir a fatura da compra ----

    private val chosen = ExistingEntry("p1", card, LocalDate.of(2026, 10, 7), -6800, "loja", card = CardEntryInfo(oct, toConfirm = false, chosenByPerson = true))
    private val cardDoc = doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -6800, "LOJA"))

    @Test fun `an invoice chosen by the person stays a doubt until they agree to correct it for that very purchase`() {
        val known = KnownState(entries = listOf(chosen))
        assertEquals(setOf(DoubtReason.InvoiceDiverges), run(card, cardDoc, billing(nov), known).items.single().doubts)

        val agreed = run(card, cardDoc, billing(nov), known, Decisions(forcedLinks = mapOf(key(card, cardDoc) to "p1"), invoiceCorrections = mapOf(key(card, cardDoc) to "p1"))).items.single()
        assertEquals(Classification.SafeLink, agreed.classification)
        assertEquals(ProposedEffect.LinkEntry("p1", correctInvoiceTo = nov), agreed.effect)
    }

    @Test fun `a correction agreed for one purchase does not cover another one linked afterwards`() {
        val other = chosen.copy(id = "p2")
        val known = KnownState(entries = listOf(chosen, other))
        val decisions = Decisions(forcedLinks = mapOf(key(card, cardDoc) to "p2"), invoiceCorrections = mapOf(key(card, cardDoc) to "p1"))
        assertEquals(setOf(DoubtReason.InvoiceDiverges), run(card, cardDoc, billing(nov), known, decisions).items.single().doubts)
    }

    @Test fun `the agreed correction becomes the S1 command for the first installment and the document month counts as affected`() {
        val known = KnownState(entries = listOf(chosen))
        val b = billing(nov)
        val decisions = Decisions(forcedLinks = mapOf(key(card, cardDoc) to "p1"), invoiceCorrections = mapOf(key(card, cardDoc) to "p1"))
        val plan = ImportSteps.build(run(card, cardDoc, b, known, decisions), DocumentKind.Card, card, "space", b)
        val step = plan.steps.single()
        assertEquals(Resolution.Linked, step.resolution)
        assertEquals(listOf(CorrectInvoiceAssignment("p1", 1, nov)), step.commands)
        assertEquals(1, plan.summary.invoiceCorrections)
        assertTrue(nov in plan.summary.affectedMonths)
    }

    // ---- sugerir a 1ª parcela ----

    private fun tenis(first: YearMonth) = CardPurchaseRecord(
        "c1", "tenis", (1..6).map { InstallmentRecord(it, 6, 20000, first.plusMonths((it - 1).toLong()), "c1", hasOrigin = false) },
    )

    private val secondInstallment = doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -20000, "TENIS", InstallmentHint(2, 6)))

    @Test fun `accepting the suggested first installment shifts the schedule so the line falls on the document invoice`() {
        val b = billing(dec, purchases = listOf(tenis(oct)))
        val doubt = run(card, secondInstallment, b).items.single()
        assertEquals(setOf(DoubtReason.InstallmentInvoiceDiverges), doubt.doubts)
        assertEquals(nov, doubt.suggestedFirstInstallment)

        val accepted = run(card, secondInstallment, b, decisions = Decisions(firstInstallmentShifts = setOf(key(card, secondInstallment)))).items.single()
        assertEquals(Classification.SafeLink, accepted.classification)
        val effect = assertIs<ProposedEffect.LinkInstallment>(accepted.effect)
        assertEquals(2, effect.number)
        assertEquals((0..5).map { oct.plusMonths(it.toLong()) }, effect.shift?.before)
        assertEquals((0..5).map { nov.plusMonths(it.toLong()) }, effect.shift?.after, "installment 2 lands on December")
    }

    @Test fun `the S1 command moves the first installment to the implied month, not to the month of the document`() {
        val b = billing(dec, purchases = listOf(tenis(oct)))
        val decisions = Decisions(firstInstallmentShifts = setOf(key(card, secondInstallment)))
        val plan = ImportSteps.build(run(card, secondInstallment, b, decisions = decisions), DocumentKind.Card, card, "space", b)
        assertEquals(listOf(CorrectInvoiceAssignment("c1", 1, nov)), plan.steps.single().commands)
        assertEquals(Resolution.InstallmentLinked, plan.steps.single().resolution)
        assertEquals((0..6).map { oct.plusMonths(it.toLong()) }.toSet() - setOf(), plan.summary.affectedMonths.filter { it >= oct }.toSet(), "every month of both schedules")
    }

    @Test fun `accepting the suggestion still stops at a paid invoice`() {
        val paid = listOf(InvoiceRecord(nov, LocalDate.of(2026, 11, 15), 50000, paid = true))
        val b = billing(dec, purchases = listOf(tenis(oct)), invoices = paid)
        val r = run(card, secondInstallment, b, decisions = Decisions(firstInstallmentShifts = setOf(key(card, secondInstallment)))).items.single()
        assertEquals(setOf(DoubtReason.InstallmentShiftBlocked), r.doubts)
        assertNull(r.effect)
    }

    // ---- estorno de um crédito ----

    private val credit = doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 25), 4000, "ESTORNO LOJA"))

    @Test fun `a credit becomes a refund of the purchase the person names, credited on the invoice of the document`() {
        val k = key(card, credit)
        assertEquals(setOf(DoubtReason.CreditToClassify), run(card, credit, billing(nov)).items.single().doubts)

        val r = run(card, credit, billing(nov), decisions = Decisions(refundByKey = mapOf(k to "p1"))).items.single()
        assertEquals(Classification.New, r.classification)
        assertEquals(ProposedEffect.CreateRefund("p1", nov), r.effect)
        assertNull(r.categoryId, "a refund takes the category of the purchase it refunds, never one of its own")

        val b = billing(nov)
        val plan = ImportSteps.build(run(card, credit, b, decisions = Decisions(refundByKey = mapOf(k to "p1"))), DocumentKind.Card, card, "space", b)
        val step = plan.steps.single()
        assertEquals(Resolution.RefundCreated, step.resolution)
        assertEquals(listOf(RegisterRefund("p1", Money(4000), RefundStatus.Confirmed, LocalDate.of(2026, 10, 25), nov)), step.commands)
        assertEquals(1, plan.summary.refunds)
        assertTrue(nov in plan.summary.affectedMonths)
    }

    @Test fun `on an account statement the refund is credited on the month of the movement`() {
        val e = doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 25), 4000, "DEVOLUCAO COMPRA"))
        val r = run(account, e, billing(null), decisions = Decisions(refundByKey = mapOf(key(account, e) to "m1"))).items.single()
        assertEquals(ProposedEffect.CreateRefund("m1", null), r.effect)
    }

    @Test fun `a refund is never decided from the sign and an investment credit stays income`() {
        val e = doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 25), 4000, "RESGATE CDB"))
        val r = run(account, e, billing(null), decisions = Decisions(refundByKey = mapOf(key(account, e) to "m1"))).items.single()
        assertIs<ProposedEffect.CreateIncome>(r.effect)
    }
}
