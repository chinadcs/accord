package app.accord.ui

import app.accord.core.LimitSetting
import app.accord.core.LimitView
import app.accord.core.Money
import app.accord.ui.budget.BarState
import app.accord.ui.budget.barFill
import app.accord.ui.budget.barState
import app.accord.ui.budget.excessMarker
import app.accord.ui.budget.percentLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetPresentationTest {
    private fun limit(total: Int?, net: Int) = LimitView.of(
        LimitSetting(total?.let { Money(it * 100L) }, emptyMap()), Money(net * 100L), emptyMap(),
    )

    @Test
    fun `sem limite definido não mostra barra, percentual nem excesso`() {
        val l = limit(null, 50)
        assertEquals(BarState.NoLimit, barState(l))
        assertEquals(0f, barFill(l))
        assertNull(percentLabel(l))
        assertNull(excessMarker(l, Money(5_000)))
    }

    @Test
    fun `dentro do limite a barra é proporcional`() {
        val l = limit(1200, 600)
        assertEquals(BarState.WithinLimit, barState(l))
        assertEquals(0.5f, barFill(l))
        assertEquals(50, percentLabel(l))
    }

    @Test
    fun `a partir de 80 por cento entra em atenção`() {
        assertEquals(BarState.WithinLimit, barState(limit(1000, 799)))
        assertEquals(BarState.Attention, barState(limit(1000, 800)))
        assertEquals(BarState.Attention, barState(limit(1200, 1200))) // no limite exato, sem excesso
    }

    @Test
    fun `excesso enche a barra e marca onde está o limite`() {
        val l = limit(900, 980)
        assertEquals(BarState.Excess, barState(l))
        assertEquals(1f, barFill(l))
        assertEquals(900f / 980f, excessMarker(l, Money(98_000)))
    }

    @Test
    fun `limite zero com gasto é excesso sem percentual`() {
        val l = limit(0, 50)
        assertEquals(BarState.Excess, barState(l))
        assertNull(percentLabel(l))
    }

    @Test
    fun `limite zero sem gasto não é excesso nem tem percentual`() {
        val l = limit(0, 0)
        assertEquals(BarState.WithinLimit, barState(l))
        assertNull(percentLabel(l))
    }

    @Test
    fun `crédito líquido nunca vira barra negativa`() {
        val l = LimitView.of(LimitSetting(Money(120_000), emptyMap()), Money(-4_000), emptyMap())
        assertEquals(0f, barFill(l))
        assertEquals(BarState.WithinLimit, barState(l))
        assertEquals(0, percentLabel(l)?.coerceAtLeast(0))
    }
}
