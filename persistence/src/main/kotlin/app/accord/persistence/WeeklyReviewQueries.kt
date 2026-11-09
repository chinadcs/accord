package app.accord.persistence

import app.accord.core.UPCOMING_DAYS
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.upcomingCommitments
import app.accord.core.upcomingMonths
import app.accord.core.forecast.Excess
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.Commitment
import app.accord.core.space.MonthCommitments
import app.accord.core.space.PendingItem
import app.accord.core.space.PurchaseActivity
import app.accord.core.space.ReviewAttention
import app.accord.core.space.ReviewAttentionItem
import app.accord.core.space.ReviewBlock
import app.accord.core.space.ReviewUpcoming
import app.accord.core.space.WeeklyReviewSummary
import java.time.LocalDate
import java.time.YearMonth

/**
 * Revisão semanal (S6, tela 29). Não calcula dinheiro novo: cada bloco vem de uma consulta que as outras telas já usam (atividade de compras,
 * previsão do S5, pendências e compromissos). A referência temporal de cada bloco é diferente e fica explícita no resultado:
 * movimentos do período (data original), situação atual das pendências e das categorias, vencimentos a partir da consulta.
 */
internal class WeeklyReviewQueries(
    private val activity: suspend (LocalDate, LocalDate) -> PurchaseActivity,
    private val forecast: suspend (YearMonth) -> MonthForecast,
    private val pendings: suspend () -> List<PendingItem>,
    private val commitments: suspend (YearMonth) -> MonthCommitments,
) {
    suspend fun review(period: ReviewPeriod, reference: LocalDate): WeeklyReviewSummary = WeeklyReviewSummary(
        period = period,
        reference = reference,
        movements = block { activity(period.start, period.endExclusive) },
        attention = block { attention(reference) },
        pendings = block { pendings() },
        upcoming = block { upcoming(reference) },
    )

    /** Falha de um bloco não derruba os outros. Cancelamento da corrotina, porém, precisa seguir adiante. */
    private suspend fun <T> block(read: suspend () -> T): ReviewBlock<T> = try {
        ReviewBlock.Ready(read())
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ReviewBlock.Failed
    }

    /** Categorias com excesso já ocorrido ou possível no mês da consulta, as ocorridas primeiro; sem limite não é excesso. */
    private suspend fun attention(reference: LocalDate): ReviewAttention {
        val month = YearMonth.from(reference)
        val items = forecast(month).categories.mapNotNull { c ->
            when (c.excess) {
                is Excess.Occurred, is Excess.Possible -> ReviewAttentionItem(c.categoryId, c.excess)
                Excess.NoLimit, Excess.None -> null
            }
        }
        val ordered = items.sortedWith(
            compareBy<ReviewAttentionItem> { if (it.excess is Excess.Occurred) 0 else 1 }
                .thenByDescending { cents(it.excess) }
                .thenBy { it.categoryId },
        )
        return ReviewAttention(month, ordered)
    }

    private fun cents(excess: Excess): Long = when (excess) {
        is Excess.Occurred -> excess.cents
        is Excess.Possible -> excess.cents
        else -> 0L
    }

    private suspend fun upcoming(reference: LocalDate): ReviewUpcoming {
        val until = reference.plusDays(UPCOMING_DAYS)
        val items: List<Commitment> = upcomingCommitments(upcomingMonths(reference).flatMap { commitments(it).items }, reference, until)
        return ReviewUpcoming(reference, until, items)
    }
}
