package app.accord.core.importing

import app.accord.core.space.EntityType
import app.accord.core.space.FieldError
import app.accord.core.space.QueryResult
import java.time.LocalDate
import java.time.YearMonth

/**
 * Import session of the financial space (S4 "Extensão da fachada: sessão de importação"). Everything before [confirm] is local work in
 * progress: it is never queued for sync and writes no operation. [confirm] is one atomic local transaction that runs the same S1 commands
 * the Chat and the manual forms use. Pure contract, no I/O: implemented over Room in `:persistence`.
 */
interface ImportSession {
    /** Idempotent by [StartImport.batchId]. A file whose fingerprint already belongs to a live batch is reported, not duplicated. */
    suspend fun start(request: StartImport): QueryResult<StartOutcome>

    /** Chooses document kind, registered origin (account or card) and, for a card document, the invoice it belongs to. Review only. */
    suspend fun setSource(batchId: String, source: BatchSource, expectedRevision: Long): QueryResult<SourceOutcome>

    suspend fun decide(batchId: String, decision: ImportDecision, expectedRevision: Long): QueryResult<DecideOutcome>

    /** Recomputed from the stored extraction, the decisions and the current local state, so data received by sync is reflected. */
    suspend fun review(batchId: String): QueryResult<BatchReview?>

    /**
     * [confirmationId] is the idempotency key of the intention to confirm; [summaryToken] is the one of the [BatchReview] the person saw.
     * A token that no longer matches means something relevant changed: nothing is applied and the new review comes back.
     */
    suspend fun confirm(batchId: String, confirmationId: String, summaryToken: String): QueryResult<ConfirmOutcome>

    /** Only an unconfirmed batch; the extracted data of the batch is deleted. Repeating it is harmless. */
    suspend fun discard(batchId: String): QueryResult<DiscardOutcome>

    suspend fun result(batchId: String): QueryResult<BatchResult?>
    suspend fun batches(): QueryResult<List<BatchInfo>>

    // ---- dúvidas que ficaram guardadas num lote confirmado (S4 "Fila de pendências") ----

    /** Movements still open in confirmed batches, re-read against the current state: one that became clear shows as ready to apply. */
    suspend fun pendings(): QueryResult<List<PendingMovement>>

    /** One open movement with the evidence to decide it. Null when it no longer is open (resolved here or on the other device). */
    suspend fun pending(batchId: String, ordinal: Int): QueryResult<PendingDetail?>

    /**
     * Applies what the person decided for the open movement [ordinal], through the same commands as the confirmation, in one local transaction.
     * [summaryToken] is the one of the [PendingDetail] the person saw; [operationId] is the idempotency key of the intention.
     */
    suspend fun resolve(batchId: String, ordinal: Int, operationId: String, summaryToken: String): QueryResult<ResolveOutcome>
}

/** Local life of a batch. Only [Confirmed] is shared with the other device; a currency block is a derived state of [InReview]. */
enum class BatchStatus { InReview, Confirmed }

data class StartImport(
    val batchId: String,
    val fileName: String,
    val sizeBytes: Long,
    val extraction: ExtractionResult,
    val source: BatchSource = BatchSource(),
)

/** Person's choices; each may stay undefined until the confirmation. [originId] is an account id or the card id. */
data class BatchSource(val kind: DocumentKind? = null, val originId: String? = null, val documentInvoice: YearMonth? = null)

sealed interface StartOutcome {
    data class Started(val info: BatchInfo) : StartOutcome

    /** The same [StartImport.batchId] was started before (retry after a lost answer). */
    data class AlreadyStarted(val info: BatchInfo) : StartOutcome

    /** The same file (fingerprint) belongs to a live batch: open its result or review instead of preparing another one. */
    data class KnownFile(val info: BatchInfo) : StartOutcome
}

sealed interface SourceOutcome {
    /** [droppedDecisions]: link/separate choices that pointed at entries of the previous origin and went back to doubt. */
    data class Applied(val revision: Long, val droppedDecisions: Int) : SourceOutcome
    data class Stale(val currentRevision: Long) : SourceOutcome
    data class Rejected(val errors: List<FieldError>) : SourceOutcome
    data object NotFound : SourceOutcome
}

/** What the person decides about a movement ([ordinal] is the extraction order; the origin key is derived when needed). */
sealed interface ImportDecision {
    data class Category(val ordinal: Int, val categoryId: String) : ImportDecision

    /** Batch-wide default for new movements without a chosen category. [categoryId] null clears it. */
    data class DefaultCategory(val categoryId: String?) : ImportDecision

    /** Accepts the suggestion shown for each of [ordinals]; null means every suggestion currently shown. */
    data class AcceptSuggestions(val ordinals: List<Int>? = null) : ImportDecision

    data class LinkTo(val ordinal: Int, val entryId: String) : ImportDecision

    /** "They are different purchases": persists a distinct pair that stays out of every future match, re-imports included. */
    data class KeepSeparate(val ordinal: Int, val entryId: String) : ImportDecision

    /** Back to the automatic proposal for this movement (clears its category, link and separate choices). */
    data class Reset(val ordinal: Int) : ImportDecision

    /** "It is a new movement": clears the doubts that only say it may not be (divergent FITID, possible repetition, removed before). */
    data class ConfirmNew(val ordinal: Int) : ImportDecision

    /** A credit that is income, in the chosen income category. Never decided from the sign of the value. Refused on a card document: there a credit is a refund. */
    data class AsIncome(val ordinal: Int, val incomeCategoryId: String) : ImportDecision

    /** "It repeats a movement already registered": nothing is created; the origin is remembered so a re-import does not ask again. */
    data class Dismiss(val ordinal: Int) : ImportDecision

    /** Hold this movement out of the confirmation: it stays a pending item, with its original, and nothing is created for it. */
    data class ReviewLater(val ordinal: Int) : ImportDecision

    /**
     * "It repeats another movement of this same file": like [Dismiss] (nothing is created, the origin is remembered) but the link to the
     * original is kept, and both originals stay. The original must exist, be another movement and not be a repetition itself.
     */
    data class RepeatOf(val ordinal: Int, val ofOrdinal: Int) : ImportDecision

    /**
     * "It is the same purchase and the right invoice is the one of the document": links the movement to [entryId] and lets the confirmation
     * correct the invoice the person had chosen. Without it a chosen invoice that differs from the document stays a doubt.
     */
    data class CorrectInvoice(val ordinal: Int, val entryId: String) : ImportDecision

    /** Accepts the first installment the doubt suggests (the document's invoice minus n - 1 months): the whole schedule shifts, shown before and after. */
    data class ShiftFirstInstallment(val ordinal: Int) : ImportDecision

    /** A credit that returns money of an earlier purchase or payment ([originId]): a refund, never income. Needs the person's choice of the origin. */
    data class AsRefund(val ordinal: Int, val originId: String) : ImportDecision

    /**
     * An installment line (n of N) without a registered purchase: the person confirms its [totalCents] and the purchase is created with every installment,
     * the ones before n on past invoices. The total must split into this line's value; the category comes from the usual choice or suggestion.
     */
    data class AsInstallmentPurchase(val ordinal: Int, val totalCents: Long) : ImportDecision

    /**
     * Accepts the total each installment purchase of the file suggests (what the lines show plus the missing ones at the same value): one tap for every series of
     * [ordinals] (null: all that may be accepted in bulk; a refinancing of the invoice never is). The decision is kept on the first line of each series.
     */
    data class AcceptInstallmentTotals(val ordinals: List<Int>? = null) : ImportDecision

    /** A card credit whose purchase is not in the app: registers a purchase of the same value and date, in the chosen category, and refunds all of it. */
    data class AsNewPurchaseRefund(val ordinal: Int) : ImportDecision
}

sealed interface DecideOutcome {
    data class Applied(val revision: Long, val affectedOrdinals: List<Int>) : DecideOutcome
    data class Stale(val currentRevision: Long) : DecideOutcome
    data class Rejected(val errors: List<FieldError>) : DecideOutcome
    data object NotFound : DecideOutcome
}

sealed interface DiscardOutcome {
    data object Discarded : DiscardOutcome
    data object NotAllowed : DiscardOutcome
}

/** Why a batch cannot be confirmed right now. Each one is explainable on screen. */
enum class Blocker {
    /** Currency other than BRL: diagnostic only, no automatic conversion. */
    UnsupportedCurrency,

    /** Document kind or registered origin not chosen (or not found). */
    SourceUndefined,

    /** Nothing new or safely linked to confirm. */
    NothingChecked,

    /** Already confirmed. */
    AlreadyConfirmed,
}

data class BatchInfo(
    val id: String,
    val status: BatchStatus,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val format: SourceFormat,
    val declaredKind: DocumentKind?,
    val source: BatchSource,
    val currency: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val dueDate: LocalDate?,
    val institution: String?,
    val movementCount: Int,
    val unrecognizedSegments: Int,
    val startedAtMillis: Long,
    val confirmedAtMillis: Long?,
    /** Bumps on every change of source or decisions. */
    val revision: Long,
)

/** What a confirmation would do, to be shown before it happens. */
data class ConfirmationSummary(
    val creates: Int,
    val links: Int,
    val invoiceCorrections: Int,
    val installmentLinks: Int,
    val invoicePayments: Int,
    val boletoPayments: Int,
    val incomes: Int,
    val alreadyKnown: Int,
    /** Dismissed by the person as a repetition: remembered, nothing created. */
    val dismissed: Int = 0,
    /** Credits the person said refund an earlier purchase. */
    val refunds: Int = 0,
    /** Movements that stay as doubts after confirming: they become pending items, never effects. */
    val pendingDoubts: Int,
    /** Installment schedule before and after, for each purchase whose first installment changes invoice. */
    val installmentShifts: List<InstallmentShift>,
    val affectedMonths: Set<YearMonth>,
)

data class BatchReview(
    val info: BatchInfo,
    val preview: Preview,
    val blockers: Set<Blocker>,
    val summary: ConfirmationSummary,
    /** Identifies exactly what the person saw: the same value comes back only if nothing relevant changed. */
    val token: String,
    /** The registered entries a movement may still be linked to (the origin's entries without an origin record). */
    val linkableEntries: List<LinkableEntry>,
)

/** [invoice] and [invoiceToConfirm] are the card purchase's estimated invoice (due month) and its "a conferir" mark; null for an account entry. */
data class LinkableEntry(
    val id: String,
    val date: LocalDate,
    val cents: Long,
    val description: String,
    val invoice: YearMonth? = null,
    val invoiceToConfirm: Boolean = false,
)

/** A purchase or payment a credit may refund: [totalCents] is the whole amount (positive), [remainingCents] what can still be refunded. */
data class RefundCandidate(val id: String, val date: LocalDate, val totalCents: Long, val remainingCents: Long, val description: String)

/** What happened to one movement when the batch was confirmed. */
enum class Resolution {
    /** A new entry was created from the movement. */
    Created,

    /** Linked to an entry that already existed; the entry itself is untouched (an invoice correction may accompany it). */
    Linked,
    InstallmentLinked,
    InvoicePaid,
    BoletoPaid,
    IncomeCreated,

    /** A credit the person said refunds an earlier purchase: a confirmed refund linked to it, not income. */
    RefundCreated,

    /** Recognised from an earlier import: nothing to do. */
    AlreadyKnown,

    /** The person dismissed it as a repetition of something already registered: remembered, nothing created. */
    Dismissed,

    /** A doubt left for the pending queue; the original is preserved. */
    Pending,
}

data class ResultItem(
    val ordinal: Int,
    val key: String,
    val resolution: Resolution,
    val targetType: EntityType?,
    val targetId: String?,
    val doubts: Set<DoubtReason>,
    /** The movement as extracted, so the result can be read without the file: normalised fields and the original texts. */
    val date: LocalDate? = null,
    val cents: Long? = null,
    val currency: String? = null,
    val description: String = "",
    val originalFields: List<Pair<String, String>> = emptyList(),
    /** The ordinal of the movement of the same file this one was marked a repetition of. */
    val repeatOf: Int? = null,
)

data class BatchResult(
    val info: BatchInfo,
    val items: List<ResultItem>,
) {
    fun count(resolution: Resolution) = items.count { it.resolution == resolution }

    /** Complete when no doubt is left and nothing in the file was left unread (S4 "Cobertura e período conferido"). */
    val complete: Boolean get() = count(Resolution.Pending) == 0 && info.unrecognizedSegments == 0
}

sealed interface ConfirmOutcome {
    data class Applied(val result: BatchResult) : ConfirmOutcome

    /** The same confirmation id was applied before: the stored result, nothing applied again. */
    data class AlreadyApplied(val result: BatchResult) : ConfirmOutcome

    /** Something relevant changed since the review the person saw; nothing was applied. */
    data class ReviewNeeded(val review: BatchReview) : ConfirmOutcome
    data class Blocked(val blockers: Set<Blocker>) : ConfirmOutcome

    /** The common validator refused a step (for example a category removed meanwhile); nothing was applied. */
    data class Invalid(val errors: List<FieldError>) : ConfirmOutcome
    data object NotFound : ConfirmOutcome
}

/** An open movement of a confirmed batch. [doubts] and [classification] are re-read now; [ready] means applying it would work. */
data class PendingMovement(
    val batchId: String,
    val ordinal: Int,
    val fileName: String,
    val kind: DocumentKind?,
    val date: LocalDate?,
    val cents: Long?,
    val currency: String?,
    val description: String,
    val classification: Classification,
    val doubts: Set<DoubtReason>,
    val ready: Boolean,
    val batchStartedAtMillis: Long,
    /** The invoice (due month) the card document belongs to, when it was a card document. */
    val documentInvoice: YearMonth? = null,
    /** The person chose "review later" for it when the batch was confirmed. */
    val postponed: Boolean = false,
)

data class PendingDetail(
    val pending: PendingMovement,
    val reconciled: ReconciledMovement,
    /** Entries of the origin that could still be linked to this movement (candidates first is up to the screen). */
    val linkableEntries: List<LinkableEntry>,
    /** Identifies what the person saw; a different value at [ImportSession.resolve] means the evidence changed. */
    val token: String,
    /** Purchases or payments of the origin, recent first, that this movement could refund if it is a credit. */
    val refundCandidates: List<RefundCandidate> = emptyList(),
)

sealed interface ResolveOutcome {
    data class Applied(val resolution: Resolution) : ResolveOutcome
    data class AlreadyApplied(val resolution: Resolution) : ResolveOutcome

    /** What the person saw no longer holds; nothing was applied. */
    data class ReviewNeeded(val detail: PendingDetail) : ResolveOutcome

    /** There is still a doubt, or a choice is missing; nothing was applied. */
    data class NotReady(val detail: PendingDetail) : ResolveOutcome
    data class Invalid(val errors: List<FieldError>) : ResolveOutcome
    data object NotFound : ResolveOutcome
}
