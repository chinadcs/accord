package app.accord.ui

import androidx.lifecycle.SavedStateHandle
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntrySituation
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
}
