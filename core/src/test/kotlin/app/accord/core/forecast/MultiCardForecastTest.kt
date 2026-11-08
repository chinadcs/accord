package app.accord.core.forecast

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class MultiCardForecastTest {
    @Test fun `cartao fechado nao encerra o outro e canal fora do cartao conta uma vez`() {
        val month = YearMonth.of(2026, 10)
        val today = LocalDate.of(2026, 10, 10)
        val spends = listOf(
            VariableSpend(LocalDate.of(2026, 9, 1), Channel.Card, 3000, "a"),
            VariableSpend(LocalDate.of(2026, 9, 1), Channel.Card, 6000, "b"),
            VariableSpend(LocalDate.of(2026, 9, 1), Channel.Other, 9000),
        )
        val cards = listOf(CardForecastConfig("a", "Azul", CardCycle(8, 15), false), CardForecastConfig("b", "Verde", CardCycle(20, 28), false))
        val request = ForecastRequest(month, today, CardCycle(), LocalDate.of(2026, 8, 1),
            listOf(CategoryForecastInput("compras", null, 10000, spends)), periods = 1, cards = cards)
        val result = Forecast.compute(request)
        val channels = result.categories.single().explanation
        assertFalse(result.invoice.closed)
        assertEquals(3, channels.size)
        assertEquals(ChannelState.Ended, channels.single { it.cardId == "a" }.state)
        assertEquals(ChannelState.Estimated, channels.single { it.cardId == "b" }.state)
        assertEquals(10, channels.single { it.cardId == "b" }.remainingDays)
        assertEquals(1935, channels.single { it.cardId == "b" }.estimateCents)
        assertEquals(6600, channels.single { it.channel == Channel.Other }.estimateCents)
        assertEquals(18535, result.categories.single().totalCents)
    }
}
