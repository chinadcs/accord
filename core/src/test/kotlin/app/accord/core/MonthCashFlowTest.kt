package app.accord.core

import app.accord.core.forecast.*
import app.accord.core.space.*
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class MonthCashFlowTest {
    private val month = YearMonth.of(2026, 11)

    private fun row(id: String, net: Long, estimate: Estimate, confidence: Confidence = Confidence.NoKnownCaveats) = CategoryForecast(
        id, null, net, estimate, net + ((estimate as? Estimate.Value)?.cents ?: 0), Excess.NoLimit, confidence, emptyList(),
    )

    private fun forecast(vararg rows: CategoryForecast) = MonthForecast(
        month, Forecast.RULE_VERSION, InvoiceClosure(false, ClosureSource.Configuration), rows.toList(), ToClassifySummary(0, 0),
    )

    private fun budget(received: Long, toReceive: Long, paid: Long, toClassify: Long, links: Int = 0) = MonthBudget(
        month, Money.ZERO, Money.ZERO, Money(paid), ToClassifyLine(Money(toClassify), if (toClassify > 0) 1 else 0), Money.ZERO, links,
        IncomeTotals(Money(received), Money(toReceive)), emptyList(),
    )

    @Test fun `soma comprometido mais a classificar e estimado sem somar o pago de novo`() {
        val flow = monthCashFlow(
            budget(received = 300_000, toReceive = 200_000, paid = 150_000, toClassify = 5_000),
            forecast(row("a", 100_000, Estimate.Value(20_000)), row("b", 80_000, Estimate.Value(0))),
        )
        assertEquals(185_000L, flow.knownCents)
        assertEquals(150_000L, flow.paidCents)
        assertEquals(20_000L, flow.estimatedCents)
        assertEquals(205_000L, flow.projectedSpendCents)
    }

    @Test fun `renda prevista nao entra no resultado so com a recebida`() {
        val flow = monthCashFlow(budget(300_000, 200_000, 0, 0), forecast(row("a", 100_000, Estimate.Value(20_000))))
        assertEquals(180_000L, flow.resultWithReceivedCents)
        assertEquals(380_000L, flow.resultWithExpectedCents)
    }

    @Test fun `resultado negativo e estimativa indisponivel nao vira zero silencioso`() {
        val flow = monthCashFlow(
            budget(10_000, 0, 0, 0),
            forecast(row("a", 30_000, Estimate.Unavailable(UnavailableReason.NoCompletePeriod)), row("b", 0, Estimate.NotApplicable)),
        )
        assertEquals(-20_000L, flow.resultWithReceivedCents)
        assertEquals(1, flow.categoriesWithoutEstimate)
        assertEquals(0L, flow.estimatedCents)
    }

    @Test fun `provisorio por vinculo a conferir ou por ressalva de confianca`() {
        val clean = forecast(row("a", 1, Estimate.Value(0)))
        assertFalse(monthCashFlow(budget(0, 0, 0, 0), clean).provisional)
        assertTrue(monthCashFlow(budget(0, 0, 0, 0, links = 1), clean).provisional)
        val low = forecast(row("a", 1, Estimate.Value(0), Confidence.Low(listOf(ConfidenceReason.InsufficientHistory))))
        assertTrue(monthCashFlow(budget(0, 0, 0, 0), low).provisional)
    }

    @Test fun `meses diferentes sao recusados`() {
        val other = forecast().copy(month = YearMonth.of(2026, 12))
        assertFailsWith<IllegalArgumentException> { monthCashFlow(budget(0, 0, 0, 0), other) }
    }
}
