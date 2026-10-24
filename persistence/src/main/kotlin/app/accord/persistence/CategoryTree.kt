package app.accord.persistence

import java.time.YearMonth

/**
 * Árvore de categorias ao longo do tempo. Mover subcategoria vale só daqui para frente: meses
 * anteriores continuam contando os gastos na categoria de origem. Categorias principais nunca
 * mudam de nível (não existe promover nem rebaixar).
 */
internal class CategoryTree(
    private val rows: Map<String, CategoryRow>,
    moves: List<CategoryMoveRow>,
) {
    private val movesBySub: Map<String, List<CategoryMoveRow>> =
        moves.groupBy { it.subcategoryId }.mapValues { (_, v) -> v.sortedBy { it.fromMonth } }

    fun row(id: String): CategoryRow? = rows[id]

    /** Categoria principal que contém [id] em [month]; nulo se [id] é principal. */
    fun parentAt(id: String, month: YearMonth): String? {
        val row = rows[id] ?: return null
        val move = movesBySub[id]?.lastOrNull { YearMonth.parse(it.fromMonth) <= month }
        return move?.parentId ?: row.parentId
    }

    /** Categoria principal do orçamento de [month] para uma classificação (a própria, se já é principal). */
    fun mainAt(id: String, month: YearMonth): String = parentAt(id, month) ?: id

    /** A subcategoria, se a classificação é de subcategoria; nulo é "Sem subcategoria". */
    fun subAt(id: String, month: YearMonth): String? = if (parentAt(id, month) != null) id else null

    fun isMain(id: String): Boolean = rows[id]?.parentId == null && rows.containsKey(id)

    /** Irmãos de um nível em [month], na ordem de apresentação. */
    fun childrenAt(parentId: String?, month: YearMonth): List<CategoryRow> =
        rows.values.filter { parentAt(it.id, month) == parentId }.sortedWith(compareBy({ it.sortOrder }, { it.name }))

    val mains: List<CategoryRow> get() = rows.values.filter { it.parentId == null }.sortedBy { it.sortOrder }
}
