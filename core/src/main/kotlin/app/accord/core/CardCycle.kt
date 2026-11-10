package app.accord.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Ciclo do cartão: dia de fechamento e dia de vencimento (padrão 8 e 15). Os dias sugerem a
 * fatura; não garantem a atribuição do emissor (plano §4.1). Um cartão por espaço no P0.
 *
 * Uma fatura é identificada pelo **mês de vencimento**. Dia que o mês não tem (31 em novembro,
 * 30 em fevereiro) usa o último dia do mês (decisão de 23/10/2026).
 */
data class CardCycle(val closingDay: Int = 8, val dueDay: Int = 15) {
    init {
        require(closingDay in 1..31) { "Dia de fechamento deve estar entre 1 e 31" }
        require(dueDay in 1..31) { "Dia de vencimento deve estar entre 1 e 31" }
    }

    /** Data de fechamento da fatura que fecha em [month]. */
    fun closingDate(month: YearMonth): LocalDate = month.atDayClamped(closingDay)

    /**
     * Data de vencimento da fatura que fecha em [closingMonth]: o primeiro vencimento
     * estritamente depois do fechamento (S1 §7.2).
     */
    fun dueDate(closingMonth: YearMonth): LocalDate {
        val sameMonth = closingMonth.atDayClamped(dueDay)
        return if (sameMonth.isAfter(closingDate(closingMonth))) sameMonth
        else closingMonth.plusMonths(1).atDayClamped(dueDay)
    }

    /** Fatura estimada para uma compra em [purchase], com o selo "a conferir" da janela. */
    fun assign(purchase: LocalDate): InvoiceAssignment {
        val month = YearMonth.from(purchase)
        // No dia do fechamento ou depois dele, a compra vai para a próxima fatura.
        val closingMonth = if (purchase.isBefore(closingDate(month))) month else month.plusMonths(1)
        val due = dueDate(closingMonth)
        return InvoiceAssignment(
            dueMonth = YearMonth.from(due),
            closingDate = closingDate(closingMonth),
            dueDate = due,
            toConfirm = inClosingWindow(purchase),
        )
    }

    /** Véspera, dia e dia seguinte ao fechamento; a janela pode atravessar a virada do mês. */
    private fun inClosingWindow(date: LocalDate): Boolean {
        val month = YearMonth.from(date)
        return listOf(month.minusMonths(1), month, month.plusMonths(1)).any { m ->
            val closing = closingDate(m)
            !date.isBefore(closing.minusDays(1)) && !date.isAfter(closing.plusDays(1))
        }
    }

    /** Data de vencimento da fatura identificada pelo mês de vencimento. */
    fun dueDateOfInvoice(dueMonth: YearMonth): LocalDate = dueMonth.atDayClamped(dueDay)

    /** Data de fechamento da fatura identificada pelo mês de vencimento. */
    fun closingDateOfInvoice(dueMonth: YearMonth): LocalDate {
        val closingMonth = listOf(dueMonth, dueMonth.minusMonths(1))
            .first { YearMonth.from(dueDate(it)) == dueMonth }
        return closingDate(closingMonth)
    }

    /** "Em aberto" até o dia de fechamento; depois dele, "fechada (estimada)". Só exibição. */
    fun phase(dueMonth: YearMonth, today: LocalDate): InvoicePhase =
        if (today.isBefore(closingDateOfInvoice(dueMonth))) InvoicePhase.Open else InvoicePhase.ClosedEstimated
}

data class InvoiceAssignment(
    val dueMonth: YearMonth,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val toConfirm: Boolean,
)

enum class InvoicePhase { Open, ClosedEstimated }

private fun YearMonth.atDayClamped(day: Int): LocalDate = atDay(minOf(day, lengthOfMonth()))

object Installments {
    /**
     * Divide o [total] em [count] parcelas: cada uma recebe o total dividido, truncado em
     * centavos, e a diferença vai na última. R$ 100 em 3× = 33,33 + 33,33 + 33,34.
     */
    fun splitTotal(total: Money, count: Int): List<Money> {
        require(count >= 1) { "Quantidade de parcelas deve ser ao menos 1" }
        val base = total.cents / count
        require(base >= 1) { "Cada parcela precisa ter ao menos 1 centavo" }
        val remainder = total.cents - base * count
        return List(count) { i -> Money(if (i == count - 1) base + remainder else base) }
    }

    /**
     * Se uma linha de [line] centavos pode ser a parcela [number] de um total de [total] em [count] parcelas. O banco põe os centavos que sobram numa
     * parcela (em geral a primeira) e o app na última (R$ 100 em 3× = 33,33 + 33,33 + 33,34), então a linha do documento pode diferir do cronograma
     * por centavos de arredondamento: até [count] centavos, nunca mais que isso (accord-qid0, ADR 0076).
     */
    fun lineFits(total: Long, count: Int, number: Int, line: Long): Boolean {
        if (count < 1 || number !in 1..count || total / count < 1) return false
        return kotlin.math.abs(splitTotal(Money(total), count)[number - 1].cents - line) <= count
    }

    /** Valor da parcela informado: o total é parcela × quantidade. */
    fun fromEach(each: Money, count: Int): List<Money> {
        require(count >= 1) { "Quantidade de parcelas deve ser ao menos 1" }
        require(each.cents >= 1) { "Cada parcela precisa ter ao menos 1 centavo" }
        return List(count) { each }
    }

    /** Faturas (mês de vencimento) das parcelas: uma por mês a partir da 1ª. */
    fun schedule(first: YearMonth, count: Int): List<YearMonth> = List(count) { first.plusMonths(it.toLong()) }

    /**
     * Corrigir a fatura da 1ª parcela desloca todas as seguintes. Bloqueia se o deslocamento
     * tocar fatura quitada ou se alguma parcela estiver cancelada (decisão de 23/10/2026);
     * nada é movido nesse caso.
     */
    fun shiftFirst(
        oldFirst: YearMonth,
        count: Int,
        newFirst: YearMonth,
        paidMonths: Set<YearMonth>,
        cancelledNumbers: Set<Int>,
    ): ShiftResult {
        val before = schedule(oldFirst, count)
        if (oldFirst == newFirst) return ShiftResult.Shifted(before, before)
        val after = schedule(newFirst, count)
        val blockedPaid = (before + after).toSortedSet().filter { it in paidMonths }
        val blockedCancelled = cancelledNumbers.filter { it in 1..count }.sorted()
        if (blockedPaid.isNotEmpty() || blockedCancelled.isNotEmpty()) {
            return ShiftResult.Blocked(blockedPaid, blockedCancelled)
        }
        return ShiftResult.Shifted(before, after)
    }
}

sealed interface ShiftResult {
    data class Shifted(val before: List<YearMonth>, val after: List<YearMonth>) : ShiftResult
    data class Blocked(val paidMonths: List<YearMonth>, val cancelledNumbers: List<Int>) : ShiftResult
}
