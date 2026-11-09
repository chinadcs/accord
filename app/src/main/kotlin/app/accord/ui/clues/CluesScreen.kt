package app.accord.ui.clues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.clues.*
import app.accord.ui.categoryPath
import app.accord.ui.fullLabel
import app.accord.ui.label
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing
import java.time.LocalDate
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton

@Composable
fun CluesRoute(vm: CluesViewModel, padding: PaddingValues, onBack: () -> Unit, onOpen: (String, LocalDate) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    RefreshOnResume(vm::refresh)
    CluesScreen(state, padding, onBack, onOpen, vm::refresh)
}

@Composable
fun CluesScreen(state: CluesUiState, padding: PaddingValues, onBack: () -> Unit,
    onOpen: (String, LocalDate) -> Unit, onRetry: () -> Unit) {
    var evaluated by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("clues-list"), state = scroll, contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        item { DetailHeader(stringResource(R.string.clues_title), onBack) }
        item { Text(stringResource(R.string.clues_explanation)) }
        item { Text(stringResource(R.string.clues_local), style = MaterialTheme.typography.bodySmall) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                FilterChip(!evaluated, { evaluated = false }, label = { Text(stringResource(R.string.clues_unreviewed)) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin))
                FilterChip(evaluated, { evaluated = true }, label = { Text(stringResource(R.string.clues_reviewed)) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin))
            }
        }
        if (state.loading) item { Text(stringResource(R.string.state_loading)) }
        if (state.failed) item { QueryError(onRetry) }
        if (!state.loading && !state.coverageAdequate) item { Text(stringResource(R.string.clues_coverage_limited), style = MaterialTheme.typography.bodyMedium) }
        val items = state.items.filter { it.evaluated == evaluated }
        if (!state.loading && items.isEmpty()) item { Text(stringResource(if (evaluated) R.string.clues_no_reviewed else R.string.clues_no_unreviewed)) }
        items(items, key = { it.key }) { item ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item.key, state.reference) }) {
                Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Text(clueTitle(item.snapshot, state), style = MaterialTheme.typography.titleMedium)
                    Text(periodLabel(item.snapshot), style = MaterialTheme.typography.bodyMedium)
                    if (item.snapshot.kind == ClueKind.FrequencyIncrease.name) {
                        Text(stringResource(R.string.clues_counts, item.snapshot.previousCount ?: 0, item.snapshot.currentCount ?: 0))
                    }
                    if (item.evaluations.isNotEmpty() && !item.evaluated) Text(stringResource(R.string.clues_reopened))
                    Text(stringResource(if (item.evaluated) R.string.clues_reviewed else R.string.clues_unreviewed), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ClueDetailRoute(vm: CluesViewModel, padding: PaddingValues, onBack: () -> Unit, onEntry: (String) -> Unit, onPending: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    RefreshOnResume(vm::refresh)
    ClueDetailScreen(state, padding, onBack, onEntry, vm::refresh, vm::updateAnalysis, vm::dismiss, onPending)
}

@Composable
fun ClueDetailScreen(state: CluesUiState, padding: PaddingValues, onBack: () -> Unit, onEntry: (String) -> Unit,
    onRetry: () -> Unit, onUpdate: () -> Unit, onDismiss: () -> Unit, onPending: () -> Unit = {}) {
    var comparing by rememberSaveable { mutableStateOf(false) }
    val snapshot = state.seen
    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("clue-detail-list"), state = rememberLazyListState(),
        contentPadding = PaddingValues(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        item { DetailHeader(snapshot?.let { clueTitle(it, state) } ?: stringResource(R.string.clues_title), onBack) }
        item { Text(stringResource(R.string.clues_explanation)) }
        item { Text(stringResource(R.string.clues_local), style = MaterialTheme.typography.bodySmall) }
        if (state.loading) item { Text(stringResource(R.string.state_loading)) }
        if (state.failed) item { QueryError(onRetry) }
        if (state.changed) item {
            Text(stringResource(R.string.clues_changed))
            OutlinedButton(onUpdate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clues_update)) }
        }
        if (!state.loading && (state.selected?.current == null || !state.evidenceAvailable)) item { Text(stringResource(R.string.clues_insufficient)) }
        if (state.hasImportPending) item {
            OutlinedButton(onPending, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clues_existing_pending)) }
        }
        if (snapshot != null) {
            item { Text(periodLabel(snapshot)) }
            if (snapshot.kind == ClueKind.FrequencyIncrease.name) item {
                Text(stringResource(R.string.clues_counts, snapshot.previousCount ?: 0, snapshot.currentCount ?: 0))
                Text(stringResource(R.string.clues_frequency_rule))
            } else item { Text(stringResource(R.string.clues_not_proof)) }
            item {
                Text(stringResource(if (state.coverageAdequate) R.string.clues_coverage_known else R.string.clues_coverage_unconfirmed))
                Text(stringResource(R.string.clues_coverage_caveat), style = MaterialTheme.typography.bodySmall)
            }
            items(state.coverage, key = { "coverage-${it.batchId}" }) { coverage ->
                Text(stringResource(R.string.clues_coverage_interval,
                    coverage.from?.fullLabel() ?: stringResource(R.string.clues_unknown_date),
                    coverage.untilExclusive?.minusDays(1)?.fullLabel() ?: stringResource(R.string.clues_unknown_date),
                    stringResource(if (coverage.reviewed) R.string.clues_document_reviewed else R.string.clues_document_partial)),
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                OutlinedButton({ comparing = !comparing }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (comparing) R.string.clues_hide_origins else R.string.clues_compare_origins)) }
            }
            items(snapshot.evidence, key = { it.id }) { evidence ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Text(evidence.description, style = MaterialTheme.typography.titleMedium)
                        Text(Money(evidence.cents).format())
                        Text(LocalDate.ofEpochDay(evidence.epochDay).fullLabel())
                        val current = state.details[evidence.id]
                        if (current == null || current.voided) Text(stringResource(R.string.clues_evidence_unavailable))
                        else {
                            Text(current.origin?.label() ?: stringResource(R.string.clues_origin_unknown))
                            if (current.revision != evidence.revision) Text(stringResource(R.string.clues_evidence_changed))
                            Button({ onEntry(evidence.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clues_check_entry)) }
                        }
                        if (comparing) {
                            val origins = state.origins[evidence.id].orEmpty()
                            if (origins.isEmpty()) Text(stringResource(R.string.clues_no_import_origin))
                            origins.forEach { origin ->
                                Text(stringResource(R.string.clues_import_ordinal, origin.ordinal))
                                origin.originalFields.forEach { (field, value) -> Text("$field: $value", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
            if (state.selected?.current != null && !state.selected.evaluated) item {
                Button(onDismiss, enabled = !state.saving && !state.changed && !state.failed && state.evidenceAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(if (state.saving) R.string.clues_saving else R.string.clues_dismiss))
                }
                Text(stringResource(R.string.clues_dismiss_effect), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.saveFailed) item { Text(stringResource(R.string.clues_save_failed)) }
        items(state.selected?.evaluations.orEmpty(), key = { "evaluation-${it.id}" }) { evaluation ->
            Text(stringResource(R.string.clues_evaluated_by, state.memberNames[evaluation.authorId] ?: evaluation.authorId))
            Text(evaluation.sync.label(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QueryError(onRetry: () -> Unit) {
    Column {
        Text(stringResource(R.string.clues_query_failed))
        OutlinedButton(onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun clueTitle(snapshot: ClueSnapshot, state: CluesUiState): String =
    if (snapshot.kind == ClueKind.FrequencyIncrease.name) stringResource(R.string.clues_frequency_title,
        categoryPath(state.categories, snapshot.subcategoryId) ?: stringResource(R.string.clues_subcategory_unknown))
    else stringResource(R.string.clues_similar_title, snapshot.evidence.firstOrNull()?.description.orEmpty())

@Composable
private fun periodLabel(snapshot: ClueSnapshot): String {
    val from = LocalDate.ofEpochDay(snapshot.fromDay)
    val until = LocalDate.ofEpochDay(snapshot.untilDay)
    return if (snapshot.kind == ClueKind.FrequencyIncrease.name) stringResource(R.string.clues_frequency_periods,
        from.fullLabel(), until.minusDays(29).fullLabel(), until.minusDays(28).fullLabel(), until.minusDays(1).fullLabel())
    else if (from == until.minusDays(1)) from.fullLabel() else "${from.fullLabel()} – ${until.minusDays(1).fullLabel()}"
}
