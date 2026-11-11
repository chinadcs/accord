package app.accord.ui

import app.accord.core.GENERAL_BUDGET_ID
import app.accord.core.LimitSetting
import app.accord.core.LimitView
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.core.space.CategoryLimit
import app.accord.core.space.GeneralBudgetView
import app.accord.core.space.IncomeTotals
import app.accord.core.space.MonthBudget
import app.accord.core.space.ToClassifyLine
import app.accord.ui.budget.BudgetData
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetDataStateTest {
    private val month = YearMonth.of(2026, 12)

    private fun category(limit: Long?, gross: Long = 0) = CategoryBudget(
        "c", Money(gross), Money.ZERO, Money(gross), Money.ZERO, emptyMap(),
        LimitView.of(LimitSetting(limit?.let(::Money), emptyMap()), Money(gross), emptyMap()),
    )

    private fun general(total: Long?): GeneralBudgetView {
        val setting = LimitSetting(total?.let(::Money), emptyMap())
        return GeneralBudgetView(CategoryLimit(GENERAL_BUDGET_ID, setting, null, false, emptyList()), Money.ZERO, LimitView.of(setting, Money.ZERO, emptyMap()))
    }

    private fun data(limit: Long?, general: Long?, gross: Long = 0) = BudgetData(
        MonthBudget(
            month, Money(gross), Money(limit ?: 0), Money.ZERO, ToClassifyLine(Money.ZERO, 0), Money.ZERO, 0,
            IncomeTotals(Money.ZERO, Money.ZERO), listOf(category(limit, gross)), general(general),
        ),
        emptyMap(),
    )

    @Test fun `sem limite nem geral e sem lancamentos e vazio`() {
        val d = data(null, null)
        assertEquals(true, d.isEmpty)
        assertEquals(false, d.hasLimits)
    }

    @Test fun `mes sem lancamentos com limite vigente nao e vazio`() {
        val d = data(50_000, null)
        assertEquals(false, d.isEmpty)
        assertEquals(true, d.noActivity)
        assertEquals(true, d.hasLimits)
    }

    @Test fun `mes sem lancamentos so com orcamento geral nao e vazio e nao tem limite de categoria`() {
        val d = data(null, 300_000)
        assertEquals(false, d.isEmpty)
        assertEquals(false, d.hasLimits)
    }

    @Test fun `limite zero definido conta como limite`() {
        val d = data(0, null)
        assertEquals(true, d.hasLimits)
        assertEquals(false, d.isEmpty)
    }

    @Test fun `com lancamento nunca e vazio mesmo sem limite`() {
        val d = data(null, null, gross = 1_000)
        assertEquals(false, d.isEmpty)
        assertEquals(false, d.noActivity)
    }
}
