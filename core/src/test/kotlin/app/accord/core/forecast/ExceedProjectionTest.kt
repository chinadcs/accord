package app.accord.core.forecast

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class ExceedProjectionTest {
    private val today = LocalDate.parse("2026-11-10")

    private fun channel(estimate: Long?, remaining: Int, state: ChannelState = ChannelState.Estimated) = ChannelExplanation(
        Channel.Other, Period(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-01")), state, remaining, 3, emptyList(), estimate,
    )

    private fun category(id: String, limit: Long?, known: Long, vararg channels: ChannelExplanation, confidence: Confidence = Confidence.NoKnownCaveats): CategoryForecast {
        val estimated = channels.sumOf { it.estimateCents ?: 0 }
        val total = known + estimated
        val excess = when {
            limit == null -> Excess.NoLimit
            known > limit -> Excess.Occurred(known - limit)
            total > limit -> Excess.Possible(total - limit)
            else -> Excess.None
        }
        return CategoryForecast(id, limit, known, Estimate.Value(estimated), total, excess, confidence, channels.toList())
    }

    private fun forecast(vararg rows: CategoryForecast) = MonthForecast(
        YearMonth.of(2026, 11), Forecast.RULE_VERSION, InvoiceClosure(false, ClosureSource.Configuration), rows.toList(), ToClassifySummary(0, 0),
    )

    @Test fun `data do estouro e o primeiro dia em que o acumulado passa do limite`() {
        // 21 dias restantes, estimado 21000 = 1000 por dia; folga de 5000: passa no 6º dia (6000 > 5000), isto é, daqui a 5 dias.
        val result = exceedProjections(forecast(category("lazer", 100_000, 95_000, channel(21_000, 21))), today).single()
        assertEquals(5, result.daysFromToday)
        assertEquals(LocalDate.parse("2026-11-15"), result.date)
        assertEquals(16_000L, result.projectedExcessCents)
    }

    @Test fun `folga exata nao estoura naquele dia`() {
        // Folga de 5000 e 1000 por dia: no 5º dia chega a 5000, que é igual ao limite e não excede.
        val result = exceedProjections(forecast(category("lazer", 100_000, 95_000, channel(21_000, 21))), today).single()
        assertEquals(6, result.daysFromToday + 1)
    }

    @Test fun `estouro hoje tem zero dias`() {
        val result = exceedProjections(forecast(category("a", 100_000, 99_500, channel(21_000, 21))), today).single()
        assertEquals(0, result.daysFromToday)
        assertEquals(today, result.date)
    }

    @Test fun `ja ocorrido, sem limite, dentro do limite e sem estimativa nao projetam`() {
        val rows = forecast(
            category("ocorrido", 100_000, 120_000, channel(21_000, 21)),
            category("sem-limite", null, 1_000, channel(21_000, 21)),
            category("folga", 100_000, 10_000, channel(21_000, 21)),
            category("indisponivel", 100_000, 99_999, channel(null, 21, ChannelState.Unavailable)),
        )
        assertTrue(exceedProjections(rows, today).isEmpty())
    }

    @Test fun `dois canais com prazos diferentes somam o ritmo de cada um ate o fim dele`() {
        // Cartão: 3000 em 3 dias (1000 por dia). Outros: 10000 em 10 dias (1000 por dia). Folga de 6500.
        // Dias 1..3 acumulam 2000 por dia (6000 no 3º); no 4º, 3000+4000 = 7000 > 6500, estouro daqui a 3 dias.
        val result = exceedProjections(forecast(category("a", 100_000, 93_500, channel(3_000, 3), channel(10_000, 10))), today).single()
        assertEquals(3, result.daysFromToday)
    }

    @Test fun `ordena da data mais proxima e marca ressalvas de confianca`() {
        val low = Confidence.Low(listOf(ConfidenceReason.InsufficientHistory))
        val rows = forecast(
            category("tarde", 100_000, 90_000, channel(21_000, 21)),
            category("cedo", 100_000, 99_000, channel(21_000, 21), confidence = low),
        )
        val result = exceedProjections(rows, today)
        assertEquals(listOf("cedo", "tarde"), result.map { it.categoryId })
        assertTrue(result[0].lowConfidence)
        assertFalse(result[1].lowConfidence)
    }

    @Test fun `ultimo dia do ritmo fecha exatamente com o estimado da previsao`() {
        // 7 dias, 10 centavos: o arredondamento diário não pode perder nem sobrar centavo no fim.
        val result = exceedProjections(forecast(category("a", 100_000, 99_995, channel(10, 7))), today).single()
        assertEquals(10L - 5, result.projectedExcessCents)
        assertTrue(result.daysFromToday in 0..6)
    }
}
