package app.accord.persistence

import app.accord.core.importing.BatchSource
import app.accord.core.importing.Classification as ImportClassification
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.PendingDetail
import app.accord.core.importing.ResolveOutcome
import app.accord.core.importing.Resolution
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.sync.PullPage
import app.accord.core.sync.RemoteEntityState
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-c9m.5: doubts left in a confirmed batch are read again, decided one by one and applied through the same commands. */
class ImportPendingsTest : SpaceTestBase() {
    private val groceries get() = seed("alimentacao.mercado")
    private val oct = ym(2026, 10)

    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()

    private suspend fun Handle.account(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, fitid: String = "F$ordinal") =
        ExtractedMovement(ordinal, listOf("FITID" to fitid, "TRNAMT" to cents.toString()), date, cents, "BRL", description, null, emptySet())

    private fun doc(sha: String, vararg m: ExtractedMovement) =
        ExtractionResult(sha, DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, DocumentKind.Account, null, null, null), m.toList(), 0, emptyList())

    /** Starts and confirms a batch whose only clear movement is the first one; the rest stay open. Returns the batch id. */
    private suspend fun Handle.confirmedWith(account: String, id: String, extraction: ExtractionResult, categoryFor: Int? = 1): String {
        val info = (imports().start(StartImport(id, "$id.ofx", 10, extraction, BatchSource(DocumentKind.Account, account))).value() as StartOutcome.Started).info
        if (categoryFor != null) imports().decide(id, ImportDecision.Category(categoryFor, groceries), info.revision)
        val review = checkNotNull(imports().review(id).value())
        assertIs<ConfirmOutcome.Applied>(imports().confirm(id, "c-$id", review.token).value())
        return id
    }

    private suspend fun Handle.detail(batchId: String, ordinal: Int): PendingDetail = checkNotNull(imports().pending(batchId, ordinal).value())

    private suspend fun Handle.revision(batchId: String) = checkNotNull(imports().batches().value().first { it.id == batchId }).revision

    private suspend fun Handle.decide(batchId: String, d: ImportDecision) =
        assertIs<DecideOutcome.Applied>(imports().decide(batchId, d, revision(batchId)).value())

    private suspend fun Handle.resolve(batchId: String, ordinal: Int, operation: String = "res-$batchId-$ordinal") =
        imports().resolve(batchId, ordinal, operation, detail(batchId, ordinal).token).value()

    private fun twoMovements(sha: String = "sha") = doc(sha, mv(1, d(2026, 10, 20), -1234, "MERCADO"), mv(2, d(2026, 10, 21), -900, "PADARIA"))

    @Test fun `a doubt left in a confirmed batch is listed, and becomes ready only after the missing choice`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.confirmedWith(account, "b1", twoMovements())
        val open = h.imports().pendings().value()
        val item = open.single()
        assertEquals(2, item.ordinal)
        assertEquals(setOf(DoubtReason.CategoryUnknown), item.doubts)
        assertEquals(false, item.ready)
        assertEquals(1, h.space.movements(oct).value().size)

        val notReady = h.resolve(id, 2)
        assertIs<ResolveOutcome.NotReady>(notReady)
        assertEquals(1, h.space.movements(oct).value().size, "nothing applied without the choice")

        h.decide(id, ImportDecision.Category(2, groceries))
        assertEquals(true, h.imports().pendings().value().single().ready)
    }

    @Test fun `resolving applies the same commands, records the result and the operation, and clears the queue`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.confirmedWith(account, "b1", twoMovements())
        val opsBefore = h.space.operations().value().size
        h.decide(id, ImportDecision.Category(2, groceries))

        val outcome = assertIs<ResolveOutcome.Applied>(h.resolve(id, 2))
        assertEquals(Resolution.Created, outcome.resolution)
        assertEquals(setOf("MERCADO", "PADARIA"), h.space.movements(oct).value().map { it.description }.toSet())
        assertEquals(emptyList(), h.imports().pendings().value())
        assertNull(h.imports().pending(id, 2).value(), "no longer open")

        val result = checkNotNull(h.imports().result(id).value())
        assertEquals(2, result.count(Resolution.Created))
        assertTrue(result.complete)
        assertEquals(emptyList(), h.database.db.dao().importDecisions(id), "the local decisions are gone")

        val ops = h.space.operations(afterSequence = opsBefore.toLong()).value()
        assertEquals(listOf("RegisterDirectPayment", "ResolveImportItem"), ops.map { it.commandType })
        assertEquals(EntityType.ImportBatch, ops.last().changes.single().type)
        assertEquals(id, ops.last().changes.single().id)
    }

    @Test fun `repeating the same operation id applies nothing twice`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.confirmedWith(account, "b1", twoMovements())
        h.decide(id, ImportDecision.Category(2, groceries))
        val token = h.detail(id, 2).token
        assertIs<ResolveOutcome.Applied>(h.imports().resolve(id, 2, "same-op", token).value())
        assertIs<ResolveOutcome.AlreadyApplied>(h.imports().resolve(id, 2, "same-op", token).value())
        assertEquals(2, h.space.movements(oct).value().size)
    }

    @Test fun `evidence that changed after the person looked is returned for a new look and nothing is applied`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.confirmedWith(account, "b1", twoMovements())
        h.decide(id, ImportDecision.Category(2, groceries))
        val seen = h.detail(id, 2).token
        // Someone registers the same payment by hand meanwhile.
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "padaria", Classification.Category(groceries), d(2026, 10, 21), app.accord.core.Money(900), account)).applied()

        val outcome = h.imports().resolve(id, 2, "op", seen).value()
        assertIs<ResolveOutcome.ReviewNeeded>(outcome)
        assertEquals(2, h.space.movements(oct).value().size, "only the first import and the manual entry exist")
    }

    @Test fun `a failure in the middle leaves the doubt open and trying again works`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.confirmedWith(account, "b1", twoMovements())
        h.decide(id, ImportDecision.Category(2, groceries))
        val token = h.detail(id, 2).token
        val opsBefore = h.space.operations().value().size
        h.fault = { error("falha injetada") }
        assertIs<QueryResult.Failure>(h.imports().resolve(id, 2, "op-1", token))
        h.fault = null
        assertEquals(1, h.space.movements(oct).value().size)
        assertEquals(opsBefore, h.space.operations().value().size)
        assertEquals(1, h.imports().pendings().value().size)
        assertIs<ResolveOutcome.Applied>(h.imports().resolve(id, 2, "op-1", token).value())
        assertEquals(2, h.space.movements(oct).value().size)
    }

    @Test fun `a divergent FITID pair is decided one by one - one is new, the other is a repetition that is remembered`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val extraction = doc("sha-d", mv(1, d(2026, 10, 20), -1000, "LOJA", "SAME"), mv(2, d(2026, 10, 21), -2000, "LOJA", "SAME"), mv(3, d(2026, 10, 22), -300, "CAFE"))
        val id = h.confirmedWith(account, "b1", extraction, categoryFor = 3)
        assertEquals(setOf(1, 2), h.imports().pendings().value().map { it.ordinal }.toSet())
        assertTrue(h.imports().pendings().value().all { DoubtReason.FitidDivergent in it.doubts })

        h.decide(id, ImportDecision.ConfirmNew(1))
        h.decide(id, ImportDecision.Category(1, groceries))
        h.decide(id, ImportDecision.Dismiss(2))
        assertEquals(Resolution.Created, assertIs<ResolveOutcome.Applied>(h.resolve(id, 1)).resolution)
        assertEquals(Resolution.Dismissed, assertIs<ResolveOutcome.Applied>(h.resolve(id, 2)).resolution)
        assertEquals(2, h.space.movements(oct).value().size, "the clear one and the new one exist; the dismissed one creates nothing")
        assertEquals(emptyList(), h.imports().pendings().value())

        // Another export of the same lines: both are known now, the dismissed one included.
        val again = (h.imports().start(StartImport("b2", "b2.ofx", 10, doc("sha-d2", mv(1, d(2026, 10, 20), -1000, "LOJA", "SAME"), mv(2, d(2026, 10, 21), -2000, "LOJA", "SAME"), mv(3, d(2026, 10, 22), -300, "CAFE")),
            BatchSource(DocumentKind.Account, account))).value() as StartOutcome.Started).info
        assertEquals(2, checkNotNull(h.imports().review(again.id).value()).preview.items.count { it.classification == ImportClassification.AlreadyKnown && it.movement.ordinal in setOf(1, 2) })
    }

    @Test fun `a credit becomes income only in the income category the person chooses`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val extraction = doc("sha-c", mv(1, d(2026, 10, 20), -1234, "MERCADO"), mv(2, d(2026, 10, 25), 9900, "TRANSFERENCIA RECEBIDA"))
        val id = h.confirmedWith(account, "b1", extraction)
        assertEquals(setOf(DoubtReason.CreditToClassify), h.imports().pendings().value().single().doubts)

        val salary = DeterministicIds.seedIncomeCategory(spaceId, "salario")
        h.decide(id, ImportDecision.AsIncome(2, salary))
        val applied = assertIs<ResolveOutcome.Applied>(h.resolve(id, 2))
        assertEquals(Resolution.IncomeCreated, applied.resolution)
        val target = checkNotNull(h.imports().result(id).value()).items.first { it.ordinal == 2 }.targetId!!
        assertEquals(salary, checkNotNull(h.space.income(target).value()).incomeCategoryId)
        assertEquals(9900, h.space.incomes(oct).value().received.cents)
    }

    @Test fun `a possible match is linked or kept separate, never decided for the person`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        // Two manual entries make the match ambiguous.
        val first = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), app.accord.core.Money(1234), account)).applied().changes.single().id
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado 2", Classification.Category(groceries), d(2026, 10, 20), app.accord.core.Money(1234), account)).applied()
        val extraction = doc("sha-m", mv(1, d(2026, 10, 20), -1234, "MERCADO"), mv(2, d(2026, 10, 22), -500, "PADARIA"), mv(3, d(2026, 10, 23), -300, "CAFE"))
        val id = h.confirmedWith(account, "b1", extraction, categoryFor = 3)
        val pending = h.imports().pendings().value().first { it.ordinal == 1 }
        assertTrue(DoubtReason.PossibleMatch in pending.doubts)

        h.decide(id, ImportDecision.LinkTo(1, first))
        val applied = assertIs<ResolveOutcome.Applied>(h.resolve(id, 1))
        assertEquals(Resolution.Linked, applied.resolution)
        assertEquals(first, checkNotNull(h.imports().result(id).value()).items.first { it.ordinal == 1 }.targetId)
        assertEquals(3, h.space.movements(oct).value().size, "two manual entries and the clear import; the link created nothing")
    }

    @Test fun `separating every candidate and choosing a category creates a new entry and stores the pairs`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val one = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), app.accord.core.Money(1234), account)).applied().changes.single().id
        val two = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado 2", Classification.Category(groceries), d(2026, 10, 20), app.accord.core.Money(1234), account)).applied().changes.single().id
        val extraction = doc("sha-s", mv(1, d(2026, 10, 20), -1234, "MERCADO"), mv(2, d(2026, 10, 23), -300, "CAFE"))
        val id = h.confirmedWith(account, "b1", extraction, categoryFor = 2)

        h.decide(id, ImportDecision.KeepSeparate(1, one))
        h.decide(id, ImportDecision.KeepSeparate(1, two))
        h.decide(id, ImportDecision.Category(1, groceries))
        assertEquals(Resolution.Created, assertIs<ResolveOutcome.Applied>(h.resolve(id, 1)).resolution)
        assertEquals(setOf(one, two), h.database.db.dao().importPairs(id).map { it.entryId }.toSet())
        assertEquals(4, h.space.movements(oct).value().size, "the two manual entries, the clear import and the new one")
    }

    @Test fun `decisions on a confirmed batch only reach open doubts`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val id = h.confirmedWith(account, "b1", twoMovements())
        val rev = h.revision(id)
        assertIs<DecideOutcome.Rejected>(h.imports().decide(id, ImportDecision.Category(1, groceries), rev).value(), "ordinal 1 was already created")
        assertIs<DecideOutcome.Rejected>(h.imports().decide(id, ImportDecision.DefaultCategory(groceries), rev).value())
        assertIs<DecideOutcome.Rejected>(h.imports().decide(id, ImportDecision.AcceptSuggestions(), rev).value())
        assertIs<DecideOutcome.Applied>(h.imports().decide(id, ImportDecision.Category(2, groceries), rev).value())
        assertEquals(rev, h.revision(id), "a local decision on a sent batch does not touch its synced revision")
    }

    // ---- accord-c9m.12: estorno, fatura da compra e 1ª parcela ----

    private val cardId get() = DeterministicIds.of(spaceId, "card", "main")
    private val nov = ym(2026, 11)
    private val dec = ym(2026, 12)

    private fun cmv(ordinal: Int, date: LocalDate, cents: Long, description: String, installment: app.accord.core.importing.InstallmentHint? = null) =
        ExtractedMovement(ordinal, listOf("TRNAMT" to cents.toString()), date, cents, "BRL", description, installment, emptySet())

    private fun cardDoc(sha: String, vararg m: ExtractedMovement) =
        ExtractionResult(sha, DeclaredMetadata(SourceFormat.Pdf, "layout", "BRL", null, null, DocumentKind.Card, null, null, null), m.toList(), 0, emptyList())

    /** Starts and confirms a card document of [invoice] whose last movement is clear (default category) and the others stay open. */
    private suspend fun Handle.confirmedCard(id: String, extraction: ExtractionResult, invoice: java.time.YearMonth): String {
        val info = (imports().start(StartImport(id, "$id.pdf", 10, extraction, BatchSource(DocumentKind.Card, cardId, invoice))).value() as StartOutcome.Started).info
        imports().decide(id, ImportDecision.DefaultCategory(groceries), info.revision)
        val review = checkNotNull(imports().review(id).value())
        assertIs<ConfirmOutcome.Applied>(imports().confirm(id, "c-$id", review.token).value())
        return id
    }

    @Test fun `a credit on a statement refunds the payment the person names, abates its category and is not income`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val extraction = doc("sha-r", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL"), mv(2, d(2026, 10, 25), 1234, "ESTORNO MERCADO"))
        val id = h.confirmedWith(account, "b1", extraction)
        val payment = checkNotNull(h.imports().result(id).value()).items.first { it.ordinal == 1 }.targetId!!
        assertEquals(setOf(DoubtReason.CreditToClassify), h.imports().pendings().value().single().doubts)
        assertEquals(0, h.space.budget(oct).value().credits.cents)

        val candidates = h.detail(id, 2).refundCandidates
        assertEquals(listOf(payment), candidates.map { it.id }, "the payment of the statement can still be refunded")
        assertEquals(1234, candidates.single().remainingCents)

        h.decide(id, ImportDecision.AsRefund(2, payment))
        assertEquals(true, h.imports().pendings().value().single().ready)
        val applied = assertIs<ResolveOutcome.Applied>(h.resolve(id, 2))
        assertEquals(Resolution.RefundCreated, applied.resolution)

        val refund = h.space.refunds(payment).value().single()
        assertEquals(1234, refund.amount.cents)
        assertEquals(oct, refund.creditMonth)
        assertEquals(1234, h.space.budget(oct).value().credits.cents, "the credit abates the month, apart from the gross")
        assertEquals(0, h.space.incomes(oct).value().received.cents, "a refund is never income")
        assertEquals(emptyList(), h.imports().pendings().value())
    }

    @Test fun `a refund that passes what can still be refunded stays open, with nothing applied`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val extraction = doc("sha-r2", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL"), mv(2, d(2026, 10, 25), 5000, "ESTORNO MERCADO"))
        val id = h.confirmedWith(account, "b1", extraction)
        val payment = checkNotNull(h.imports().result(id).value()).items.first { it.ordinal == 1 }.targetId!!
        h.decide(id, ImportDecision.AsRefund(2, payment))

        val pending = h.imports().pendings().value().single()
        assertEquals(false, pending.ready)
        assertEquals(setOf(DoubtReason.CannotApply), pending.doubts)
        assertIs<ResolveOutcome.NotReady>(h.resolve(id, 2))
        assertEquals(emptyList(), h.space.refunds(payment).value())
    }

    @Test fun `a refund needs a real purchase, and choosing income afterwards replaces it`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val extraction = doc("sha-r3", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL"), mv(2, d(2026, 10, 25), 1234, "ESTORNO MERCADO"))
        val id = h.confirmedWith(account, "b1", extraction)
        val payment = checkNotNull(h.imports().result(id).value()).items.first { it.ordinal == 1 }.targetId!!
        assertIs<DecideOutcome.Rejected>(h.imports().decide(id, ImportDecision.AsRefund(2, "nao-existe"), h.revision(id)).value())

        h.decide(id, ImportDecision.AsRefund(2, payment))
        h.decide(id, ImportDecision.AsIncome(2, DeterministicIds.seedIncomeCategory(spaceId, "salario")))
        assertEquals(Resolution.IncomeCreated, assertIs<ResolveOutcome.Applied>(h.resolve(id, 2)).resolution)
        assertEquals(emptyList(), h.space.refunds(payment).value())
    }

    @Test fun `an invoice the person chose is corrected to the document's only after they agree, and the same purchase is kept`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val purchase = h.space.execute(h.env(), RegisterCardPurchase("loja", Classification.Category(groceries), d(2026, 10, 7), brl(68), invoice = oct)).applied().changes.single().id
        val id = h.confirmedCard("c1", cardDoc("card-1", cmv(1, d(2026, 10, 7), -6800, "LOJA"), cmv(2, d(2026, 10, 20), -1000, "PADARIA")), nov)
        val pending = h.imports().pendings().value().single()
        assertEquals(setOf(DoubtReason.InvoiceDiverges), pending.doubts)
        val linkable = h.detail(id, pending.ordinal).linkableEntries.single { it.id == purchase }
        assertEquals(oct, linkable.invoice)

        assertIs<ResolveOutcome.NotReady>(h.resolve(id, pending.ordinal))
        assertEquals(oct, h.space.purchase(purchase).value()!!.installments.single().invoiceMonth)

        h.decide(id, ImportDecision.CorrectInvoice(pending.ordinal, purchase))
        assertEquals(Resolution.Linked, assertIs<ResolveOutcome.Applied>(h.resolve(id, pending.ordinal)).resolution)
        assertEquals(nov, h.space.purchase(purchase).value()!!.installments.single().invoiceMonth)
        assertEquals(1, h.space.purchase(purchase).value()!!.installments.size, "one purchase, not two")
        assertEquals(emptyList(), h.imports().pendings().value())
    }

    @Test fun `correcting the invoice is refused for an entry that is not a card purchase`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val manual = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), app.accord.core.Money(1234), account)).applied().changes.single().id
        val id = h.confirmedWith(account, "b1", twoMovements())
        assertIs<DecideOutcome.Rejected>(h.imports().decide(id, ImportDecision.CorrectInvoice(2, manual), h.revision(id)).value())
    }

    @Test fun `a later installment on another invoice shifts the whole schedule once the person accepts the suggested first one`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val purchase = h.space.execute(h.env(), RegisterCardPurchase("tenis", Classification.Category(groceries), d(2026, 10, 7), brl(1200), amountMeaning = app.accord.core.space.AmountMeaning.Total, installments = 6, invoice = oct)).applied().changes.single().id
        val id = h.confirmedCard("c1", cardDoc("card-2", cmv(1, d(2026, 10, 7), -20000, "TENIS", app.accord.core.importing.InstallmentHint(2, 6)), cmv(2, d(2026, 10, 20), -1000, "PADARIA")), dec)
        val pending = h.imports().pendings().value().single()
        assertEquals(setOf(DoubtReason.InstallmentInvoiceDiverges), pending.doubts)
        assertIs<ResolveOutcome.NotReady>(h.resolve(id, pending.ordinal))

        h.decide(id, ImportDecision.ShiftFirstInstallment(pending.ordinal))
        assertEquals(Resolution.InstallmentLinked, assertIs<ResolveOutcome.Applied>(h.resolve(id, pending.ordinal)).resolution)
        assertEquals((0..5).map { nov.plusMonths(it.toLong()) }, h.space.purchase(purchase).value()!!.installments.map { it.invoiceMonth })
        assertEquals(emptyList(), h.imports().pendings().value())
    }

    @Test fun `the resolution travels with the batch and the other device sees the doubt closed`() = runBlocking<Unit> {
        val a = open("ana")
        val account = a.account()
        val id = a.confirmedWith(account, "b1", twoMovements())
        a.decide(id, ImportDecision.Category(2, groceries))
        assertIs<ResolveOutcome.Applied>(a.resolve(id, 2))

        val b = open("bia")
        val sync = a.database.db.syncDao()
        val ops = sync.operationsIn(OpState.OPEN)
        val changes = sync.changesOfAll(ops.map { it.operationId }).groupBy { it.operationId }
        var cursor = 0L
        val versions = mutableMapOf<String, Long>()
        val states = ops.flatMap { op ->
            changes[op.operationId].orEmpty().map { c ->
                val v = (versions[c.entityType + c.entityId] ?: 0) + 1
                versions[c.entityType + c.entityId] = v
                RemoteEntityState(c.entityType, c.entityId, v, c.state, c.tombstone, "ana", 0, ++cursor)
            }
        }
        val latest = states.groupBy { it.type + "/" + it.id }.values.map { it.maxBy { s -> s.version } }.sortedBy { it.cursor }
        assertEquals(0, SyncStore(b.database.db).applyPage(PullPage(latest, cursor, hasMore = false)).conflicts)

        assertEquals(emptyList(), b.imports().pendings().value())
        assertEquals(2, checkNotNull(b.imports().result(id).value()).count(Resolution.Created))
        assertNotNull(b.space.movements(oct).value().firstOrNull { it.description == "PADARIA" })
    }
}
