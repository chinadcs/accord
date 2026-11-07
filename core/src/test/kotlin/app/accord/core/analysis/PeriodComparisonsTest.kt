package app.accord.core.analysis

import app.accord.core.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PeriodComparisonsTest {
    private fun brl(reais: Long) = Money(reais * 100)
    private val food = ComparisonGroupKey("food")
    private val home = ComparisonGroupKey("home")
    private val classify = ComparisonGroupKey(null)

    private fun side(vararg pairs: Pair<ComparisonGroupKey, Money>) = SideValues(true, mapOf(*pairs))

    @Test
    fun `800 para 980 de alimentação é mais 180 reais e mais 22,5 por cento`() {
        val rows = PeriodComparisons.rows(side(food to brl(800)), side(food to brl(980)))
        val row = rows.single()
        assertEquals(Measure.Value(brl(800)), row.a)
        assertEquals(Measure.Value(brl(980)), row.b)
        val change = assertIs<PeriodChange.Computed>(row.change)
        assertEquals(brl(180), change.difference)
        assertEquals(PercentChange.Value(225), change.percent)
    }

    @Test
    fun `grupo que só existe em um lado é zero apurado no outro, com base zero sem percentual`() {
        val rows = PeriodComparisons.rows(side(food to brl(800)), side(food to brl(800), home to brl(120)))
        val homeRow = rows.single { it.key == home }
        assertEquals(Measure.Value(Money.ZERO), homeRow.a)
        val change = assertIs<PeriodChange.Computed>(homeRow.change)
        assertEquals(brl(120), change.difference)
        assertEquals(PercentChange.NoBase(PercentChange.Reason.ZeroBase), change.percent)
    }

    @Test
    fun `período sem dados não vira zero em nenhuma célula`() {
        val rows = PeriodComparisons.rows(SideValues(false, emptyMap()), side(food to brl(980)))
        val row = rows.single()
        assertEquals(Measure.NoData, row.a)
        assertEquals(PeriodChange.Unavailable(Measure.NoData, Measure.Value(brl(980))), row.change)
        assertEquals(Measure.NoData, PeriodComparisons.total(SideValues(false, emptyMap())))
    }

    @Test
    fun `período que falhou identifica a falha e as linhas vêm do outro lado`() {
        val rows = PeriodComparisons.rows(null, side(food to brl(980)))
        val row = rows.single()
        assertEquals(Measure.Failed, row.a)
        assertEquals(Measure.Value(brl(980)), row.b)
        assertEquals(PeriodChange.Unavailable(Measure.Failed, Measure.Value(brl(980))), row.change)
        assertEquals(Measure.Failed, PeriodComparisons.total(null))
        // Se os dois falham, não há linhas para inventar.
        assertEquals(emptyList(), PeriodComparisons.rows(null, null))
    }

    @Test
    fun `linhas em que os dois lados são zero apurado saem`() {
        val rows = PeriodComparisons.rows(side(food to Money.ZERO, home to brl(10)), side(food to Money.ZERO, home to brl(20)))
        assertEquals(listOf(home), rows.map { it.key })
    }

    @Test
    fun `grupo sem gasto em período sem dados não gera linha`() {
        val rows = PeriodComparisons.rows(SideValues(false, emptyMap()), side(food to brl(980), home to Money.ZERO))
        assertEquals(listOf(food), rows.map { it.key })
    }

    @Test
    fun `ordena do maior valor em módulo para o menor, com a chave como desempate`() {
        val a = side(food to brl(100), home to brl(500), classify to brl(50))
        val b = side(food to brl(300), home to brl(400), classify to brl(50))
        assertEquals(listOf(home, food, classify), PeriodComparisons.rows(a, b).map { it.key })
        val tied = PeriodComparisons.rows(side(home to brl(10), food to brl(10)), side(home to brl(10), food to brl(10)))
        assertEquals(listOf(food, home), tied.map { it.key })
    }

    @Test
    fun `total fecha com a soma das linhas, inclusive a classificar`() {
        val a = side(food to brl(800), home to brl(100), classify to brl(50))
        assertEquals(Measure.Value(brl(950)), a.total())
        assertEquals(Measure.Value(brl(950)), PeriodComparisons.total(a))
    }

    @Test
    fun `base negativa por créditos maiores que gastos fica sem percentual`() {
        val rows = PeriodComparisons.rows(side(food to brl(-50)), side(food to brl(100)))
        assertEquals(PercentChange.NoBase(PercentChange.Reason.NegativeBase), (rows.single().change as PeriodChange.Computed).percent)
    }
}
