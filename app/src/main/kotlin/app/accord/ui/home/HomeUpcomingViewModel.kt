package app.accord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.UpcomingEvent
import app.accord.core.upcomingMonths
import app.accord.core.upcomingWeek
import app.accord.core.space.CommitmentKind
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUpcomingState {
    data object Loading : HomeUpcomingState
    data object Failed : HomeUpcomingState
    data class Ready(val events: List<UpcomingEvent>) : HomeUpcomingState
}

/** "Próximos 7 dias" do Início: lê compromissos e cartões locais; falha ou carga nunca viram "nada previsto". */
class HomeUpcomingViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val mutable = MutableStateFlow<HomeUpcomingState>(HomeUpcomingState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        mutable.value = HomeUpcomingState.Loading
        job = viewModelScope.launch {
            try {
                val today = provider.clock.today()
                val space = provider.space()
                val cards = (space.cards() as? QueryResult.Data)?.value
                val months = upcomingMonths(today).map { (space.commitments(it, CommitmentKind.All) as? QueryResult.Data)?.value }
                mutable.value = if (cards == null || months.any { it == null }) HomeUpcomingState.Failed
                else HomeUpcomingState.Ready(upcomingWeek(months.flatMap { it!!.items }, cards, today))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeUpcomingState.Failed
            }
        }
    }
}
