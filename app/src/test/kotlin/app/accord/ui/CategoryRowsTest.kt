package app.accord.ui

import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.categories.CategoriesContent
import app.accord.ui.categories.CategoryRow
import app.accord.ui.categories.DragOrder
import app.accord.ui.categories.moved
import app.accord.ui.categories.CategoryTab
import app.accord.ui.categories.MainCategory
import app.accord.ui.categories.buildRows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class CategoryRowsTest {
    private val food = CategoryNode("food", null, "Alimentação", 0, "utensils", "alimentacao")
    private val delivery = CategoryNode("delivery", "food", "Delivery", 0, "bike", null)
    private val market = CategoryNode("market", "food", "Mercado", 1, "shopping-cart", null)
    private val shop = CategoryNode("shop", null, "Compras", 1, "shopping-bag", "compras")
    private val salary = IncomeCategoryNode("sal", "Salário", 0, "briefcase", "salario")
    private val bonus = IncomeCategoryNode("bonus", "Bônus", 0, "gift", null, parentId = "sal")
    private val extra = IncomeCategoryNode("extra", "Renda Extra", 1, "plus", "renda-extra")

    private val ready = CategoriesContent.Ready(
        listOf(MainCategory(food, listOf(delivery, market)), MainCategory(shop, emptyList())),
        listOf(extra, bonus, salary),
    )

    @Test
    fun `grupos recolhidos mostram so as principais com a contagem de subcategorias`() {
        val rows = buildRows(ready, CategoryTab.Expense, emptySet())
        assertEquals(listOf("main:food", "main:shop"), rows.map { it.key })
        assertEquals(listOf(2, 0), rows.map { (it as CategoryRow.Main).subCount })
        assertTrue(rows.none { (it as CategoryRow.Main).expanded })
    }

    @Test
    fun `grupo expandido traz as subcategorias na ordem e Nova subcategoria por ultimo`() {
        val rows = buildRows(ready, CategoryTab.Expense, setOf("food"))
        assertEquals(listOf("main:food", "sub:delivery", "sub:market", "new:food", "main:shop"), rows.map { it.key })
        assertTrue((rows[0] as CategoryRow.Main).expanded)
        // A subcategoria usa a cor da principal, a escolhida ou a derivada da semente.
        assertEquals("alimentacao", (rows[1] as CategoryRow.Sub).colorKey)
    }

    @Test
    fun `principal sem subcategorias expande so para mostrar Nova subcategoria`() {
        val rows = buildRows(ready, CategoryTab.Expense, setOf("shop"))
        assertEquals(listOf("main:food", "main:shop", "new:shop"), rows.map { it.key })
    }

    @Test
    fun `so a subcategoria de despesa tem menu`() {
        val expense = buildRows(ready, CategoryTab.Expense, setOf("food")).filterIsInstance<CategoryRow.Sub>()
        assertTrue(expense.all { it.hasMenu })
        val income = buildRows(ready, CategoryTab.Income, setOf("sal")).filterIsInstance<CategoryRow.Sub>()
        assertEquals(1, income.size)
        assertTrue(income.none { it.hasMenu })
    }

    @Test
    fun `aba Renda lista principais por ordem e deixa as filhas dentro do grupo`() {
        val rows = buildRows(ready, CategoryTab.Income, setOf("sal"))
        assertEquals(listOf("main:sal", "sub:bonus", "new:sal", "main:extra"), rows.map { it.key })
        assertTrue((rows[0] as CategoryRow.Main).isIncome)
        assertEquals(1, (rows[0] as CategoryRow.Main).subCount)
    }

    @Test
    fun `sem dados prontos nao ha linhas`() {
        assertTrue(buildRows(CategoriesContent.Loading, CategoryTab.Expense, setOf("food")).isEmpty())
        assertTrue(buildRows(CategoriesContent.Failed, CategoryTab.Income, emptySet()).isEmpty())
    }

    @Test
    fun `ordem provisoria reordena so o nivel arrastado e informa a posicao`() {
        val rows = buildRows(ready, CategoryTab.Expense, setOf("food"), DragOrder("food", listOf("market", "delivery")))
        assertEquals(listOf("main:food", "sub:market", "sub:delivery", "new:food", "main:shop"), rows.map { it.key })
        assertEquals(listOf(1, 2), listOf(rows[1], rows[2]).map { (it as CategoryRow.Sub).pos })
        val mains = buildRows(ready, CategoryTab.Expense, emptySet(), DragOrder(null, listOf("shop", "food")))
        assertEquals(listOf("main:shop", "main:food"), mains.map { it.key })
        assertEquals(2, (mains[1] as CategoryRow.Main).pos)
    }

    @Test
    fun `moved leva o id ao destino e devolve a mesma lista quando nada muda`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), ids.moved("a", 2))
        assertEquals(listOf("c", "a", "b"), ids.moved("c", -5))
        assertEquals(ids, ids.moved("b", 1))
    }
}
