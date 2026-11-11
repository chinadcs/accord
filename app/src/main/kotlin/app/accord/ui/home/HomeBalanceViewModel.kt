package app.accord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.DateCriterion
import app.accord.core.space.QueryResult
import app.accord.core.space.IncomeSummary
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
    data class Ready(val value: TotalBalance, val incomes: HomeIncomeState = HomeIncomeState.NotRequested) : HomeBalanceState
}

sealed interface HomeIncomeState {
    data object NotRequested : HomeIncomeState
    data object Loading : HomeIncomeState
    data object Failed : HomeIncomeState
    data class Ready(val value: IncomeSummary) : HomeIncomeState
}

/**
 * Saldo total acumulado ate o fim do mes escolhido (leitura local; comeca no mes atual), com o criterio de data escolhido em Mais;
 * carga e falha nunca viram zero. Mudar o mes e so olhar: nada e gravado.
 */
class HomeBalanceViewModel(private val provider: SpaceProvider, private val criterion: () -> DateCriterion) : ViewModel() {
    private val mutable = MutableStateFlow<HomeBalanceState>(HomeBalanceState.Loading)
    val state = mutable.asStateFlow()
    private val chosen = MutableStateFlow<YearMonth?>(null)
    /** Mes exibido; segue o mes atual enquanto ninguem escolher outro. */
    val month = chosen.asStateFlow()
    private var job: Job? = null

    fun shiftMonth(by: Long) {
        chosen.value = (chosen.value ?: provider.clock.currentMonth()).plusMonths(by)
        refresh()
    }

    fun refresh() {
        job?.cancel()
        // Mantem o ultimo resultado pronto durante a releitura local; so falha de leitura o troca.
        val previous = mutable.value
        if (previous is HomeBalanceState.Ready) {
            mutable.value = previous.copy(incomes = HomeIncomeState.Loading)
        } else mutable.value = HomeBalanceState.Loading
        job = viewModelScope.launch {
            try {
                val month = chosen.value ?: provider.clock.currentMonth()
                val space = provider.space()
                val result = space.totalBalance(criterion(), month)
                val balance = (result as? QueryResult.Data)?.value
                if (balance == null) {
                    mutable.value = HomeBalanceState.Failed
                } else {
                    val incomes = try {
                        (space.incomes(month) as? QueryResult.Data)?.value?.let { HomeIncomeState.Ready(it) } ?: HomeIncomeState.Failed
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        HomeIncomeState.Failed
                    }
                    mutable.value = HomeBalanceState.Ready(balance, incomes)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeBalanceState.Failed
            }
        }
    }
}
