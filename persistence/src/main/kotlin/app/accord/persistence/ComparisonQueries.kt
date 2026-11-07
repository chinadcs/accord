package app.accord.persistence

import app.accord.core.Money
import app.accord.core.analysis.ComparisonGroupKey
import app.accord.core.analysis.PeriodChange
import app.accord.core.analysis.PeriodComparisons
import app.accord.core.analysis.SideValues
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonPeriod
import app.accord.core.space.ComparisonSide
import app.accord.core.space.DateCriterion
import app.accord.core.space.MonthBudget
import app.accord.core.space.PeriodComparison
import app.accord.core.space.PurchaseActivity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Comparações históricas (S6, tela 33). Não calcula dinheiro novo: cada lado vem da mesma consulta que as outras telas usam (o comprometido do
 * orçamento ou a atividade de compras), de modo que a tela e o Chat nunca divergem. Aqui só se agrega, se alinha e se compara.
 */
internal class ComparisonQueries(
    private val budget: suspend (YearMonth) -> MonthBudget,
    private val activity: suspend (LocalDate, LocalDate) -> PurchaseActivity,
    private val today: () -> LocalDate,
) {
    private class Loaded(val values: SideValues, val provisionalLinks: Int, val toClassify: Money?)

    suspend fun compare(
        criterion: DateCriterion,
        grouping: ComparisonGrouping,
        a: ComparisonPeriod,
        b: ComparisonPeriod,
    ): PeriodComparison {
        if (criterion == DateCriterion.BudgetMonth) {
            require(a.isWholeMonths() && b.isWholeMonths()) { "No critério do orçamento os períodos são meses inteiros" }
        }
        // Cada período é lido sozinho: se um falhar, o outro continua e a falha fica identificada, nunca vira zero.
        val left = runCatching { load(criterion, grouping, a) }.getOrNull()
        val right = runCatching { load(criterion, grouping, b) }.getOrNull()
        val rows = PeriodComparisons.rows(left?.values, right?.values)
        val totalA = PeriodComparisons.total(left?.values)
        val totalB = PeriodComparisons.total(right?.values)
        return PeriodComparison(
            criterion = criterion, grouping = grouping,
            a = side(a, left), b = side(b, right),
            differentDuration = a.days != b.days,
            rows = rows, totalA = totalA, totalB = totalB, totalChange = PeriodChange.of(totalA, totalB),
        )
    }

    private fun side(period: ComparisonPeriod, loaded: Loaded?) = ComparisonSide(
        period = period,
        failed = loaded == null,
        inProgressOrFuture = !period.untilExclusive.minusDays(1).isBefore(today()),
        provisionalLinks = loaded?.provisionalLinks ?: 0,
        toClassify = loaded?.toClassify,
    )

    private suspend fun load(criterion: DateCriterion, grouping: ComparisonGrouping, p: ComparisonPeriod): Loaded =
        if (criterion == DateCriterion.BudgetMonth) loadBudget(grouping, p) else loadActivity(grouping, p)

    private fun ComparisonPeriod.isWholeMonths() = from.dayOfMonth == 1 && untilExclusive.dayOfMonth == 1

    // ---- critério do orçamento: o mesmo comprometido das telas 05 e 06, somado mês a mês ----

    private suspend fun loadBudget(grouping: ComparisonGrouping, p: ComparisonPeriod): Loaded {
        val values = mutableMapOf<ComparisonGroupKey, Money>()
        fun add(key: ComparisonGroupKey, amount: Money) {
            values[key] = (values[key] ?: Money.ZERO) + amount
        }
        var links = 0
        var toClassify = Money.ZERO
        var toClassifyCount = 0
        var hasData = false
        generateSequence(YearMonth.from(p.from)) { it.plusMonths(1) }.takeWhile { it.atDay(1).isBefore(p.untilExclusive) }.forEach { month ->
            val budget = budget(month)
            links += budget.invoiceLinksToConfirm
            toClassify += budget.toClassify.amount
            toClassifyCount += budget.toClassify.count
            if (budget.committed.cents != 0L || budget.credits.cents != 0L || budget.toClassify.count > 0) hasData = true
            budget.categories.forEach { c ->
                when (grouping) {
                    ComparisonGrouping.Category -> add(ComparisonGroupKey(c.categoryId), c.net)
                    ComparisonGrouping.Subcategory -> {
                        // Subcategoria líquida de estornos; "Sem subcategoria" é o resto, para a soma fechar com a categoria.
                        val subs = c.limit.subs.values
                        subs.forEach { add(ComparisonGroupKey(c.categoryId, it.id), it.spent) }
                        add(ComparisonGroupKey(c.categoryId, null), c.net - subs.map { it.spent }.fold(Money.ZERO) { acc, m -> acc + m })
                    }
                }
            }
        }
        if (toClassifyCount > 0 || toClassify.cents != 0L) add(ComparisonGroupKey(null), toClassify)
        return Loaded(SideValues(hasData, values), links, toClassify.takeIf { toClassifyCount > 0 })
    }

    // ---- critério da data da compra: a atividade de compras do período ----

    private suspend fun loadActivity(grouping: ComparisonGrouping, p: ComparisonPeriod): Loaded {
        val activity = activity(p.from, p.untilExclusive)
        val values = mutableMapOf<ComparisonGroupKey, Money>()
        activity.groups.forEach { g ->
            when (grouping) {
                ComparisonGrouping.Category -> values[ComparisonGroupKey(g.categoryId)] = g.line.total
                ComparisonGrouping.Subcategory ->
                    g.subgroups.forEach { values[ComparisonGroupKey(g.categoryId, it.subcategoryId)] = it.line.total }
            }
        }
        activity.toClassify?.let { values[ComparisonGroupKey(null)] = it.total }
        return Loaded(SideValues(activity.total.count > 0 || activity.toClassify != null, values), 0, activity.toClassify?.total)
    }
}
