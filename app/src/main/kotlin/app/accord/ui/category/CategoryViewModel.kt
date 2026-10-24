package app.accord.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.CategoryBudget
import app.accord.core.space.CategoryNode
import app.accord.ui.budget.BudgetData
import app.accord.ui.budget.loadBudgetData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Categoria e mês recebidos da origem e preservados ao voltar. */
data class CategoryData(val data: BudgetData, val category: CategoryBudget, val node: CategoryNode) {
    val month: YearMonth get() = data.month

    /** Subcategorias com gasto ou parte do limite no mês, na ordem do cadastro; nulo é "Sem subcategoria". */
    val subcategories: List<String?>
        get() {
            val ids = (category.bySubcategory.keys.filterNotNull() + category.limit.subs.keys).distinct()
            val ordered = ids.sortedWith(compareBy({ data.categories[it]?.order ?: Int.MAX_VALUE }, { data.categories[it]?.name.orEmpty() }))
            return ordered + if (category.bySubcategory.containsKey(null)) listOf<String?>(null) else emptyList()
        }
}

sealed interface CategoryContent {
    data object Loading : CategoryContent
    data class Ready(val value: CategoryData) : CategoryContent
    data object Failed : CategoryContent
}

class CategoryViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
) : ViewModel() {
    val categoryId: String = checkNotNull(saved[ARG_CATEGORY])
    val month: YearMonth = YearMonth.parse(checkNotNull(saved[ARG_MONTH]))

    private val _state = MutableStateFlow<CategoryContent>(CategoryContent.Loading)
    val state: StateFlow<CategoryContent> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    /** Volta de outra tela: relê mantendo a visão atual até a nova chegar. */
    fun refresh() = load(keepContent = true)

    fun load(keepContent: Boolean = false) {
        if (!keepContent) _state.value = CategoryContent.Loading
        viewModelScope.launch {
            val data = loadBudgetData(provider, month)
            val category = data?.budget?.categories?.firstOrNull { it.categoryId == categoryId }
            val node = data?.categories?.get(categoryId)
            _state.value = if (data != null && category != null && node != null) {
                CategoryContent.Ready(CategoryData(data, category, node))
            } else {
                CategoryContent.Failed
            }
        }
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
        const val ARG_MONTH = "month"
    }
}
