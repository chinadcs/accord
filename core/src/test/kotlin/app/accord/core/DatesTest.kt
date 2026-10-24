package app.accord.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DatesTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    // ---- relógio e fuso ----

    @Test
    fun `data padrão usa o fuso de São Paulo, não UTC`() {
        // 31/10/2026 23h30 em São Paulo (UTC-3) já é 01/11 02h30 em UTC.
        val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-11-01T02:30:00Z"), java.time.ZoneOffset.UTC))
        assertEquals(d(2026, 10, 31), clock.today())
        assertEquals(ym(2026, 10), clock.currentMonth())
    }

    @Test
    fun `fuso de referência é America Sao_Paulo`() {
        assertEquals("America/Sao_Paulo", BudgetClock.ZONE.id)
    }

    // ---- mês do orçamento ----

    @Test
    fun `boleto não pago fica no mês do vencimento mesmo depois de vencido`() {
        assertEquals(ym(2026, 10), BudgetMonths.forScheduled(scheduled = d(2026, 10, 20), effective = null))
    }

    @Test
    fun `boleto vence em outubro e é pago em novembro ocupa novembro`() {
        assertEquals(ym(2026, 11), BudgetMonths.forScheduled(d(2026, 10, 20), d(2026, 11, 3)))
    }

    @Test
    fun `boleto vence em novembro e é pago em outubro ocupa outubro`() {
        assertEquals(ym(2026, 10), BudgetMonths.forScheduled(d(2026, 11, 5), d(2026, 10, 30)))
    }

    @Test
    fun `renda prevista para 30 10 recebida em 03 11 conta em novembro`() {
        assertEquals(ym(2026, 11), BudgetMonths.forScheduled(d(2026, 10, 30), d(2026, 11, 3)))
    }

    // ---- recorrência ----

    @Test
    fun `mensal no dia 31 usa o último dia dos meses curtos sem perder o dia original`() {
        val r = Recurrence(anchor = d(2026, 10, 31), frequency = Frequency.Monthly)
        assertEquals(d(2026, 10, 31), r.dateOf(1))
        assertEquals(d(2026, 11, 30), r.dateOf(2))
        assertEquals(d(2026, 12, 31), r.dateOf(3))
        assertEquals(d(2027, 2, 28), r.dateOf(5))
        assertEquals(d(2027, 3, 31), r.dateOf(6))
    }

    @Test
    fun `anual em 29 de fevereiro cai em 28 de fevereiro nos anos comuns`() {
        val r = Recurrence(anchor = d(2028, 2, 29), frequency = Frequency.Annual)
        assertEquals(d(2028, 2, 29), r.dateOf(1))
        assertEquals(d(2029, 2, 28), r.dateOf(2))
        assertEquals(d(2032, 2, 29), r.dateOf(5))
    }

    @Test
    fun `numeração das ocorrências é a da sequência original`() {
        val r = Recurrence(anchor = d(2026, 9, 5), frequency = Frequency.Monthly)
        assertEquals(1, r.numberIn(ym(2026, 9)))
        assertEquals(2, r.numberIn(ym(2026, 10)))
        assertEquals(3, r.numberIn(ym(2026, 11)))
        // janeiro de 2027 é a 5ª, e não muda se alguma ocorrência for excluída
        assertEquals(5, r.numberIn(ym(2027, 1)))
    }

    @Test
    fun `mês anterior à primeira ocorrência não tem ocorrência`() {
        val r = Recurrence(anchor = d(2026, 9, 5), frequency = Frequency.Monthly)
        assertNull(r.numberIn(ym(2026, 8)))
    }

    @Test
    fun `anual só tem ocorrência no mês do aniversário`() {
        val r = Recurrence(anchor = d(2026, 3, 10), frequency = Frequency.Annual)
        assertEquals(1, r.numberIn(ym(2026, 3)))
        assertNull(r.numberIn(ym(2026, 4)))
        assertEquals(2, r.numberIn(ym(2027, 3)))
    }

    @Test
    fun `recorrência sem término ainda tem ocorrência em 2027 e além`() {
        val r = Recurrence(anchor = d(2026, 9, 5), frequency = Frequency.Monthly)
        assertEquals(d(2027, 12, 5), r.dateOf(16))
        assertEquals(16, r.numberIn(ym(2027, 12)))
    }

    @Test
    fun `número de ocorrência precisa ser positivo`() {
        val r = Recurrence(anchor = d(2026, 9, 5), frequency = Frequency.Monthly)
        assertFailsWith<IllegalArgumentException> { r.dateOf(0) }
    }
}
