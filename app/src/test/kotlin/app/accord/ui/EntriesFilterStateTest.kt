package app.accord.ui

import androidx.lifecycle.SavedStateHandle
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntryTypeFilter
import app.accord.core.space.PaymentMethod
import app.accord.ui.entries.EntriesFilterState
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntriesFilterStateTest {
    private val oct = YearMonth.of(2026, 10)
    private val base = EntriesFilterState(oct, oct)

    @Test
    fun `sem restrições a lista não está filtrada e nenhum filtro conta`() {
        assertFalse(base.isFiltered)
        assertEquals(0, base.activeCount)
    }

    @Test
    fun `categoria, forma de pagamento e situação contam como filtros ativos`() {
        val f = base.copy(categoryId = "c", methods = setOf(PaymentMethod.Pix), situations = setOf(EntrySituation.ToClassify))
        assertEquals(3, f.activeCount)
        assertTrue(f.isFiltered)
    }

    @Test
    fun `busca sozinha deixa a lista filtrada mas não conta como filtro do painel`() {
        val f = base.copy(search = "mercado")
        assertTrue(f.isFiltered)
        assertEquals(0, f.activeCount)
    }

    @Test
    fun `limpar filtros mantém período e critério e tira o resto`() {
        val f = EntriesFilterState(
            from = oct, to = oct.plusMonths(2), criterion = DateCriterion.PurchaseDate,
            categoryId = "c", methods = setOf(PaymentMethod.Card), situations = setOf(EntrySituation.Overdue), search = "x",
        ).cleared()
        assertEquals(oct, f.from)
        assertEquals(oct.plusMonths(2), f.to)
        assertEquals(DateCriterion.PurchaseDate, f.criterion)
        assertNull(f.categoryId)
        assertEquals(emptySet(), f.methods)
        assertEquals(emptySet(), f.situations)
        assertEquals("", f.search)
    }

    @Test
    fun `a consulta apara a busca e a omite quando vazia`() {
        assertEquals("pão", base.copy(search = "  pão ").toQuery().search)
        assertNull(base.copy(search = "   ").toQuery().search)
    }

    @Test
    fun `filtros sobrevivem à recriação do processo`() {
        val handle = SavedStateHandle()
        val saved = EntriesFilterState(
            from = oct, to = oct.plusMonths(1), criterion = DateCriterion.PurchaseDate, categoryId = "cat",
            methods = setOf(PaymentMethod.Pix, PaymentMethod.Boleto), situations = setOf(EntrySituation.InvoiceLinkToConfirm), search = "luz",
        )
        saved.saveTo(handle)
        assertEquals(saved, EntriesFilterState.restore(handle, base))
    }

    @Test
    fun `sem nada salvo usa o padrão`() {
        assertEquals(base, EntriesFilterState.restore(SavedStateHandle(), base))
    }

    @Test
    fun `conta, categoria de receita, cada forma e cada situacao contam um a um`() {
        val f = base.copy(
            accountId = "a", incomeCategoryId = "ic", methods = setOf(PaymentMethod.Pix, PaymentMethod.Card),
            situations = setOf(EntrySituation.Paid, EntrySituation.Overdue, EntrySituation.NotPaid),
        )
        assertEquals(7, f.activeCount)
    }

    @Test
    fun `tipo restringe a lista mas nao conta como filtro do painel`() {
        val f = base.copy(type = EntryTypeFilter.Expenses)
        assertEquals(0, f.activeCount)
        assertTrue(f.isFiltered)
        assertEquals(EntryTypeFilter.Expenses, f.toQuery().type)
    }

    @Test
    fun `a consulta leva conta e categoria de receita`() {
        val q = base.copy(accountId = "a", incomeCategoryId = "ic").toQuery()
        assertEquals("a", q.accountId)
        assertEquals("ic", q.incomeCategoryId)
    }

    @Test
    fun `limpar tira tipo, conta e categoria de receita e pode manter a busca`() {
        val f = base.copy(
            from = oct, to = oct.plusMonths(1), criterion = DateCriterion.PurchaseDate,
            type = EntryTypeFilter.Incomes, accountId = "a", incomeCategoryId = "ic", search = "luz",
        )
        val cleared = f.cleared(keepSearch = true)
        assertEquals(EntryTypeFilter.All, cleared.type)
        assertNull(cleared.accountId)
        assertNull(cleared.incomeCategoryId)
        assertEquals("luz", cleared.search)
        assertEquals(oct.plusMonths(1), cleared.to)
        assertEquals(DateCriterion.PurchaseDate, cleared.criterion)
        assertEquals("", f.cleared().search)
    }

    @Test
    fun `campos novos sobrevivem a recriacao e o formato antigo continua valido`() {
        val handle = SavedStateHandle()
        val saved = base.copy(type = EntryTypeFilter.Expenses, accountId = "a", incomeCategoryId = "ic")
        saved.saveTo(handle)
        assertEquals(saved, EntriesFilterState.restore(handle, base))

        val old = SavedStateHandle(mapOf("from" to "2026-10", "to" to "2026-10", "categoryId" to "c"))
        val restored = EntriesFilterState.restore(old, base)
        assertEquals("c", restored.categoryId)
        assertEquals(EntryTypeFilter.All, restored.type)
        assertNull(restored.accountId)
        assertNull(restored.incomeCategoryId)
    }
}
