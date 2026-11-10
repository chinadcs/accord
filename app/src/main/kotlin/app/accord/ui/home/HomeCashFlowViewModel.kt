package app.accord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.MonthCashFlow
import app.accord.core.monthCashFlow
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

sealed interface HomeCashFlowState {
    data object Loading : HomeCashFlowState
    data object Failed : HomeCashFlowState
    data class Ready(val value: MonthCashFlow) : HomeCashFlowState
}

/** Fluxo do mês do Início: só lê orçamento e previsão locais; falha ou carga nunca viram zero. */
class HomeCashFlowViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val mutable = MutableStateFlow<HomeCashFlowState>(HomeCashFlowState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        // Mantém o último resultado pronto durante a releitura local; só falha de leitura o troca.
        if (mutable.value !is HomeCashFlowState.Ready) mutable.value = HomeCashFlowState.Loading
        job = viewModelScope.launch {
            try {
                val month = YearMonth.from(provider.clock.today())
                val space = provider.space()
                val budget = (space.budget(month) as? QueryResult.Data)?.value
                val forecast = (space.forecasts(month) as? QueryResult.Data)?.value
                mutable.value = if (budget == null || forecast == null) HomeCashFlowState.Failed
                else HomeCashFlowState.Ready(monthCashFlow(budget, forecast))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeCashFlowState.Failed
            }
        }
    }
}
