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
import java.time.YearMonth

sealed interface HomeBalanceState {
    data object Loading : HomeBalanceState
    data object Failed : HomeBalanceState
    data class Ready(val value: TotalBalance) : HomeBalanceState
}

/**
 * Saldo total acumulado até o fim do mês escolhido (leitura local; começa no mês atual), com o critério de data escolhido em Mais;
 * carga e falha nunca viram zero. Mudar o mês é só olhar: nada é gravado.
 */
class HomeBalanceViewModel(private val provider: SpaceProvider, private val criterion: () -> DateCriterion) : ViewModel() {
    private val mutable = MutableStateFlow<HomeBalanceState>(HomeBalanceState.Loading)
    val state = mutable.asStateFlow()
    private val chosen = MutableStateFlow<YearMonth?>(null)
    /** Mês exibido; segue o mês atual enquanto ninguém escolher outro. */
    val month = chosen.asStateFlow()
    private var job: Job? = null

    fun shiftMonth(by: Long) {
        chosen.value = (chosen.value ?: provider.clock.currentMonth()).plusMonths(by)
        refresh()
    }

    fun refresh() {
        job?.cancel()
        // Mantém o último resultado pronto durante a releitura local; só falha de leitura o troca.
        if (mutable.value !is HomeBalanceState.Ready) mutable.value = HomeBalanceState.Loading
        job = viewModelScope.launch {
            try {
                val result = provider.space().totalBalance(criterion(), chosen.value ?: provider.clock.currentMonth())
                mutable.value = (result as? QueryResult.Data)?.value?.let { HomeBalanceState.Ready(it) } ?: HomeBalanceState.Failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeBalanceState.Failed
            }
        }
    }
}
