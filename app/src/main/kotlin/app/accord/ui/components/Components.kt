package app.accord.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.budget.BarState
import app.accord.core.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

/** Ícone Lucide pelo nome (`utensils`, `shopping-bag`); nome desconhecido usa `tag`, o das categorias do casal. */
@Composable
@DrawableRes
fun lucideRes(key: String): Int {
    val context = LocalContext.current
    return remember(key) {
        val id = context.resources.getIdentifier("lucide_ic_" + key.replace('-', '_'), "drawable", context.packageName)
        if (id != 0) id else Lucide.drawable.lucide_ic_tag
    }
}

/** Ícone decorativo para o TalkBack: o nome ao lado é o que se lê (docs/icones-categorias.md §3). */
@Composable
fun DecorativeIcon(@DrawableRes res: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(painterResource(res), contentDescription = null, modifier = modifier.size(24.dp), tint = tint)
}

/** Seletor de mês: o mês escolhido controla toda a tela e é anunciado quando muda. */
@Composable
fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.month_previous))
        }
        Text(
            month.label(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onNext, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.month_next))
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

/**
 * Barra de categoria (tokens §7). O preenchimento nunca é o único sinal: o texto ao lado diz o
 * estado, e a barra é oculta do TalkBack porque a linha inteira traz o equivalente textual.
 */
@Composable
fun BudgetBar(fill: Float, state: BarState, marker: Float?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = when (state) {
        BarState.Excess -> MaterialTheme.colorScheme.error
        BarState.Attention -> Accord.colors.attentionFill
        else -> MaterialTheme.colorScheme.primary
    }
    val markerColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { }) {
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(track, size = size, cornerRadius = radius)
        if (state != BarState.NoLimit && fill > 0f) {
            drawRoundRect(fillColor, size = Size(size.width * fill, size.height), cornerRadius = radius)
        }
        if (marker != null) {
            // Marcador do limite, 2 dp, quando há excesso.
            drawRect(markerColor, topLeft = Offset(size.width * marker - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
        }
    }
}

/** Texto ampliado a partir desta escala: valores e rótulos empilham em vez de quebrar palavras. */
private const val STACK_FONT_SCALE = 1.3f

/**
 * Rótulo à esquerda e valor à direita. Com texto ampliado o valor desce para a linha de baixo,
 * para nada ser cortado nem quebrado no meio da palavra (alvo de acessibilidade das specs).
 */
@Composable
fun AdaptiveRow(modifier: Modifier = Modifier, start: @Composable () -> Unit, end: @Composable () -> Unit) {
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            start()
            end()
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { start() }
            Box(Modifier.padding(start = AccordSpacing.s2)) { end() }
        }
    }
}

/**
 * Atualiza os dados sempre que a tela volta a ficar ativa, por exemplo depois de excluir ou editar
 * um lançamento no detalhe. A composição da tela é refeita ao voltar, então não dá para distinguir
 * "primeira vez" de "voltei"; ler de novo na abertura é barato e o resultado anterior continua
 * visível até o novo chegar.
 */
@Composable
fun RefreshOnResume(onRefresh: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefresh() }
}
