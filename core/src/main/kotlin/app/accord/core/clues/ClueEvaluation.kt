package app.accord.core.clues

import app.accord.core.space.ItemSync
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class ClueEvidenceSnapshot(val id: String, val kind: String, val description: String, val cents: Long,
    val epochDay: Long, val categoryId: String?, val revision: Long)

@Serializable
data class ClueSnapshot(val kind: String, val fromDay: Long, val untilDay: Long, val subcategoryId: String?,
    val previousCount: Int?, val currentCount: Int?, val evidence: List<ClueEvidenceSnapshot>)

fun Clue.snapshot() = ClueSnapshot(kind.name, from.toEpochDay(), untilExclusive.toEpochDay(), subcategoryId, previousCount, currentCount,
    evidence.map { ClueEvidenceSnapshot(it.entry.id, it.entry.kind.name, it.entry.description, it.entry.amount.cents,
        it.entry.date.toEpochDay(), it.entry.categoryId, it.revision) })

data class ClueEvaluation(val id: String, val key: String, val fingerprint: String, val authorId: String,
    val evaluatedAtMillis: Long, val snapshot: ClueSnapshot, val sync: ItemSync)
