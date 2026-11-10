package app.accord.ui.home

import app.accord.core.MonthCashFlow

/**
 * Geometria e texto-base das duas barras da "Previsão até o fim do mês" (ADR 0078, decisão 4). Só reapresenta os números de
 * [MonthCashFlow] sem somar nada novo: o pago é subconjunto do comprometido e nunca entra aqui.
 *
 * [inflowCents] é a renda do mês (recebida mais prevista a receber); [outflowCents] são os gastos do mês (comprometidos mais
 * variáveis estimados). As duas barras usam a mesma [scaleCents], o maior dos dois totais, para o comprimento comparar.
 */
internal data class ForecastBars(val inflowCents: Long, val outflowCents: Long, val scaleCents: Long) {
    /** Sem renda não há barra de entradas; o mesmo vale para saídas. Total zero ou negativo não desenha trilho vazio. */
    val showInflow: Boolean get() = inflowCents > 0 && scaleCents > 0
    val showOutflow: Boolean get() = outflowCents > 0 && scaleCents > 0
}

internal fun forecastBars(value: MonthCashFlow): ForecastBars {
    val inflow = value.receivedCents + value.toReceiveCents
    val outflow = value.projectedSpendCents
    return ForecastBars(inflow, outflow, maxOf(inflow, outflow, 0L))
}

/** Sem renda nem gasto previsto não há o que prever: a Início diz isso em vez de "Sobra R$ 0,00", que seria um falso resultado. */
internal fun hasForecastData(value: MonthCashFlow): Boolean = value.receivedCents + value.toReceiveCents != 0L || value.projectedSpendCents != 0L

/** Como a palavra do resultado é escolhida: nunca só o sinal nem só a cor. */
internal enum class ForecastOutcome {
    Surplus, Shortfall, Even;

    companion object {
        fun of(cents: Long): ForecastOutcome = when {
            cents > 0 -> Surplus
            cents < 0 -> Shortfall
            else -> Even
        }
    }
}
