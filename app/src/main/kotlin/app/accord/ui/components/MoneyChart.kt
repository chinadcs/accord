package app.accord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.core.Money
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.himanshoe.charty.bar.BarChart
import com.himanshoe.charty.bar.config.BarChartConfig
import com.himanshoe.charty.bar.data.BarData
import com.himanshoe.charty.color.ChartyColor
import com.himanshoe.charty.common.config.Animation
import com.himanshoe.charty.common.config.ChartInteractionConfig
import com.himanshoe.charty.common.config.ChartScaffoldConfig
import com.himanshoe.charty.common.config.ReferenceLineConfig
import com.himanshoe.charty.common.config.ReferenceLineStrokeStyle
import com.himanshoe.charty.common.tooltip.ChartTooltip

/** Money stays exact here. Float is used only for the geometry, never to recover displayed money. */
data class MoneyChartValue(
    val label: String,
    val amount: Money?,
    val unavailable: String = "",
    val projected: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

internal fun chartContrast(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

/**
 * Texto na cor semântica [preferred] só se ela alcança 4,5:1 sobre [background]; senão, [fallback] (o texto do tema). Algumas
 * paletas claras têm `error` abaixo disso sobre os cartões. O significado nunca depende só da cor: quem chama também usa ícone e palavra.
 */
internal fun textInk(preferred: Color, background: Color, fallback: Color): Color =
    if (chartContrast(preferred, background) >= 4.5f) preferred else fallback

/** Some appearance palettes have pastel dataNeutral. Use the semantic text color when needed. */
internal fun chartInk(preferred: Color, background: Color, fallback: Color): Color =
    if (chartContrast(preferred, background) >= 3f) preferred else fallback

/**
 * One source for bars and their always-visible, numbered textual alternative. Missing values have
 * no bar; real zero stays zero. No animation (including reduced motion), tooltip or rounded money.
 * Charty's English/Float semantics are replaced by the exact pt-BR list below the decorative plot.
 */
@Composable
fun MoneyChart(
    title: String?, criterion: String, values: List<MoneyChartValue>, modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    if (values.isEmpty()) return
    val text = MaterialTheme.colorScheme.onSurface
    val neutral = chartInk(Accord.colors.dataNeutral, background, text)
    val bars = values.mapIndexedNotNull { index, value ->
        value.amount?.let {
            BarData(
                label = (index + 1).toString(), value = it.cents.toFloat() / 100f,
                color = ChartyColor.Solid(if (value.projected) text else neutral),
            )
        }
    }
    Column(modifier.fillMaxWidth().background(background).padding(vertical = AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        // Dentro de uma seção recolhível o título já é o cabeçalho dela.
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        Text(criterion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bars.any { it.value != 0f }) {
            Text(stringResource(R.string.chart_order), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                // Keep every category, even on narrow screens; long names live in the wrapping list.
                val plotWidth = maxOf(maxWidth, (bars.size * 64).dp)
                // Charty 3.2.0 uses 20 physical pixels at each side with labels off.
                val gutter = with(LocalDensity.current) { 20f.toDp() }
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(plotWidth)) {
                        BarChart(
                            data = { bars },
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            color = ChartyColor.Solid(neutral),
                            barConfig = BarChartConfig(
                                animation = Animation.Disabled, animateValueChanges = false,
                                showDataLabels = false,
                                referenceLine = ReferenceLineConfig(value = 0f, color = ChartyColor.Solid(neutral), strokeStyle = ReferenceLineStrokeStyle.SOLID, showValueInLabelWhenNoText = false),
                            ),
                            scaffoldConfig = ChartScaffoldConfig(
                                showLabels = false, showGrid = false,
                                axisColor = ChartyColor.Solid(neutral),
                                labelTextStyle = MaterialTheme.typography.bodySmall.copy(color = text),
                            ),
                            interactionConfig = ChartInteractionConfig(accessibilityDescription = ""),
                            tooltip = ChartTooltip.none(),
                        )
                        // Labels outside the plot cannot be clipped at extrema or lose contrast on bars.
                        Row(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                            bars.forEach { bar ->
                                Text(bar.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = text)
                            }
                        }
                    }
                }
            }
        }
        values.forEachIndexed { index, value ->
            val line = stringResource(R.string.chart_value, index + 1, value.label, value.amount?.format() ?: value.unavailable)
            val rowModifier = value.onClick?.let { action ->
                Modifier.heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button, onClick = action)
            } ?: Modifier
            Text(
                line, style = MaterialTheme.typography.bodyLarge,
                modifier = rowModifier.fillMaxWidth().padding(vertical = AccordSpacing.s1),
            )
        }
    }
}
