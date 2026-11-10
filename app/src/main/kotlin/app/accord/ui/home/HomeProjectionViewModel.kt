package app.accord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.forecast.ExceedProjection
import app.accord.core.forecast.exceedProjections
import app.accord.core.space.QueryResult
import app.accord.ui.colorKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

/** [iconKey] e [colorKey] vêm da categoria (cor da principal); nulos quando ela não foi encontrada. */
data class ProjectionRow(val categoryName: String, val projection: ExceedProjection, val iconKey: String? = null, val colorKey: String? = null)

sealed interface HomeProjectionState {
    data object Loading : HomeProjectionState
    data object Failed : HomeProjectionState
    data class Ready(val rows: List<ProjectionRow>) : HomeProjectionState
}

/** Projeção de estouro do Início: só lê a previsão do mês; não cria aviso, não consome a cota semanal de alertas. */
class HomeProjectionViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val mutable = MutableStateFlow<HomeProjectionState>(HomeProjectionState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        // Mantém o último resultado pronto durante a releitura local; só falha de leitura o troca.
        if (mutable.value !is HomeProjectionState.Ready) mutable.value = HomeProjectionState.Loading
        job = viewModelScope.launch {
            try {
                val today = provider.clock.today()
                val space = provider.space()
                val forecast = (space.forecasts(YearMonth.from(today)) as? QueryResult.Data)?.value
                val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
                mutable.value = if (forecast == null || categories == null) HomeProjectionState.Failed
                else HomeProjectionState.Ready(
                    exceedProjections(forecast, today).map {
                        val node = categories[it.categoryId]
                        ProjectionRow(node?.name ?: it.categoryId, it, node?.iconKey, node?.colorKey(categories))
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeProjectionState.Failed
            }
        }
    }
}
