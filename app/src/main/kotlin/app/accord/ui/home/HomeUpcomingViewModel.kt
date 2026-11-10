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
import java.time.LocalDate

sealed interface HomeUpcomingState {
    data object Loading : HomeUpcomingState
    data object Failed : HomeUpcomingState
    data class Ready(val events: List<UpcomingEvent>) : HomeUpcomingState
}

/**
 * Lê cartões e os compromissos dos meses que a janela de 7 dias toca e devolve os eventos a partir de [today];
 * nulo se qualquer leitura falhar (falha nunca vira "nada previsto"). Compartilhada pela Início e por Compromissos; só lê.
 */
suspend fun loadUpcomingEvents(provider: SpaceProvider, today: LocalDate): List<UpcomingEvent>? {
    val space = provider.space()
    val cards = (space.cards() as? QueryResult.Data)?.value ?: return null
    val months = upcomingMonths(today).map { (space.commitments(it, CommitmentKind.All) as? QueryResult.Data)?.value ?: return null }
    return upcomingWeek(months.flatMap { it.items }, cards, today)
}

/** "Próximos 7 dias" do Início: lê compromissos e cartões locais; falha ou carga nunca viram "nada previsto". */
class HomeUpcomingViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val mutable = MutableStateFlow<HomeUpcomingState>(HomeUpcomingState.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        // Mantém o último resultado pronto durante a releitura local; só falha de leitura o troca.
        if (mutable.value !is HomeUpcomingState.Ready) mutable.value = HomeUpcomingState.Loading
        job = viewModelScope.launch {
            try {
                mutable.value = loadUpcomingEvents(provider, provider.clock.today())
                    ?.let(HomeUpcomingState::Ready) ?: HomeUpcomingState.Failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HomeUpcomingState.Failed
            }
        }
    }
}
