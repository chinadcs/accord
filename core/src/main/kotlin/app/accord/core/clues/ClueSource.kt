package app.accord.core.clues

import app.accord.core.space.EntryRow
import app.accord.core.space.Origin
import java.time.LocalDate

/** Read-only evidence, in original purchase dates, never a second financial ledger. */
data class ClueEvidence(
    val entry: EntryRow,
    val revision: Long,
    val purchaseId: String?,
    val refundOf: String?,
    val subcategoryId: String?,
    val origin: Origin?,
    val importPending: Boolean,
)

/** Declared document interval; dates are inclusive/exclusive. No inferred completeness from silence. */
data class ClueCoverage(
    val batchId: String,
    val from: LocalDate?,
    val untilExclusive: LocalDate?,
    val reviewed: Boolean,
    val originId: String?,
)

data class ClueSource(
    val from: LocalDate,
    val untilExclusive: LocalDate,
    val evidence: List<ClueEvidence>,
    val coverage: List<ClueCoverage>,
)

data class ClueImportOrigin(
    val batchId: String,
    val ordinal: Int,
    val description: String,
    val originalFields: List<Pair<String, String>>,
    val pending: Boolean,
)
