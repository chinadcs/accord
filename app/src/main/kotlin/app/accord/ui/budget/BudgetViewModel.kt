package app.accord.ui.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.CategoryNode
import app.accord.core.space.MonthBudget
import app.accord.core.space.QueryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Um pedido "orçamento do mês" alimenta todos os blocos da tela, que trocam juntos. */
data class BudgetData(val budget: MonthBudget, val categories: Map<String, CategoryNode>) {
    val month: YearMonth get() = budget.month

    /** Nada registrado no mês: zero conhecido é diferente de informação indisponível. */
    val isEmpty: Boolean
        get() = budget.committed.cents == 0L && budget.toClassify.count == 0 && budget.credits.cents == 0L &&
            budget.income.received.cents == 0L && budget.income.toReceive.cents == 0L &&
            budget.categories.all { it.gross.cents == 0L && it.credits.cents == 0L }

    /** Há alguma categoria com limite definido; sem isso o total dos limites não se aplica. */
    val hasLimits: Boolean get() = budget.categories.any { it.limit.total != null }
}

/** Lê o orçamento do mês e os nomes das categorias; nulo se qualquer leitura falhar (nunca vira zero). */
suspend fun loadBudgetData(provider: SpaceProvider, month: YearMonth): BudgetData? {
    val space = provider.space()
    val budget = (space.budget(month) as? QueryResult.Data)?.value ?: return null
    val categories = (space.categories() as? QueryResult.Data)?.value ?: return null
    return BudgetData(budget, categories.associateBy { it.id })
}

sealed interface BudgetContent {
    data object Loading : BudgetContent

    data class Ready(val data: BudgetData) : BudgetContent

    /** Falha de leitura: mantém a última visão, identificada pelo próprio mês, e oferece tentar de novo. */
    data class Failed(val last: BudgetData?) : BudgetContent
}

data class BudgetUiState(val month: YearMonth, val content: BudgetContent)

/**
 * Estado do Orçamento. O mês escolhido sobrevive à recriação do processo (SavedStateHandle) e a
 * troca de mês atualiza todos os blocos juntos. Leitura da fachada fora da thread de UI.
 */
class BudgetViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val initialMonth = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: provider.clock.currentMonth()
    private val _state = MutableStateFlow(BudgetUiState(initialMonth, BudgetContent.Loading))
    val state: StateFlow<BudgetUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastGood: BudgetData? = null

    init {
        load(initialMonth)
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun previousMonth() = select(_state.value.month.minusMonths(1))

    fun nextMonth() = select(_state.value.month.plusMonths(1))

    fun retry() = load(_state.value.month)

    /** Volta de outra tela: relê o mês mantendo a visão atual até a nova chegar. */
    fun refresh() = load(_state.value.month, keepContent = true)

    private fun select(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
        load(month)
    }

    private fun load(month: YearMonth, keepContent: Boolean = false) {
        job?.cancel()
        if (!keepContent) _state.value = BudgetUiState(month, BudgetContent.Loading)
        job = viewModelScope.launch {
            val data = loadBudgetData(provider, month)
            _state.value = BudgetUiState(
                month,
                if (data != null) BudgetContent.Ready(data).also { lastGood = data } else BudgetContent.Failed(lastGood),
            )
        }
    }

    private companion object {
        const val KEY_MONTH = "month"
    }
}
