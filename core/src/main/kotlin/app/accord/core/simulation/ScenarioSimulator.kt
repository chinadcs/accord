package app.accord.core.simulation

import app.accord.core.BudgetClock
import app.accord.core.CardCycle
import app.accord.core.forecast.Estimate
import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import java.time.YearMonth

/**
 * Simulador de compra (S7, tela 36): **consulta pura**. Só chama consultas da [FinanceSpace] — nunca `execute`, comando de aviso nem
 * avaliação de alertas —, então simular não grava despesa, não entra na fila, não gera evento de alerta e não consome a cota semanal (S5).
 * O cenário é comparado com a base local; offline ela pode não incluir registros ainda não recebidos do outro aparelho.
 */
class ScenarioSimulator(private val space: FinanceSpace, private val clock: BudgetClock) {

    /** Primeira fatura sugerida: a que o motor de S1 estimaria para uma compra hoje. É estimativa; o casal escolhe. */
    suspend fun suggestedFirstMonth(): YearMonth? {
        val card = (space.card() as? QueryResult.Data)?.value ?: return null
        return CardCycle(card.closingDay, card.dueDay).assign(clock.today()).dueMonth
    }

    suspend fun simulate(scenario: Scenario): SimulationResult {
        val invalid = Simulation.validate(scenario)
        if (invalid.isNotEmpty()) return SimulationResult.Invalid(invalid)
        val baselines = HashMap<YearMonth, MonthBaseline>()
        for (month in Simulation.months(scenario)) {
            baselines[month] = baselineOf(month, scenario.categoryId) ?: return SimulationResult.Unavailable
        }
        return Simulation.run(scenario) { baselines.getValue(it) }
    }

    private suspend fun baselineOf(month: YearMonth, categoryId: String?): MonthBaseline? {
        val known = (space.commitments(month) as? QueryResult.Data)?.value?.knownTotal ?: return null
        val forecast = (space.forecasts(month) as? QueryResult.Data)?.value ?: return null
        val estimates = forecast.categories.mapNotNull { (it.estimate as? Estimate.Value)?.cents }
        val category = categoryId?.let { id ->
            val budget = (space.budget(month) as? QueryResult.Data)?.value ?: return null
            // Categoria sem lançamentos no mês não aparece no orçamento: conhecidos 0 e limite pela consulta de limites.
            val line = budget.categories.firstOrNull { it.categoryId == id }
            if (line != null) {
                CategoryBaseline(id, line.limit.total?.cents, line.net.cents)
            } else {
                val limit = (space.limits(month) as? QueryResult.Data)?.value?.firstOrNull { it.categoryId == id }?.setting?.total?.cents
                CategoryBaseline(id, limit, 0)
            }
        }
        return MonthBaseline(month, known.cents, estimates.takeIf { it.isNotEmpty() }?.sum(), category)
    }
}
