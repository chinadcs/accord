package app.accord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** Cabeçalho de seção (Parcelas, Estornos, Recorrência…), anunciado como título. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** Par rótulo/valor; o TalkBack lê os dois como uma unidade ("rótulo, valor"). */
@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label, $value" },
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Chip de filtro ativo; o ✕ é o único alvo de toque (48 × 48 dp) e diz "Remover filtro: …". */
@Composable
fun RemovableChip(text: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.heightIn(min = AccordSpacing.touchMin),
    ) {
        Row(Modifier.padding(start = AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Start)
            IconButton(onClick = onRemove, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.chip_remove_filter, text))
            }
        }
    }
}
