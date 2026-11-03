package app.accord.core.importing

import java.time.LocalDate
import kotlin.test.*

/** Decision of 02/11/2026: money coming back from an investment (redemption, yield) is income; money going into one is not decided yet. */
class InvestmentReconcilerTest {
    private val origin = "bradesco"
    private val day = LocalDate.of(2024, 9, 13)

    private fun mv(ordinal: Int, cents: Long, description: String, type: String = "CREDIT") =
        ExtractedMovement(ordinal, listOf("FITID" to "F$ordinal", "TRNTYPE" to type), day, cents, "BRL", description, null, emptySet())

    private fun doc(vararg m: ExtractedMovement) = ExtractionResult(
        "sha", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, DocumentKind.Account, null, null, null), m.toList(), 0, emptyList())

    private fun preview(e: ExtractionResult, known: KnownState = KnownState(), billing: BillingContext? = null) =
        Reconciler.preview("b1", origin, e, known, Decisions(), billing = billing)

    private fun Preview.of(ordinal: Int) = items.single { it.movement.ordinal == ordinal }

    @Test fun `a redemption or a yield is income, with no expense category and nothing left to decide`() {
        val p = preview(doc(mv(1, 1594, "Resgate Inv Fac"), mv(2, 1, "Rent.inv.facil")))
        for (i in 1..2) {
            assertEquals(Classification.New, p.of(i).classification)
            assertEquals(ProposedEffect.CreateIncome(IncomeBasis.InvestmentCredit, "investimento"), p.of(i).effect)
            assertTrue(p.of(i).doubts.isEmpty())
            assertNull(p.of(i).categoryId)
        }
        assertEquals(2, p.counts.checked)
    }

    @Test fun `money going into an investment is an ordinary expense on the account and needs a category like any debit`() {
        val e = doc(mv(1, -15004, "Apl.invest Fac", "DEBIT"), mv(2, -22162, "Aplic.autom.investfacil*", "DEBIT"))
        val undecided = preview(e)
        for (i in 1..2) assertEquals(setOf(DoubtReason.CategoryUnknown), undecided.of(i).doubts)
        val decided = Reconciler.preview("b1", origin, e, KnownState(), Decisions(defaultCategoryId = "outros"))
        for (i in 1..2) {
            assertEquals(Classification.New, decided.of(i).classification)
            assertEquals("outros", decided.of(i).categoryId)
            assertTrue(decided.of(i).doubts.isEmpty())
        }
    }

    @Test fun `other credits are still credits to classify and similar words do not count as investments`() {
        val p = preview(doc(mv(1, 500000, "Transfe Pix Rem: CLIENTE"), mv(2, 1000, "RENDA EXTRA APLICATIVO"), mv(3, -3000, "Parcela do aplicativo", "DEBIT"), mv(4, -900, "Brentford", "DEBIT")))
        assertEquals(setOf(DoubtReason.CreditToClassify), p.of(1).doubts)
        assertEquals(setOf(DoubtReason.CreditToClassify), p.of(2).doubts)
        assertEquals(DoubtReason.CategoryUnknown, p.of(3).doubts.single())
        assertEquals(DoubtReason.CategoryUnknown, p.of(4).doubts.single())
    }

    @Test fun `a zero investment line and a foreign currency keep their own doubts`() {
        val p = preview(doc(mv(1, 0, "Rent.inv.facil")))
        assertEquals(setOf(DoubtReason.ZeroAmount), p.of(1).doubts)
        val eur = ExtractionResult("sha", DeclaredMetadata(SourceFormat.Ofx, "102", "EUR", null, null, DocumentKind.Account, null, null, null), listOf(mv(1, 1594, "Resgate Inv Fac")), 0, emptyList())
        assertEquals(Classification.BlockedByCurrency, preview(eur).of(1).classification)
    }

    @Test fun `importing the redemption again finds it known and does not count the income twice`() {
        val e = doc(mv(1, 1594, "Resgate Inv Fac"))
        val first = preview(e)
        val second = Reconciler.preview("b2", origin, e, KnownState(origins = Reconciler.originRecords(first, origin)), Decisions())
        assertEquals(Classification.AlreadyKnown, second.of(1).classification)
        assertNull(second.of(1).effect)
    }

    @Test fun `the rule works with and without the billing context`() {
        val withBilling = preview(doc(mv(1, 1594, "Resgate Inv Fac")), billing = BillingContext(app.accord.core.CardCycle(), null, null))
        assertEquals(ProposedEffect.CreateIncome(IncomeBasis.InvestmentCredit, "investimento"), withBilling.of(1).effect)
    }
}
