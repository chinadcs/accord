package app.accord.core.clues

import app.accord.core.space.EntryKind
import app.accord.core.space.EntrySituation
import app.accord.core.space.Origin
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

enum class ClueKind { SimilarCharges, FrequencyIncrease }

data class Clue(
    val key: String,
    val kind: ClueKind,
    val from: LocalDate,
    val untilExclusive: LocalDate,
    val evidence: List<ClueEvidence>,
    /** Relevant content, not revision: unrelated edits must not reopen an evaluation. */
    val fingerprint: String,
    val subcategoryId: String? = null,
    val previousCount: Int? = null,
    val currentCount: Int? = null,
)

data class ClueAnalysis(val clues: List<Clue>, val frequencyCoverageAdequate: Boolean)

/** ADR 0056. No commands, network, clock reads, random IDs, or financial side effects. */
object ClueDetector {
    fun analyze(source: ClueSource, reference: LocalDate): ClueAnalysis {
        val previousStart = reference.minusDays(56)
        require(source.from <= previousStart && source.untilExclusive >= reference)
        val currentStart = reference.minusDays(28)
        val purchases = source.evidence.filter {
            it.entry.kind in setOf(EntryKind.DirectPayment, EntryKind.CardPurchase, EntryKind.RecurrenceOccurrence) &&
                !it.importPending && it.entry.amount.cents > 0 &&
                EntrySituation.Cancelled !in it.entry.situations &&
                EntrySituation.CancelRequested !in it.entry.situations &&
                it.entry.date >= previousStart && it.entry.date < reference
        }.distinctBy { it.entry.id }.sortedBy { it.entry.id }
        val clues = mutableListOf<Clue>()
        purchases.filter { it.entry.kind != EntryKind.RecurrenceOccurrence && normalized(it.entry.description).isNotEmpty() }
            .groupBy { Triple(it.entry.date, it.entry.amount.cents, normalized(it.entry.description)) }
            .values.filter { it.size >= 2 }.forEach { items ->
                val key = digest(listOf("similar-v1") + items.map { it.entry.id })
                clues += Clue(key, ClueKind.SimilarCharges, items.first().entry.date, items.first().entry.date.plusDays(1),
                    items, fingerprint(items, false))
            }
        val covered = adequateCoverage(source, previousStart, reference)
        if (covered) purchases.filter { it.subcategoryId != null }.groupBy { it.subcategoryId!! }.toSortedMap().forEach { (sub, items) ->
            val previous = items.count { it.entry.date < currentStart }
            val current = items.size - previous
            if (previous >= 3 && current >= 6 && current.toLong() >= previous.toLong() * 2) {
                val key = digest(listOf("frequency-v1", sub, previousStart.toString(), currentStart.toString(), reference.toString()))
                clues += Clue(key, ClueKind.FrequencyIncrease, previousStart, reference, items,
                    fingerprint(items, true), sub, previous, current)
            }
        }
        return ClueAnalysis(clues.sortedBy { it.key }, covered)
    }

    /** Every known imported channel must cover every day; a manual/unknown channel cannot certify completeness. */
    private fun adequateCoverage(source: ClueSource, from: LocalDate, until: LocalDate): Boolean {
        if (source.evidence.any { it.origin != Origin.Import || it.importPending }) return false
        val channels = source.coverage.mapNotNull { it.originId }.toSet()
        if (channels.isEmpty()) return false
        return channels.all { channel ->
            val intervals = source.coverage.filter { it.originId == channel && it.reviewed && it.from != null && it.untilExclusive != null }
                .sortedBy { it.from }
            var cursor = from
            for (interval in intervals) {
                if (interval.from!! > cursor) break
                if (interval.untilExclusive!! > cursor) cursor = interval.untilExclusive
            }
            cursor >= until
        }
    }

    private fun fingerprint(items: List<ClueEvidence>, frequency: Boolean): String = digest(items.sortedBy { it.entry.id }.flatMap {
        listOf(it.entry.id, it.entry.date.toString(), it.entry.amount.cents.toString(), normalized(it.entry.description)) +
            if (frequency) listOf(it.subcategoryId.orEmpty()) else emptyList()
    })

    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Length-prefix avoids collisions from delimiters inside user descriptions/IDs. */
    private fun digest(parts: List<String>): String {
        val bytes = parts.joinToString("") { "${it.toByteArray(StandardCharsets.UTF_8).size}:$it" }.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
