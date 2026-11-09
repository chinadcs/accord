package app.accord.persistence

import app.accord.core.clues.*
import app.accord.core.space.*
import java.time.LocalDate

internal class ClueQueries(
    private val dao: SpaceDao,
    private val entries: EntryQueries,
    private val detail: suspend (String) -> EntryDetail?,
    private val pendingCorrespondences: suspend () -> Set<String> = { emptySet() },
) {
    suspend fun origins(entryId: String): List<ClueImportOrigin> {
        val root = entryId.substringBefore('#')
        return dao.confirmedImportBatches().flatMap { batch ->
            dao.importMovements(batch.id).filter { it.targetId == root }.map {
                ClueImportOrigin(batch.id, it.ordinal, it.description, ImportCodec.decodeFields(it.originalFields), it.pending.isNotEmpty())
            }
        }
    }

    suspend fun source(from: LocalDate, until: LocalDate): ClueSource {
        require(from < until)
        val batches = dao.confirmedImportBatches()
        val pendingBatches = dao.batchesWithPending().toSet()
        val pendingTargets = batches.flatMap { dao.importMovements(it.id) }
            .filter { it.pending.isNotEmpty() }.mapNotNull { it.targetId }.toSet() + pendingCorrespondences()
        val tree = CategoryTree(dao.categories().associateBy { it.id }, dao.categoryMoves())
        val evidence = entries.clueRows(from, until).mapNotNull { row ->
            val current = detail(row.id)?.takeUnless { it.voided } ?: return@mapNotNull null
            ClueEvidence(
                row, current.revision,
                row.id.takeIf { row.kind == EntryKind.CardPurchase },
                if (row.kind == EntryKind.Refund) dao.refund(row.id)?.originId else null,
                row.categoryId?.let { tree.subAt(it, java.time.YearMonth.from(row.date)) },
                current.origin, row.id.substringBefore('#') in pendingTargets,
            )
        }
        val coverage = batches.map { b ->
            ClueCoverage(b.id, b.periodStartDay?.let(LocalDate::ofEpochDay), b.periodEndDay?.let { LocalDate.ofEpochDay(it).plusDays(1) },
                b.id !in pendingBatches && b.unrecognizedSegments == 0, b.originId)
        }
        return ClueSource(from, until, evidence, coverage)
    }
}
