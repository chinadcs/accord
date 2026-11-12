package app.accord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** Altura mínima do cabeçalho recolhível (ADR 0086): maior que o alvo de toque, porque carrega título e resumo. */
val CollapsibleHeaderMinHeight = 60.dp

/**
 * Seção recolhível com estado hoistado: quem usa guarda [expanded] (com chave estável, para sobreviver à rolagem e a voltar
 * de um detalhe) e recebe [onToggle]. O cabeçalho é um botão que anuncia aberto/fechado; [summary] é a linha de resumo visível
 * mesmo recolhida (por exemplo, o resultado do fluxo) e [content] só é composto aberto.
 */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val state = stringResource(if (expanded) R.string.collapsible_open else R.string.collapsible_closed)
    AccordCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = CollapsibleHeaderMinHeight)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = state }
                    .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    summary?.invoke()
                }
                DecorativeIcon(if (expanded) Lucide.drawable.lucide_ic_chevron_up else Lucide.drawable.lucide_ic_chevron_down)
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = AccordSpacing.s4, end = AccordSpacing.s4, bottom = AccordSpacing.s4)) { content() }
            }
        }
    }
}
