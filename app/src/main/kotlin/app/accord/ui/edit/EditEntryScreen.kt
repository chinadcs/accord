package app.accord.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.RecurrenceScope
import app.accord.core.label
import app.accord.core.space.ExtraReason
import app.accord.ui.categoryPath
import app.accord.ui.fullLabel
import app.accord.ui.register.AccountDialog
import app.accord.ui.register.CategoryDialog
import app.accord.ui.register.DatePickerSheet
import app.accord.ui.register.FormTextField
import app.accord.ui.register.PickerField
import app.accord.ui.register.accountLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch

@Composable
fun EditEntryRoute(viewModel: EditEntryViewModel, padding: PaddingValues, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit) { if (exit) onExit() }
    BackHandler(enabled = !exit) { viewModel.requestBack() }
    EditEntryScreen(
        state, padding,
        onChange = viewModel::update, onSave = { viewModel.save() }, onBack = viewModel::requestBack,
        onConfirmPrompt = viewModel::confirmPrompt, onDismissPrompt = viewModel::dismissPrompt,
        onAskDelete = viewModel::askDelete, onDelete = { viewModel.deleteOccurrence(it) }, onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditEntryScreen(
    state: EditUiState,
    padding: PaddingValues,
    onChange: ((EditForm) -> EditForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onConfirmPrompt: () -> Unit,
    onDismissPrompt: () -> Unit,
    onAskDelete: () -> Unit,
    onDelete: (RecurrenceScope) -> Unit,
    onRetry: () -> Unit,
) {
    val focus = remember { EditField.entries.associateWith { FocusRequester() } }
    val bring = remember { EditField.entries.associateWith { BringIntoViewRequester() } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(state.focusTick) {
        val field = state.focusField ?: return@LaunchedEffect
        scope.launch { bring.getValue(field).bringIntoView() }
        runCatching { focus.getValue(field).requestFocus() }
    }

    val original = state.original
    val form = state.form
    var picking by remember { mutableStateOf<Picker?>(null) }

    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DetailHeader(stringResource(R.string.register_edit_title), onBack)

            when (state.content) {
                EditContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
                EditContent.Failed -> {
                    Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                EditContent.NotFound -> Text(stringResource(R.string.edit_not_found), style = MaterialTheme.typography.bodyLarge)
                EditContent.NotEditable -> Text(stringResource(R.string.edit_not_editable), style = MaterialTheme.typography.bodyLarge)
                is EditContent.Ready -> if (original != null && form != null) {
                    state.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Fields(state, original, form, onChange, focus, bring) { picking = it }
                    Notes(state, original, form)
                    if (original.needsScope) {
                        OutlinedButton(onClick = onAskDelete, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.edit_delete_occurrence), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (original != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    if (state.nothingChanged) {
                        Text(stringResource(R.string.edit_nothing_changed), style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = onSave, enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
                    ) { Text(stringResource(if (state.saving) R.string.edit_saving else R.string.edit_save)) }
                }
            }
        }
    }

    if (form != null) {
        when (picking) {
            null -> Unit
            Picker.Date -> DatePickerSheet(
                initial = form.date ?: java.time.LocalDate.now(),
                onPick = { picked -> onChange { it.copy(date = picked) }; picking = null },
                onDismiss = { picking = null },
            )
            Picker.Category -> CategoryDialog(state.categories, form.categoryId, onPick = { id ->
                onChange { it.copy(categoryId = id) }; picking = null
            }, onDismiss = { picking = null })
            Picker.Account -> AccountDialog(state.accounts, form.accountId, onPick = { id ->
                onChange { it.copy(accountId = id) }; picking = null
            }, onDismiss = { picking = null })
        }
    }

    PromptDialogs(state, onConfirmPrompt, onDismissPrompt, onDelete)
}

private enum class Picker { Date, Category, Account }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Fields(
    state: EditUiState,
    original: EditOriginal,
    form: EditForm,
    onChange: ((EditForm) -> EditForm) -> Unit,
    focus: Map<EditField, FocusRequester>,
    bring: Map<EditField, BringIntoViewRequester>,
    onPick: (Picker) -> Unit,
) {
    FormTextField(
        value = form.description, onChange = { v -> onChange { it.copy(description = v) } },
        label = stringResource(R.string.register_description), error = state.errors[EditField.Description],
        focus = focus.getValue(EditField.Description), bring = bring.getValue(EditField.Description),
    )
    if (original.amountEditable) {
        FormTextField(
            value = form.amountText, onChange = { v -> onChange { it.copy(amountText = v) } },
            label = stringResource(R.string.register_amount), hint = stringResource(R.string.register_amount_hint),
            error = state.errors[EditField.Amount], keyboard = KeyboardType.Decimal,
            focus = focus.getValue(EditField.Amount), bring = bring.getValue(EditField.Amount),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.edit_amount_locked, original.amount.format()), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.edit_amount_locked_action), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (original.dateEditable) {
        val label = when (original.target) {
            EditTarget.Boleto -> R.string.register_date_due
            EditTarget.Income -> R.string.register_date_expected
            else -> R.string.register_date
        }
        PickerField(
            label = stringResource(label), value = form.date?.fullLabel().orEmpty(), error = state.errors[EditField.Date],
            bring = bring.getValue(EditField.Date),
        ) { onPick(Picker.Date) }
    }
    if (original.categoryEditable) {
        PickerField(
            label = stringResource(R.string.register_category),
            value = categoryPath(state.categories, form.categoryId) ?: stringResource(R.string.register_category_pick),
            error = state.errors[EditField.Category], bring = bring.getValue(EditField.Category),
        ) { onPick(Picker.Category) }
    }
    if (original.accountEditable) {
        val account = state.accounts.firstOrNull { it.id == form.accountId }
        PickerField(
            label = stringResource(R.string.register_account),
            value = account?.let { accountLabel(it) } ?: stringResource(R.string.register_account_pick),
            error = state.errors[EditField.Account], bring = bring.getValue(EditField.Account),
        ) { onPick(Picker.Account) }
    }
    if (original.needsScope) ScopeChoice(form.scope, state.errors[EditField.Scope] != null, bring.getValue(EditField.Scope)) { scope ->
        onChange { it.copy(scope = scope) }
    }
}

/** Alcance da recorrência: sem opção pré-selecionada; a pessoa escolhe e o erro aparece junto do grupo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScopeChoice(selected: RecurrenceScope?, error: Boolean, bring: BringIntoViewRequester, onPick: (RecurrenceScope) -> Unit) {
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(bring),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        Text(stringResource(R.string.edit_scope_title), style = MaterialTheme.typography.titleMedium)
        listOf(RecurrenceScope.OnlyThis to R.string.edit_scope_only_this, RecurrenceScope.ThisAndNext to R.string.edit_scope_this_and_next)
            .forEach { (value, label) -> ScopeRow(stringResource(label), selected == value) { onPick(value) } }
        Text(stringResource(R.string.edit_scope_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error) {
            Text(stringResource(R.string.error_scope_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScopeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}

/** O que a edição não muda, o que ela vai tocar e o que a data indicaria: tudo antes de gravar. */
@Composable
private fun Notes(state: EditUiState, original: EditOriginal, form: EditForm) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (original.target == EditTarget.CardPurchase && original.installments > 1) {
        Text(stringResource(R.string.edit_scope_all_installments), style = body, color = muted)
        if (form.date != original.date) Text(stringResource(R.string.edit_date_installments_note), style = body, color = muted)
    }
    state.suggestedInvoice?.let {
        Text(stringResource(R.string.edit_suggested_invoice, it.label()), style = body, color = Accord.colors.attention)
    }
    if (original.target == EditTarget.Occurrence && state.replacedOccurrences.isNotEmpty()) {
        Text(
            stringResource(R.string.edit_replaced_occurrences, state.replacedOccurrences.joinToString(", ")),
            style = body, color = Accord.colors.attention,
        )
    }
    if (original.paid) Text(stringResource(R.string.edit_paid_note), style = body, color = muted)
}

@Composable
private fun PromptDialogs(state: EditUiState, onConfirm: () -> Unit, onDismiss: () -> Unit, onDelete: (RecurrenceScope) -> Unit) {
    when (val p = state.prompt) {
        null -> Unit
        is EditPrompt.Extra -> ConfirmDialog(
            title = R.string.edit_extra_title,
            body = {
                p.reasons.forEach {
                    Text(stringResource(if (it == ExtraReason.PaidInvoice) R.string.edit_extra_paid_invoice else R.string.edit_extra_imported))
                }
                Text(stringResource(R.string.edit_extra_body))
            },
            confirm = R.string.edit_extra_confirm, onConfirm = onConfirm, onDismiss = onDismiss,
        )
        is EditPrompt.Realized -> ConfirmDialog(
            title = R.string.edit_realized_title,
            body = { Text(stringResource(R.string.edit_realized_body, p.numbers.joinToString(", "))) },
            confirm = if (p.delete) R.string.edit_delete_confirm else R.string.edit_realized_confirm, onConfirm = onConfirm, onDismiss = onDismiss,
        )
        EditPrompt.Stale -> ConfirmDialog(
            title = R.string.edit_stale_title, body = { Text(stringResource(R.string.edit_stale_body)) },
            confirm = R.string.edit_stale_confirm, onConfirm = onConfirm, onDismiss = onDismiss,
        )
        EditPrompt.Discard -> ConfirmDialog(
            title = R.string.edit_discard_title, body = { Text(stringResource(R.string.edit_discard_body)) },
            confirm = R.string.edit_discard_confirm, dismiss = R.string.edit_discard_keep, onConfirm = onConfirm, onDismiss = onDismiss,
        )
        EditPrompt.DeleteScope -> DeleteScopeDialog(onDelete, onDismiss)
    }
}

@Composable
private fun ConfirmDialog(
    title: Int,
    body: @Composable () -> Unit,
    confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: Int = R.string.edit_cancel,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) { body() } },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(dismiss)) }
        },
    )
}

/** Exclusão de ocorrência: o alcance é obrigatório e o botão só vale depois de escolhê-lo. */
@Composable
private fun DeleteScopeDialog(onDelete: (RecurrenceScope) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf<RecurrenceScope?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_delete_title)) },
        text = {
            Column {
                ScopeRow(stringResource(R.string.edit_scope_only_this), chosen == RecurrenceScope.OnlyThis) { chosen = RecurrenceScope.OnlyThis }
                ScopeRow(stringResource(R.string.edit_scope_this_and_next), chosen == RecurrenceScope.ThisAndNext) { chosen = RecurrenceScope.ThisAndNext }
                chosen?.let {
                    Text(
                        stringResource(if (it == RecurrenceScope.OnlyThis) R.string.edit_delete_note_only_this else R.string.edit_delete_note_next),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { chosen?.let(onDelete) }, enabled = chosen != null,
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            ) { Text(stringResource(R.string.edit_delete_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_cancel)) }
        },
    )
}
