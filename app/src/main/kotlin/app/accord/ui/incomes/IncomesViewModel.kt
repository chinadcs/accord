package app.accord.ui.incomes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.IncomeSummary
import app.accord.core.space.IncomeView
import app.accord.core.space.QueryResult
import app.accord.ui.detail.LinkedActionsController
import app.accord.ui.detail.LinkedForm
import app.accord.ui.detail.LinkedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

sealed interface IncomesContent {
    data object Loading : IncomesContent
    data object Failed : IncomesContent
    data class Ready(val summary: IncomeSummary) : IncomesContent
}

/** [incomeCategories] resolve o identificador da categoria de renda para o nome, o ícone e a cor exibidos na lista. */
data class IncomesUiState(
    val month: YearMonth,
    val content: IncomesContent = IncomesContent.Loading,
    val incomeCategories: Map<String, IncomeCategoryNode> = emptyMap(),
)

/**
 * Rendas do mês (tela 28): recebido e a receber são partes do esperado, nunca duas rendas. Renda
 * prevista não é saldo. Registrar o recebimento vincula o fato à mesma renda, sem somar duas vezes.
 */
class IncomesViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        IncomesUiState(
            month = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: saved.get<String>(ARG_MONTH)?.let(YearMonth::parse)
                ?: provider.clock.currentMonth(),
        ),
    )
    val state: StateFlow<IncomesUiState> = _state.asStateFlow()

    private val controller = LinkedActionsController(provider, viewModelScope) { refresh() }
    val linked: StateFlow<LinkedState?> = controller.state

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun previousMonth() = moveTo(_state.value.month.minusMonths(1))

    fun nextMonth() = moveTo(_state.value.month.plusMonths(1))

    private fun moveTo(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month) }
        load()
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val month = _state.value.month
        if (!keepContent) _state.update { it.copy(content = IncomesContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val summary = (space.incomes(month) as? QueryResult.Data)?.value
            val categories = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty().associateBy { it.id }
            if (_state.value.month != month) return@launch
            _state.update {
                it.copy(content = summary?.let(IncomesContent::Ready) ?: IncomesContent.Failed, incomeCategories = categories)
            }
        }
    }

    fun receive(income: IncomeView) = controller.open(incomeReceiveAction(income))

    fun updateLinked(change: (LinkedForm) -> LinkedForm) = controller.update(change)

    fun submitLinked() = controller.submit()

    fun closeLinked() = controller.close()

    companion object {
        const val ARG_MONTH = "month"
        private const val KEY_MONTH = "incomesMonth"
    }
}
