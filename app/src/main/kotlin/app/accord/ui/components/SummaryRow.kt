package app.accord.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Linha de resumo de um cartão: ícone decorativo, [text], [supporting] opcional, [value] à direita (desce para a linha
 * de baixo em texto ampliado, como [AdaptiveRow]) e seta quando há [onClick]. Com ação, a linha inteira é um botão com
 * [onClickLabel]; a seta e o ícone não são lidos. [minHeight] é 60 dp para a faixa "Pede atenção" (ADR 0078) e
 * 48 dp onde a linha for densa; nunca fica abaixo do alvo mínimo de toque.
 */
@Composable
fun SummaryRow(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    minHeight: Dp = AccordSpacing.rowMedium,
) {
    val rowModifier = modifier.fillMaxWidth().heightIn(min = maxOf(minHeight, AccordSpacing.touchMin))
        .let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
        .padding(vertical = AccordSpacing.s2)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        icon?.let {
            Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val label: @Composable () -> Unit = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (value == null) {
            Column(Modifier.weight(1f)) { label() }
        } else {
            AdaptiveRow(Modifier.weight(1f), start = label, end = {
                Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
            })
        }
        if (onClick != null) {
            Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
