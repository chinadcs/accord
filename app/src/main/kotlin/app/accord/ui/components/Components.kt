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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.budget.BarState
import app.accord.core.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onCurrent: (() -> Unit)? = null, // não nulo = mês diferente do atual: mostra o atalho para voltar a ele
    onPick: ((YearMonth) -> Unit)? = null, // não nulo = tocar no mês abre a grade dos 12 meses do ano exibido
    currentMonth: YearMonth = YearMonth.now(),
) {
    var picking by remember { mutableStateOf(false) }
    val labelFocus = remember { FocusRequester() }
    if (picking && onPick != null) MonthGrid(month, currentMonth, onPick = { picking = false; onPick(it) }, onDismiss = { picking = false; labelFocus.requestFocus() })
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(AccordSpacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.month_previous))
            }
            Text(
                month.label(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
                    .then(
                        if (onPick == null) Modifier else Modifier
                            .focusRequester(labelFocus).focusable()
                            .heightIn(min = AccordSpacing.touchMin).wrapContentHeight(Alignment.CenterVertically)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.month_pick)) { picking = true },
                    ),
            )
            onCurrent?.let {
                TextButton(onClick = it, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.month_current))
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.month_next))
            }
        }
    }
}

/** Os 12 meses do ano do mês exibido; outros anos ficam nas setas. Mês atual e selecionado são ditos em texto, não só em cor. */
@Composable
private fun MonthGrid(shown: YearMonth, current: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    val short = java.util.Locale.forLanguageTag("pt-BR")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_back)) } },
        title = { Text(stringResource(R.string.month_pick_title, shown.year), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                for (row in 0 until 4) Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    for (col in 0 until 3) {
                        val m = YearMonth.of(shown.year, row * 3 + col + 1)
                        val isSelected = m == shown
                        val state = when {
                            isSelected -> stringResource(R.string.month_selected)
                            m == current -> stringResource(R.string.month_current_state)
                            else -> null
                        }
                        val name = m.month.getDisplayName(java.time.format.TextStyle.SHORT, short).replaceFirstChar { it.titlecase(short) }
                        val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        Surface(
                            color = container, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin).semantics(mergeDescendants = true) {
                                contentDescription = m.label(); selected = isSelected; state?.let { stateDescription = it }
                            }.clickable(role = Role.Button) { onPick(m) },
                        ) {
                            Column(Modifier.padding(AccordSpacing.s2), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                // Marca textual para "selecionado" e "atual", além da cor.
                                Text(if (isSelected) "✓" else if (m == current) "•" else " ", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
    )
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
    val markerColor = if (state == BarState.Excess) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { }) {
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(track, size = size, cornerRadius = radius)
        if (state != BarState.NoLimit && fill > 0f) {
            drawRoundRect(fillColor, size = Size(size.width * fill, size.height), cornerRadius = radius)
        }
        if (marker != null) {
            // Mantém a marca do limite dentro da barra e com contraste próprio do estado de excesso.
            val markerWidth = 2.dp.toPx()
            val markerX = (size.width * marker - markerWidth / 2f).coerceIn(markerWidth, size.width - markerWidth * 2f)
            drawRect(markerColor, topLeft = Offset(markerX, 0f), size = Size(markerWidth, size.height))
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

/**
 * Um ciclo de envio e recebimento, disparado ao puxar a lista para atualizar. Quem provê (a raiz do
 * aplicativo) roda `syncNow` e avisa se não houve rede; o padrão não faz nada, para pré-visualizações e testes.
 */
val LocalPullSync = staticCompositionLocalOf<suspend () -> Unit> { {} }

/**
 * Puxar para atualizar: roda a sincronização e, ao final, recarrega a tela. A leitura local acontece
 * mesmo sem rede, então o que está salvo no aparelho continua à vista e a falha só é dita, sem bloquear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sync = LocalPullSync.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                try {
                    sync()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Falha de rede ou de serviço não impede a leitura local; o aviso de "salvo no aparelho" é de quem provê o sync.
                } finally {
                    onRefresh()
                    refreshing = false
                }
            }
        },
        modifier = modifier,
    ) { content() }
}
