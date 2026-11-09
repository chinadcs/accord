package app.accord.core

import app.accord.core.space.*
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class SpendingAllowanceTest {
    private fun row(id: String, limit: Long?, gross: Long, credit: Long = 0) = CategoryBudget(
        id, Money(gross), Money(credit), Money(gross - credit), Money(gross), emptyMap(),
        LimitView.of(LimitSetting(limit?.let(::Money), emptyMap()), Money(gross - credit), emptyMap()),
    )

    private fun budget(date: String, vararg rows: CategoryBudget) = MonthBudget(
        YearMonth.from(LocalDate.parse(date)), Money.ZERO, Money.ZERO, Money.ZERO,
        ToClassifyLine(Money(999_999), 2), Money.ZERO, 1,
        IncomeTotals(Money(100_000), Money(200_000)), rows.toList(),
    )

    @Test fun `considera so limites definidos e liquido uma vez sem somar pago renda ou sem limite`() {
        // Novembro 9: 22 dias restantes e 7 até domingo. 1000 - (600 - 40) = 440 reais.
        val b = budget("2026-11-09", row("a", 100_000, 60_000, 4_000), row("b", null, 900_000))
        val result = spendingAllowance(b, LocalDate.parse("2026-11-09"))
        assertEquals(44_000L, result.remainingCents)
        assertEquals(2_000L, result.todayCents)
        assertEquals(14_000L, result.weekCents)
        assertEquals("2026-11-15", result.weekEnd)
        assertEquals(1, result.excludedCategories)
        assertEquals(2, result.unclassifiedCount)
        assertTrue(result.provisional)
    }

    @Test fun `ausencia e zero nao sao equivalentes e excesso nao vira margem negativa`() {
        val date = LocalDate.parse("2026-11-09")
        assertNull(spendingAllowance(budget(date.toString(), row("a", null, 100)), date).todayCents)
        val zero = spendingAllowance(budget(date.toString(), row("a", 0, 0)), date)
        assertEquals(0L, zero.todayCents)
        assertNull(zero.excessCents)
        val exceeded = spendingAllowance(budget(date.toString(), row("a", 0, 123)), date)
        assertEquals(123L, exceeded.excessCents)
        assertEquals(0L, exceeded.todayCents)
        assertEquals(0L, exceeded.weekCents)
    }

    @Test fun `divisao proporcional em centavos nao arredonda acima nem perde resto da semana`() {
        // 3 dias de mês; 2 até domingo. 10 / 3 = 3 por dia; 20 / 3 = 6 para a semana.
        val date = LocalDate.parse("2026-08-29")
        val result = spendingAllowance(budget(date.toString(), row("a", 10, 0)), date)
        assertEquals(3, result.daysRemaining)
        assertEquals(3L, result.todayCents)
        assertEquals(6L, result.weekCents)
        assertEquals("2026-08-30", result.weekEnd)
    }

    @Test fun `domingo ultimo dia e fevereiro bissexto respeitam periodo inclusivo`() {
        for ((date, days, weekDays) in listOf(Triple("2026-11-15", 16, 1), Triple("2026-12-31", 1, 1), Triple("2028-02-28", 2, 2))) {
            val value = spendingAllowance(budget(date, row("a", 3200, 0)), LocalDate.parse(date))
            assertEquals(days, value.daysRemaining)
            assertEquals(weekDays, value.weekDays)
            assertTrue(LocalDate.parse(value.weekEnd) <= YearMonth.from(LocalDate.parse(date)).atEndOfMonth())
        }
    }

    @Test fun `mes divergente e estouro nao fornecem numero incorreto`() {
        assertFailsWith<IllegalArgumentException> { spendingAllowance(budget("2026-11-09"), LocalDate.parse("2026-12-01")) }
        assertFailsWith<ArithmeticException> {
            spendingAllowance(budget("2026-11-09", row("a", Long.MAX_VALUE, 0), row("b", 1, 0)), LocalDate.parse("2026-11-09"))
        }
    }
}
