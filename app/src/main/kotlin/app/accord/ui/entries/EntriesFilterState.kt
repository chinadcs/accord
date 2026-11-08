package app.accord.ui.entries

import androidx.lifecycle.SavedStateHandle
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntryFilter
import app.accord.core.space.EntrySituation
import app.accord.core.space.PaymentMethod
import java.time.YearMonth

/**
 * Filtros da lista de Lançamentos. Período, critério, categoria, forma de pagamento, situação e
 * busca sobrevivem à recriação do processo; cancelar o painel nunca muda o que está aplicado.
 */
data class EntriesFilterState(
    val from: YearMonth,
    val to: YearMonth,
    val criterion: DateCriterion = DateCriterion.PurchaseDate,
    val categoryId: String? = null,
    val methods: Set<PaymentMethod> = emptySet(),
    val situations: Set<EntrySituation> = emptySet(),
    val search: String = "",
    val cardId: String? = null,
) {
    /** Quantos filtros do painel estão ativos (a busca e o critério aparecem à parte). */
    val activeCount: Int
        get() = listOf(categoryId != null, methods.isNotEmpty(), situations.isNotEmpty(), cardId != null).count { it }

    /** Há algo além do período padrão restringindo a lista. */
    val isFiltered: Boolean get() = activeCount > 0 || search.isNotBlank()

    fun toQuery() = EntryFilter(
        from = from, to = to, criterion = criterion, categoryId = categoryId,
        cardId = cardId, methods = methods, situations = situations, search = search.trim().ifEmpty { null },
    )

    /** "Limpar filtros" mantém o período e o critério; só tira o que restringe. */
    fun cleared() = copy(cardId = null, categoryId = null, methods = emptySet(), situations = emptySet(), search = "")

    fun saveTo(handle: SavedStateHandle) {
        handle[K_FROM] = from.toString()
        handle[K_TO] = to.toString()
        handle[K_CRITERION] = criterion.name
        handle["filteredCardId"] = cardId
        handle[K_CATEGORY] = categoryId
        handle[K_METHODS] = ArrayList(methods.map { it.name })
        handle[K_SITUATIONS] = ArrayList(situations.map { it.name })
        handle[K_SEARCH] = search
    }

    companion object {
        const val K_FROM = "from"
        const val K_TO = "to"
        const val K_CRITERION = "criterion"
        const val K_CATEGORY = "categoryId"
        const val K_METHODS = "methods"
        const val K_SITUATIONS = "situations"
        const val K_SEARCH = "search"

        /** Restaura do que foi salvo; o que faltar usa [default]. */
        fun restore(handle: SavedStateHandle, default: EntriesFilterState): EntriesFilterState {
            val from = handle.get<String>(K_FROM)?.let(YearMonth::parse) ?: default.from
            val to = handle.get<String>(K_TO)?.let(YearMonth::parse) ?: default.to
            return EntriesFilterState(
                cardId = if (handle.contains("filteredCardId")) handle.get<String>("filteredCardId") else default.cardId,
                from = from,
                to = to,
                criterion = handle.get<String>(K_CRITERION)?.let(DateCriterion::valueOf) ?: default.criterion,
                categoryId = if (handle.contains(K_CATEGORY)) handle.get<String>(K_CATEGORY) else default.categoryId,
                methods = handle.get<ArrayList<String>>(K_METHODS)?.map(PaymentMethod::valueOf)?.toSet() ?: default.methods,
                situations = handle.get<ArrayList<String>>(K_SITUATIONS)?.map(EntrySituation::valueOf)?.toSet() ?: default.situations,
                search = handle.get<String>(K_SEARCH) ?: default.search,
            )
        }
    }
}
