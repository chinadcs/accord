package app.accord.core

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Relógio do orçamento. A data atual é sempre a do fuso de referência (America/Sao_Paulo),
 * nunca a de UTC. Nos testes, injeta-se um [Clock] fixo.
 */
class BudgetClock private constructor(private val clock: Clock) {
    fun today(): LocalDate = LocalDate.now(clock)

    /** O instante atual, para a janela dos avisos (S5); nos testes, o do [Clock] fixo. */
    fun now(): java.time.Instant = java.time.Instant.now(clock)
    fun currentMonth(): YearMonth = YearMonth.from(today())

    companion object {
        val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

        fun system() = of(Clock.systemUTC())

        /** Qualquer [Clock] serve; o fuso é reaplicado aqui para não depender do que o chamador usou. */
        fun of(clock: Clock) = BudgetClock(clock.withZone(ZONE))
    }
}

object BudgetMonths {
    /**
     * Mês do orçamento de um item fora do cartão (boleto, ocorrência recorrente, renda):
     * o mês previsto enquanto não houver data efetiva; depois, só o mês da data efetiva.
     * As duas datas continuam guardadas pelo chamador.
     */
    fun forScheduled(scheduled: LocalDate, effective: LocalDate?): YearMonth =
        YearMonth.from(effective ?: scheduled)
}

enum class Frequency { Monthly, Annual }

/**
 * Sequência original de uma série recorrente, sem término. A ocorrência de número `n`
 * (1 = a primeira) mantém a numeração mesmo que outras sejam excluídas ou corrigidas.
 * Dia que o mês não tem usa o último dia, sem perder o dia original (31 → 30/11 → 31/12).
 */
data class Recurrence(val anchor: LocalDate, val frequency: Frequency) {
    private val anchorMonth = YearMonth.from(anchor)

    fun dateOf(number: Int): LocalDate {
        require(number >= 1) { "Número da ocorrência começa em 1" }
        val steps = (number - 1).toLong()
        val month = when (frequency) {
            Frequency.Monthly -> anchorMonth.plusMonths(steps)
            Frequency.Annual -> anchorMonth.plusYears(steps)
        }
        return month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth()))
    }

    /** Menor número de ocorrência cuja data prevista é igual ou posterior a [date] (1 se [date] é anterior à âncora). */
    fun firstNumberOnOrAfter(date: LocalDate): Int {
        if (!date.isAfter(anchor)) return 1
        val months = anchorMonth.until(YearMonth.from(date), java.time.temporal.ChronoUnit.MONTHS)
        val estimate = when (frequency) {
            Frequency.Monthly -> months + 1
            Frequency.Annual -> months / 12 + 1
        }
        // Começa uma ocorrência antes da estimativa: o dia do mês pode ficar antes ou depois de [date].
        var number = maxOf(1L, estimate - 1).toInt()
        while (dateOf(number).isBefore(date)) number++
        return number
    }

    /** Número da ocorrência que cai em [month] segundo a data prevista, ou null se não houver. */
    fun numberIn(month: YearMonth): Int? {
        val monthsBetween = anchorMonth.until(month, java.time.temporal.ChronoUnit.MONTHS)
        if (monthsBetween < 0) return null
        return when (frequency) {
            Frequency.Monthly -> (monthsBetween + 1).toInt()
            Frequency.Annual -> if (monthsBetween % 12 == 0L) (monthsBetween / 12 + 1).toInt() else null
        }
    }
}
