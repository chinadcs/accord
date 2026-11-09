package app.accord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.DateCriterion
import app.accord.core.space.QueryResult
import app.accord.core.space.TotalBalance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeBalanceState {
    data object Loading : HomeBalanceState
    data object Failed : HomeBalanceState
    data class Ready(val value: TotalBalance) : HomeBalanceState
}

/** Saldo total acumulado até o fim do mês atual (leitura local), com o critério de data escolhido em Mais; carga e falha nunca viram zero. */
class HomeBalanceViewModel(private val provider: SpaceProvider, private val criterion: () -> DateCriterion) : ViewModel() {
    private val mutable = MutableStateFlow<HomeBalanceState>(HomeBalanceState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        mutable.value = HomeBalanceState.Loading
        job = viewModelScope.launch {
            try {
                val result = provider.space().totalBalance(criterion(), provider.clock.currentMonth())
                mutable.value = (result as? QueryResult.Data)?.value?.let { HomeBalanceState.Ready(it) } ?: HomeBalanceState.Failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeBalanceState.Failed
            }
        }
    }
}
