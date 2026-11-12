package app.accord.ui.intentions

import app.accord.ui.components.AccordCard
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.space.IntentionView
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun IntentionsRoute(
    viewModel: IntentionsViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSimulate: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    IntentionsScreen(state, padding, onBack, onOpen, onNew, onSimulate, viewModel::retry)
}

/** Tela 34: lista compartilhada de intenções. O rótulo de que não ocupam o orçamento é permanente. */
@Composable
fun IntentionsScreen(
    state: IntentionsUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSimulate: () -> Unit,
    onRetry: () -> Unit,
) {
    // A rolagem fica guardada com a tela: voltar de uma intenção reencontra o mesmo ponto da lista.
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf(IntentionFilter.All) }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding), state = listState,
        contentPadding = PaddingValues(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item { DetailHeader(stringResource(R.string.intentions_title), onBack) }
        item { NotBudgetLabel(stringResource(R.string.intentions_not_budget)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.intentions_new)) }
                OutlinedButton(onClick = onSimulate, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.intentions_simulate))
                }
            }
        }
        item {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                IntentionFilter.entries.forEach { option ->
                    androidx.compose.material3.FilterChip(
                        selected = filter == option, onClick = { filter = option },
                        label = { Text(stringResource(option.label)) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
        }
        when (val content = state.content) {
            IntentionsContent.Loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            is IntentionsContent.Failed -> {
                item { FailureBanner(onRetry) }
                content.last?.let { rows(it.filtered(filter), state.currentPersonId, onOpen) }
            }
            is IntentionsContent.Ready -> {
                if (content.items.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                            Text(stringResource(R.string.intentions_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.intentions_empty_body), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else if (content.items.filtered(filter).isEmpty()) {
                    item { Text(stringResource(R.string.intentions_filter_empty), style = MaterialTheme.typography.bodyLarge) }
                } else {
                    rows(content.items.filtered(filter), state.currentPersonId, onOpen)
                }
            }
        }
    }
}

/** Filtro por situação (tela 34): a situação "convertida" é derivada do compromisso vinculado. */
enum class IntentionFilter(@androidx.annotation.StringRes val label: Int) {
    All(R.string.intention_filter_all), Planned(R.string.intention_filter_planned), Converted(R.string.intention_filter_converted),
}

private fun List<IntentionView>.filtered(filter: IntentionFilter) = when (filter) {
    IntentionFilter.All -> this
    IntentionFilter.Planned -> filter { it.situation == app.accord.core.space.IntentionSituation.Planned }
    IntentionFilter.Converted -> filter { it.situation == app.accord.core.space.IntentionSituation.Converted }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rows(items: List<IntentionView>, currentPersonId: String, onOpen: (String) -> Unit) {
    items(items, key = { it.id }) { IntentionRow(it, currentPersonId, onOpen) }
}

/** Rótulo permanente: ícone e texto, nunca só cor. */
@Composable
internal fun NotBudgetLabel(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            DecorativeIcon(Lucide.drawable.lucide_ic_info)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FailureBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.intentions_failed), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun IntentionRow(item: IntentionView, currentPersonId: String, onOpen: (String) -> Unit) {
    val value = item.estimatedValue?.format() ?: stringResource(R.string.intentions_no_value)
    val sync = item.sync.label()
    val who = personName(item.updatedBy, currentPersonId)
    val converted = if (item.conversion != null) stringResource(R.string.intentions_converted_tag) + ". " else ""
    val spoken = stringResource(R.string.intentions_row_a11y, item.name, value, converted + stringResource(R.string.intentions_by_updated, who) + ". " + sync)
    AccordCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onOpen(item.id) }
            .semantics(mergeDescendants = true) { contentDescription = spoken },) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(item.name, style = MaterialTheme.typography.titleSmall) },
                end = { Text(value, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            item.desiredMonth?.let { Text(stringResource(R.string.intentions_when, it.label()), style = MaterialTheme.typography.bodyMedium) }
            if (item.conversion != null) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_circle_check)
                    Text(stringResource(R.string.intentions_converted_tag), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                stringResource(R.string.intentions_by_updated, who) + " · " + sync,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "você" ou "a outra pessoa": o espaço é de duas pessoas e a identidade bruta nunca aparece. */
@Composable
internal fun personName(personId: String, currentPersonId: String): String =
    stringResource(if (personId == currentPersonId) R.string.intentions_by_you else R.string.intentions_by_other)
