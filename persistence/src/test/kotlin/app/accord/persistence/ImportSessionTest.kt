package app.accord.persistence

import app.accord.core.importing.BatchSource
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.Blocker
import app.accord.core.importing.Classification as ImportClassification
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DiscardOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.Resolution
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.SourceOutcome
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.Origin
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.sync.PullPage
import app.accord.core.sync.RemoteEntityState
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** accord-c9m.3: the import session over the real facade (S4 "Sessão de importação", "Confirmação, atomicidade e idempotência"). */
class ImportSessionTest : SpaceTestBase() {
    private val cardId get() = DeterministicIds.of(spaceId, "card", "main")
    private val groceries get() = seed("alimentacao.mercado")
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()

    private suspend fun Handle.account(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, fitid: String? = "F$ordinal", installment: app.accord.core.importing.InstallmentHint? = null) =
        ExtractedMovement(
            ordinal, listOfNotNull(fitid?.let { "FITID" to it }, "TRNAMT" to cents.toString()), date, cents, "BRL", description, installment, emptySet(),
        )

    private fun doc(kind: DocumentKind, sha: String, vararg m: ExtractedMovement, currency: String = "BRL") = ExtractionResult(
        sha, DeclaredMetadata(SourceFormat.Ofx, "102", currency, null, null, kind, null, null, null), m.toList(), 0, emptyList(),
    )

    private suspend fun Handle.start(id: String, extraction: ExtractionResult, source: BatchSource = BatchSource()) =
        (imports().start(StartImport(id, "extrato.ofx", 1234, extraction, source)).value() as StartOutcome.Started).info

    private suspend fun Handle.review(id: String) = checkNotNull(imports().review(id).value())

    private suspend fun Handle.confirm(id: String, confirmationId: String = "confirm-$id"): ConfirmOutcome {
        val token = review(id).token
        return imports().confirm(id, confirmationId, token).value()
    }

    private suspend fun Handle.statement(accountId: String, sha: String = "sha-1") = doc(
        DocumentKind.Account, sha,
        mv(1, d(2026, 10, 20), -12_34, "MERCADO CENTRAL"),
        mv(2, d(2026, 10, 21), -50_00, "PIX ENVIADO Maria"),
    ).also { assertNotNull(accountId) }

    // ---- início ----

    @Test fun `starting persists the extraction without touching the budget or the operation log`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val before = h.space.operations().value().size
        val info = h.start("b1", h.statement(account))
        assertEquals(BatchStatus.InReview, info.status)
        assertEquals(2, info.movementCount)
        assertEquals(before, h.space.operations().value().size, "no operation before confirming")
        assertEquals(emptyList(), h.space.movements(oct).value())
        // The original fields stay exactly as read.
        val stored = h.database.db.dao().importMovements("b1")
        assertEquals(listOf("FITID" to "F1", "TRNAMT" to "-1234"), ImportCodec.decodeFields(stored.first().originalFields))
    }

    @Test fun `starting twice with the same id answers the same batch, and the same file is reported, not duplicated`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account))
        val again = h.imports().start(StartImport("b1", "extrato.ofx", 1234, h.statement(account))).value()
        assertIs<StartOutcome.AlreadyStarted>(again)
        val sameFile = h.imports().start(StartImport("b2", "outro-nome.ofx", 1234, h.statement(account))).value()
        assertIs<StartOutcome.KnownFile>(sameFile)
        assertEquals("b1", sameFile.info.id)
        assertEquals(1, h.imports().batches().value().size)
    }

    // ---- bloqueios ----

    @Test fun `without a chosen source nothing can be confirmed and the review says why`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account))
        assertTrue(Blocker.SourceUndefined in h.review("b1").blockers)
        assertEquals(ConfirmOutcome.Blocked(h.review("b1").blockers), h.confirm("b1"))
        assertEquals(emptyList(), h.space.movements(oct).value())
    }

    @Test fun `a document in another currency is diagnostic only and confirmation is blocked without conversion`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val opsBefore = h.space.operations().value().size
        h.start("b1", doc(DocumentKind.Account, "eur", mv(1, d(2026, 10, 20), -1000, "SHOP"), currency = "EUR"), BatchSource(DocumentKind.Account, account))
        val review = h.review("b1")
        assertTrue(Blocker.UnsupportedCurrency in review.blockers)
        assertEquals(1, review.preview.counts.blocked)
        val outcome = h.confirm("b1")
        assertIs<ConfirmOutcome.Blocked>(outcome)
        assertEquals(emptyList(), h.space.movements(oct).value())
        assertEquals(opsBefore, h.space.operations().value().size)
    }

    // ---- confirmação ----

    @Test fun `confirming creates the entries with the same commands, records the origin and queues one operation for the batch`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val opsBefore = h.space.operations().value().size
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        val v = h.review("b1").info.revision
        assertIs<DecideOutcome.Applied>(h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), v).value())

        val outcome = h.confirm("b1")
        val result = assertIs<ConfirmOutcome.Applied>(outcome).result
        assertEquals(2, result.count(Resolution.Created))
        assertTrue(result.complete)

        val rows = h.space.movements(oct).value()
        assertEquals(setOf("MERCADO CENTRAL", "PIX ENVIADO Maria"), rows.map { it.description }.toSet())
        assertEquals(DirectPaymentKind.Pix, DirectPaymentKind.valueOf(h.database.db.dao().movement(result.items[1].targetId!!)!!.kind))

        val ops = h.space.operations(afterSequence = opsBefore.toLong()).value()
        assertEquals(listOf("RegisterDirectPayment", "RegisterDirectPayment", "ConfirmImportBatch"), ops.map { it.commandType })
        assertTrue(ops.all { it.origin == Origin.Import })
        val batchChange = ops.last().changes.single()
        assertEquals(EntityType.ImportBatch, batchChange.type)
        assertEquals("b1", batchChange.id)

        val state = checkNotNull(EntityStates.read(h.database.db.dao(), EntityType.ImportBatch, "b1"))
        assertFalse(state.tombstone)
        assertTrue(state.references.any { it.type == "Movement" && it.id == result.items[0].targetId }, "the batch cites the entries it created")
        assertEquals(BatchStatus.Confirmed, h.imports().batches().value().single().status)
    }

    @Test fun `repeating the confirmation id returns the stored result and applies nothing again`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        val token = h.review("b1").token
        val first = assertIs<ConfirmOutcome.Applied>(h.imports().confirm("b1", "same", token).value())
        val again = assertIs<ConfirmOutcome.AlreadyApplied>(h.imports().confirm("b1", "same", token).value())
        assertEquals(first.result.items, again.result.items)
        assertEquals(2, h.space.movements(oct).value().size)
        // Another confirmation of an already confirmed batch is refused, not applied.
        val other = h.imports().confirm("b1", "other", token).value()
        assertEquals(ConfirmOutcome.Blocked(setOf(Blocker.AlreadyConfirmed)), other)
        assertEquals(2, h.space.movements(oct).value().size)
    }

    @Test fun `a failure in the middle of a confirmation leaves nothing behind, and trying again applies everything once`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        val token = h.review("b1").token
        val opsBefore = h.space.operations().value().size
        var calls = 0
        h.fault = { if (++calls == 3) error("falha injetada") }
        assertIs<app.accord.core.space.QueryResult.Failure>(h.imports().confirm("b1", "c1", token))
        h.fault = null

        assertEquals(emptyList(), h.space.movements(oct).value(), "no entry survives the failed confirmation")
        assertEquals(opsBefore, h.space.operations().value().size, "no operation survives either")
        assertEquals(BatchStatus.InReview, h.imports().batches().value().single().status)
        assertEquals(null, h.imports().result("b1").value())

        assertIs<ConfirmOutcome.Applied>(h.imports().confirm("b1", "c1", token).value())
        assertEquals(2, h.space.movements(oct).value().size)
    }

    @Test fun `a review that changed after the person saw it is returned for a new look and nothing is applied`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        val seen = h.review("b1").token
        // Someone registers the same payment by hand meanwhile: the movement is no longer plainly new.
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), Money12_34, account)).applied()

        val outcome = h.imports().confirm("b1", "c1", seen).value()
        val review = assertIs<ConfirmOutcome.ReviewNeeded>(outcome).review
        assertTrue(review.token != seen)
        assertEquals(1, h.space.movements(oct).value().size, "only the manual entry exists")
        assertEquals(ImportClassification.SafeLink, review.preview.items.first { it.movement.ordinal == 1 }.classification)
    }

    private val Money12_34 get() = app.accord.core.Money(1234)

    // ---- vínculo, pares distintos e reimportação ----

    @Test fun `a safe link only records the origin - the entry keeps its identity and the total does not grow`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val manual = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), Money12_34, account)).applied().changes.single().id
        h.start("b1", doc(DocumentKind.Account, "s", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL")), BatchSource(DocumentKind.Account, account))
        val review = h.review("b1")
        assertEquals(ImportClassification.SafeLink, review.preview.items.single().classification)
        assertEquals(manual, review.preview.items.single().linkedEntryId)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("b1")).result
        assertEquals(Resolution.Linked, result.items.single().resolution)
        assertEquals(manual, result.items.single().targetId)
        assertEquals(1, h.space.movements(oct).value().size)
        assertEquals("mercado", h.space.movements(oct).value().single().description, "description and category of the entry are untouched")

        // The entry now has an origin record: another file with the same line cannot link to it again.
        h.start("b2", doc(DocumentKind.Account, "s2", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL", fitid = "OTHER")), BatchSource(DocumentKind.Account, account))
        assertTrue(h.review("b2").linkableEntries.isEmpty())
    }

    @Test fun `keeping two purchases separate stores a distinct pair that survives the confirmation`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val manual = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), Money12_34, account)).applied().changes.single().id
        h.start("b1", doc(DocumentKind.Account, "s", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL")), BatchSource(DocumentKind.Account, account))
        assertEquals(ImportClassification.SafeLink, h.review("b1").preview.items.single().classification)
        h.imports().decide("b1", ImportDecision.KeepSeparate(1, manual), h.review("b1").info.revision)
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        val review = h.review("b1")
        assertEquals(ImportClassification.New, review.preview.items.single().classification, "the pair is excluded from matching")

        assertIs<ConfirmOutcome.Applied>(h.confirm("b1"))
        assertEquals(2, h.space.movements(oct).value().size, "both purchases exist")
        val pairs = h.database.db.dao().importPairs("b1")
        assertEquals(listOf(manual), pairs.map { it.entryId })
    }

    @Test fun `importing the same movements again from another export creates nothing and confirmation has nothing to do`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account, "sha-1"), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        assertIs<ConfirmOutcome.Applied>(h.confirm("b1"))

        h.start("b2", h.statement(account, "sha-2"), BatchSource(DocumentKind.Account, account))
        val review = h.review("b2")
        assertEquals(2, review.preview.counts.alreadyKnown)
        assertTrue(Blocker.NothingChecked in review.blockers)
        assertEquals(2, h.space.movements(oct).value().size)
    }

    @Test fun `a movement whose entry was deleted comes back as a doubt, never recreated silently`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account, "sha-1"), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        val created = assertIs<ConfirmOutcome.Applied>(h.confirm("b1")).result.items[0].targetId!!
        h.space.execute(h.env(), app.accord.core.space.DeleteEntry(created, confirmExtra = true)).applied()

        h.start("b2", h.statement(account, "sha-2"), BatchSource(DocumentKind.Account, account))
        val first = h.review("b2").preview.items.first { it.movement.ordinal == 1 }
        assertEquals(ImportClassification.Doubt, first.classification)
        assertTrue(app.accord.core.importing.DoubtReason.RemovedPreviously in first.doubts)
    }

    // ---- decisões ----

    @Test fun `choosing a category for one movement leaves the others as doubts that stay pending after confirming`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        assertEquals(2, h.review("b1").preview.counts.doubts, "without a category both are doubts")
        h.imports().decide("b1", ImportDecision.Category(1, groceries), h.review("b1").info.revision)
        val review = h.review("b1")
        assertEquals(1, review.preview.counts.new)
        assertEquals(1, review.summary.pendingDoubts)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("b1")).result
        assertEquals(1, result.count(Resolution.Created))
        assertEquals(1, result.count(Resolution.Pending))
        assertEquals(setOf(app.accord.core.importing.DoubtReason.CategoryUnknown), result.items.first { it.resolution == Resolution.Pending }.doubts)
        assertFalse(result.complete, "a batch with a doubt left is partially conferred")
        assertEquals(1, h.space.movements(oct).value().size)
    }

    @Test fun `a suggestion from the history is only applied when accepted, and then in one step for all`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", doc(DocumentKind.Account, "s1", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL")), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.DefaultCategory(groceries), h.review("b1").info.revision)
        assertIs<ConfirmOutcome.Applied>(h.confirm("b1"))

        h.start("b2", doc(DocumentKind.Account, "s2", mv(1, d(2026, 11, 3), -2000, "MERCADO CENTRAL", fitid = "G1"), mv(2, d(2026, 11, 4), -3000, "MERCADO CENTRAL", fitid = "G2")), BatchSource(DocumentKind.Account, account))
        val before = h.review("b2")
        assertEquals(2, before.preview.counts.doubts)
        assertTrue(before.preview.items.all { it.suggestedCategoryId == groceries })
        val accepted = h.imports().decide("b2", ImportDecision.AcceptSuggestions(), before.info.revision).value()
        assertEquals(listOf(1, 2), assertIs<DecideOutcome.Applied>(accepted).affectedOrdinals)
        assertEquals(2, h.review("b2").preview.counts.new)
    }

    @Test fun `a stale revision or an unknown entry is refused and changes nothing`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        val stale = h.imports().decide("b1", ImportDecision.Category(1, groceries), 99).value()
        assertEquals(DecideOutcome.Stale(1), stale)
        val rejected = h.imports().decide("b1", ImportDecision.LinkTo(1, "nao-existe"), 1).value()
        assertIs<DecideOutcome.Rejected>(rejected)
        assertEquals(DecideOutcome.NotFound, h.imports().decide("nada", ImportDecision.Reset(1), 1).value())
        assertEquals(1, h.review("b1").info.revision)
    }

    @Test fun `changing the origin drops the choices that pointed at the other origin and says how many`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val manual = h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Debit, "mercado", Classification.Category(groceries), d(2026, 10, 20), Money12_34, account)).applied().changes.single().id
        h.start("b1", doc(DocumentKind.Account, "s", mv(1, d(2026, 10, 20), -1234, "MERCADO CENTRAL")), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b1", ImportDecision.KeepSeparate(1, manual), 1)
        val outcome = h.imports().setSource("b1", BatchSource(DocumentKind.Card, cardId, nov), 2).value()
        assertEquals(1, assertIs<SourceOutcome.Applied>(outcome).droppedDecisions)
        assertEquals(emptyList(), h.database.db.dao().importDecisions("b1").filter { it.type == ImportDecisionType.SEPARATE })
    }

    // ---- cartão, fatura, renda ----

    @Test fun `a card document takes the invoice of the document and a to-confirm purchase moves to it`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val toConfirm = h.space.execute(h.env(), RegisterCardPurchase("loja", Classification.Category(groceries), d(2026, 10, 7), brl(68))).applied().changes.single().id
        assertEquals(oct, h.space.purchase(toConfirm).value()!!.installments.single().invoiceMonth, "estimated on the October invoice")
        h.start(
            "c1",
            doc(DocumentKind.Card, "card-sha", mv(1, d(2026, 10, 7), -6800, "LOJA"), mv(2, d(2026, 10, 20), -1000, "PADARIA")),
            BatchSource(DocumentKind.Card, cardId, nov),
        )
        h.imports().decide("c1", ImportDecision.DefaultCategory(groceries), h.review("c1").info.revision)
        val review = h.review("c1")
        assertEquals(1, review.summary.invoiceCorrections)
        assertEquals(1, review.summary.creates)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("c1")).result
        assertEquals(nov, h.space.purchase(toConfirm).value()!!.installments.single().invoiceMonth, "moved to the invoice of the document")
        val created = result.items.single { it.resolution == Resolution.Created }
        assertEquals(EntityType.CardPurchase, created.targetType)
        assertEquals(nov, h.space.purchase(created.targetId!!).value()!!.installments.single().invoiceMonth)
        assertEquals(2, h.space.invoice(nov).value().let { 2 })
    }

    @Test fun `a card document without a single invoice puts each purchase on the invoice of its own date`() = runBlocking<Unit> {
        val h = open()
        h.account()
        // Fechamento 8 e vencimento 15 (configuração inicial): 02/10 fecha em 08/10 e vence 15/10; 20/10 fecha em 08/11 e vence 15/11; 08/10 já cai na seguinte.
        h.start(
            "c1",
            doc(DocumentKind.Card, "card-sha", mv(1, d(2026, 10, 2), -1000, "ANTES DO FECHAMENTO"), mv(2, d(2026, 10, 20), -2000, "DEPOIS"), mv(3, d(2026, 10, 8), -3000, "NO FECHAMENTO")),
            BatchSource(DocumentKind.Card, cardId),
        )
        h.imports().decide("c1", ImportDecision.DefaultCategory(groceries), 1)
        val review = h.review("c1")
        assertEquals(emptySet(), review.blockers, "no single invoice is needed")
        assertEquals(setOf(oct, nov), review.summary.affectedMonths.filter { it >= oct }.toSet(), "the invoices of both dates are affected")

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("c1")).result
        val invoiceOf = result.items.associate { item -> item.ordinal to h.space.purchase(item.targetId!!).value()!!.installments.single().invoiceMonth }
        assertEquals(oct, invoiceOf.getValue(1))
        assertEquals(nov, invoiceOf.getValue(2))
        assertEquals(nov, invoiceOf.getValue(3), "on the closing day the purchase goes to the next invoice")
    }

    @Test fun `a single invoice chosen for the document still overrides the date`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.start("c1", doc(DocumentKind.Card, "card-sha", mv(1, d(2026, 10, 2), -1000, "COMPRA")), BatchSource(DocumentKind.Card, cardId, nov))
        h.imports().decide("c1", ImportDecision.DefaultCategory(groceries), 1)
        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("c1")).result
        assertEquals(nov, h.space.purchase(result.items.single().targetId!!).value()!!.installments.single().invoiceMonth)
    }

    @Test fun `a full invoice payment on the statement pays the conferred invoice without creating an expense`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.space.execute(h.env(), RegisterCardPurchase("loja", Classification.Category(groceries), d(2026, 10, 20), brl(500), invoice = nov)).applied()
        h.space.execute(h.env(), RegisterInvoiceTotal(nov, brl(500))).applied()
        h.start("a1", doc(DocumentKind.Account, "st", mv(1, d(2026, 11, 14), -50_000, "PGTO FATURA CARTAO")), BatchSource(DocumentKind.Account, account))
        val review = h.review("a1")
        assertEquals(1, review.summary.invoicePayments)

        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("a1")).result
        assertEquals(Resolution.InvoicePaid, result.items.single().resolution)
        assertEquals(EntityType.InvoicePayment, result.items.single().targetType)
        assertTrue(h.space.invoice(nov).value().paid)
        assertEquals(emptyList(), h.space.movements(nov).value(), "no expense for the payment")
    }

    @Test fun `a credit that comes back from an investment becomes a received income in the Investimento category`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("a1", doc(DocumentKind.Account, "st", mv(1, d(2026, 10, 30), 15_000, "RESGATE CDB")), BatchSource(DocumentKind.Account, account))
        val result = assertIs<ConfirmOutcome.Applied>(h.confirm("a1")).result
        assertEquals(Resolution.IncomeCreated, result.items.single().resolution)
        val income = checkNotNull(h.space.income(result.items.single().targetId!!).value())
        assertEquals(DeterministicIds.seedIncomeCategory(spaceId, "investimento"), income.incomeCategoryId)
        assertEquals(15_000, h.space.incomes(oct).value().received.cents)
    }

    // ---- rótulo de categoria do banco ----

    @Test fun `the bank label on a PDF line is a suggestion that only counts once accepted`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val line = ExtractedMovement(
            1, listOf("LINE" to "x", "CATEGORY_HINT" to "supermercado"), d(2026, 10, 20), -1500, "BRL", "PADARIA DO ZE", null, emptySet(),
        )
        val extraction = ExtractionResult(
            "pdf-sha", DeclaredMetadata(SourceFormat.Pdf, "itau-cartao-fatura-v1", "BRL", null, null, DocumentKind.Card, null, null, null), listOf(line), 0, emptyList(),
        )
        h.start("c1", extraction, BatchSource(DocumentKind.Card, cardId, nov))
        val before = h.review("c1").preview.items.single()
        assertEquals(ImportClassification.Doubt, before.classification)
        assertEquals(groceries, before.suggestedCategoryId)
        assertEquals(app.accord.core.importing.SuggestionSource.BankLabel, before.suggestionSource)
        assertEquals(emptyList(), h.space.movements(nov).value(), "nothing is created from a suggestion")

        h.imports().decide("c1", ImportDecision.AcceptSuggestions(), h.review("c1").info.revision)
        assertEquals(ImportClassification.New, h.review("c1").preview.items.single().classification)
        val created = assertIs<ConfirmOutcome.Applied>(h.confirm("c1")).result.items.single()
        assertEquals(groceries, h.database.db.dao().purchase(created.targetId!!)!!.classificationId)
    }

    // ---- descarte ----

    @Test fun `discarding deletes the extracted data, can be repeated, and is refused for a confirmed batch`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.start("b1", h.statement(account), BatchSource(DocumentKind.Account, account))
        assertEquals(DiscardOutcome.Discarded, h.imports().discard("b1").value())
        assertEquals(DiscardOutcome.Discarded, h.imports().discard("b1").value())
        assertEquals(emptyList(), h.database.db.dao().importMovements("b1"))
        assertEquals(emptyList(), h.imports().batches().value())

        h.start("b2", h.statement(account, "sha-2"), BatchSource(DocumentKind.Account, account))
        h.imports().decide("b2", ImportDecision.DefaultCategory(groceries), 1)
        assertIs<ConfirmOutcome.Applied>(h.confirm("b2"))
        assertEquals(DiscardOutcome.NotAllowed, h.imports().discard("b2").value())
    }

    // ---- sincronização ----

    @Test fun `the confirmed batch travels as a state and the other device recognises the same movements`() = runBlocking<Unit> {
        val a = open("ana")
        val account = a.account()
        a.start("b1", a.statement(account), BatchSource(DocumentKind.Account, account))
        a.imports().decide("b1", ImportDecision.DefaultCategory(groceries), 1)
        assertIs<ConfirmOutcome.Applied>(a.confirm("b1"))

        val b = open("bia")
        // Replicates every operation state, in order, as the service would hand them out.
        val sync = a.database.db.syncDao()
        val ops = sync.operationsIn(OpState.OPEN)
        val changes = sync.changesOfAll(ops.map { it.operationId }).groupBy { it.operationId }
        var cursor = 0L
        val states = ops.flatMap { op ->
            changes[op.operationId].orEmpty().map { c -> RemoteEntityState(c.entityType, c.entityId, 1, c.state, c.tombstone, "ana", 0, ++cursor) }
        }
        assertTrue(states.any { it.type == "ImportBatch" })
        val summary = SyncStore(b.database.db).applyPage(PullPage(states, cursor, hasMore = false))
        assertEquals(0, summary.conflicts)

        val received = checkNotNull(b.imports().result("b1").value())
        assertEquals(2, received.count(Resolution.Created))
        assertEquals(BatchStatus.Confirmed, received.info.status)
        assertEquals(2, b.space.movements(oct).value().size)

        // The second device, importing the same lines from another export, sees them as already known.
        b.start("b2", b.statement(account, "sha-2"), BatchSource(DocumentKind.Account, account))
        assertEquals(2, b.review("b2").preview.counts.alreadyKnown)
    }
}
