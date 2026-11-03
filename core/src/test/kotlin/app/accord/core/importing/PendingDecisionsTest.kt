package app.accord.core.importing

import app.accord.core.CardCycle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-c9m.5: what the person can decide for a doubt that stayed open (confirm as new, credit as income, dismiss a repetition). */
class PendingDecisionsTest {
    private val account = "acc"

    private fun mv(ordinal: Int, cents: Long, description: String, fitid: String) =
        ExtractedMovement(ordinal, listOf("FITID" to fitid, "TRNAMT" to cents.toString()), LocalDate.of(2026, 10, 20 + ordinal), cents, "BRL", description, null, emptySet())

    private fun doc(vararg m: ExtractedMovement) =
        ExtractionResult("sha", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, DocumentKind.Account, null, null, null), m.toList(), 0, emptyList())

    private val billing = BillingContext(CardCycle(8, 15), null, "fees")

    private fun run(e: ExtractionResult, decisions: Decisions, known: KnownState = KnownState()) =
        Reconciler.preview("b1", account, e, known, decisions, billing = billing)

    private fun keys(e: ExtractionResult) = e.movements.map { Reconciler.key(account, it, e.movements) }

    private val divergent = doc(mv(1, -1000, "LOJA", "SAME"), mv(2, -2000, "LOJA", "SAME"))

    @Test fun `a divergent FITID stays a doubt for both movements until the person says they are new`() {
        val before = run(divergent, Decisions(defaultCategoryId = "cat"))
        assertTrue(before.items.all { DoubtReason.FitidDivergent in it.doubts })

        val keys = keys(divergent)
        val after = run(divergent, Decisions(defaultCategoryId = "cat", confirmedNew = setOf(keys[0])))
        assertEquals(Classification.New, after.items[0].classification, "the one confirmed is new")
        assertEquals(Classification.Doubt, after.items[1].classification, "the other keeps asking")
    }

    @Test fun `a movement removed before is recreated only when the person confirms it as new`() {
        val e = doc(mv(1, -1000, "LOJA", "F1"))
        val key = keys(e).single()
        val removed = KnownState(origins = listOf(KnownOrigin(key, account, "F1", LocalDate.of(2026, 10, 21), -1000, "BRL", "LOJA", "old", entryRemoved = true)))
        assertEquals(setOf(DoubtReason.RemovedPreviously), run(e, Decisions(defaultCategoryId = "cat"), removed).items.single().doubts)
        val confirmed = run(e, Decisions(defaultCategoryId = "cat", confirmedNew = setOf(key)), removed).items.single()
        assertEquals(Classification.New, confirmed.classification)
        assertEquals(ProposedEffect.CreateEntry("cat", null), confirmed.effect)
    }

    @Test fun `a credit is income only when the person chooses the income category, never from the sign`() {
        val e = doc(mv(1, 9900, "TRANSFERENCIA RECEBIDA", "C1"))
        assertEquals(setOf(DoubtReason.CreditToClassify), run(e, Decisions()).items.single().doubts)
        val chosen = run(e, Decisions(incomeByKey = mapOf(keys(e).single() to "inc-salary"))).items.single()
        assertEquals(Classification.New, chosen.classification)
        assertEquals(ProposedEffect.CreateIncome(IncomeBasis.ChosenByPerson, null, "inc-salary"), chosen.effect)
        assertNull(chosen.categoryId, "an income has no expense category")
    }

    @Test fun `dismissing a repetition creates nothing and is remembered as effected`() {
        val key = keys(divergent)[1]
        val p = run(divergent, Decisions(defaultCategoryId = "cat", dismissed = setOf(key)))
        val item = p.items[1]
        assertEquals(Classification.AlreadyKnown, item.classification)
        assertTrue(item.dismissed)
        assertTrue(item.doubts.isEmpty())
        assertNull(item.effect)

        val steps = ImportSteps.build(p, DocumentKind.Account, account, "space", billing)
        assertEquals(Resolution.Dismissed, steps.steps[1].resolution)
        assertTrue(steps.steps[1].commands.isEmpty())
        assertEquals(1, steps.summary.dismissed)
        assertFalse(steps.steps[0].resolution == Resolution.Dismissed)
    }

    @Test fun `a dismissed movement in a foreign currency file is still blocked by the currency`() {
        val e = ExtractionResult("sha", DeclaredMetadata(SourceFormat.Ofx, "102", "EUR", null, null, DocumentKind.Account, null, null, null), listOf(mv(1, -1000, "SHOP", "E1")), 0, emptyList())
        val p = run(e, Decisions(dismissed = setOf(keys(e).single())))
        assertEquals(Classification.BlockedByCurrency, p.items.single().classification)
    }
}
