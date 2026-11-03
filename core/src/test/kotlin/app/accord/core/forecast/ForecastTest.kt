package app.accord.core.forecast

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** accord-j0s.1: a regra da previsão do S5. Exemplos de calendário calculados à mão, independentes da implementação. */
class ForecastTest {
    private val cycle = CardCycle(closingDay = 8, dueDay = 15)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val start = LocalDate.of(2026, 1, 1)

    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun other(date: LocalDate, cents: Long) = VariableSpend(date, Channel.Other, cents)
    private fun card(date: LocalDate, cents: Long) = VariableSpend(date, Channel.Card, cents)

    private fun category(known: Long, limit: Long? = 120_000, vararg spends: VariableSpend, id: String = "cat") =
        CategoryForecastInput(id, limit, known, spends.toList())

    private fun run(
        month: YearMonth, today: LocalDate, vararg categories: CategoryForecastInput,
        historyStart: LocalDate? = start, quality: DataQuality = DataQuality(), conferredClosed: Boolean? = null,
    ) = Forecast.compute(ForecastRequest(month, today, cycle, historyStart, categories.toList(), quality, conferredClosed)).categories.single()

    // ---- o exemplo do spec: R$ 980 conhecidos + R$ 320 estimados contra R$ 1.200 ----

    @Test fun `known plus estimated is the total and the excess is the part above the limit`() {
        // Jul 31 + Ago 31 + Set 30 = 92 dias; R$ 1.840 neles dá R$ 20/dia; restam 16 dias (16 a 31/10, hoje incluído).
        val spends = arrayOf(other(d(7, 10), 60_000), other(d(8, 12), 64_000), other(d(9, 20), 60_000))
        val c = run(oct, d(10, 16), category(98_000, 120_000, *spends))
        assertEquals(Estimate.Value(32_000), c.estimate)
        assertEquals(130_000, c.totalCents)
        assertEquals(Excess.Possible(10_000), c.excess)
    }

    @Test fun `the day of the calculation counts as a remaining day and the last day of the period too`() {
        val spends = arrayOf(other(d(7, 10), 92_000))
        val firstOfMonth = run(oct, d(10, 1), category(0, null, *spends)).explanation.single { it.channel == Channel.Other }
        assertEquals(31, firstOfMonth.remainingDays)
        val lastDay = run(oct, d(10, 31), category(0, null, *spends)).explanation.single { it.channel == Channel.Other }
        assertEquals(1, lastDay.remainingDays)
    }

    @Test fun `each channel is rounded once and the total is exactly known plus the displayed estimate`() {
        // Outros: R$ 10,00 em 92 dias × 16 dias = 173,913 → 174. Cartão (fatura de novembro: 8/10 a 7/11 = 31 dias; 19 dias restantes em 20/10).
        val c = run(
            nov, d(10, 20),
            category(
                50_000, null,
                other(d(7, 5), 1_000), card(d(9, 1), 1_000), // um por canal
            ),
        )
        val sum = c.explanation.sumOf { it.estimateCents ?: 0L }
        assertEquals(Estimate.Value(sum), c.estimate)
        assertEquals(50_000 + sum, c.totalCents)
    }

    // ---- limites ----

    @Test fun `an absent limit is not zero and a zero limit counts as defined`() {
        val spends = arrayOf(other(d(9, 20), 30_000))
        assertEquals(Excess.NoLimit, run(oct, d(10, 16), category(5_000, null, *spends)).excess)
        assertTrue(run(oct, d(10, 16), category(5_000, 0, *spends)).excess is Excess.Occurred)
    }

    @Test fun `known already above the limit is an occurred excess even when the estimate is unavailable`() {
        val c = run(oct, d(10, 16), category(150_000, 120_000), historyStart = null)
        assertIs<Estimate.Unavailable>(c.estimate)
        assertEquals(Excess.Occurred(30_000), c.excess)
        assertEquals(150_000, c.totalCents)
    }

    // ---- histórico ----

    @Test fun `without a complete period the estimate is unavailable, never zero`() {
        // O espaço começou em 5/10: nenhum mês anterior está coberto por inteiro.
        val c = run(oct, d(10, 16), category(10_000, 120_000), historyStart = d(10, 5), conferredClosed = true)
        assertEquals(Estimate.Unavailable(UnavailableReason.NoCompletePeriod), c.estimate)
        assertEquals(10_000, c.totalCents)
        assertEquals(Confidence.Low(listOf(ConfidenceReason.InsufficientHistory)), c.confidence)
    }

    @Test fun `fewer periods than N are still used, with low confidence`() {
        // Cobre só agosto e setembro (começou em 1/8): 31 + 30 = 61 dias.
        val c = run(oct, d(10, 16), category(0, null, other(d(8, 5), 31_000), other(d(9, 5), 30_000)), historyStart = d(8, 1), conferredClosed = true)
        // R$ 610 em 61 dias = R$ 10/dia × 16 dias = R$ 160.
        assertEquals(Estimate.Value(16_000), c.estimate)
        assertEquals(listOf(ConfidenceReason.InsufficientHistory), (c.confidence as Confidence.Low).reasons)
    }

    @Test fun `only the last N periods count and expenses of the other channel are not mixed`() {
        val spends = arrayOf(
            other(d(6, 10), 999_999), // junho fica fora: só os 3 últimos
            other(d(7, 10), 31_000), other(d(8, 10), 31_000), other(d(9, 10), 30_000),
            card(d(8, 20), 888_888), // cartão não entra no canal "outros"
        )
        val other = run(oct, d(10, 16), category(0, null, *spends)).explanation.single { it.channel == Channel.Other }
        assertEquals(listOf(30_000L, 31_000L, 31_000L), other.samples.map { it.cents })
        assertEquals(Period(d(9, 1), d(10, 1)), other.samples.first().period)
    }

    // ---- cartão ----

    @Test fun `a card purchase on the closing day belongs to the next invoice period`() {
        // Fatura de outubro: 8/9 a 7/10 (fim exclusivo 8/10). A compra de 8/10 é da fatura de novembro.
        val spends = arrayOf(card(d(9, 8), 10_000), card(d(10, 7), 20_000), card(d(10, 8), 40_000))
        val c = run(nov, d(10, 20), category(0, null, *spends), historyStart = d(1, 1))
        val cardChannel = c.explanation.single { it.channel == Channel.Card }
        assertEquals(Period(d(10, 8), d(11, 8)), cardChannel.exposure)
        val october = cardChannel.samples.first { it.period == Period(d(9, 8), d(10, 8)) }
        assertEquals(30_000, october.cents, "a compra de 8/10 não entra na fatura que fecha em 8/10")
        assertEquals(19, cardChannel.remainingDays)
    }

    @Test fun `a closed invoice estimates nothing more for the card and says so when the closing is only configured`() {
        // Em 16/10 a fatura de outubro (fecha em 8/10) já terminou pelo dia configurado.
        val c = run(oct, d(10, 16), category(0, null, card(d(9, 1), 50_000), other(d(9, 1), 30_000)))
        val cardChannel = c.explanation.single { it.channel == Channel.Card }
        assertEquals(ChannelState.Ended, cardChannel.state)
        assertEquals(0, cardChannel.estimateCents)
        assertTrue(ConfidenceReason.InvoiceClosedByConfigurationOnly in (c.confidence as Confidence.Low).reasons)
    }

    @Test fun `a closing confirmed by the invoice conference needs no caveat`() {
        // Hoje ainda é antes do dia configurado, mas a conferência já deu a fatura como fechada.
        val c = run(oct, d(10, 5), category(0, null, other(d(9, 1), 30_000)), conferredClosed = true)
        assertEquals(ChannelState.InvoiceClosed, c.explanation.single { it.channel == Channel.Card }.state)
        assertTrue(ConfidenceReason.InvoiceClosedByConfigurationOnly !in ((c.confidence as? Confidence.Low)?.reasons.orEmpty()))
    }

    // ---- meses encerrados e futuros ----

    @Test fun `a month already over shows only the known amounts`() {
        val c = run(YearMonth.of(2026, 9), d(10, 16), category(80_000, 70_000, other(d(8, 3), 99_000)))
        assertEquals(Estimate.NotApplicable, c.estimate)
        assertEquals(80_000, c.totalCents)
        assertEquals(Excess.Occurred(10_000), c.excess)
    }

    @Test fun `a future month projects every day of its exposure`() {
        val c = run(YearMonth.of(2026, 12), d(10, 16), category(0, null, other(d(8, 3), 31_000), other(d(9, 3), 30_000), other(d(10, 3), 31_000)))
        val other = c.explanation.single { it.channel == Channel.Other }
        assertEquals(31, other.remainingDays)
        // Outubro e novembro ainda não terminaram em 16/10: os três últimos períodos completos são julho, agosto e setembro.
        assertEquals(listOf(Period(d(7, 1), d(8, 1)), Period(d(8, 1), d(9, 1)), Period(d(9, 1), d(10, 1))), other.samples.map { it.period }.sortedBy { it.start })
    }

    // ---- confiança ----

    @Test fun `no known caveat is the best the rule says`() {
        val spends = arrayOf(other(d(7, 10), 1_000), other(d(8, 10), 1_000), other(d(9, 10), 1_000), card(d(8, 20), 100))
        val c = run(nov, d(10, 20), category(0, null, *spends))
        assertEquals(Confidence.NoKnownCaveats, c.confidence)
    }

    @Test fun `quality problems each add their reason, pendings without category reach every category`() {
        val spends = arrayOf(other(d(7, 10), 1_000))
        val request = ForecastRequest(
            oct, d(10, 16), cycle, start,
            listOf(CategoryForecastInput("a", null, 0, spends.toList()), CategoryForecastInput("b", null, 0, spends.toList())),
            DataQuality(
                coverageComplete = false, pendingWithoutCategory = 1, pendingByCategory = mapOf("a" to 2),
                toClassifyCount = 3, toClassifyCents = 4_500, sharedViewPossiblyStale = true,
            ),
        )
        val result = Forecast.compute(request)
        val expected = listOf(
            ConfidenceReason.IncompleteCoverage, ConfidenceReason.OpenPendings, ConfidenceReason.ToClassifyInPeriod, ConfidenceReason.SharedViewPossiblyStale,
        )
        result.categories.forEach { c -> assertTrue(expected.all { it in (c.confidence as Confidence.Low).reasons }, c.categoryId) }
        assertEquals(ToClassifySummary(3, 4_500), result.toClassify)
        assertEquals(Forecast.RULE_VERSION, result.ruleVersion)
    }

    @Test fun `a pending of one category does not lower the others`() {
        val spends = arrayOf(other(d(7, 10), 1_000), other(d(8, 10), 1_000), other(d(9, 10), 1_000), card(d(8, 20), 100))
        val request = ForecastRequest(
            nov, d(10, 20), cycle, start,
            listOf(CategoryForecastInput("a", null, 0, spends.toList()), CategoryForecastInput("b", null, 0, spends.toList())),
            DataQuality(pendingByCategory = mapOf("a" to 1)),
        )
        val (a, b) = Forecast.compute(request).categories
        assertEquals(listOf(ConfidenceReason.OpenPendings), (a.confidence as Confidence.Low).reasons)
        assertEquals(Confidence.NoKnownCaveats, b.confidence)
    }
}
