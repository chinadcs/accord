package app.accord.core

import app.accord.core.space.MonthBudget
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/** Cache de leitura compartilhado pelo Início e Chat. Datas inclusivas, em ISO. ADR 0068. */
@Serializable
data class SpendingAllowance(
    val date: String,
    val weekEnd: String,
    val daysRemaining: Int,
    val weekDays: Int,
    val limitedCategories: Int,
    val excludedCategories: Int,
    val unclassifiedCount: Int,
    val remainingCents: Long?,
    val todayCents: Long?,
    val weekCents: Long?,
    val excessCents: Long?,
    val provisional: Boolean,
    /** Soma dos limites das categorias com limite (a base de "remaining"); nulo sem limites. Só leitura para a barra de uso. */
    val limitTotalCents: Long? = null,
    /** Gasto líquido das mesmas categorias, já com compromissos conhecidos, estornos e sem somar pago; nulo sem limites. */
    val netSpentCents: Long? = null,
)

/** Só limites principais definidos. O líquido mensal já inclui os compromissos conhecidos. */
fun spendingAllowance(budget: MonthBudget, today: LocalDate): SpendingAllowance {
    require(budget.month == YearMonth.from(today))
    val included = budget.categories.filter { it.limit.total != null }
    val days = budget.month.lengthOfMonth() - today.dayOfMonth + 1
    val weekDays = minOf(8 - today.dayOfWeek.value, days)
    val remaining = if (included.isEmpty()) null else included.fold(Money.ZERO) { sum, row ->
        sum + (row.limit.total!! - row.net)
    }.cents
    // Só apresentação: não entra em "remaining". Estouro aritmético vira "indisponível", nunca altera a margem.
    val limitTotal = if (included.isEmpty()) null else runCatching { included.map { it.limit.total!! }.sum().cents }.getOrNull()
    val netSpent = if (included.isEmpty()) null else runCatching { included.map { it.net }.sum().cents }.getOrNull()
    val available = remaining?.coerceAtLeast(0)
    // Quotient + remainder avoids overflow without rounding above the available amount.
    val week = available?.let { (it / days) * weekDays + (it % days) * weekDays / days }
    return SpendingAllowance(
        today.toString(), today.plusDays(weekDays - 1L).toString(), days, weekDays,
        included.size, budget.categories.size - included.size, budget.toClassify.count,
        remaining, available?.div(days), week,
        remaining?.takeIf { it < 0 }?.let(Math::negateExact), budget.invoiceLinksToConfirm > 0,
        limitTotal, netSpent,
    )
}
