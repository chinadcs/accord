package app.accord.core.importing

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** accord-96km (ADR 0074): in a card document every positive value is a refund, never income; the account keeps "refund or income". */
class CardCreditIsRefundTest {
    private val origin = "origin-1"
    private val dec = YearMonth.of(2026, 12)

    private fun credit(description: String, cents: Long = 5000) =
        ExtractedMovement(1, listOf("LINE" to description), LocalDate.of(2026, 12, 3), cents, "BRL", description, null, emptySet())

    private fun doc(kind: DocumentKind, m: ExtractedMovement) =
        ExtractionResult("sha", DeclaredMetadata(SourceFormat.Pdf, "layout", "BRL", null, null, kind, null, null, null), listOf(m), 0, emptyList())

    private val billing = BillingContext(CardCycle(8, 15), dec, "fees")

    private fun preview(kind: DocumentKind, m: ExtractedMovement, decisions: (String) -> Decisions = { Decisions() }): ReconciledMovement {
        val e = doc(kind, m)
        return Reconciler.preview("b1", origin, e, KnownState(), decisions(Reconciler.key(origin, m, e.movements)), billing = billing).items.single()
    }

    @Test fun `a credit on a card stays a doubt until the person names the purchase`() {
        val item = preview(DocumentKind.Card, credit("CANCELAMENTO LOJA"))
        assertEquals(setOf(DoubtReason.CreditToClassify), item.doubts)
        assertNull(item.effect)
    }

    @Test fun `a credit on a card that looks like an investment is still not income`() {
        val item = preview(DocumentKind.Card, credit("RESGATE CDB"))
        assertEquals(setOf(DoubtReason.CreditToClassify), item.doubts)
        assertNull(item.effect)
    }

    @Test fun `an income chosen earlier for a card credit is ignored`() {
        val item = preview(DocumentKind.Card, credit("ESTORNO LOJA")) { Decisions(incomeByKey = mapOf(it to "salario")) }
        assertEquals(setOf(DoubtReason.CreditToClassify), item.doubts)
        assertNull(item.effect)
    }

    @Test fun `the purchase the person names makes the refund, credited to the invoice of the document`() {
        val item = preview(DocumentKind.Card, credit("ESTORNO LOJA")) { Decisions(refundByKey = mapOf(it to "purchase-1")) }
        assertEquals(Classification.New, item.classification)
        assertEquals(ProposedEffect.CreateRefund("purchase-1", dec), item.effect)
    }

    @Test fun `on an account a credit can still be income or investment income`() {
        assertEquals(setOf(DoubtReason.CreditToClassify), preview(DocumentKind.Account, credit("PIX RECEBIDO")).doubts)
        assertIs<ProposedEffect.CreateIncome>(preview(DocumentKind.Account, credit("RESGATE CDB")).effect)
        assertIs<ProposedEffect.CreateIncome>(preview(DocumentKind.Account, credit("PIX RECEBIDO")) { Decisions(incomeByKey = mapOf(it to "salario")) }.effect)
    }
}
