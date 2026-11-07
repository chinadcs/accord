package app.accord.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.CategoryNode
import app.accord.core.space.PurchaseActivity
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ActivityData(val activity: PurchaseActivity, val categories: Map<String, CategoryNode>)

sealed interface ActivityContent {
    data object Loading : ActivityContent

    /** [last] é a última leitura, mantida na tela enquanto a nova carrega ou depois de uma falha. */
    data class Ready(val data: ActivityData) : ActivityContent
    data class Failed(val last: ActivityData?) : ActivityContent
}

data class ActivityUiState(
    val month: YearMonth,
    /** Categoria principal escolhida como filtro; nulo é "todas". */
    val categoryId: String? = null,
    val content: ActivityContent = ActivityContent.Loading,
)

/**
 * Atividade de compras (tela 30): compras pela data original do mês escolhido. Mês e categoria sobrevivem à volta das telas de origem e à
 * recriação do processo. Trocar o período recalcula só a consulta; nada muda na competência financeira.
 */
class PurchaseActivityViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ActivityUiState(
            month = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: provider.clock.currentMonth(),
            categoryId = saved[KEY_CATEGORY],
        ),
    )
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun previousMonth() = moveTo(_state.value.month.minusMonths(1))

    fun nextMonth() = moveTo(_state.value.month.plusMonths(1))

    fun setCategory(categoryId: String?) {
        if (categoryId == _state.value.categoryId) return
        saved[KEY_CATEGORY] = categoryId
        _state.update { it.copy(categoryId = categoryId) }
        load()
    }

    private fun moveTo(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month) }
        load()
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val request = _state.value
        val last = (request.content as? ActivityContent.Ready)?.data ?: (request.content as? ActivityContent.Failed)?.last
        if (!keepContent) _state.update { it.copy(content = last?.let(ActivityContent::Ready) ?: ActivityContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val activity = (space.purchaseActivity(request.month.atDay(1), request.month.plusMonths(1).atDay(1), request.categoryId) as? QueryResult.Data)?.value
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            // Um pedido antigo que chega depois da troca de mês ou de categoria não sobrescreve a tela.
            if (_state.value.month != request.month || _state.value.categoryId != request.categoryId) return@launch
            _state.update {
                it.copy(content = if (activity == null || categories == null) ActivityContent.Failed(last) else ActivityContent.Ready(ActivityData(activity, categories)))
            }
        }
    }

    companion object {
        private const val KEY_MONTH = "activityMonth"
        private const val KEY_CATEGORY = "activityCategory"
    }
}
