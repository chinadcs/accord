package app.accord.persistence

import app.accord.core.importing.BatchSource
import app.accord.core.importing.Classification as ImportClassification
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.ResolveOutcome
import app.accord.core.importing.Resolution
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.DeterministicIds
import app.accord.core.space.PayInvoice
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-c9m.9: "review later", "repetition of another movement of this file" and a step the validator refuses not blocking the batch. */
class ImportHoldAndRepeatTest : SpaceTestBase() {
    private val groceries get() = seed("alimentacao.mercado")
    private val cardId get() = DeterministicIds.of(spaceId, "card", "main")
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()

    private suspend fun Handle.account(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, fitid: String? = "F$ordinal") =
        ExtractedMovement(ordinal, listOfNotNull(fitid?.let { "FITID" to it }, "TRNAMT" to cents.toString()), date, cents, "BRL", description, null, emptySet())

    private fun doc(kind: DocumentKind, sha: String, vararg m: ExtractedMovement) =
        ExtractionResult(sha, DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, kind, null, null, null), m.toList(), 0, emptyList())

    private suspend fun Handle.start(id: String, extraction: ExtractionResult, source: BatchSource) =
        (imports().start(StartImport(id, "$id.ofx", 10, extraction, source)).value() as StartOutcome.Started).info

    private suspend fun Handle.review(id: String) = checkNotNull(imports().review(id).value())

    private suspend fun Handle.decide(id: String, d: ImportDecision) =
        imports().decide(id, d, review(id).info.revision).value()

    private suspend fun Handle.confirm(id: String) = imports().confirm(id, "c-$id", review(id).token).value()

    // ---- revisar depois ----

    @Test fun `review later holds a clear movement out of the confirmation and keeps it as a pending item`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", doc(DocumentKind.Account, "s1", mv(1, d(2026, 10, 20), -1234, "MERCADO"), mv(2, d(2026, 10, 21), -900, "PADARIA")), BatchSource(DocumentKind.Account, account))
        h.decide("b1", ImportDecision.DefaultCategory(groceries))
        assertEquals(2, h.review("b1").preview.counts.new)

        assertIs<DecideOutcome.Applied>(h.decide("b1", ImportDecision.ReviewLater(2)))
        val review = h.review("b1")
        val held = review.preview.items.first { it.movement.ordinal == 2 }
        assertEquals(ImportClassification.Doubt, held.classification)
        assertTrue(DoubtReason.ReviewLater in held.doubts)
        assertEquals(1, review.preview.counts.checked)
        assertEquals(1, review.summary.pendingDoubts)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("b1")).result
        assertEquals(listOf("MERCADO"), h.space.movements(oct).value().map { it.description }, "the held movement creates nothing")
        assertEquals(Resolution.Pending, result.items.first { it.ordinal == 2 }.resolution)
        assertTrue(DoubtReason.ReviewLater in result.items.first { it.ordinal == 2 }.doubts)
        assertEquals("PADARIA", result.items.first { it.ordinal == 2 }.description, "the original stays")
    }

    @Test fun `a held movement shows in the queue as postponed and ready, and applying it later creates it`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", doc(DocumentKind.Account, "s1", mv(1, d(2026, 10, 20), -1234, "MERCADO"), mv(2, d(2026, 10, 21), -900, "PADARIA")), BatchSource(DocumentKind.Account, account))
        h.decide("b1", ImportDecision.DefaultCategory(groceries))
        h.decide("b1", ImportDecision.ReviewLater(2))
        assertIs<ConfirmOutcome.Applied>(h.confirm("b1"))

        val pending = h.imports().pendings().value().single()
        assertEquals(2, pending.ordinal)
        assertTrue(pending.postponed)
        assertTrue(pending.ready, "the category it would have had stays with it; only the hold is gone")

        val token = checkNotNull(h.imports().pending("b1", 2).value()).token
        assertEquals(Resolution.Created, assertIs<ResolveOutcome.Applied>(h.imports().resolve("b1", 2, "op", token).value()).resolution)
        assertEquals(2, h.space.movements(oct).value().size)
        assertEquals(emptyList(), h.imports().pendings().value())
    }

    @Test fun `going back to the automatic proposal undoes the hold before confirming`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", doc(DocumentKind.Account, "s1", mv(1, d(2026, 10, 20), -1234, "MERCADO")), BatchSource(DocumentKind.Account, account))
        h.decide("b1", ImportDecision.DefaultCategory(groceries))
        h.decide("b1", ImportDecision.ReviewLater(1))
        assertEquals(ImportClassification.Doubt, h.review("b1").preview.items.single().classification)
        h.decide("b1", ImportDecision.Reset(1))
        assertEquals(ImportClassification.New, h.review("b1").preview.items.single().classification)
    }

    // ---- repetição de outro movimento do arquivo ----

    private suspend fun Handle.twoIdenticalLines(account: String, id: String = "b1", sha: String = "sa"): String {
        start(id, doc(DocumentKind.Account, sha, mv(1, d(2026, 10, 20), -2500, "CAFE", null), mv(2, d(2026, 10, 20), -2500, "CAFE", null), mv(3, d(2026, 10, 22), -300, "PAO")), BatchSource(DocumentKind.Account, account))
        decide(id, ImportDecision.DefaultCategory(groceries))
        return id
    }

    @Test fun `marking a line as a repetition of another keeps both originals and creates only one entry`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.twoIdenticalLines(account)
        assertEquals(3, h.review(id).preview.counts.new, "two identical lines are two purchases until the person says otherwise")

        assertIs<DecideOutcome.Applied>(h.decide(id, ImportDecision.RepeatOf(2, 1)))
        val second = h.review(id).preview.items.first { it.movement.ordinal == 2 }
        assertTrue(second.dismissed)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm(id)).result
        assertEquals(2, h.space.movements(oct).value().size, "CAFE once and PAO")
        val row2 = result.items.first { it.ordinal == 2 }
        assertEquals(Resolution.Dismissed, row2.resolution)
        assertEquals(1, row2.repeatOf)
        assertEquals(Resolution.Created, result.items.first { it.ordinal == 1 }.resolution)
        assertEquals("CAFE", row2.description, "the repeated line's original is kept")
        assertEquals(2, h.database.db.dao().importMovements(id).count { it.description == "CAFE" }, "both originals stay")
    }

    @Test fun `a re-import of the same lines recognises both, the repetition included`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.twoIdenticalLines(account)
        h.decide(id, ImportDecision.RepeatOf(2, 1))
        assertIs<ConfirmOutcome.Applied>(h.confirm(id))

        h.start("b2", doc(DocumentKind.Account, "sb", mv(1, d(2026, 10, 20), -2500, "CAFE", null), mv(2, d(2026, 10, 20), -2500, "CAFE", null), mv(3, d(2026, 10, 22), -300, "PAO")), BatchSource(DocumentKind.Account, account))
        assertEquals(3, h.review("b2").preview.counts.alreadyKnown)
    }

    @Test fun `the original must be another movement, not a repetition, and not the original of someone else`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.twoIdenticalLines(account)
        assertIs<DecideOutcome.Rejected>(h.decide(id, ImportDecision.RepeatOf(1, 1)), "itself")
        assertIs<DecideOutcome.Rejected>(h.decide(id, ImportDecision.RepeatOf(1, 99)), "unknown")
        assertIs<DecideOutcome.Applied>(h.decide(id, ImportDecision.RepeatOf(2, 1)))
        assertIs<DecideOutcome.Rejected>(h.decide(id, ImportDecision.RepeatOf(3, 2)), "2 is itself a repetition")
        assertIs<DecideOutcome.Rejected>(h.decide(id, ImportDecision.RepeatOf(1, 2)), "1 is the original of 2, and 2 is a repetition")
    }

    @Test fun `a repetition replaces the hold, and going back to the automatic proposal removes the repetition`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.twoIdenticalLines(account)
        h.decide(id, ImportDecision.ReviewLater(2))
        h.decide(id, ImportDecision.RepeatOf(2, 1))
        assertTrue(h.review(id).preview.items.first { it.movement.ordinal == 2 }.dismissed, "the repetition beats the hold")
        h.decide(id, ImportDecision.Reset(2))
        assertEquals(ImportClassification.New, h.review(id).preview.items.first { it.movement.ordinal == 2 }.classification)
    }

    // ---- passo que o validador recusaria ----

    @Test fun `a step the validator would refuse is kept as a doubt and does not block the rest of the batch`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        // A purchase close to the closing date, estimated on the October invoice, which is then conferred and paid in full.
        val purchase = h.space.execute(h.env(), RegisterCardPurchase("loja", Classification.Category(groceries), d(2026, 10, 7), brl(68))).applied().changes.single().id
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(68))).applied()
        h.space.execute(h.env(), PayInvoice(oct, brl(68), d(2026, 10, 15), account)).applied()

        // The November document says that purchase is on the November invoice: moving it would touch the paid October invoice.
        h.start("c1", doc(DocumentKind.Card, "card-sha", mv(1, d(2026, 10, 7), -6800, "LOJA"), mv(2, d(2026, 10, 20), -1000, "PADARIA")), BatchSource(DocumentKind.Card, cardId, nov))
        h.decide("c1", ImportDecision.DefaultCategory(groceries))
        val review = h.review("c1")
        val refused = review.preview.items.first { it.movement.ordinal == 1 }
        assertEquals(ImportClassification.Doubt, refused.classification)
        assertTrue(DoubtReason.CannotApply in refused.doubts)
        assertEquals(emptySet(), review.blockers, "the batch is not blocked")
        assertEquals(1, review.preview.counts.new)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("c1")).result
        assertEquals(Resolution.Pending, result.items.first { it.ordinal == 1 }.resolution)
        assertEquals(Resolution.Created, result.items.first { it.ordinal == 2 }.resolution)
        assertEquals(oct, h.space.purchase(purchase).value()!!.installments.single().invoiceMonth, "the purchase on the paid invoice was left alone")
        assertNull(h.database.db.dao().importMovements("c1").first { it.ordinal == 1 }.targetId)
    }
}
