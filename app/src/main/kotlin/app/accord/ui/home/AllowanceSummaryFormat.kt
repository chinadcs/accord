package app.accord.ui.home

import app.accord.core.SpendingAllowance
import app.accord.ui.components.usagePercent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Uso do orçamento considerado: o líquido gasto e a soma dos limites das categorias com limite, com o percentual do texto. */
internal data class AllowanceUsage(val usedCents: Long, val limitCents: Long, val percent: Int)

/**
 * Nulo quando não há como mostrar o uso sem inventar número: sem limite total, sem gasto líquido (o estouro aritmético
 * do core vira "indisponível") ou limite total zero. Nunca altera a margem do ADR 0068; só apresenta os mesmos números.
 */
internal fun allowanceUsage(value: SpendingAllowance): AllowanceUsage? {
    val limit = value.limitTotalCents ?: return null
    val used = value.netSpentCents ?: return null
    val percent = usagePercent(used, limit) ?: return null
    return AllowanceUsage(used, limit, percent)
}

/** A janela semanal acaba no domingo; antes disso, só quando o mês termina antes dele. */
internal fun weekEndsOnSunday(value: SpendingAllowance): Boolean = LocalDate.parse(value.weekEnd).dayOfWeek == DayOfWeek.SUNDAY

/** `dd/MM` do último dia da janela semanal. */
internal fun weekEndShort(value: SpendingAllowance): String = LocalDate.parse(value.weekEnd).format(DateTimeFormatter.ofPattern("dd/MM"))

/** Mês de [isoDate] por extenso e em minúsculas, como "novembro" ("Pelo orçamento de novembro"). */
internal fun allowanceMonthName(isoDate: String): String =
    LocalDate.parse(isoDate).month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR")).lowercase(Locale.forLanguageTag("pt-BR"))
