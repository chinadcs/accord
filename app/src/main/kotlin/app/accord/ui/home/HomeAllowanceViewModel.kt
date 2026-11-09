package app.accord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.SpendingAllowance
import app.accord.core.spendingAllowance
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

sealed interface HomeAllowanceState {
    data object Loading : HomeAllowanceState
    data object Failed : HomeAllowanceState
    data class Ready(val value: SpendingAllowance) : HomeAllowanceState
}

class HomeAllowanceViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val mutable = MutableStateFlow<HomeAllowanceState>(HomeAllowanceState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        mutable.value = HomeAllowanceState.Loading
        job = viewModelScope.launch {
            try {
                val today = provider.clock.today()
                val budget = (provider.space().budget(YearMonth.from(today)) as? QueryResult.Data)?.value
                mutable.value = budget?.let { HomeAllowanceState.Ready(spendingAllowance(it, today)) } ?: HomeAllowanceState.Failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeAllowanceState.Failed
            }
        }
    }
}
