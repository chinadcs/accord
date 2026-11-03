package app.accord.core.importing

import java.time.LocalDate
import kotlin.test.*

/** accord-c9m.2: reconciliation preview rules 1-7, 12 and 15 of the S4 spec. Synthetic movements only. */
class ReconcilerTest {
    private val origin = "itau-conta"
    private val d17 = LocalDate.of(2026, 10, 17)

    private fun mv(ordinal: Int, fitid: String?, date: LocalDate?, cents: Long?, description: String = "MERCADO", currency: String? = "BRL",
        doubts: Set<MovementDoubt> = emptySet(), installment: InstallmentHint? = null): ExtractedMovement {
        val fields = buildList {
            if (fitid != null) add("FITID" to fitid)
            add("DTPOSTED" to (date?.toString()?.replace("-", "") ?: "xx"))
            add("TRNAMT" to (cents?.let { "%d.%02d".format(it / 100, kotlin.math.abs(it % 100)) } ?: "abc"))
            add("MEMO" to description)
        }
        return ExtractedMovement(ordinal, fields, date, cents, currency, description, installment, doubts)
    }

    private fun extraction(vararg movements: ExtractedMovement, currency: String? = "BRL", kind: DocumentKind = DocumentKind.Account) = ExtractionResult(
        "sha", DeclaredMetadata(SourceFormat.Ofx, "102", currency, null, null, kind, null, null, null), movements.toList(), 0, emptyList())

    private fun preview(e: ExtractionResult, known: KnownState = KnownState(), decisions: Decisions = Decisions(defaultCategoryId = "cat"), batch: String = "b1") =
        Reconciler.preview(batch, origin, e, known, decisions)

    private fun Preview.of(ordinal: Int) = items.single { it.movement.ordinal == ordinal }

    // ---- counts and the 12 = 7 + 3 + 2 scenario ----

    @Test fun `twelve movements are seven new, three safe links and two doubts and the counts add up`() {
        val movements = (1..12).map { i ->
            when (i) {
                11 -> mv(i, "F$i", d17, 5000, "CREDITO") // a credit: always a doubt
                12 -> mv(i, "F$i", d17, -1200, "SEM CATEGORIA")
                else -> mv(i, "F$i", d17.plusDays(i.toLong()), -1000L * i, "LOJA $i")
            }
        }
        val entries = listOf(8, 9, 10).map { ExistingEntry("e$it", origin, d17.plusDays(it.toLong()), -1000L * it, "loja $it") }
        val decisions = Decisions(categoryByKey = (1..7).associate { Reconciler.key(origin, movements[it - 1], movements) to "cat" })
        val p = preview(extraction(*movements.toTypedArray()), KnownState(entries = entries), decisions)
        assertEquals(PreviewCounts(alreadyKnown = 0, safeLinks = 3, new = 7, doubts = 2, blocked = 0), p.counts)
        assertEquals(12, p.counts.total)
        assertEquals(10, p.counts.checked)
        assertEquals(setOf("e8", "e9", "e10"), p.items.mapNotNull { it.linkedEntryId }.toSet())
        assertEquals(setOf(DoubtReason.CreditToClassify), p.of(11).doubts)
        assertEquals(setOf(DoubtReason.CategoryUnknown), p.of(12).doubts)
        assertEquals(BatchState.InReview, p.state)
    }

    @Test fun `importing again reports the same movements as known, the same doubts and nothing new`() {
        val movements = (1..5).map { mv(it, "F$it", d17.plusDays(it.toLong()), -1000L * it, "LOJA $it") } + mv(6, "F6", d17, 700, "CREDITO")
        val e = extraction(*movements.toTypedArray())
        val first = preview(e)
        assertEquals(5, first.counts.new)
        val known = KnownState(origins = Reconciler.originRecords(first, origin))
        val second = preview(e, known, batch = "b2")
        assertEquals(PreviewCounts(alreadyKnown = 5, safeLinks = 0, new = 0, doubts = 1, blocked = 0), second.counts)
        assertEquals(first.items.map { it.key }, second.items.map { it.key })
    }

    // ---- origin key (rule 1) ----

    @Test fun `the key is deterministic, tied to origin, fitid, day, cents and currency, and does not depend on the ordinal or time of day`() {
        val a = mv(1, "F1", d17, -500)
        val b = mv(9, "F1", d17, -500, "OUTRA DESCRICAO")
        assertEquals(Reconciler.key(origin, a, listOf(a)), Reconciler.key(origin, b, listOf(b)))
        assertNotEquals(Reconciler.key(origin, a, listOf(a)), Reconciler.key("outra-origem", a, listOf(a)))
        assertNotEquals(Reconciler.key(origin, a, listOf(a)), Reconciler.key(origin, mv(1, "F1", d17, -501), listOf(a)))
        assertNotEquals(Reconciler.key(origin, a, listOf(a)), Reconciler.key(origin, mv(1, "F1", d17, -500, currency = "USD"), listOf(a)))
    }

    @Test fun `without a fitid identical lines in one document get distinct keys by their order, stable across exports`() {
        val first = mv(1, null, d17, -5000, "Mercado")
        val second = mv(2, null, d17, -5000, "Mercado")
        val keys = listOf(first, second).map { Reconciler.key(origin, it, listOf(first, second)) }
        assertEquals(2, keys.toSet().size)
        // A later export that has only the first purchase recognises it; the second purchase would be new.
        assertEquals(keys[0], Reconciler.key(origin, first, listOf(first)))
        // Spaces in the description do not change the key.
        assertEquals(keys[0], Reconciler.key(origin, mv(1, null, d17, -5000, "  Mercado "), listOf(first)))
    }

    // ---- legitimate equal purchases and manual entries (rules 5, 6) ----

    @Test fun `two equal purchases in the file are two new movements`() {
        val withFitid = preview(extraction(mv(1, "A", d17, -5000, "Mercado"), mv(2, "B", d17, -5000, "Mercado")))
        assertEquals(2, withFitid.counts.new)
        val withoutFitid = preview(extraction(mv(1, null, d17, -5000, "Mercado"), mv(2, null, d17, -5000, "Mercado")))
        assertEquals(2, withoutFitid.counts.new)
    }

    @Test fun `one equal manual entry and two equal movements never link on their own`() {
        val entry = ExistingEntry("m1", origin, d17, -5000, "mercado")
        val p = preview(extraction(mv(1, "A", d17, -5000, "Mercado"), mv(2, "B", d17, -5000, "Mercado")), KnownState(entries = listOf(entry)))
        assertEquals(listOf(Classification.Doubt, Classification.Doubt), p.items.map { it.classification })
        assertTrue(p.items.all { DoubtReason.PossibleMatch in it.doubts && it.linkedEntryId == null && it.candidateEntryIds == listOf("m1") })
    }

    @Test fun `saying they are different purchases keeps both movements and is not asked again`() {
        val e = extraction(mv(1, "A", d17, -5000, "Mercado"), mv(2, "B", d17, -5000, "Mercado"))
        val entry = ExistingEntry("m1", origin, d17, -5000, "mercado")
        val keys = e.movements.map { Reconciler.key(origin, it, e.movements) }
        val known = KnownState(entries = listOf(entry), distinctPairs = keys.map { DistinctPair(it, "m1") }.toSet())
        val p = preview(e, known)
        assertEquals(2, p.counts.new)
        assertEquals(0, p.counts.doubts)
    }

    @Test fun `a single equal movement and a single equal entry link safely, keeping the entry identity`() {
        val p = preview(extraction(mv(1, "A", d17, -5000, "Mercado")), KnownState(entries = listOf(ExistingEntry("m1", origin, d17, -5000, "x"))))
        assertEquals(Classification.SafeLink, p.of(1).classification)
        assertEquals("m1", p.of(1).linkedEntryId)
    }

    @Test fun `anything less than a clean pair is a possible match`() {
        val e = extraction(mv(1, "A", d17, -5000, "Mercado Central"))
        fun doubt(vararg entries: ExistingEntry) = preview(e, KnownState(entries = entries.toList())).of(1)
        // next day, same value
        assertEquals(setOf(DoubtReason.PossibleMatch), doubt(ExistingEntry("m1", origin, d17.plusDays(1), -5000, "x")).doubts)
        // same day, other value, shared word in the description
        assertEquals(setOf(DoubtReason.PossibleMatch), doubt(ExistingEntry("m1", origin, d17, -4990, "compras no mercado")).doubts)
        // two candidates
        assertEquals(listOf("m1", "m2"), doubt(ExistingEntry("m1", origin, d17, -5000, "x"), ExistingEntry("m2", origin, d17, -5000, "y")).candidateEntryIds)
        // exact entry plus a near one disputes the pair
        assertEquals(Classification.Doubt, doubt(ExistingEntry("m1", origin, d17, -5000, "x"), ExistingEntry("m2", origin, d17.plusDays(2), -5000, "y")).classification)
    }

    @Test fun `unrelated entries are not candidates`() {
        val e = extraction(mv(1, "A", d17, -5000, "Mercado"))
        val known = KnownState(entries = listOf(
            ExistingEntry("other-origin", "outra-conta", d17, -5000, "mercado"),
            ExistingEntry("far", origin, d17.plusDays(30), -5000, "mercado"),
            ExistingEntry("same-day-other-thing", origin, d17, -9999, "padaria"),
            ExistingEntry("has-origin", origin, d17, -5000, "mercado", hasOrigin = true),
        ))
        val p = preview(e, known)
        assertEquals(Classification.New, p.of(1).classification)
        assertTrue(p.of(1).candidateEntryIds.isEmpty())
    }

    @Test fun `one entry wanted by two movements is a doubt for both`() {
        val e = extraction(mv(1, "A", d17, -5000, "Mercado"), mv(2, "B", d17, -5000, "Mercado"))
        assertTrue(preview(e, KnownState(entries = listOf(ExistingEntry("m1", origin, d17, -5000, "x")))).items.all { it.classification == Classification.Doubt })
    }

    @Test fun `a link chosen by the person is a link, even when the automatic rules would only suspect`() {
        val e = extraction(mv(1, "A", d17, -5000, "Mercado"))
        val key = Reconciler.key(origin, e.movements[0], e.movements)
        val p = preview(e, KnownState(entries = listOf(ExistingEntry("m1", origin, d17.plusDays(1), -5000, "x"))), Decisions(forcedLinks = mapOf(key to "m1"), defaultCategoryId = "cat"))
        assertEquals(Classification.SafeLink, p.of(1).classification)
        assertEquals("m1", p.of(1).linkedEntryId)
    }

    // ---- fitid divergence, removed origin, repetition (rules 2-4) ----

    @Test fun `the same fitid with another date or amount inside the file is a doubt for every movement involved`() {
        val flagged = setOf(MovementDoubt.RepeatedFitidInFile)
        val p = preview(extraction(mv(1, "X", d17, -100, doubts = flagged), mv(2, "X", d17.plusDays(5), -250, doubts = flagged), mv(3, "Y", d17, -300)))
        assertEquals(setOf(DoubtReason.FitidDivergent), p.of(1).doubts)
        assertEquals(setOf(DoubtReason.FitidDivergent), p.of(2).doubts)
        assertEquals(Classification.New, p.of(3).classification)
        assertEquals(3, p.counts.total)
    }

    @Test fun `the same fitid with the same day and amount twice is a possible repetition and both stay`() {
        val flagged = setOf(MovementDoubt.RepeatedFitidInFile)
        val p = preview(extraction(mv(1, "X", d17, -100, doubts = flagged), mv(2, "X", d17, -100, doubts = flagged)))
        assertEquals(listOf(setOf(DoubtReason.PossibleRepetition), setOf(DoubtReason.PossibleRepetition)), p.items.map { it.doubts })
        assertEquals(2, p.items.map { it.key }.toSet().size)
    }

    @Test fun `a fitid already recorded with another day or amount is divergent against the earlier record`() {
        val earlier = KnownOrigin("k0", origin, "X", d17, -100, "BRL", "MERCADO", "b0")
        val p = preview(extraction(mv(1, "X", d17.plusDays(1), -100)), KnownState(origins = listOf(earlier)))
        assertEquals(setOf(DoubtReason.FitidDivergent), p.of(1).doubts)
        val sameOtherOrigin = preview(extraction(mv(1, "X", d17.plusDays(1), -100)), KnownState(origins = listOf(earlier.copy(originId = "outra"))))
        assertEquals(Classification.New, sameOtherOrigin.of(1).classification)
    }

    @Test fun `a key of a removed entry is a doubt and is never recreated silently`() {
        val m = mv(1, "F", d17, -100)
        val key = Reconciler.key(origin, m, listOf(m))
        val p = preview(extraction(m), KnownState(origins = listOf(KnownOrigin(key, origin, "F", d17, -100, "BRL", "MERCADO", "b0", entryRemoved = true))))
        assertEquals(setOf(DoubtReason.RemovedPreviously), p.of(1).doubts)
    }

    @Test fun `same fields in another batch under a different key is a possible repetition`() {
        val earlier = KnownOrigin("k0", origin, "OLD", d17, -100, "BRL", "MERCADO", "b0")
        val p = preview(extraction(mv(1, "NEW", d17, -100, "MERCADO")), KnownState(origins = listOf(earlier)))
        assertEquals(setOf(DoubtReason.PossibleRepetition), p.of(1).doubts)
        // The same batch does not count against itself.
        assertEquals(Classification.New, preview(extraction(mv(1, "NEW", d17, -100, "MERCADO")), KnownState(origins = listOf(earlier.copy(batchId = "b1")))).of(1).classification)
    }

    // ---- credits, zero, unread values (rule 12 and the reader's doubts) ----

    @Test fun `every credit without a known key is a doubt even with an equal entry, and never income by sign`() {
        val e = extraction(mv(1, "C", d17, 7000, "PIX RECEBIDO"))
        val p = preview(e, KnownState(entries = listOf(ExistingEntry("m1", origin, d17, 7000, "x"))))
        assertEquals(setOf(DoubtReason.CreditToClassify), p.of(1).doubts)
        assertNull(p.of(1).linkedEntryId)
    }

    @Test fun `a zero line and unread values are doubts that name the reason`() {
        val p = preview(extraction(mv(1, "Z", d17, 0, "Saldo restante da fatura anterior"),
            mv(2, "A", d17, null, doubts = setOf(MovementDoubt.AmountNotInterpreted)), mv(3, "B", null, -100, doubts = setOf(MovementDoubt.DateNotInterpreted))))
        assertEquals(setOf(DoubtReason.ZeroAmount), p.of(1).doubts)
        assertEquals(setOf(DoubtReason.AmountNotInterpreted), p.of(2).doubts)
        assertEquals(setOf(DoubtReason.DateNotInterpreted), p.of(3).doubts)
        assertEquals(3, p.counts.doubts)
    }

    // ---- currency (rule 15) ----

    @Test fun `a document in another currency is diagnostic only and every movement is blocked but still shows its doubts`() {
        val flagged = setOf(MovementDoubt.RepeatedFitidInFile)
        val p = preview(extraction(mv(1, "X", d17, -100, currency = "EUR", doubts = flagged), mv(2, "X", d17.plusDays(3), -200, currency = "EUR", doubts = flagged), mv(3, "Y", d17, 900, currency = "EUR"), currency = "EUR"))
        assertEquals(BatchState.DiagnosticOnly, p.state)
        assertEquals(PreviewCounts(alreadyKnown = 0, safeLinks = 0, new = 0, doubts = 0, blocked = 3), p.counts)
        assertEquals(setOf(DoubtReason.FitidDivergent), p.of(1).doubts)
        assertEquals(setOf(DoubtReason.FitidDivergent), p.of(2).doubts)
        assertTrue(p.items.all { it.classification == Classification.BlockedByCurrency && it.linkedEntryId == null })
    }

    @Test fun `an undeclared document currency is blocked, not assumed`() {
        val p = preview(extraction(mv(1, "F", d17, -100), currency = null))
        assertEquals(BatchState.DiagnosticOnly, p.state)
        assertEquals(Classification.BlockedByCurrency, p.of(1).classification)
    }

    @Test fun `a foreign movement inside a Brazilian document is a doubt and is not applied`() {
        val p = preview(extraction(mv(1, "F", d17, -100, currency = "USD"), mv(2, "G", d17, -200)))
        assertEquals(setOf(DoubtReason.ForeignCurrencyMovement), p.of(1).doubts)
        assertEquals(Classification.New, p.of(2).classification)
        assertEquals(BatchState.InReview, p.state)
    }

    // ---- new needs a category (rule 14, without suggestions) ----

    @Test fun `a new movement without a chosen or default category is a doubt`() {
        val p = preview(extraction(mv(1, "F", d17, -100)), decisions = Decisions())
        assertEquals(setOf(DoubtReason.CategoryUnknown), p.of(1).doubts)
        val key = Reconciler.key(origin, mv(1, "F", d17, -100), listOf(mv(1, "F", d17, -100)))
        assertEquals(Classification.New, preview(extraction(mv(1, "F", d17, -100)), decisions = Decisions(categoryByKey = mapOf(key to "lazer"))).of(1).classification)
    }

    @Test fun `the record to persist for each confirmed movement carries the key and the original values`() {
        val movements = listOf(mv(1, "F1", d17, -100, "A"), mv(2, "F2", d17, 50, "B"))
        val p = preview(extraction(*movements.toTypedArray()))
        val records = Reconciler.originRecords(p, origin)
        assertEquals(1, records.size) // the credit is a doubt, not effected
        assertEquals(KnownOrigin(p.of(1).key, origin, "F1", d17, -100, "BRL", "A", "b1"), records.single())
    }
}
