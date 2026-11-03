package app.accord.ui.imports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.importing.BatchResult
import app.accord.core.importing.Resolution
import app.accord.core.importing.ResultItem
import app.accord.core.space.EntityType
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun ImportResultRoute(
    viewModel: ImportResultViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onSeeEntries: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onResolvePending: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::load)
    ImportResultScreen(state, padding, onBack, viewModel::load, viewModel::toggle, onSeeEntries, onOpenEntry, onResolvePending)
}

@Composable
fun ImportResultScreen(
    state: ImportResultUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (Int) -> Unit,
    onSeeEntries: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onResolvePending: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        when (val content = state.content) {
            ResultContent.Loading -> Plain(onBack, R.string.import_result_title) { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            ResultContent.Failed -> Plain(onBack, R.string.import_result_title) {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            ResultContent.Missing -> Plain(onBack, R.string.import_result_title) { Text(stringResource(R.string.import_result_missing), style = MaterialTheme.typography.bodyLarge) }
            is ResultContent.Ready -> {
                val result = content.result
                val partial = !result.complete
                LazyColumn(
                    Modifier.weight(1f), contentPadding = PaddingValues(AccordSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    item {
                        DetailHeader(
                            stringResource(if (partial) R.string.import_result_partial else R.string.import_result_done), onBack,
                            subtitle = period(result.info.periodStart, result.info.periodEnd)?.let { stringResource(R.string.import_result_covers, it) },
                        )
                    }
                    item { Counts(result) }
                    item { Coverage(result, partial) }
                    item { MessageBanner(syncMessage(content.sync)) }
                    items(result.items, key = { it.ordinal }) { item -> ItemRow(item, item.ordinal in state.expanded, onToggle, onOpenEntry) }
                    item { Text(stringResource(R.string.import_result_originals), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        val pending = result.count(Resolution.Pending)
                        // Com dúvidas guardadas, a ação principal é resolvê-las (fila filtrada pelo lote); sem elas, ver os lançamentos.
                        if (pending > 0) {
                            Button(onClick = onResolvePending, modifier = Modifier.fillMaxWidth()) {
                                Text(pluralStringResource(R.plurals.import_result_resolve, pending, pending))
                            }
                            OutlinedButton(onClick = onSeeEntries, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_result_see_entries)) }
                        } else {
                            Button(onClick = onSeeEntries, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_result_see_entries)) }
                        }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_result_back_review)) }
                    }
                }
            }
        }
    }
}

private fun syncMessage(sync: ResultSync) = when (sync) {
    ResultSync.SavedOnDevice -> ScreenMessage(MessageKind.Neutral, R.string.import_sync_saved)
    ResultSync.Acknowledged -> ScreenMessage(MessageKind.Success, R.string.import_sync_acknowledged)
    ResultSync.ReceivedFromOtherDevice -> ScreenMessage(MessageKind.Neutral, R.string.import_sync_received)
}

@Composable
private fun Plain(onBack: () -> Unit, title: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
        DetailHeader(stringResource(title), onBack)
        content()
    }
}

@Composable
private fun Counts(result: BatchResult) {
    val created = result.count(Resolution.Created) + result.count(Resolution.IncomeCreated)
    val linked = result.count(Resolution.Linked) + result.count(Resolution.RefundCreated) + result.count(Resolution.InstallmentLinked) + result.count(Resolution.InvoicePaid) + result.count(Resolution.BoletoPaid)
    val pending = result.count(Resolution.Pending)
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.import_result_counts, created, linked, pending), style = MaterialTheme.typography.bodyLarge)
        val known = result.count(Resolution.AlreadyKnown)
        if (known > 0) {
            Text(pluralStringResource(R.plurals.import_summary_known, known, known), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val dismissed = result.count(Resolution.Dismissed)
        if (dismissed > 0) {
            Text(pluralStringResource(R.plurals.import_summary_dismissed, dismissed, dismissed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (created + linked + known + dismissed + pending == 0) {
            Text(stringResource(R.string.import_result_nothing), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** O período do arquivo não prova que todos os gastos foram registrados: a situação nomeia o que foi conferido e o que falta. */
@Composable
private fun Coverage(result: BatchResult, partial: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(
            stringResource(if (partial) R.string.import_coverage_partial else R.string.import_coverage_complete),
            style = MaterialTheme.typography.titleSmall,
        )
        val pending = result.count(Resolution.Pending)
        if (pending > 0) Text(pluralStringResource(R.plurals.import_result_pending_kept, pending, pending), style = MaterialTheme.typography.bodyMedium)
        if (result.info.unrecognizedSegments > 0) {
            Text(pluralStringResource(R.plurals.import_result_unread, result.info.unrecognizedSegments, result.info.unrecognizedSegments), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val ENTRY_TYPES = setOf(EntityType.Movement, EntityType.CardPurchase, EntityType.Boleto, EntityType.Income)

@Composable
private fun ItemRow(item: ResultItem, expanded: Boolean, onToggle: (Int) -> Unit, onOpenEntry: (String) -> Unit) {
    val amount = movementAmount(item.cents, item.currency) ?: stringResource(R.string.import_amount_unknown)
    val resolution = item.resolution.label()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .semantics(mergeDescendants = true) { contentDescription = "${item.description}, $amount, $resolution" }
            .clickable(role = Role.Button) { onToggle(item.ordinal) },
    ) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(item.description.ifBlank { stringResource(R.string.import_no_description) }, style = MaterialTheme.typography.titleSmall) },
                end = { Text(amount, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                DecorativeIcon(if (item.resolution == Resolution.Pending) Lucide.drawable.lucide_ic_triangle_alert else Lucide.drawable.lucide_ic_circle_check)
                Text(listOfNotNull(item.date?.shortLabel(), resolution).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            item.repeatOf?.let { Text(stringResource(R.string.import_result_repeat_of, it), style = MaterialTheme.typography.bodySmall) }
            if (expanded) {
                item.doubts.forEach { Text(it.label(), style = MaterialTheme.typography.bodySmall) }
                item.originalFields.forEach { (name, value) -> Text("$name: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val target = item.targetId
                if (target != null && item.targetType in ENTRY_TYPES) {
                    OutlinedButton(onClick = { onOpenEntry(target) }) { Text(stringResource(R.string.import_result_open_entry)) }
                }
            }
        }
    }
}
