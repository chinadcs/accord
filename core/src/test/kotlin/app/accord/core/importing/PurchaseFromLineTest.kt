package app.accord.core.importing

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.AmountMeaning
import app.accord.core.space.Classification as ChosenCategory
import app.accord.core.space.EntityType
import app.accord.core.space.RegisterCardPurchase
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-z724: a card line n/N with no registered purchase becomes the whole purchase once the person confirms the total (S4 rule 9, case 1). */
class PurchaseFromLineTest {
    private val card = "card-1"
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    private fun line(n: Int, total: Int = 10, cents: Long = -15000, date: LocalDate = LocalDate.of(2026, 12, 5)) =
        ExtractedMovement(1, listOf("LINE" to "x"), date, cents, "BRL", "MAGAZINE LUIZA", InstallmentHint(n, total), emptySet())

    private fun doc(m: ExtractedMovement) =
        ExtractionResult("sha", DeclaredMetadata(SourceFormat.Pdf, "layout", "BRL", null, null, DocumentKind.Card, null, null, null), listOf(m), 0, emptyList())

    private fun billing(docInvoice: YearMonth? = dec, invoices: List<InvoiceRecord> = emptyList()) =
        BillingContext(CardCycle(8, 15), docInvoice, "fees", emptyList(), invoices)

    /** The person's choices for [m], under its real origin key. */
    private fun decided(m: ExtractedMovement, totalCents: Long?, categoryId: String? = "casa"): Decisions {
        val key = Reconciler.key(card, m, doc(m).movements)
        return Decisions(
            categoryByKey = categoryId?.let { mapOf(key to it) } ?: emptyMap(),
            installmentPurchaseTotals = totalCents?.let { mapOf(key to it) } ?: emptyMap(),
        )
    }

    private fun preview(m: ExtractedMovement, b: BillingContext, d: Decisions) = Reconciler.preview("b1", card, doc(m), KnownState(), d, billing = b)

    @Test fun `without the person's total the line stays a doubt and nothing is guessed`() {
        val m = line(3)
        val item = preview(m, billing(), decided(m, null)).items.single()
        assertEquals(setOf(DoubtReason.InstallmentPurchaseNotRegistered), item.doubts)
        assertNull(item.effect)
    }

    @Test fun `line 3 of 10 creates the whole purchase with the 1st installment two months before the document's invoice`() {
        val m = line(3)
        val item = preview(m, billing(), decided(m, 150000)).items.single()
        assertEquals(Classification.New, item.classification)
        assertEquals(ProposedEffect.CreateInstallmentPurchase("casa", 150000, 3, 10, oct, (0L..9L).map { oct.plusMonths(it) }), item.effect)
        assertTrue(item.doubts.isEmpty())
    }

    @Test fun `line 1 of N starts on the document's invoice and touches no past month`() {
        val m = line(1, total = 6)
        val effect = assertIs<ProposedEffect.CreateInstallmentPurchase>(preview(m, billing(), decided(m, 90000)).items.single().effect)
        assertEquals(dec, effect.firstInvoice)
        assertEquals(listOf(dec), effect.months.take(1))
    }

    @Test fun `a total that does not split into this line's value is not accepted`() {
        val m = line(3)
        val item = preview(m, billing(), decided(m, 140000)).items.single()
        assertEquals(setOf(DoubtReason.InstallmentPurchaseNotRegistered), item.doubts)
        assertNull(item.effect)
    }

    @Test fun `the rounding difference on the last installment is allowed when the line is the last one`() {
        // 100,00 em 3x = 33,33 + 33,33 + 33,34: a linha 3/3 vale 33,34.
        val m = line(3, total = 3, cents = -3334)
        assertIs<ProposedEffect.CreateInstallmentPurchase>(preview(m, billing(), decided(m, 10000)).items.single().effect)
    }

    @Test fun `without a category the person is asked for one and the total is kept`() {
        val m = line(3)
        val item = preview(m, billing(), decided(m, 150000, categoryId = null)).items.single()
        assertEquals(setOf(DoubtReason.CategoryUnknown), item.doubts)
    }

    @Test fun `a schedule that reaches a paid invoice is blocked instead of changing a closed month`() {
        val m = line(3)
        val paid = listOf(InvoiceRecord(nov, LocalDate.of(2026, 11, 15), 50000, paid = true))
        val item = preview(m, billing(invoices = paid), decided(m, 150000)).items.single()
        assertEquals(setOf(DoubtReason.InstallmentScheduleHitsPaidInvoice), item.doubts)
        assertNull(item.effect)
    }

    @Test fun `without an invoice declared by the document the 1st installment is the invoice the purchase date falls in`() {
        // A data da linha é a da compra: 20/12 fecha depois do dia 8, então a 1ª parcela é a de janeiro de 2027 e a 3ª, a de março (ADR 0076).
        val m = line(3, date = LocalDate.of(2026, 12, 20))
        val effect = assertIs<ProposedEffect.CreateInstallmentPurchase>(preview(m, billing(docInvoice = null), decided(m, 150000)).items.single().effect)
        assertEquals(YearMonth.of(2027, 1), effect.firstInvoice)
    }

    @Test fun `confirming registers one installment purchase with the confirmed total and ties the line to its installment`() {
        val m = line(3)
        val b = billing()
        val plan = ImportSteps.build(preview(m, b, decided(m, 150000)), DocumentKind.Card, card, "space", b)
        val step = plan.steps.single()
        assertEquals(Resolution.Created, step.resolution)
        assertEquals(EntityType.CardPurchase, step.createsType)
        assertEquals(3, step.createsNumber)
        val command = assertIs<RegisterCardPurchase>(step.commands.single())
        assertEquals(Money(150000), command.amount)
        assertEquals(AmountMeaning.Total, command.amountMeaning)
        assertEquals(10, command.installments)
        assertEquals(oct, command.invoice)
        assertEquals(ChosenCategory.Category("casa"), command.classification)
        assertEquals(card, command.cardId)
        assertEquals(1, plan.summary.creates)
        // O resumo mostra, antes de confirmar, os meses passados que mudam.
        assertTrue(listOf(oct, nov, dec).all { it in plan.summary.affectedMonths })
    }
}
