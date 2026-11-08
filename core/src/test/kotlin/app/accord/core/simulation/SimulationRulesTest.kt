package app.accord.core.simulation

import app.accord.core.Money
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regras puras da simulação (S7), sem banco: o que o cenário acrescenta ao excesso e a recusa de entradas inválidas. */
class SimulationRulesTest {
    private val nov = YearMonth.of(2026, 11)
    private fun brl(r: Int) = Money(r * 100L)

    private fun run(scenario: Scenario, category: CategoryBaseline?, estimate: Long? = null) =
        assertIs<SimulationResult.Ready>(
            Simulation.run(scenario) { MonthBaseline(it, knownCents = 20_000, variableEstimateCents = estimate, category = category) },
        )

    @Test
    fun `o excesso do cenário é só o que ele acrescenta ao que já excedia`() {
        val s = Scenario(brl(400), 2, nov, categoryId = "c")
        // Já gasto 250 contra limite 300: com +200 passa a 450 (excesso 150, todo ele acrescentado pelo cenário).
        assertEquals(brl(150), run(s, CategoryBaseline("c", 30_000, 25_000)).months.first().category!!.scenarioExcess)
        // Já gasto 350 contra limite 300 (excesso 50 existente): com +200 o excesso vai a 250, o cenário acrescenta 200.
        assertEquals(brl(200), run(s, CategoryBaseline("c", 30_000, 35_000)).months.first().category!!.scenarioExcess)
        // Folga suficiente: nenhum excesso.
        assertEquals(Money.ZERO, run(s, CategoryBaseline("c", 100_000, 0)).months.first().category!!.scenarioExcess)
    }

    @Test
    fun `sem limite o excesso é nulo e o limite zero definido gera excesso`() {
        val s = Scenario(brl(400), 2, nov, categoryId = "c")
        assertNull(run(s, CategoryBaseline("c", null, 0)).months.first().category!!.scenarioExcess)
        assertEquals(brl(200), run(s, CategoryBaseline("c", 0, 0)).months.first().category!!.scenarioExcess)
    }

    @Test
    fun `a estimativa de variáveis fica separada e não entra no total com o cenário`() {
        val month = run(Scenario(brl(400), 2, nov), category = null, estimate = 5_000).months.first()
        assertEquals(Money(5_000), month.variableEstimate)
        assertEquals(brl(400), month.totalWithScenario) // 200 conhecidos + 200 do cenário
    }

    @Test
    fun `entrada inválida não devolve resultado`() {
        assertIs<SimulationResult.Invalid>(Simulation.run(Scenario(Money.ZERO, 3, nov)) { error("não lê a base") })
        assertTrue(Simulation.months(Scenario(Money(-1), 3, nov)).isEmpty())
    }
}
