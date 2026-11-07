package app.accord.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.CategoryNode
import app.accord.core.space.QueryResult
import app.accord.core.space.SubscriptionDetail
import app.accord.core.space.SubscriptionList
import app.accord.core.space.SubscriptionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Período consultado na tela 31: o mês escolhido; no mês corrente, a partir de hoje (o que já passou não é "próxima cobrança"). */
object SubscriptionsPeriod {
    fun of(month: YearMonth, today: LocalDate): Pair<LocalDate, LocalDate> {
        val from = if (month == YearMonth.from(today)) today else month.atDay(1)
        return from to month.plusMonths(1).atDay(1)
    }
}

/** Filtro por situação conhecida; o filtro por próxima cobrança é o mês. */
enum class SubscriptionFilter {
    All,
    Active,
    Ended,
    ;

    fun accepts(status: SubscriptionStatus) = when (this) {
        All -> true
        Active -> status == SubscriptionStatus.Active
        Ended -> status == SubscriptionStatus.Ended
    }
}

data class SubscriptionsData(val list: SubscriptionList, val categories: Map<String, CategoryNode>)

sealed interface SubscriptionsContent {
    data object Loading : SubscriptionsContent

    /** [last] é a última leitura, mantida na tela enquanto a nova carrega ou depois de uma falha. */
    data class Ready(val data: SubscriptionsData) : SubscriptionsContent
    data class Failed(val last: SubscriptionsData?) : SubscriptionsContent
}

data class SubscriptionsUiState(
    val month: YearMonth,
    val filter: SubscriptionFilter = SubscriptionFilter.All,
    val content: SubscriptionsContent = SubscriptionsContent.Loading,
)

/**
 * Assinaturas (tela 31). Mês e filtro sobrevivem à volta do detalhe e à recriação do processo; consultar não altera nada.
 */
class SubscriptionsViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SubscriptionsUiState(
            month = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: provider.clock.currentMonth(),
            filter = saved.get<String>(KEY_FILTER)?.let { runCatching { SubscriptionFilter.valueOf(it) }.getOrNull() } ?: SubscriptionFilter.All,
        ),
    )
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun previousMonth() = moveTo(_state.value.month.minusMonths(1))

    fun nextMonth() = moveTo(_state.value.month.plusMonths(1))

    fun setFilter(filter: SubscriptionFilter) {
        if (filter == _state.value.filter) return
        saved[KEY_FILTER] = filter.name
        _state.update { it.copy(filter = filter) }
    }

    private fun moveTo(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month) }
        load()
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val request = _state.value
        val last = (request.content as? SubscriptionsContent.Ready)?.data ?: (request.content as? SubscriptionsContent.Failed)?.last
        if (!keepContent) _state.update { it.copy(content = last?.let(SubscriptionsContent::Ready) ?: SubscriptionsContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val (from, until) = SubscriptionsPeriod.of(request.month, provider.clock.today())
            val list = (space.subscriptions(from, until) as? QueryResult.Data)?.value
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            // Um pedido antigo que chega depois da troca de mês não sobrescreve a tela.
            if (_state.value.month != request.month) return@launch
            _state.update {
                it.copy(content = if (list == null || categories == null) SubscriptionsContent.Failed(last) else SubscriptionsContent.Ready(SubscriptionsData(list, categories)))
            }
        }
    }

    companion object {
        private const val KEY_MONTH = "subscriptionsMonth"
        private const val KEY_FILTER = "subscriptionsFilter"
    }
}

sealed interface SubscriptionDetailContent {
    data object Loading : SubscriptionDetailContent
    data object NotFound : SubscriptionDetailContent
    data object Failed : SubscriptionDetailContent
    data class Ready(val detail: SubscriptionDetail, val categories: Map<String, CategoryNode>) : SubscriptionDetailContent
}

/** Detalhe da assinatura (tela 32): histórico comparável e a previsão, sem reescrever cobranças registradas. */
class SubscriptionDetailViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
) : ViewModel() {
    val recurrenceId: String = checkNotNull(saved[ARG_ID])

    private val _content = MutableStateFlow<SubscriptionDetailContent>(SubscriptionDetailContent.Loading)
    val content: StateFlow<SubscriptionDetailContent> = _content.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = SubscriptionDetailContent.Loading
        viewModelScope.launch {
            val space = provider.space()
            val detail = when (val r = space.subscription(recurrenceId)) {
                is QueryResult.Data -> r.value
                is QueryResult.Failure -> { _content.value = SubscriptionDetailContent.Failed; return@launch }
            }
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            _content.value = when {
                detail == null -> SubscriptionDetailContent.NotFound
                categories == null -> SubscriptionDetailContent.Failed
                else -> SubscriptionDetailContent.Ready(detail, categories)
            }
        }
    }

    companion object {
        const val ARG_ID = "subscriptionId"
    }
}
