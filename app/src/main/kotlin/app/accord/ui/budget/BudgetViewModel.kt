package app.accord.ui.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.MonthCashFlow
import app.accord.core.monthCashFlow
import app.accord.core.space.CategoryNode
import app.accord.core.space.MonthBudget
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Um pedido "orçamento do mês" alimenta todos os blocos da tela, que trocam juntos. */
data class BudgetData(val budget: MonthBudget, val categories: Map<String, CategoryNode>) {
    val month: YearMonth get() = budget.month

    /** Nada registrado no mês: zero conhecido é diferente de informação indisponível. */
    val noActivity: Boolean
        get() = budget.committed.cents == 0L && budget.toClassify.count == 0 && budget.credits.cents == 0L &&
            budget.income.received.cents == 0L && budget.income.toReceive.cents == 0L &&
            budget.categories.all { it.gross.cents == 0L && it.credits.cents == 0L }

    /** Há alguma categoria com limite definido (presença, não valor: R$ 0,00 conta); sem isso o total dos limites não se aplica. */
    val hasLimits: Boolean get() = budget.categories.any { it.limit.total != null }

    /** Estado vazio só sem movimento, sem limite vigente e sem Orçamento geral (ADR 0086). */
    val isEmpty: Boolean get() = noActivity && !hasLimits && !budget.general.isDefined
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

/**
 * Fluxo do mês exibido, lido à parte do orçamento: se só ele falhar, o resto da tela continua (e vice-versa).
 * Carga e falha nunca viram zero; a falha não guarda valor antigo para não apresentá-lo como atual.
 */
sealed interface BudgetFlowState {
    data object Loading : BudgetFlowState

    data object Failed : BudgetFlowState

    data class Ready(val value: MonthCashFlow) : BudgetFlowState
}

/** Lê o fluxo do mês escolhido (orçamento e previsão do mesmo mês); nulo se qualquer leitura falhar. */
suspend fun loadMonthCashFlow(provider: SpaceProvider, month: YearMonth): MonthCashFlow? {
    val space = provider.space()
    val budget = (space.budget(month) as? QueryResult.Data)?.value ?: return null
    val forecast = (space.forecasts(month) as? QueryResult.Data)?.value ?: return null
    return monthCashFlow(budget, forecast)
}

data class BudgetUiState(
    val month: YearMonth,
    val content: BudgetContent,
    val flow: BudgetFlowState = BudgetFlowState.Loading,
)

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
    private var flowJob: Job? = null
    private var lastGood: BudgetData? = null

    init {
        load(initialMonth)
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun previousMonth() = select(_state.value.month.minusMonths(1))

    fun nextMonth() = select(_state.value.month.plusMonths(1))

    fun goToCurrentMonth() = select(provider.clock.currentMonth())

    fun isCurrentMonth(month: YearMonth) = month == provider.clock.currentMonth()

    fun retry() = load(_state.value.month)

    /** Tentar de novo só do fluxo do mês: não recarrega nem esconde o resto da tela. */
    fun retryFlow() = loadFlow(_state.value.month, keepReady = false)

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
            _state.update {
                it.copy(
                    month = month,
                    content = if (data != null) BudgetContent.Ready(data).also { lastGood = data } else BudgetContent.Failed(lastGood),
                )
            }
        }
        loadFlow(month, keepReady = keepContent)
    }

    /**
     * O fluxo acompanha o mês escolhido. Só relê sem voltar ao esqueleto ([keepReady]) quando já há um resultado pronto
     * do mesmo mês; troca de mês ou falha anterior mostram a carga. Resultado de outro mês nunca é gravado.
     */
    private fun loadFlow(month: YearMonth, keepReady: Boolean) {
        flowJob?.cancel()
        _state.update {
            if (keepReady && it.month == month && it.flow is BudgetFlowState.Ready) it
            else it.copy(month = month, flow = BudgetFlowState.Loading)
        }
        flowJob = viewModelScope.launch {
            val result = try {
                loadMonthCashFlow(provider, month)?.let(BudgetFlowState::Ready) ?: BudgetFlowState.Failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BudgetFlowState.Failed
            }
            _state.update { if (it.month == month) it.copy(flow = result) else it }
        }
    }

    private companion object {
        const val KEY_MONTH = "month"
    }
}
