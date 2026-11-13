package app.accord.ui.entries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.BudgetClock
import app.accord.core.space.AccountInfo
import app.accord.core.space.CardInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.EntryPage
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySummary
import app.accord.core.space.QueryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class EntriesStatus { Loading, Ready, Failed }

/**
 * Lista de Lançamentos. Enquanto carrega ou falha, as linhas anteriores continuam visíveis e
 * identificadas; um erro nunca apaga o que já estava na tela.
 */
data class EntriesUiState(
    val filter: EntriesFilterState,
    val rows: List<EntryRow> = emptyList(),
    val total: Int = 0,
    val summary: EntrySummary? = null,
    /** Mês do calendário hoje; o atalho "Mês atual" só aparece quando o período da lista é outro. */
    val currentMonth: YearMonth = YearMonth.now(BudgetClock.ZONE),
    val nextCursor: Int? = null,
    val status: EntriesStatus = EntriesStatus.Loading,
    /** A última tentativa de buscar mais linhas falhou; as já carregadas permanecem. */
    val loadMoreFailed: Boolean = false,
    val categories: Map<String, CategoryNode> = emptyMap(),
    val incomeCategories: Map<String, IncomeCategoryNode> = emptyMap(),
    /** Cartões e contas (a Carteira por último) para os filtros; vazios até a primeira leitura. */
    val cards: List<CardInfo> = emptyList(),
    val accounts: List<AccountInfo> = emptyList(),
    /** O resultado mostrado vem de uma consulta anterior (carregando ou falha). */
    val stale: Boolean = false,
)

class EntriesViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
    initial: EntriesFilterState,
) : ViewModel() {
    private val _state = MutableStateFlow(
        EntriesUiState(filter = EntriesFilterState.restore(saved, initial), currentMonth = provider.clock.currentMonth()),
    )
    val state: StateFlow<EntriesUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
        refreshCurrentMonth()
        viewModelScope.launch { provider.feedback.undone.collect { reload() } }
    }

    fun setSearch(text: String) {
        apply(_state.value.filter.copy(search = text), debounce = true)
    }

    fun applyFilter(filter: EntriesFilterState) = apply(filter.copy(search = _state.value.filter.search), debounce = false)

    fun clearFilters() = apply(_state.value.filter.cleared(), debounce = false)

    /** Quantos lançamentos [filter] devolveria (para "Ver N lançamentos"); nulo se a consulta falhar. */
    suspend fun count(filter: EntriesFilterState): Int? =
        (provider.space().entries(filter.toQuery(), 0, 1) as? QueryResult.Data)?.value?.total

    fun retry() {
        refresh()
        refreshCurrentMonth()
    }

    /** Volta de outra tela: relê o que está filtrado, mantendo as linhas até o resultado novo chegar. */
    fun reload() {
        refresh()
        refreshCurrentMonth()
    }

    /** Carrega a página seguinte sem repetir nem reordenar linhas. */
    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.status == EntriesStatus.Loading) return
        job?.cancel()
        _state.value = current.copy(status = EntriesStatus.Loading, loadMoreFailed = false)
        job = viewModelScope.launch {
            when (val result = provider.space().entries(current.filter.toQuery(), cursor, PAGE_SIZE)) {
                is QueryResult.Data -> _state.value = merge(current, result.value, append = true)
                is QueryResult.Failure -> _state.value = current.copy(status = EntriesStatus.Ready, loadMoreFailed = true)
            }
        }
    }

    private fun apply(filter: EntriesFilterState, debounce: Boolean) {
        filter.saveTo(saved)
        _state.value = _state.value.copy(filter = filter, status = EntriesStatus.Loading, stale = true)
        refresh(debounce)
    }

    /** O atalho "Mês atual" compara com o mês do calendário hoje. O saldo total mora no Início (ADR 0071). */
    private fun refreshCurrentMonth() {
        _state.value = _state.value.copy(currentMonth = provider.clock.currentMonth())
    }

    private fun refresh(debounce: Boolean = false) {
        job?.cancel()
        val filter = _state.value.filter
        _state.value = _state.value.copy(status = EntriesStatus.Loading, stale = _state.value.rows.isNotEmpty())
        job = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val space = provider.space()
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            val incomeCategories = (space.incomeCategories() as? QueryResult.Data)?.value?.associateBy { it.id }
            val cards = (space.cards() as? QueryResult.Data)?.value
            val accounts = (space.accounts() as? QueryResult.Data)?.value?.let { it.accounts + listOfNotNull(it.wallet) }
            when (val result = space.entries(filter.toQuery(), 0, PAGE_SIZE)) {
                is QueryResult.Data -> _state.value = merge(
                    _state.value.copy(
                        filter = filter, categories = categories ?: _state.value.categories,
                        incomeCategories = incomeCategories ?: _state.value.incomeCategories,
                        cards = cards ?: _state.value.cards, accounts = accounts ?: _state.value.accounts,
                    ),
                    result.value, append = false,
                )
                is QueryResult.Failure -> _state.value = _state.value.copy(
                    status = EntriesStatus.Failed, stale = _state.value.rows.isNotEmpty(),
                    categories = categories ?: _state.value.categories,
                    incomeCategories = incomeCategories ?: _state.value.incomeCategories,
                    cards = cards ?: _state.value.cards, accounts = accounts ?: _state.value.accounts,
                )
            }
        }
    }

    private fun merge(base: EntriesUiState, page: EntryPage, append: Boolean) = base.copy(
        rows = if (append) (base.rows + page.rows).distinctBy { it.id } else page.rows,
        total = page.total,
        summary = page.summary,
        nextCursor = page.nextCursor,
        status = EntriesStatus.Ready,
        loadMoreFailed = false,
        stale = false,
    )

    private companion object {
        const val PAGE_SIZE = 30
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
