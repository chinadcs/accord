package app.accord.persistence

import app.accord.core.CardCycle
import app.accord.core.importing.BankCategoryHints
import app.accord.core.importing.BatchReview
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.Blocker
import app.accord.core.importing.BillingContext
import app.accord.core.importing.BoletoRecord
import app.accord.core.importing.CardEntryInfo
import app.accord.core.importing.CardPurchaseRecord
import app.accord.core.importing.Decisions
import app.accord.core.importing.DistinctPair
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExistingEntry
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportSteps
import app.accord.core.importing.InstallmentRecord
import app.accord.core.importing.InvoiceRecord
import app.accord.core.importing.KnownOrigin
import app.accord.core.importing.KnownState
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.Preview
import app.accord.core.importing.ProposedEffect
import app.accord.core.importing.Reconciler
import app.accord.core.importing.StepsPlan
import app.accord.core.space.CategorySeed
import app.accord.core.space.Command
import app.accord.core.space.DeterministicIds
import app.accord.core.space.InvoiceView
import app.accord.core.space.SessionProvider
import app.accord.persistence.ImportCodec.metadata
import app.accord.persistence.ImportCodec.source
import app.accord.persistence.ImportCodec.toExtraction
import app.accord.persistence.ImportCodec.toInfo
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/** What the reconciliation of one batch needs from the space, read from the local database. */
internal data class ImportFacts(
    val known: KnownState,
    val billing: BillingContext,
    /** Normalised description to the category the couple chose for it in earlier imports. */
    val history: Map<String, String>,
    val entries: List<ExistingEntry>,
)

internal data class Evaluation(
    val batch: ImportBatchRow,
    val extraction: ExtractionResult,
    val kind: DocumentKind?,
    val originId: String,
    val facts: ImportFacts,
    val preview: Preview,
    val blockers: Set<Blocker>,
    val plan: StepsPlan,
    val token: String,
) {
    fun review() = BatchReview(
        info = batch.toInfo(),
        preview = preview,
        blockers = blockers,
        summary = plan.summary,
        token = token,
        linkableEntries = facts.linkable(),
    )
}

/** The entries a movement may still be linked to, with the invoice of a card purchase so a divergence can be shown. */
internal fun ImportFacts.linkable(): List<LinkableEntry> = entries.filter { !it.hasOrigin }.map {
    LinkableEntry(it.id, it.date, it.cents, it.description, it.card?.assignedDueMonth, it.card?.toConfirm == true)
}

/**
 * Recomputes the reconciliation of a batch from its stored extraction, the person's decisions and the current local state (S4 "Reconciliação").
 * Nothing about the preview is stored: data received by sync is reflected the next time it is read, and confirming revalidates the same way.
 */
internal class ImportEvaluator(
    private val dao: SpaceDao,
    private val sessions: SessionProvider,
    private val cycle: suspend () -> CardCycle,
    private val invoice: suspend (YearMonth) -> InvoiceView,
    private val cardId: (String) -> String,
    /** Whether the common validator would accept the command now (planning only, nothing is written). */
    private val applicable: suspend (Command) -> Boolean,
) {
    suspend fun evaluate(batch: ImportBatchRow): Evaluation {
        val first = compute(batch, emptySet())
        // A step the validator would refuse now must not block the whole batch: it is kept as a doubt, with the reason, and retried on every reading.
        val refused = first.plan.steps.filter { it.commands.isNotEmpty() && !it.commands.all { command -> applicable(command) } }.map { it.item.key }.toSet()
        return if (refused.isEmpty()) first else compute(batch, refused)
    }

    private suspend fun compute(batch: ImportBatchRow, refused: Set<String>): Evaluation {
        val spaceId = sessions.current().spaceId
        val declared = batch.metadata()
        val chosen = batch.source()
        val kind = chosen.kind ?: declared.kind
        val extraction = batch.toExtraction(dao.importMovements(batch.id)).let { it.copy(metadata = it.metadata.copy(kind = kind)) }
        val originId = chosen.originId.orEmpty()
        val movements = extraction.movements
        val keyOf = movements.associate { it.ordinal to Reconciler.key(originId, it, movements) }

        val facts = loadFacts(batch, movements.mapNotNull { it.date }, kind, originId, spaceId, chosen.documentInvoice)
        val rows = dao.importDecisions(batch.id)
        fun byOrdinal(type: String) = rows.filter { it.type == type && it.ordinal != ImportDecisionType.BATCH_WIDE }
        val decisions = Decisions(
            categoryByKey = byOrdinal(ImportDecisionType.CATEGORY).mapNotNull { r -> keyOf[r.ordinal]?.let { it to r.value } }.toMap(),
            defaultCategoryId = rows.firstOrNull { it.type == ImportDecisionType.DEFAULT }?.value,
            forcedLinks = byOrdinal(ImportDecisionType.LINK).mapNotNull { r -> keyOf[r.ordinal]?.let { it to r.value } }.toMap(),
            categorySuggestions = facts.history,
            bankCategoryHints = bankHints(spaceId),
            confirmedNew = byOrdinal(ImportDecisionType.CONFIRM_NEW).mapNotNull { keyOf[it.ordinal] }.toSet(),
            incomeByKey = byOrdinal(ImportDecisionType.INCOME).mapNotNull { r -> keyOf[r.ordinal]?.let { it to r.value } }.toMap(),
            dismissed = (byOrdinal(ImportDecisionType.DISMISS) + byOrdinal(ImportDecisionType.REPEAT)).mapNotNull { keyOf[it.ordinal] }.toSet(),
            postponed = byOrdinal(ImportDecisionType.POSTPONE).mapNotNull { keyOf[it.ordinal] }.toSet(),
            invoiceCorrections = byOrdinal(ImportDecisionType.CORRECT_INVOICE).mapNotNull { r -> keyOf[r.ordinal]?.let { it to r.value } }.toMap(),
            firstInstallmentShifts = byOrdinal(ImportDecisionType.SHIFT_FIRST).mapNotNull { keyOf[it.ordinal] }.toSet(),
            refundByKey = byOrdinal(ImportDecisionType.REFUND).mapNotNull { r -> keyOf[r.ordinal]?.let { it to r.value } }.toMap(),
            refused = refused,
        )
        val separate = byOrdinal(ImportDecisionType.SEPARATE).mapNotNull { r -> keyOf[r.ordinal]?.let { DistinctPair(it, r.value) } }
        val known = facts.known.copy(distinctPairs = facts.known.distinctPairs + separate)

        val preview = Reconciler.preview(batch.id, originId, extraction, known, decisions, billing = facts.billing)
        val plan = ImportSteps.build(preview, kind ?: DocumentKind.Account, originId, spaceId, facts.billing)
        val blockers = if (batch.status == BatchStatus.Confirmed.name) setOf(Blocker.AlreadyConfirmed) else buildSet {
            if (preview.state == app.accord.core.importing.BatchState.DiagnosticOnly) add(Blocker.UnsupportedCurrency)
            if (!sourceExists(kind, originId, spaceId)) add(Blocker.SourceUndefined)
            if (preview.counts.checked == 0) add(Blocker.NothingChecked)
        }
        val fact = facts.copy(known = known)
        return Evaluation(batch, extraction, kind, originId, fact, preview, blockers, plan, tokenOf(batch, fact, preview, originId))
    }

    /** The bank's labels mapped to the initial categories that still exist in this space (a renamed or removed one is simply not suggested). */
    private suspend fun bankHints(spaceId: String): Map<String, String> = BankCategoryHints.seedKeyByLabel.mapNotNull { (label, key) ->
        DeterministicIds.seedCategory(spaceId, key).takeIf { dao.category(it) != null }?.let { label to it }
    }.toMap()

    private suspend fun sourceExists(kind: DocumentKind?, originId: String, spaceId: String): Boolean = when {
        originId.isBlank() -> false
        kind == DocumentKind.Account -> dao.account(originId) != null
        kind == DocumentKind.Card -> originId == cardId(spaceId)
        else -> false
    }

    private suspend fun loadFacts(batch: ImportBatchRow, dates: List<LocalDate>, kind: DocumentKind?, originId: String, spaceId: String, documentInvoice: YearMonth?): ImportFacts {
        val batches = dao.confirmedImportBatches().associateBy { it.id }
        val effected = dao.effectedImportMovements()
        val tied = effected.filter { it.targetId != null }
        val withOrigin = tied.map { it.targetType to it.targetId }.toSet()
        val installmentOrigins = tied.filter { it.targetNumber != null }.map { it.targetId to it.targetNumber }.toSet()

        val origins = effected.mapNotNull { r ->
            val owner = batches[r.batchId]?.originId ?: return@mapNotNull null
            val key = r.key ?: return@mapNotNull null
            KnownOrigin(
                key = key, originId = owner,
                fitid = ImportCodec.decodeFields(r.originalFields).firstOrNull { it.first == "FITID" }?.second?.takeIf { it.isNotBlank() },
                date = r.epochDay?.let(LocalDate::ofEpochDay), cents = r.amountCents, currency = r.currency, description = r.description,
                batchId = r.batchId, entryRemoved = removed(r),
            )
        }
        val pairs = dao.confirmedImportPairs().map { DistinctPair(it.key, it.entryId) }.toSet()

        val entries = entriesFor(kind, originId, spaceId, dates, withOrigin)
        val cycle = cycle()
        val billing = BillingContext(
            cycle = cycle,
            documentInvoice = documentInvoice,
            chargesCategoryId = DeterministicIds.seedCategory(spaceId, CategorySeed.FEES_AND_INTEREST),
            purchases = dao.installmentPurchases().map { p ->
                val rows = dao.installments(p.id)
                CardPurchaseRecord(
                    p.id, p.description,
                    rows.map { r ->
                        InstallmentRecord(
                            number = r.number, total = rows.size, cents = r.amountCents, dueMonth = YearMonth.parse(r.invoiceMonth), entryId = p.id,
                            cancelled = r.cancelState == CancellationPlanner.CANCEL_DONE, hasOrigin = (p.id to r.number) in installmentOrigins,
                            toConfirm = r.assignment == "ToConfirm",
                        )
                    },
                )
            },
            invoices = dao.invoiceMonthsWithData().distinct().sorted().map { month ->
                val ym = YearMonth.parse(month)
                val view = invoice(ym)
                InvoiceRecord(ym, cycle.dueDateOfInvoice(ym), view.checkedTotal?.cents.takeIf { view.conferred }, view.paid)
            },
            boletos = dao.unpaidBoletos().map { BoletoRecord(it.id, it.expectedCents, LocalDate.ofEpochDay(it.dueEpochDay), paid = false) },
        )
        val history = linkedMapOf<String, String>()
        effected.filter { it.resolution == "Created" }.forEach { r ->
            val category = when (r.targetType) {
                "Movement" -> r.targetId?.let { dao.movement(it) }?.classificationId
                "CardPurchase" -> r.targetId?.let { dao.purchase(it) }?.classificationId
                else -> null
            }
            if (category != null) history[r.description] = category
        }
        return ImportFacts(KnownState(origins, entries, pairs), billing, history, entries)
    }

    private suspend fun removed(r: ImportMovementRow): Boolean {
        val id = r.targetId ?: return false
        return when (r.targetType) {
            "Movement" -> dao.movement(id)?.voided
            "CardPurchase" -> dao.purchase(id)?.voided
            "Boleto" -> dao.boleto(id)?.voided
            "Income" -> dao.income(id)?.voided
            "InvoicePayment" -> dao.invoicePayment(id)?.voided
            else -> null
        } == true
    }

    /** Entries of the chosen origin near the dates of the file that have no origin record yet. Chat drafts are not entries and never appear. */
    private suspend fun entriesFor(kind: DocumentKind?, originId: String, spaceId: String, dates: List<LocalDate>, withOrigin: Set<Pair<String?, String?>>): List<ExistingEntry> {
        if (originId.isBlank() || dates.isEmpty()) return emptyList()
        val from = dates.min().minusDays(WINDOW_DAYS).toEpochDay()
        val to = dates.max().plusDays(WINDOW_DAYS).toEpochDay()
        return when {
            kind == DocumentKind.Account -> dao.movementsOfAccount(originId, from, to).map {
                ExistingEntry(it.id, originId, LocalDate.ofEpochDay(it.epochDay), -it.amountCents, it.description, hasOrigin = ("Movement" to it.id) in withOrigin)
            }
            kind == DocumentKind.Card && originId == cardId(spaceId) -> dao.purchasesBetween(from, to).mapNotNull { p ->
                val installments = dao.installments(p.id)
                val first = installments.singleOrNull() ?: return@mapNotNull null
                ExistingEntry(
                    p.id, originId, LocalDate.ofEpochDay(p.purchaseEpochDay), -p.totalCents, p.description, hasOrigin = ("CardPurchase" to p.id) in withOrigin,
                    card = CardEntryInfo(YearMonth.parse(first.invoiceMonth), toConfirm = first.assignment == "ToConfirm", chosenByPerson = first.assignment == "Confirmed"),
                )
            }
            else -> emptyList()
        }
    }

    private fun tokenOf(batch: ImportBatchRow, facts: ImportFacts, preview: Preview, originId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(text: String) = digest.update(text.toByteArray(Charsets.UTF_8))
        add("${batch.id}|${batch.revision}|${batch.status}|")
        add(preview.items.toString())
        add(facts.known.origins.filter { it.originId == originId }.toString())
        add(facts.known.entries.toString())
        add(facts.known.distinctPairs.sortedBy { it.key + it.entryId }.toString())
        add(facts.billing.toString())
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** Entries this far from the file's dates are still read, so the reconciliation can show a near date as a possible match. */
        const val WINDOW_DAYS = 7L
    }
}
