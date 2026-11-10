package app.accord.ui.home

import app.accord.core.UpcomingEvent
import app.accord.ui.pending.PendingQueueUiState

/** As três leituras que alimentam a faixa "Pede atenção", na ordem fixa em que aparecem (ADR 0078). */
enum class AttentionSource { Review, Dates, Exceed }

/** Uma linha da faixa. Carga e falha são linhas próprias: nunca viram zero, vazio nem "tudo em dia". */
sealed interface AttentionLine {
    val source: AttentionSource

    data class Loading(override val source: AttentionSource) : AttentionLine
    data class Failed(override val source: AttentionSource) : AttentionLine

    /** [count] zero é a linha neutra de Revisar, que nunca desaparece (ADR 0067). */
    data class Review(val count: Int) : AttentionLine {
        override val source get() = AttentionSource.Review
    }

    /** Há ao menos um evento; o primeiro é o mais próximo. */
    data class Dates(val events: List<UpcomingEvent>) : AttentionLine {
        override val source get() = AttentionSource.Dates
    }

    /** Há ao menos uma categoria projetada a estourar; a primeira é a mais próxima. */
    data class Exceed(val rows: List<ProjectionRow>) : AttentionLine {
        override val source get() = AttentionSource.Exceed
    }

    /** Só as linhas com dado levam a um destino; carga e falha de datas e estouro não têm alvo de toque. */
    val opensDestination: Boolean
        get() = this is Review || this is Dates || this is Exceed || (this is Failed && source == AttentionSource.Review)
}

/**
 * O que a faixa mostra. [allClear] ("Nada nos próximos 7 dias e nenhuma categoria deve passar do limite") só é verdadeiro
 * quando as três leituras terminaram, nenhuma falhou e todas estão vazias; [failed] diz de quais leituras oferecer repetir.
 */
data class AttentionPlan(val lines: List<AttentionLine>, val allClear: Boolean, val failed: Set<AttentionSource>)

/**
 * Compõe a faixa a partir das três leituras já feitas, sem ler nada. Revisar está sempre presente; datas e estouro só
 * aparecem com dado, em carga ou em falha. A fila de pendências falha antes de carregar, como em [pendingSummary].
 */
fun attentionPlan(pending: PendingQueueUiState, upcoming: HomeUpcomingState, projection: HomeProjectionState): AttentionPlan {
    val review = when {
        pending.failed -> AttentionLine.Failed(AttentionSource.Review)
        pending.loading -> AttentionLine.Loading(AttentionSource.Review)
        else -> AttentionLine.Review(pending.items.size)
    }
    val dates = when (upcoming) {
        HomeUpcomingState.Loading -> AttentionLine.Loading(AttentionSource.Dates)
        HomeUpcomingState.Failed -> AttentionLine.Failed(AttentionSource.Dates)
        is HomeUpcomingState.Ready -> upcoming.events.takeIf { it.isNotEmpty() }?.let(AttentionLine::Dates)
    }
    val exceed = when (projection) {
        HomeProjectionState.Loading -> AttentionLine.Loading(AttentionSource.Exceed)
        HomeProjectionState.Failed -> AttentionLine.Failed(AttentionSource.Exceed)
        is HomeProjectionState.Ready -> projection.rows.takeIf { it.isNotEmpty() }?.let(AttentionLine::Exceed)
    }
    val lines = listOfNotNull(review, dates, exceed)
    val failed = lines.filterIsInstance<AttentionLine.Failed>().map { it.source }.toSet()
    val allClear = lines.size == 1 && (review as? AttentionLine.Review)?.count == 0 &&
        upcoming is HomeUpcomingState.Ready && projection is HomeProjectionState.Ready
    return AttentionPlan(lines, allClear, failed)
}
