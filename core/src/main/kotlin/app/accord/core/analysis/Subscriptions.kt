package app.accord.core.analysis

import app.accord.core.Money
import java.time.LocalDate

/**
 * Cobrança realizada de uma série recorrente (S6, telas 31 e 32): a ocorrência [number] foi efetivada em [date] pelo [amount] que de fato saiu,
 * que pode diferir do esperado. Só cobranças efetivadas contam; as previstas não entram na comparação.
 */
data class SubscriptionCharge(val recurrenceId: String, val number: Int, val date: LocalDate, val amount: Money) {
    /** ID do lançamento da cobrança (tela 08), no formato das ocorrências: `série:número`. */
    val entryId: String get() = "$recurrenceId:$number"
}

/** Comparação de preço entre cobranças realizadas da mesma série, que tem frequência única e moeda BRL. */
sealed interface ChargeComparison {
    /**
     * "Diferença observada" entre as duas últimas cobranças realizadas: [latest] − [previous]. É informação para avaliar, nunca prova de cobrança
     * indevida. [missingCycles] > 0 quando há ciclos sem cobrança registrada entre elas: a diferença continua visível, com aviso de cobertura.
     */
    data class Observed(
        val previous: SubscriptionCharge,
        val latest: SubscriptionCharge,
        val difference: Money,
        val missingCycles: Int,
    ) : ChargeComparison

    /** Menos de duas cobranças realizadas: a tela mostra só a previsão e explica a limitação, sem inventar variação. */
    data object InsufficientHistory : ChargeComparison
}

object SubscriptionAnalysis {
    /** Compara as duas cobranças realizadas de maior número; a ordem de [charges] não importa. */
    fun compare(charges: List<SubscriptionCharge>): ChargeComparison {
        val ordered = charges.sortedBy { it.number }
        if (ordered.size < 2) return ChargeComparison.InsufficientHistory
        val previous = ordered[ordered.size - 2]
        val latest = ordered.last()
        return ChargeComparison.Observed(previous, latest, latest.amount - previous.amount, latest.number - previous.number - 1)
    }
}
