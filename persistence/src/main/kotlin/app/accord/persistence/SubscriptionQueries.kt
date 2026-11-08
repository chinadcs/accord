package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.analysis.SubscriptionAnalysis
import app.accord.core.analysis.SubscriptionCharge
import app.accord.core.sum
import app.accord.core.space.FrequencySubtotal
import app.accord.core.space.OccurrenceView
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RecurrenceView
import app.accord.core.space.SubscriptionDetail
import app.accord.core.space.SubscriptionList
import app.accord.core.space.SubscriptionRow
import app.accord.core.space.SubscriptionStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * Consultas de leitura de Assinaturas (S6, telas 31 e 32). Uma assinatura é uma série de despesa recorrente cuja categoria atual está em
 * Assinaturas; não existe entidade separada. Só cobranças efetivadas (pagamentos registrados da ocorrência) entram na comparação de preço:
 * valores esperados de ocorrências previstas nunca viram "aumento". Nada aqui grava.
 */
internal class SubscriptionQueries(
    private val dao: SpaceDao,
    private val tree: suspend () -> CategoryTree,
    private val subscriptionsMainId: suspend () -> String,
    private val load: suspend (RecurrenceRow) -> LoadedSeries,
    private val place: suspend (LoadedSeries, Int) -> OccurrenceView?,
    private val month: () -> YearMonth,
) {
    private class Candidate(val loaded: LoadedSeries, val categoryId: String?)

    /** Séries de despesa vivas cuja categoria atual (a do modelo mais recente) é Assinaturas ou uma de suas subcategorias. */
    private suspend fun candidates(onlyId: String? = null): List<Candidate> {
        val tree = tree()
        val main = subscriptionsMainId()
        val now = month()
        return dao.activeRecurrences()
            .filter { it.kind == RecurrenceKind.Expense.name && (onlyId == null || it.id == onlyId) }
            .map { load(it).let { loaded -> Candidate(loaded, loaded.series.latestTemplate.classificationId) } }
            .filter { c -> c.categoryId != null && tree.row(c.categoryId) != null && tree.mainAt(c.categoryId, now) == main }
    }

    private fun charges(loaded: LoadedSeries): List<SubscriptionCharge> =
        loaded.settlements.values.map {
            SubscriptionCharge(loaded.row.id, it.number, LocalDate.ofEpochDay(it.epochDay), Money(it.cents))
        }.sortedBy { it.number }

    private suspend fun rowOf(c: Candidate, from: LocalDate): SubscriptionRow {
        val series = c.loaded.series
        val nextNumber = series.nextOnOrAfter(from)?.number
        val next = nextNumber?.let { place(c.loaded, it) }
        return SubscriptionRow(
            recurrenceId = c.loaded.row.id,
            description = next?.description ?: series.latestTemplate.description,
            categoryId = next?.categoryId ?: c.categoryId,
            frequency = Frequency.valueOf(c.loaded.row.frequency),
            form = RecurrenceForm.valueOf(c.loaded.row.form),
            status = if (next == null) SubscriptionStatus.Ended else SubscriptionStatus.Active,
            next = next,
            comparison = SubscriptionAnalysis.compare(charges(c.loaded)),
        )
    }

    suspend fun list(from: LocalDate, untilExclusive: LocalDate): SubscriptionList {
        require(from.isBefore(untilExclusive)) { "O período precisa ter ao menos um dia" }
        val found = candidates()
        val rows = found.map { rowOf(it, from) }
        val active = rows.filter { it.status == SubscriptionStatus.Active }

        val subtotals = active.groupBy { it.frequency }
            .map { (frequency, group) -> FrequencySubtotal(frequency, group.size, group.mapNotNull { it.next?.expectedAmount }.sum()) }
            .sortedBy { it.frequency.ordinal }

        var dueCount = 0
        var due = Money.ZERO
        for (c in found) {
            for (occurrence in c.loaded.series.occurrencesBetween(from, untilExclusive)) {
                dueCount++
                due += c.loaded.settlements[occurrence.number]?.let { Money(it.cents) } ?: occurrence.template.amount
            }
        }

        val ordered = active.sortedWith(compareBy({ it.next!!.scheduledDate }, { it.description.lowercase() }, { it.recurrenceId })) +
            rows.filter { it.status == SubscriptionStatus.Ended }.sortedWith(compareBy({ it.description.lowercase() }, { it.recurrenceId }))
        return SubscriptionList(from, untilExclusive, ordered, subtotals, due, dueCount)
    }

    suspend fun detail(recurrenceId: String, today: LocalDate): SubscriptionDetail? {
        val c = candidates(onlyId = recurrenceId).singleOrNull() ?: return null
        val row = rowOf(c, today)
        val charges = charges(c.loaded)
        val r = c.loaded.row
        val lastCharge = charges.lastOrNull()
        val expected = row.next?.expectedAmount
        return SubscriptionDetail(
            recurrence = RecurrenceView(
                id = r.id, kind = RecurrenceKind.valueOf(r.kind), form = RecurrenceForm.valueOf(r.form), frequency = Frequency.valueOf(r.frequency),
                firstDate = LocalDate.ofEpochDay(r.anchorEpochDay), endsBefore = r.endsBefore, revision = r.revision, voided = r.voided,
            ),
            description = row.description,
            categoryId = row.categoryId,
            status = row.status,
            next = row.next,
            charges = charges,
            comparison = row.comparison,
            nextVersusLastCharge = if (expected != null && lastCharge != null) expected - lastCharge.amount else null,
        )
    }
}
