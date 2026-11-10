package app.accord.ui.categories

import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.ownColorKey

/**
 * Linha achatada da lista de Categorias (ADR 0084): a tela é uma `LazyColumn` e cada item é uma destas linhas, já na ordem de
 * exibição. As regras de quem tem ⋮, contagem e subcategorias visíveis ficam aqui, fora do Compose, para serem testadas na JVM.
 */
sealed interface CategoryRow {
    /** Chave estável do item na lista: o expandir e recolher não recria as demais linhas. */
    val key: String

    /** Categoria principal; o toque expande ou recolhe. Exatamente um dos nós ([expense], [income]) é preenchido. */
    data class Main(
        val name: String,
        val iconKey: String,
        val colorKey: String,
        val subCount: Int,
        val expanded: Boolean,
        val expense: CategoryNode? = null,
        val income: IncomeCategoryNode? = null,
    ) : CategoryRow {
        val id get() = expense?.id ?: income!!.id
        override val key get() = "main:$id"
        val isIncome get() = income != null
    }

    /** Subcategoria, recuada, na cor da principal; o toque abre a edição. */
    data class Sub(
        val name: String,
        val iconKey: String,
        val colorKey: String,
        val parentName: String,
        val expense: CategoryNode? = null,
        val income: IncomeCategoryNode? = null,
    ) : CategoryRow {
        val id get() = expense?.id ?: income!!.id
        override val key get() = "sub:$id"
        val isIncome get() = income != null

        /** "Passar para outra categoria…" existe só na despesa; a subcategoria de renda abre a edição pelo toque, sem ⋮. */
        val hasMenu get() = expense != null
    }

    /** Última linha de um grupo expandido. */
    data class NewSub(val parentId: String, val parentName: String) : CategoryRow {
        override val key get() = "new:$parentId"
    }
}

/** Linhas da aba [tab]: cada principal e, se estiver em [expanded], suas subcategorias e "Nova subcategoria". Vazio fora de `Ready`. */
fun buildRows(content: CategoriesContent, tab: CategoryTab, expanded: Set<String>): List<CategoryRow> {
    val ready = content as? CategoriesContent.Ready ?: return emptyList()
    return when (tab) {
        CategoryTab.Expense -> ready.mains.flatMap { main ->
            val isOpen = main.node.id in expanded
            val colorKey = main.node.ownColorKey()
            buildList {
                add(CategoryRow.Main(main.node.name, main.node.iconKey, colorKey, main.children.size, isOpen, expense = main.node))
                if (isOpen) {
                    main.children.forEach { add(CategoryRow.Sub(it.name, it.iconKey, colorKey, main.node.name, expense = it)) }
                    add(CategoryRow.NewSub(main.node.id, main.node.name))
                }
            }
        }
        CategoryTab.Income -> ready.incomes.filter { it.parentId == null }.sortedBy { it.order }.flatMap { main ->
            val children = ready.incomes.filter { it.parentId == main.id }.sortedBy { it.order }
            val isOpen = main.id in expanded
            val colorKey = main.ownColorKey()
            buildList {
                add(CategoryRow.Main(main.name, main.iconKey, colorKey, children.size, isOpen, income = main))
                if (isOpen) {
                    children.forEach { add(CategoryRow.Sub(it.name, it.iconKey, colorKey, main.name, income = it)) }
                    add(CategoryRow.NewSub(main.id, main.name))
                }
            }
        }
    }
}
