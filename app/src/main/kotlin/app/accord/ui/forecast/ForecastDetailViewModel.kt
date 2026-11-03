package app.accord.ui.forecast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.AlertEventView
import app.accord.core.space.CategoryNode
import app.accord.core.space.ForecastDetail
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ForecastDetailData(
    val detail: ForecastDetail,
    val categories: Map<String, CategoryNode>,
    /** O evento do aviso que abriu a tela, quando foi aberta por notificação. */
    val event: AlertEventView?,
) {
    val name: String get() = categories[detail.forecast.categoryId]?.name.orEmpty()
}

sealed interface ForecastDetailContent {
    data object Loading : ForecastDetailContent
    data class Ready(val data: ForecastDetailData) : ForecastDetailContent
    data class Failed(val last: ForecastDetailData?) : ForecastDetailContent

    /** A categoria foi removida ou não é principal: a tela explica e leva de volta às previsões. */
    data object Gone : ForecastDetailContent
}

/**
 * Detalhe da previsão (tela 21). Sempre mostra o cálculo atual; aberta por aviso, mostra também o que o aviso dizia e a frase de que a previsão foi atualizada.
 */
class ForecastDetailViewModel(private val provider: SpaceProvider, saved: SavedStateHandle) : ViewModel() {
    val categoryId: String = checkNotNull(saved[ARG_CATEGORY])
    val month: YearMonth = YearMonth.parse(checkNotNull(saved[ARG_MONTH]))
    private val eventId: String? = saved.get<String>(ARG_EVENT)?.takeIf { it.isNotBlank() }

    /** Aberta por aviso: oferece o retorno às previsões (tela 21 §5). */
    val fromAlert: Boolean get() = eventId != null

    private val _state = MutableStateFlow<ForecastDetailContent>(ForecastDetailContent.Loading)
    val state: StateFlow<ForecastDetailContent> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
        viewModelScope.launch { provider.feedback.changed.collect { refresh() } }
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val last = (_state.value as? ForecastDetailContent.Ready)?.data ?: (_state.value as? ForecastDetailContent.Failed)?.last
        if (!keepContent) _state.value = last?.let(ForecastDetailContent::Ready) ?: ForecastDetailContent.Loading
        viewModelScope.launch {
            val space = provider.space()
            val detail = space.forecastDetail(categoryId, month)
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            val event = eventId?.let { (space.alertEvent(it) as? QueryResult.Data)?.value }
            _state.value = when {
                detail !is QueryResult.Data || categories == null -> ForecastDetailContent.Failed(last)
                detail.value == null -> ForecastDetailContent.Gone
                else -> ForecastDetailContent.Ready(ForecastDetailData(detail.value!!, categories, event))
            }
        }
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
        const val ARG_MONTH = "month"
        const val ARG_EVENT = "event"
    }
}
