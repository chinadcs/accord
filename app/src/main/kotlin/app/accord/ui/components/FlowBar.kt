package app.accord.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.accord.ui.theme.Accord

/** Fração da largura do trilho coberta pela parte cheia e pela tracejada; a soma nunca passa de 1. */
internal data class FlowBarFractions(val solid: Float, val dashed: Float)

/**
 * Geometria apenas: o dinheiro exibido continua nos centavos exatos de quem chama. Sem escala positiva não há barra (0),
 * valores negativos não desenham e o excesso é cortado na largura do trilho. A parte tracejada começa onde a cheia termina.
 */
internal fun flowBarFractions(solidCents: Long, dashedCents: Long, scaleCents: Long): FlowBarFractions {
    if (scaleCents <= 0) return FlowBarFractions(0f, 0f)
    val solid = (solidCents.coerceAtLeast(0).toDouble() / scaleCents).coerceAtMost(1.0)
    val dashed = (dashedCents.coerceAtLeast(0).toDouble() / scaleCents).coerceAtMost(1.0 - solid)
    return FlowBarFractions(solid.toFloat(), dashed.toFloat())
}

/**
 * Percentual de uso arredondado ao inteiro mais próximo, sem mostrar 100% antes de atingir o limite (fica em 99%);
 * nulo sem limite positivo. Acima do limite, passa de 100%.
 */
internal fun usagePercent(usedCents: Long, limitCents: Long): Int? {
    if (limitCents <= 0) return null
    val used = usedCents.coerceAtLeast(0).toBigInteger()
    val limit = limitCents.toBigInteger()
    val rounded = (used * 200.toBigInteger() + limit) / (limit * 2.toBigInteger())
    val capped = if (used < limit) rounded.min(99.toBigInteger()) else rounded
    return capped.min(Int.MAX_VALUE.toBigInteger()).toInt()
}

/**
 * Cor da barra com contraste de gráfico (3:1, WCAG 1.4.11) contra o trilho e o cartão; algumas paletas claras têm
 * `primary`/`error` abaixo disso e, nelas, a barra usa [fallback] (texto semântico). O excesso nunca depende só da cor:
 * quem chama também mostra ícone e texto.
 */
internal fun flowBarInk(preferred: Color, track: Color, card: Color, fallback: Color): Color =
    if (chartContrast(preferred, track) >= 3f && chartContrast(preferred, card) >= 3f) preferred else fallback

private val FlowBarHeight = 8.dp
private val FlowBarStripe = 4.dp
private val FlowBarMinVisible = 2.dp

/**
 * Barra de leitura sem biblioteca de gráfico: trilho, parte cheia ([solidCents], o confirmado ou o usado) e, opcional,
 * parte tracejada ([dashedCents], o previsto ou o que ainda não foi confirmado) na mesma escala [scaleCents]. O padrão
 * (cheio × listrado) distingue as partes sem depender da cor. O leitor de tela recebe só [description], que deve trazer o
 * texto equivalente completo (valores, escala e estado); a barra não tem nós próprios. Sem animação com movimento reduzido.
 */
@Composable
fun FlowBar(
    solidCents: Long,
    scaleCents: Long,
    description: String,
    modifier: Modifier = Modifier,
    dashedCents: Long = 0,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = flowBarInk(color, trackColor, MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface)
    val target = flowBarFractions(solidCents, dashedCents, scaleCents)
    val spec = tween<Float>(durationMillis = Accord.motion.medium)
    val solid by animateFloatAsState(target.solid, spec, label = "flowbar-solid")
    val dashed by animateFloatAsState(target.dashed, spec, label = "flowbar-dashed")
    Box(
        modifier.fillMaxWidth().height(FlowBarHeight)
            .clip(RoundedCornerShape(FlowBarHeight / 2))
            .background(trackColor)
            .drawBehind {
                val minVisible = FlowBarMinVisible.toPx()
                val solidWidth = if (solid > 0f) maxOf(size.width * solid, minVisible) else 0f
                if (solidWidth > 0f) drawRect(ink, Offset.Zero, Size(solidWidth, size.height))
                val dashedWidth = if (dashed > 0f) minOf(maxOf(size.width * dashed, minVisible), size.width - solidWidth) else 0f
                if (dashedWidth > 0f) clipRect(solidWidth, 0f, solidWidth + dashedWidth, size.height) {
                    val step = FlowBarStripe.toPx()
                    var x = solidWidth - size.height
                    while (x < solidWidth + dashedWidth) {
                        drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                        x += step
                    }
                }
            }
            .clearAndSetSemantics { contentDescription = description },
    )
}
