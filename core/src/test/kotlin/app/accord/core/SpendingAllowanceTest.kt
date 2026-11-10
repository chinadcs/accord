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

    @Test fun `expoe limite total e gasto liquido sem mudar a margem`() {
        // Limites 1000 + 500 = 1500; liquidos (600 - 40) + 100 = 660; a linha sem limite fica de fora.
        val date = LocalDate.parse("2026-11-09")
        val b = budget(date.toString(), row("a", 100_000, 60_000, 4_000), row("b", 50_000, 10_000), row("c", null, 900_000))
        val result = spendingAllowance(b, date)
        assertEquals(150_000L, result.limitTotalCents)
        assertEquals(66_000L, result.netSpentCents)
        assertEquals(84_000L, result.remainingCents)
        assertEquals(result.limitTotalCents!! - result.netSpentCents!!, result.remainingCents)
    }

    @Test fun `sem limites os dois valores ficam ausentes e nao zero`() {
        val date = LocalDate.parse("2026-11-09")
        val result = spendingAllowance(budget(date.toString(), row("a", null, 100)), date)
        assertNull(result.limitTotalCents)
        assertNull(result.netSpentCents)
        assertNull(result.remainingCents)
        val none = spendingAllowance(budget(date.toString()), date)
        assertNull(none.limitTotalCents)
        assertNull(none.netSpentCents)
    }

    @Test fun `excesso e limite zero preservam os valores reais`() {
        val date = LocalDate.parse("2026-11-09")
        val exceeded = spendingAllowance(budget(date.toString(), row("a", 10_000, 12_345)), date)
        assertEquals(10_000L, exceeded.limitTotalCents)
        assertEquals(12_345L, exceeded.netSpentCents)
        assertEquals(2_345L, exceeded.excessCents)
        val zero = spendingAllowance(budget(date.toString(), row("a", 0, 0)), date)
        assertEquals(0L, zero.limitTotalCents)
        assertEquals(0L, zero.netSpentCents)
        val zeroSpent = spendingAllowance(budget(date.toString(), row("a", 0, 123)), date)
        assertEquals(0L, zeroSpent.limitTotalCents)
        assertEquals(123L, zeroSpent.netSpentCents)
    }

    @Test fun `estorno reduz o liquido considerado`() {
        val date = LocalDate.parse("2026-11-09")
        val result = spendingAllowance(budget(date.toString(), row("a", 50_000, 20_000, 5_000)), date)
        assertEquals(15_000L, result.netSpentCents)
        assertEquals(35_000L, result.remainingCents)
    }
}
