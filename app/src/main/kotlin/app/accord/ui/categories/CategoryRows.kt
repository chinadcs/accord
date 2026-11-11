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
        /** Posição (1-based) entre as irmãs e total delas: alimentam "Subir"/"Descer" e o anúncio da posição. */
        val pos: Int = 1,
        val total: Int = 1,
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
        val pos: Int = 1,
        val total: Int = 1,
    ) : CategoryRow {
        val id get() = expense?.id ?: income!!.id
        override val key get() = "sub:$id"
        val isIncome get() = income != null

        /** "Passar para outra categoria…" existe só na despesa; a subcategoria de renda abre a edição pelo toque, sem ⋮. */
        val hasMenu get() = expense != null
    }

    /** Última linha de um grupo expandido. */
    data class NewSub(val parentId: String, val parentName: String, val noSubs: Boolean = false) : CategoryRow {
        override val key get() = "new:$parentId"
    }
}

/** Linhas da aba [tab]: cada principal e, se estiver em [expanded], suas subcategorias e "Nova subcategoria". Vazio fora de `Ready`. */
fun buildRows(content: CategoriesContent, tab: CategoryTab, expanded: Set<String>, order: DragOrder? = null): List<CategoryRow> {
    val ready = content as? CategoriesContent.Ready ?: return emptyList()
    return when (tab) {
        CategoryTab.Expense -> ready.mains.let { it.ordered(null, order) { m -> m.node.id } }.let { mains ->
            mains.mapIndexed { i, m -> Triple(m, i + 1, mains.size) }
        }.flatMap { (main, mainPos, mainTotal) ->
            val isOpen = main.node.id in expanded
            val colorKey = main.node.ownColorKey()
            buildList {
                add(CategoryRow.Main(main.node.name, main.node.iconKey, colorKey, main.children.size, isOpen, expense = main.node, pos = mainPos, total = mainTotal))
                if (isOpen) {
                    val subs = main.children.ordered(main.node.id, order) { it.id }
                    subs.forEachIndexed { i, sub -> add(CategoryRow.Sub(sub.name, sub.iconKey, colorKey, main.node.name, expense = sub, pos = i + 1, total = subs.size)) }
                    add(CategoryRow.NewSub(main.node.id, main.node.name, main.children.isEmpty()))
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
                    add(CategoryRow.NewSub(main.id, main.name, children.isEmpty()))
                }
            }
        }
    }
}

/** Ordem provisória de um nível enquanto a pessoa arrasta (ou até o serviço responder): [ids] são as irmãs de [parentId]. */
data class DragOrder(val parentId: String?, val ids: List<String>)

private fun <T> List<T>.ordered(parentId: String?, order: DragOrder?, id: (T) -> String): List<T> =
    if (order == null || order.parentId != parentId) this else sortedBy { order.ids.indexOf(id(it)) }

/** [ids] com [id] levado para [to] (limitado ao intervalo); devolve a mesma lista se nada mudar. */
fun List<String>.moved(id: String, to: Int): List<String> {
    val from = indexOf(id)
    val target = to.coerceIn(0, lastIndex)
    return if (from < 0 || from == target) this else toMutableList().also { it.add(target, it.removeAt(from)) }
}
