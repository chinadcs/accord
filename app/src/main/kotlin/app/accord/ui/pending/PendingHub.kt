package app.accord.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.theme.AccordSpacing

/**
 * Entrada da fila única em Revisar: quantas pendências esperam e o caminho para revisá-las. Nenhuma pendência conhecida não diz que tudo
 * está registrado, e o texto não afirma isso.
 */
@Composable
fun PendingHubCard(count: Int, loading: Boolean, onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(stringResource(R.string.pending_hub_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (!loading) {
                Text(
                    if (count > 0) pluralStringResource(R.plurals.pending_hub_count, count, count) else stringResource(R.string.pending_hub_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_hub_open)) }
        }
    }
}
