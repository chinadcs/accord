package app.accord.core.analysis

import app.accord.core.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnalysisBaseTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun brl(reais: Long, cents: Long = 0) = Money(reais * 100 + cents)

    // ---- período padrão da revisão ----

    @Test
    fun `em 22-10-2026 a revisão abre em 12 a 18 de outubro`() {
        // Quinta-feira 22/10/2026, 12h em São Paulo = 15h UTC.
        val p = ReviewPeriod.lastCompleteWeek(Instant.parse("2026-10-22T15:00:00Z"))
        assertEquals(d(2026, 10, 12), p.start)
        assertEquals(d(2026, 10, 18), p.lastDay)
        assertEquals(d(2026, 10, 19), p.endExclusive)
    }

    @Test
    fun `domingo 25-10 às 23h30 em São Paulo ainda não fechou a semana`() {
        // 26/10 02h30 UTC = 25/10 23h30 em São Paulo.
        val p = ReviewPeriod.lastCompleteWeek(Instant.parse("2026-10-26T02:30:00Z"))
        assertEquals(d(2026, 10, 12), p.start)
        assertEquals(d(2026, 10, 18), p.lastDay)
    }

    @Test
    fun `a partir de segunda 26-10 às 00h em São Paulo o padrão passa a 19 a 25`() {
        // 26/10 03h00 UTC = 26/10 00h00 em São Paulo (UTC-3).
        val p = ReviewPeriod.lastCompleteWeek(Instant.parse("2026-10-26T03:00:00Z"))
        assertEquals(d(2026, 10, 19), p.start)
        assertEquals(d(2026, 10, 25), p.lastDay)
        assertEquals(d(2026, 10, 26), p.endExclusive)
    }

    @Test
    fun `semana que cruza o mês mantém as datas explícitas`() {
        // Quarta 07/10/2026: última semana completa 28/09 a 04/10.
        val p = ReviewPeriod.lastCompleteWeek(Instant.parse("2026-10-07T15:00:00Z"))
        assertEquals(d(2026, 9, 28), p.start)
        assertEquals(d(2026, 10, 4), p.lastDay)
    }

    @Test
    fun `contains respeita início inclusivo e fim exclusivo`() {
        val p = ReviewPeriod(d(2026, 10, 12), d(2026, 10, 19))
        assertTrue(d(2026, 10, 12) in p)
        assertTrue(d(2026, 10, 18) in p)
        assertFalse(d(2026, 10, 19) in p)
        assertFalse(d(2026, 10, 11) in p)
    }

    @Test
    fun `período vazio ou invertido é rejeitado`() {
        assertFailsWith<IllegalArgumentException> { ReviewPeriod(d(2026, 10, 12), d(2026, 10, 12)) }
        assertFailsWith<IllegalArgumentException> { ReviewPeriod(d(2026, 10, 13), d(2026, 10, 12)) }
    }

    // ---- diferença e percentual ----

    private fun change(a: Money, b: Money) = assertIs<PeriodChange.Computed>(PeriodChange.of(Measure.Value(a), Measure.Value(b)))

    @Test
    fun `800 para 980 é mais 180 reais e mais 22,5 por cento`() {
        val c = change(brl(800), brl(980))
        assertEquals(brl(180), c.difference)
        assertEquals("+R$ 180,00", c.difference.formatSigned())
        assertEquals(PercentChange.Value(225), c.percent)
        assertEquals("+22,5%", (c.percent as PercentChange.Value).format())
    }

    @Test
    fun `980 para 800 é menos 180 reais e menos 18,4 por cento`() {
        val c = change(brl(980), brl(800))
        assertEquals("-R$ 180,00", c.difference.formatSigned())
        assertEquals("-18,4%", (c.percent as PercentChange.Value).format())
    }

    @Test
    fun `empate de arredondamento afasta do zero nos dois sentidos`() {
        // A = R$ 20,00 (2000 cents), diferença de 1 centavo = 0,05% exato.
        assertEquals("+0,1%", (change(Money(2000), Money(2001)).percent as PercentChange.Value).format())
        assertEquals("-0,1%", (change(Money(2000), Money(1999)).percent as PercentChange.Value).format())
    }

    @Test
    fun `sem variação mostra zero sem sinal`() {
        val c = change(brl(500), brl(500))
        assertEquals(Money.ZERO, c.difference)
        assertEquals("R$ 0,00", c.difference.formatSigned())
        assertEquals("0,0%", (c.percent as PercentChange.Value).format())
    }

    @Test
    fun `base zero mostra a diferença e não calcula percentual`() {
        val c = change(Money.ZERO, brl(120))
        assertEquals(brl(120), c.difference)
        assertEquals(PercentChange.NoBase(PercentChange.Reason.ZeroBase), c.percent)
    }

    @Test
    fun `base negativa por créditos maiores que gastos não calcula percentual`() {
        val c = change(brl(-50), brl(100))
        assertEquals(brl(150), c.difference)
        assertEquals(PercentChange.NoBase(PercentChange.Reason.NegativeBase), c.percent)
    }

    @Test
    fun `valor final negativo com base positiva ainda tem percentual`() {
        // 800 → -100: -900 / 800 = -112,5%.
        val c = change(brl(800), brl(-100))
        assertEquals("-112,5%", (c.percent as PercentChange.Value).format())
    }

    @Test
    fun `ausência e falha nunca viram zero na diferença`() {
        assertEquals(PeriodChange.Unavailable(Measure.NoData, Measure.of(98000)), PeriodChange.of(Measure.NoData, Measure.of(98000)))
        assertEquals(PeriodChange.Unavailable(Measure.of(80000), Measure.Failed), PeriodChange.of(Measure.of(80000), Measure.Failed))
        assertEquals(PeriodChange.Unavailable(Measure.NoData, Measure.Failed), PeriodChange.of(Measure.NoData, Measure.Failed))
    }

    @Test
    fun `zero apurado é valor e difere de sem dados`() {
        assertTrue(Measure.of(0) is Measure.Value)
        assertTrue(Measure.of(0) != Measure.NoData)
        assertIs<PeriodChange.Computed>(PeriodChange.of(Measure.of(0), Measure.of(5000)))
    }

    @Test
    fun `razão que não cabe vira sem percentual em vez de estourar`() {
        val c = change(Money(1), Money(Long.MAX_VALUE / 2))
        assertEquals(Money(Long.MAX_VALUE / 2 - 1), c.difference)
        assertEquals(PercentChange.NoBase(PercentChange.Reason.OutOfRange), c.percent)
        // Um valor realista (R$ 1 trilhão contra 1 centavo) ainda tem percentual.
        assertIs<PercentChange.Value>(change(Money(1), Money(100_000_000_000_000)).percent)
    }

    // ---- fase do produto ----

    @Test
    fun `P0 não habilita recursos P1 e P1 inclui P0`() {
        assertFalse(ProductPhase.P0.includes(ProductPhase.P1))
        assertTrue(ProductPhase.P0.includes(ProductPhase.P0))
        assertTrue(ProductPhase.P1.includes(ProductPhase.P0))
        assertTrue(ProductPhase.P1.includes(ProductPhase.P1))
    }
}
