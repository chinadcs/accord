package app.accord.ui.forecast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.AlertStatus
import app.accord.core.space.CategoryNode
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ForecastsData(
    val forecast: MonthForecast,
    val categories: Map<String, CategoryNode>,
    /** Avisos da janela atual por categoria; só valem para o mês corrente. */
    val alerts: Map<String, AlertStatus>,
    val currentMonth: Boolean,
) {
    /** Categorias que têm algo a dizer: gasto conhecido, limite ou estimativa. Excesso primeiro, depois o maior total. */
    val shown: List<CategoryForecast>
        get() = forecast.categories
            .filter { it.knownCents != 0L || it.limitCents != null || ((it.estimate as? Estimate.Value)?.cents ?: 0L) > 0L }
            .sortedWith(compareBy<CategoryForecast> { rank(it.excess) }.thenByDescending { it.totalCents })

    private fun rank(excess: Excess) = when (excess) {
        is Excess.Occurred -> 0
        is Excess.Possible -> 1
        else -> 2
    }
}

sealed interface ForecastsContent {
    data object Loading : ForecastsContent
    data class Ready(val data: ForecastsData) : ForecastsContent

    /** [last] é o último cálculo, mantido na tela: a falha da estimativa não apaga o que já era conhecido. */
    data class Failed(val last: ForecastsData?) : ForecastsContent
}

data class ForecastsUiState(val month: YearMonth, val content: ForecastsContent = ForecastsContent.Loading)

/**
 * Previsões e alertas (tela 20). O mês sobrevive à volta do detalhe e à recriação do processo; recalcular atualiza a tela e nunca dispara aviso (só mudança nos
 * dados avalia, e a cota da janela não renova).
 */
class ForecastsViewModel(private val provider: SpaceProvider, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(
        ForecastsUiState(
            saved.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: saved.get<String>(ARG_MONTH)?.let(YearMonth::parse) ?: provider.clock.currentMonth(),
        ),
    )
    val state: StateFlow<ForecastsUiState> = _state.asStateFlow()

    val month: YearMonth get() = _state.value.month

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
        viewModelScope.launch { provider.feedback.changed.collect { refresh() } }
    }

    fun previousMonth() = moveTo(month.minusMonths(1))

    fun nextMonth() = moveTo(month.plusMonths(1))

    private fun moveTo(next: YearMonth) {
        saved[KEY_MONTH] = next.toString()
        _state.update { it.copy(month = next) }
        load()
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val request = _state.value
        val last = (request.content as? ForecastsContent.Ready)?.data ?: (request.content as? ForecastsContent.Failed)?.last
        if (!keepContent) _state.update { it.copy(content = last?.let(ForecastsContent::Ready) ?: ForecastsContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val forecast = (space.forecasts(request.month) as? QueryResult.Data)?.value
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            val alerts = (space.alertStatuses() as? QueryResult.Data)?.value.orEmpty().associateBy { it.categoryId }
            // Um pedido antigo que chega depois da troca de mês não sobrescreve a tela.
            if (_state.value.month != request.month) return@launch
            _state.update {
                it.copy(
                    content = if (forecast == null || categories == null) {
                        ForecastsContent.Failed(last)
                    } else {
                        ForecastsContent.Ready(ForecastsData(forecast, categories, alerts, request.month == provider.clock.currentMonth()))
                    },
                )
            }
        }
    }

    companion object {
        const val ARG_MONTH = "month"
        private const val KEY_MONTH = "forecastMonth"
    }
}
