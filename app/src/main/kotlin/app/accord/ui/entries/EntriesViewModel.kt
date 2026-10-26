package app.accord.ui.entries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.CategoryNode
import app.accord.core.space.EntryPage
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
import app.accord.core.space.QueryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    /** Cabeçalho: saldo total acumulado e resumo do mês atual, sem filtros; independe da busca e do período da lista. */
    val balance: TotalBalance? = null,
    val nextCursor: Int? = null,
    val status: EntriesStatus = EntriesStatus.Loading,
    /** A última tentativa de buscar mais linhas falhou; as já carregadas permanecem. */
    val loadMoreFailed: Boolean = false,
    val categories: Map<String, CategoryNode> = emptyMap(),
    /** O resultado mostrado vem de uma consulta anterior (carregando ou falha). */
    val stale: Boolean = false,
)

class EntriesViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
    initial: EntriesFilterState,
) : ViewModel() {
    private val _state = MutableStateFlow(EntriesUiState(filter = EntriesFilterState.restore(saved, initial)))
    val state: StateFlow<EntriesUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var balanceJob: Job? = null

    init {
        refresh()
        refreshBalance()
        viewModelScope.launch { provider.feedback.undone.collect { reload() } }
    }

    fun setSearch(text: String) {
        apply(_state.value.filter.copy(search = text), debounce = true)
    }

    fun applyFilter(filter: EntriesFilterState) = apply(filter.copy(search = _state.value.filter.search), debounce = false)

    fun clearFilters() = apply(_state.value.filter.cleared(), debounce = false)

    fun retry() {
        refresh()
        refreshBalance()
    }

    /** Volta de outra tela: relê o que está filtrado, mantendo as linhas até o resultado novo chegar. */
    fun reload() {
        refresh()
        refreshBalance()
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
        val criterionChanged = filter.criterion != _state.value.filter.criterion
        filter.saveTo(saved)
        _state.value = _state.value.copy(filter = filter, status = EntriesStatus.Loading, stale = true)
        refresh(debounce)
        if (criterionChanged) refreshBalance()
    }

    /** O saldo total só muda com os dados ou com o critério de data; falha mantém o valor anterior. */
    private fun refreshBalance() {
        balanceJob?.cancel()
        val criterion = _state.value.filter.criterion
        balanceJob = viewModelScope.launch {
            when (val result = provider.space().totalBalance(criterion)) {
                is QueryResult.Data -> _state.value = _state.value.copy(balance = result.value)
                is QueryResult.Failure -> Unit
            }
        }
    }

    private fun refresh(debounce: Boolean = false) {
        job?.cancel()
        val filter = _state.value.filter
        _state.value = _state.value.copy(status = EntriesStatus.Loading, stale = _state.value.rows.isNotEmpty())
        job = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val space = provider.space()
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            when (val result = space.entries(filter.toQuery(), 0, PAGE_SIZE)) {
                is QueryResult.Data -> _state.value = merge(
                    _state.value.copy(filter = filter, categories = categories ?: _state.value.categories),
                    result.value, append = false,
                )
                is QueryResult.Failure -> _state.value = _state.value.copy(
                    status = EntriesStatus.Failed, stale = _state.value.rows.isNotEmpty(),
                    categories = categories ?: _state.value.categories,
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
