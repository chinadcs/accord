package app.accord.ui.budget

import app.accord.core.LimitView
import app.accord.core.Money

/** Estados da barra de categoria (tokens §7). Nenhum depende só da cor: cada um tem ícone e texto. */
enum class BarState { WithinLimit, Attention, Excess, NoLimit }

/**
 * Proposta: a barra entra em atenção a partir de 80% do limite. A regra de "perto do limite" ainda
 * não está definida nas specs (alertas são S5); ajustar aqui quando for decidida.
 */
const val ATTENTION_FRACTION = 0.8

fun barState(limit: LimitView): BarState = when {
    limit.total == null -> BarState.NoLimit
    limit.excess != null -> BarState.Excess
    (limit.usedFraction ?: 0.0) >= ATTENTION_FRACTION -> BarState.Attention
    else -> BarState.WithinLimit
}

/** Quanto da barra é preenchido, de 0 a 1. Em excesso a barra enche e o marcador mostra o limite. */
fun barFill(limit: LimitView): Float = when (barState(limit)) {
    BarState.NoLimit -> 0f
    BarState.Excess -> 1f
    else -> (limit.usedFraction ?: 0.0).coerceIn(0.0, 1.0).toFloat()
}

/** Posição do limite quando há excesso (limite ÷ líquido); nula fora do excesso. */
fun excessMarker(limit: LimitView, net: Money): Float? {
    val total = limit.total ?: return null
    if (limit.excess == null || net.cents <= 0) return null
    return (total.cents.toDouble() / net.cents).coerceIn(0.0, 1.0).toFloat()
}

/** Percentual inteiro exibido; nulo quando não faz sentido (sem limite ou limite zero). */
fun percentLabel(limit: LimitView): Int? = limit.usedFraction?.let { Math.round(it * 100).toInt().coerceAtLeast(0) }
