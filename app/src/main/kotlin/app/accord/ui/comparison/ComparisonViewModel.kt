package app.accord.ui.comparison

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.CategoryNode
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonPeriod
import app.accord.core.space.DateCriterion
import app.accord.core.space.PeriodComparison
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ComparisonData(val comparison: PeriodComparison, val categories: Map<String, CategoryNode>)

sealed interface ComparisonContent {
    data object Loading : ComparisonContent

    /** [last] é a última leitura, mantida na tela enquanto a nova carrega ou depois de uma falha. */
    data class Ready(val data: ComparisonData) : ComparisonContent
    data class Failed(val last: ComparisonData?) : ComparisonContent
}

data class ComparisonUiState(
    val criterion: DateCriterion,
    val grouping: ComparisonGrouping,
    val monthA: YearMonth,
    val monthB: YearMonth,
    val content: ComparisonContent = ComparisonContent.Loading,
) {
    val periodA get() = monthA.toPeriod()
    val periodB get() = monthB.toPeriod()
}

fun YearMonth.toPeriod() = ComparisonPeriod(atDay(1), plusMonths(1).atDay(1))

/**
 * Comparações históricas (tela 33): dois meses e um critério de data comum, sempre visíveis. Trocar o critério recalcula os dois lados; nada
 * muda na competência financeira. Critério, agrupamento e meses sobrevivem à volta das telas de origem e à recriação do processo.
 */
class ComparisonViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        provider.clock.currentMonth().let { current ->
            ComparisonUiState(
                criterion = saved.get<String>(KEY_CRITERION)?.let { runCatching { DateCriterion.valueOf(it) }.getOrNull() } ?: DateCriterion.BudgetMonth,
                grouping = saved.get<String>(KEY_GROUPING)?.let { runCatching { ComparisonGrouping.valueOf(it) }.getOrNull() } ?: ComparisonGrouping.Category,
                monthA = saved.get<String>(KEY_A)?.let(YearMonth::parse) ?: current.minusMonths(1),
                monthB = saved.get<String>(KEY_B)?.let(YearMonth::parse) ?: current,
            )
        },
    )
    val state: StateFlow<ComparisonUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun setCriterion(criterion: DateCriterion) = change(KEY_CRITERION, criterion.name) { it.copy(criterion = criterion) }

    fun setGrouping(grouping: ComparisonGrouping) = change(KEY_GROUPING, grouping.name) { it.copy(grouping = grouping) }

    fun previousA() = change(KEY_A, _state.value.monthA.minusMonths(1).toString()) { it.copy(monthA = it.monthA.minusMonths(1)) }

    fun nextA() = change(KEY_A, _state.value.monthA.plusMonths(1).toString()) { it.copy(monthA = it.monthA.plusMonths(1)) }

    fun previousB() = change(KEY_B, _state.value.monthB.minusMonths(1).toString()) { it.copy(monthB = it.monthB.minusMonths(1)) }

    fun nextB() = change(KEY_B, _state.value.monthB.plusMonths(1).toString()) { it.copy(monthB = it.monthB.plusMonths(1)) }

    private fun change(key: String, value: String, update: (ComparisonUiState) -> ComparisonUiState) {
        saved[key] = value
        _state.update(update)
        load()
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val request = _state.value
        val last = (request.content as? ComparisonContent.Ready)?.data ?: (request.content as? ComparisonContent.Failed)?.last
        if (!keepContent) _state.update { it.copy(content = last?.let(ComparisonContent::Ready) ?: ComparisonContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val comparison = (space.comparePeriods(request.criterion, request.grouping, request.periodA, request.periodB) as? QueryResult.Data)?.value
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            // Um pedido antigo que chega depois de uma troca de critério, agrupamento ou mês não sobrescreve a tela.
            val now = _state.value
            if (now.criterion != request.criterion || now.grouping != request.grouping || now.monthA != request.monthA || now.monthB != request.monthB) {
                return@launch
            }
            _state.update {
                it.copy(content = if (comparison == null || categories == null) ComparisonContent.Failed(last) else ComparisonContent.Ready(ComparisonData(comparison, categories)))
            }
        }
    }

    companion object {
        private const val KEY_CRITERION = "comparisonCriterion"
        private const val KEY_GROUPING = "comparisonGrouping"
        private const val KEY_A = "comparisonMonthA"
        private const val KEY_B = "comparisonMonthB"
    }
}
