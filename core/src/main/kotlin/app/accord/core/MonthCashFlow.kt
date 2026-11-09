package app.accord.core

import app.accord.core.forecast.Confidence
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.MonthBudget

/**
 * Fluxo do mês projetado até o fim dele: renda do mês contra o que já está comprometido e o ritmo estimado dos gastos variáveis.
 * Leitura de [MonthBudget] e [MonthForecast]; nada é gravado e não há saldo bancário: renda prevista não é saldo confirmado,
 * por isso o resultado vem em duas leituras, só com a renda já recebida e com toda a renda prevista.
 */
data class MonthCashFlow(
    val receivedCents: Long,
    val toReceiveCents: Long,
    /** Comprometido do mês: categorias (líquidas de estorno confirmado) mais "a classificar". Já inclui o pago. */
    val knownCents: Long,
    /** Subconjunto de [knownCents]; nunca somado a ele. */
    val paidCents: Long,
    /** Parte de [knownCents] sem categoria. */
    val toClassifyCents: Long,
    /** Ritmo estimado dos gastos variáveis dos dias que faltam; categorias sem estimativa não entram nem valem zero. */
    val estimatedCents: Long,
    val categoriesWithoutEstimate: Int,
    /** Verdadeiro se há vínculo de fatura "a conferir" ou se a previsão tem ressalvas de confiança. */
    val provisional: Boolean,
) {
    val projectedSpendCents: Long get() = knownCents + estimatedCents

    /** Resultado se só a renda já recebida chegar. */
    val resultWithReceivedCents: Long get() = receivedCents - projectedSpendCents

    /** Resultado se toda a renda prevista ainda a receber também chegar. */
    val resultWithExpectedCents: Long get() = receivedCents + toReceiveCents - projectedSpendCents
}

fun monthCashFlow(budget: MonthBudget, forecast: MonthForecast): MonthCashFlow {
    require(budget.month == forecast.month) { "Orçamento e previsão precisam ser do mesmo mês" }
    val toClassify = budget.toClassify.amount.cents
    val estimates = forecast.categories.map { it.estimate }
    return MonthCashFlow(
        receivedCents = budget.income.received.cents,
        toReceiveCents = budget.income.toReceive.cents,
        knownCents = forecast.categories.sumOf { it.knownCents } + toClassify,
        paidCents = budget.paid.cents,
        toClassifyCents = toClassify,
        estimatedCents = estimates.sumOf { (it as? Estimate.Value)?.cents ?: 0L },
        categoriesWithoutEstimate = estimates.count { it is Estimate.Unavailable },
        provisional = budget.invoiceLinksToConfirm > 0 || forecast.categories.any { it.confidence is Confidence.Low },
    )
}
