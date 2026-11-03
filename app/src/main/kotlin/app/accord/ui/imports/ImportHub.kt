package app.accord.ui.imports

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.importing.DocumentKind
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Importação dentro de Revisar (parte da tela 14): "Continuar revisão" quando há uma em andamento, senão "Importar arquivo";
 * cobertura por origem e histórico. A ausência de arquivos não é apresentada como cobrança nem como "dias perdidos".
 */
@Composable
fun ImportHub(
    state: ImportHubUiState,
    onImport: () -> Unit,
    onContinue: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    onDiscard: (String) -> Unit = {},
) {
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(stringResource(R.string.import_hub_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (state.failed) Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyMedium)
            val inReview = state.inReview
            if (inReview != null) {
                Text(
                    stringResource(R.string.import_hub_in_review, inReview.fileName.ifBlank { stringResource(R.string.import_file_unnamed) }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { onContinue(inReview.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_hub_continue)) }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_hub_import)) }
                TextButton(onClick = { confirmingDiscard = true }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.import_discard))
                }
                if (state.discardFailed) Text(stringResource(R.string.import_discard_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                if (confirmingDiscard) {
                    DiscardDialog(
                        onDiscard = { confirmingDiscard = false; onDiscard(inReview.id) },
                        onCancel = { confirmingDiscard = false },
                    )
                }
            } else {
                if (state.loaded && state.history.isEmpty()) {
                    Text(stringResource(R.string.import_hub_empty), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_hub_import)) }
            }
            if (state.coverage.isNotEmpty()) {
                Text(stringResource(R.string.import_hub_coverage), style = MaterialTheme.typography.titleSmall)
                state.coverage.forEach { line ->
                    val kind = stringResource(if (line.kind == DocumentKind.Card) R.string.import_kind_card else R.string.import_kind_account)
                    val situation = stringResource(if (line.latest.complete) R.string.import_coverage_complete_short else R.string.import_coverage_partial_short)
                    Text(
                        "$kind · ${line.originName}: ${period(line.latest.info.periodStart, line.latest.info.periodEnd) ?: stringResource(R.string.import_hub_no_period)} ($situation)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.history.isNotEmpty()) {
                Text(stringResource(R.string.import_hub_history), style = MaterialTheme.typography.titleSmall)
                state.history.forEach { batch ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onOpenResult(batch.info.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                    ) {
                        DecorativeIcon(if (batch.complete) Lucide.drawable.lucide_ic_circle_check else Lucide.drawable.lucide_ic_triangle_alert)
                        Column(Modifier.weight(1f)) {
                            Text(batch.info.fileName.ifBlank { stringResource(R.string.import_file_unnamed) }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    period(batch.info.periodStart, batch.info.periodEnd),
                                    stringResource(if (batch.complete) R.string.import_coverage_complete_short else R.string.import_coverage_partial_short),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
