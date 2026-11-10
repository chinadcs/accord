package app.accord.ui

import app.accord.core.MonthCashFlow
import app.accord.ui.home.ForecastOutcome
import app.accord.ui.home.forecastBars
import app.accord.ui.home.hasForecastData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Escala e estados da "Previsão até o fim do mês" (ADR 0078, accord-u8zo): mesma escala nas duas barras e pago fora da soma. */
class ForecastSummaryFormatTest {
    // received, toReceive, known, paid, toClassify, estimated, semEstimativa, provisional
    private fun flow(received: Long = 0, toReceive: Long = 0, known: Long = 0, paid: Long = 0, estimated: Long = 0) =
        MonthCashFlow(received, toReceive, known, paid, 0, estimated, 0, false)

    @Test fun `entradas somam recebida e prevista e saidas somam comprometido e estimado`() {
        val bars = forecastBars(flow(received = 600_000, toReceive = 200_000, known = 490_000, estimated = 90_000))
        assertEquals(800_000, bars.inflowCents)
        assertEquals(580_000, bars.outflowCents)
    }

    @Test fun `o pago nunca entra na soma das saidas`() {
        val withPaid = forecastBars(flow(known = 490_000, paid = 400_000, estimated = 90_000))
        val without = forecastBars(flow(known = 490_000, paid = 0, estimated = 90_000))
        assertEquals(without.outflowCents, withPaid.outflowCents)
    }

    @Test fun `a escala e o maior dos dois totais para as barras compararem`() {
        assertEquals(800_000, forecastBars(flow(received = 800_000, known = 100_000)).scaleCents)
        assertEquals(580_000, forecastBars(flow(received = 100_000, known = 580_000)).scaleCents)
    }

    @Test fun `sem renda nao ha barra de entradas e sem gasto nao ha barra de saidas`() {
        val noIncome = forecastBars(flow(known = 100_000))
        assertFalse(noIncome.showInflow)
        assertTrue(noIncome.showOutflow)
        val noSpend = forecastBars(flow(received = 100_000))
        assertTrue(noSpend.showInflow)
        assertFalse(noSpend.showOutflow)
    }

    @Test fun `tudo zero nao desenha barra nenhuma e nao ha dados`() {
        val zero = flow()
        val bars = forecastBars(zero)
        assertFalse(bars.showInflow)
        assertFalse(bars.showOutflow)
        assertFalse(hasForecastData(zero))
    }

    @Test fun `renda prevista ou gasto estimado ja bastam para haver dados`() {
        assertTrue(hasForecastData(flow(toReceive = 1)))
        assertTrue(hasForecastData(flow(estimated = 1)))
        assertTrue(hasForecastData(flow(known = 1)))
    }

    @Test fun `saida liquida negativa por estorno nao desenha trilho vazio`() {
        val bars = forecastBars(flow(received = 100_000, known = -5_000))
        assertFalse(bars.showOutflow)
        assertTrue(bars.showInflow)
    }

    @Test fun `resultado escolhe sobra falta ou zero exato pela palavra`() {
        assertEquals(ForecastOutcome.Surplus, ForecastOutcome.of(1))
        assertEquals(ForecastOutcome.Shortfall, ForecastOutcome.of(-1))
        assertEquals(ForecastOutcome.Even, ForecastOutcome.of(0))
    }
}
