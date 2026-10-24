package app.accord.ui.limits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.LimitScope
import app.accord.core.label
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.MonthSelector
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun LimitsRoute(viewModel: LimitsViewModel, padding: PaddingValues, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit) { if (exit) onExit() }
    BackHandler(enabled = !exit) { viewModel.requestBack() }
    LimitsScreen(
        state, padding, onBack = viewModel::requestBack, onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth,
        onRetry = viewModel::retry, onTotal = viewModel::setTotal, onPart = viewModel::setPart, onScope = viewModel::setScope,
        onSave = viewModel::save, onKeepEditing = viewModel::keepEditing, onDiscard = viewModel::discard,
    )
}

@Composable
fun LimitsScreen(
    state: LimitsUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onTotal: (String, String) -> Unit,
    onPart: (String, String, String) -> Unit,
    onScope: (LimitScope) -> Unit,
    onSave: () -> Unit,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.limits_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(R.string.limits_reference, state.month.label()), style = MaterialTheme.typography.bodyLarge)
            MonthSelector(state.month, onPrevious, onNext)
            if (state.monthBlocked) Text(stringResource(R.string.limits_month_blocked), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            when (state.content) {
                LimitsContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
                LimitsContent.Failed -> {
                    Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
                }
                is LimitsContent.Ready -> {
                    state.rows.forEach { row -> CategoryLimit(row, state, onTotal, onPart) }
                    AdaptiveRow(
                        start = { Text(stringResource(R.string.limits_total), style = MaterialTheme.typography.titleMedium) },
                        end = { Text(state.totalOfCategories().format(), style = Accord.typography.amountRow) },
                    )
                    Text(stringResource(R.string.limits_total_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScopeChoice(state, onScope)
                    Summary(state)
                    state.serverErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (state.content is LimitsContent.Ready) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    if (state.nothingChanged) Text(stringResource(R.string.limits_nothing_changed), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(if (state.saving) R.string.edit_saving else R.string.limits_save))
                    }
                }
            }
        }
    }
    if (state.askDiscard) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.limits_discard_body)) },
            confirmButton = { TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_discard_confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onKeepEditing, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_discard_keep)) } },
        )
    }
}

@Composable
private fun errorText(error: LimitFieldError?): String? = when (error) {
    null -> null
    LimitFieldError.Invalid -> stringResource(R.string.error_amount_invalid)
    LimitFieldError.Negative -> stringResource(R.string.limits_error_negative)
    LimitFieldError.Ambiguous -> stringResource(R.string.error_amount_ambiguous)
    is LimitFieldError.SplitExceeds -> stringResource(R.string.limits_error_split_exceeds, error.excess.format())
    LimitFieldError.SplitWithoutTotal -> stringResource(R.string.limits_error_split_without_total)
}

@Composable
private fun CategoryLimit(row: LimitRow, state: LimitsUiState, onTotal: (String, String) -> Unit, onPart: (String, String, String) -> Unit) {
    var splitOpen by remember(row.categoryId) { mutableStateOf(row.original.split.isNotEmpty()) }
    val totalText = state.draft.totals[row.categoryId].orEmpty()
    val parts = state.draft.splits[row.categoryId].orEmpty()
    val totalError = state.errors[LimitKey(row.categoryId)]
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            AdaptiveRow(
                start = { Text(row.name, style = MaterialTheme.typography.titleSmall) },
                end = { Text(stringResource(R.string.limits_spent, row.spent.format()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            OutlinedTextField(
                value = totalText, onValueChange = { onTotal(row.categoryId, it) }, singleLine = true,
                label = { Text(stringResource(R.string.limits_field_total, row.name)) },
                placeholder = { Text(stringResource(R.string.limits_undefined)) },
                isError = totalError != null, supportingText = errorText(totalError)?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            if (row.laterChanges.isNotEmpty()) {
                Text(
                    stringResource(R.string.limits_later_changes, row.laterChanges.joinToString(", ") { it.label() }),
                    style = MaterialTheme.typography.bodySmall, color = Accord.colors.attention,
                )
            }
            if (row.subs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.limits_split_toggle), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = splitOpen, onCheckedChange = { splitOpen = it })
                }
                if (splitOpen) SplitFields(row, parts, state, onPart)
            }
        }
    }
}

@Composable
private fun SplitFields(row: LimitRow, parts: Map<String, String>, state: LimitsUiState, onPart: (String, String, String) -> Unit) {
    row.subs.forEach { sub ->
        val error = state.errors[LimitKey(row.categoryId, sub.id)]
        OutlinedTextField(
            value = parts[sub.id].orEmpty(), onValueChange = { onPart(row.categoryId, sub.id, it) }, singleLine = true,
            label = { Text(sub.name) }, placeholder = { Text(stringResource(R.string.limits_no_split)) },
            isError = error != null, supportingText = errorText(error)?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
    }
    val sum = parts.values.fold(app.accord.core.Money.ZERO) { acc, text ->
        acc + ((app.accord.core.Money.parse(text) as? app.accord.core.MoneyParse.Ok)?.money ?: app.accord.core.Money.ZERO)
    }
    val total = (app.accord.core.Money.parse(state.draft.totals[row.categoryId].orEmpty()) as? app.accord.core.MoneyParse.Ok)?.money
    if (total != null) {
        Text(stringResource(R.string.limits_distributed, sum.format(), total.format()), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.limits_undistributed, (total - sum).format()), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.limits_split_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Alcance obrigatório: sem opção pré-selecionada e com o mês escrito por extenso. */
@Composable
private fun ScopeChoice(state: LimitsUiState, onScope: (LimitScope) -> Unit) {
    val month = state.month.label()
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.limits_scope_title), style = MaterialTheme.typography.titleMedium)
        listOf(
            LimitScope.OnlyThisMonth to stringResource(R.string.limits_scope_only, month),
            LimitScope.FromThisMonth to stringResource(R.string.limits_scope_from, month),
        ).forEach { (scope, label) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                    .selectable(selected = state.draft.scope == scope, role = Role.RadioButton) { onScope(scope) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.draft.scope == scope, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
            }
        }
        if (state.scopeMissing) Text(stringResource(R.string.limits_scope_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Antes de salvar: o que muda, a partir de quando e quais ajustes futuros já existentes seriam substituídos. */
@Composable
private fun Summary(state: LimitsUiState) {
    val changed = state.rows.filter { row ->
        state.draft.totals[row.categoryId].orEmpty() != (row.original.total?.formatPlain().orEmpty()) ||
            state.draft.splits[row.categoryId].orEmpty() != row.subs.associate { it.id to row.original.split[it.id]?.formatPlain().orEmpty() }
    }
    if (changed.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.limits_summary), style = MaterialTheme.typography.titleSmall)
            changed.forEach { row ->
                val before = row.original.total?.format() ?: stringResource(R.string.limits_undefined)
                val after = (app.accord.core.Money.parse(state.draft.totals[row.categoryId].orEmpty()) as? app.accord.core.MoneyParse.Ok)?.money?.format()
                    ?: stringResource(R.string.limits_undefined)
                Text(stringResource(R.string.limits_summary_line, row.name, before, after), style = MaterialTheme.typography.bodyMedium)
            }
            state.draft.scope?.let {
                Text(
                    stringResource(if (it == LimitScope.OnlyThisMonth) R.string.limits_summary_only else R.string.limits_summary_from, state.month.label()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.draft.scope == LimitScope.FromThisMonth && state.replacedMonths.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.attention)
                    Text(
                        stringResource(R.string.limits_replaced, state.replacedMonths.joinToString(", ") { it.label() }),
                        style = MaterialTheme.typography.bodyMedium, color = Accord.colors.attention,
                    )
                }
            }
        }
    }
}
