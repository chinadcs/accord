package app.accord.core.clues

import app.accord.core.Money
import app.accord.core.space.*
import java.time.LocalDate
import kotlin.test.*

class ClueDetectorTest {
    private val reference = LocalDate.of(2026,11,8)
    private val start = reference.minusDays(56)
    private fun evidence(id: String, day: LocalDate = reference.minusDays(1), kind: EntryKind = EntryKind.DirectPayment) = ClueEvidence(
        EntryRow(id, kind, "Mercado", "sub", Money(5000), day, null, PaymentMethod.Pix, null, null, emptySet(), "ana"),
        1, id.takeIf { kind == EntryKind.CardPurchase }, null, "sub", Origin.Import, false)
    private fun source(items: List<ClueEvidence>, coverage: List<ClueCoverage> = listOf(ClueCoverage("b", start, reference, true, "account"))) =
        ClueSource(start, reference, items, coverage)
    private fun counts(previous: Int, current: Int): ClueSource = source(
        (0 until previous).map { evidence("p$it", start.plusDays(it.toLong())) } +
            (0 until current).map { evidence("c$it", reference.minusDays(28).plusDays(it.toLong())) })

    @Test fun `same day amount and description yield one clue independent of input order`() {
        val items = listOf(evidence("a"), evidence("b"), evidence("c"))
        val result = ClueDetector.analyze(source(items), reference)
        assertEquals(1, result.clues.size)
        assertEquals(3, result.clues.single().evidence.size)
        assertEquals(result, ClueDetector.analyze(source(items.reversed()), reference))
    }

    @Test fun `different day value or description never produces similar charges`() {
        val a = evidence("a")
        for (b in listOf(evidence("b", reference.minusDays(2)), evidence("b").let { it.copy(entry = it.entry.copy(amount = Money(5001))) },
            evidence("b").let { it.copy(entry = it.entry.copy(description = "Padaria")) })) {
            assertTrue(ClueDetector.analyze(source(listOf(a,b)), reference).clues.isEmpty())
        }
    }

    @Test fun `installments recurrence settlement credits and pending imports do not duplicate purchases`() {
        val items = listOf(evidence("purchase", kind = EntryKind.CardPurchase),
            evidence("purchase#1", kind = EntryKind.CardInstallment), evidence("purchase#2", kind = EntryKind.CardInstallment),
            evidence("rec:1", kind = EntryKind.RecurrenceOccurrence), evidence("rec:2", kind = EntryKind.RecurrenceOccurrence),
            evidence("payment", kind = EntryKind.InvoicePayment), evidence("credit", kind = EntryKind.Refund).copy(refundOf = "purchase"),
            evidence("pending").copy(importPending = true))
        assertTrue(ClueDetector.analyze(source(items), reference).clues.isEmpty())
    }

    @Test fun `thresholds are conjunctive and conservative`() {
        for ((previous, current, expected) in listOf(Triple(3,6,true), Triple(4,8,true), Triple(4,6,false), Triple(2,6,false), Triple(0,6,false), Triple(3,5,false))) {
            assertEquals(expected, ClueDetector.analyze(counts(previous,current), reference).clues.any { it.kind == ClueKind.FrequencyIncrease }, "$previous -> $current")
        }
    }

    @Test fun `no declared coverage manual history pending or incomplete documents blocks frequency`() {
        val s = counts(3,6)
        for (limited in listOf(s.copy(coverage = emptyList()), s.copy(coverage = s.coverage.map { it.copy(reviewed = false) }),
            s.copy(coverage = s.coverage.map { it.copy(from = null) }),
            s.copy(evidence = s.evidence.map { it.copy(origin = Origin.Manual) }))) {
            val result = ClueDetector.analyze(limited, reference)
            assertFalse(result.frequencyCoverageAdequate)
            assertFalse(result.clues.any { it.kind == ClueKind.FrequencyIncrease })
        }
    }

    @Test fun `both periods need continuous coverage for each channel`() {
        val s = counts(3,6)
        val split = listOf(ClueCoverage("a", start, reference.minusDays(28), true, "account"),
            ClueCoverage("b", reference.minusDays(28), reference, true, "account"))
        assertTrue(ClueDetector.analyze(s.copy(coverage = split), reference).frequencyCoverageAdequate)
        assertFalse(ClueDetector.analyze(s.copy(coverage = split.map { if(it.batchId == "b") it.copy(from = reference.minusDays(27)) else it }), reference).frequencyCoverageAdequate)
        assertFalse(ClueDetector.analyze(s.copy(coverage = split + ClueCoverage("c", start, reference.minusDays(1), true, "card")), reference).frequencyCoverageAdequate)
    }

    @Test fun `today is excluded and the middle day belongs to current period`() {
        val s = counts(3,5)
        assertFalse(ClueDetector.analyze(s.copy(evidence = s.evidence + evidence("today", reference)), reference).clues.any { it.kind == ClueKind.FrequencyIncrease })
        val result = ClueDetector.analyze(s.copy(evidence = s.evidence + evidence("boundary", reference.minusDays(28))), reference)
        assertEquals(6, result.clues.single { it.kind == ClueKind.FrequencyIncrease }.currentCount)
    }

    @Test fun `relevant fingerprint ignores revision and author but changes with amount`() {
        val items = listOf(evidence("a"), evidence("b"))
        fun clue(e: List<ClueEvidence>) = ClueDetector.analyze(source(e), reference).clues.single()
        val before = clue(items)
        assertEquals(before.fingerprint, clue(items.map { it.copy(revision = 9, entry = it.entry.copy(authorId = "bia", categoryId = "other")) }).fingerprint)
        val changed = clue(items.map { it.copy(entry = it.entry.copy(amount = Money(6000))) })
        assertEquals(before.key, changed.key)
        assertNotEquals(before.fingerprint, changed.fingerprint)
    }
}
