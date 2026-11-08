package app.accord.ui.intentions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.space.IntentionConversion
import app.accord.core.space.IntentionView
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.MonthSelector
import app.accord.ui.label
import app.accord.ui.register.FormError
import app.accord.ui.register.FormTextField
import app.accord.ui.theme.AccordSpacing

@Composable
fun IntentionRoute(
    viewModel: IntentionViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onSimulate: (IntentionView) -> Unit,
    onAssume: (IntentionView) -> Unit,
    onOpenConversion: (IntentionConversion) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    app.accord.ui.components.RefreshOnResume(viewModel::refresh)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    IntentionScreen(
        state, padding, onBack,
        onName = viewModel::setName, onValue = viewModel::setValue, onNote = viewModel::setNote,
        onPreviousMonth = { viewModel.shiftMonth(-1) }, onNextMonth = { viewModel.shiftMonth(1) }, onClearMonth = { viewModel.setMonth(null) },
        onPickMonth = { viewModel.shiftMonth(0) },
        onEdit = viewModel::startEdit, onSave = viewModel::save, onCancelEdit = viewModel::cancelEdit, onDiscardDraft = viewModel::discardDraft,
        onSimulate = onSimulate, onRetryLoad = viewModel::retryLoad, onShowCurrent = viewModel::showCurrentVersion,
        onAssume = onAssume, onOpenConversion = onOpenConversion,
    )
}

/** Tela 35: visualizar, criar e editar a intenção no mesmo layout. "Assumir compromisso" chega com a conversão (S7.7/S7.8). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntentionScreen(
    state: IntentionUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onValue: (String) -> Unit,
    onNote: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClearMonth: () -> Unit,
    onPickMonth: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onDiscardDraft: () -> Unit,
    onSimulate: (IntentionView) -> Unit,
    onRetryLoad: () -> Unit,
    onShowCurrent: () -> Unit,
    onAssume: (IntentionView) -> Unit = {},
    onOpenConversion: (IntentionConversion) -> Unit = {},
) {
    val title = when {
        state.isNew -> R.string.intention_title_new
        state.editing -> R.string.intention_title_edit
        else -> R.string.intention_title_view
    }
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(title), onBack)
        when (state.load) {
            IntentionLoad.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            IntentionLoad.NotFound -> {
                Text(stringResource(R.string.intention_not_found_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.intention_not_found_body), style = MaterialTheme.typography.bodyLarge)
            }
            IntentionLoad.Failed -> {
                MessageBanner(ScreenMessage(MessageKind.Error, R.string.intention_failed))
                OutlinedButton(onClick = onRetryLoad, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            IntentionLoad.Ready -> {
                NotBudgetLabel(stringResource(R.string.intention_not_budget))
                Notice(state.notice, onShowCurrent)
                if (state.editing) {
                    EditForm(state, onName, onValue, onNote, onPreviousMonth, onNextMonth, onClearMonth, onPickMonth, onSave, onCancelEdit, onDiscardDraft)
                } else {
                    state.saved?.let { ViewContent(it, state, onEdit, onSimulate, onAssume, onOpenConversion) }
                }
            }
        }
    }
}

@Composable
private fun Notice(notice: IntentionNotice, onShowCurrent: () -> Unit) {
    when (notice) {
        IntentionNotice.None -> Unit
        IntentionNotice.DraftRestored -> MessageBanner(ScreenMessage(MessageKind.Neutral, R.string.intention_draft_restored))
        IntentionNotice.SaveFailed -> MessageBanner(ScreenMessage(MessageKind.Error, R.string.intention_save_failed))
        IntentionNotice.Stale -> {
            MessageBanner(ScreenMessage(MessageKind.Attention, R.string.intention_stale))
            OutlinedButton(onClick = onShowCurrent, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.intention_stale_load)) }
        }
    }
}

@Composable
private fun ViewContent(
    view: IntentionView,
    state: IntentionUiState,
    onEdit: () -> Unit,
    onSimulate: (IntentionView) -> Unit,
    onAssume: (IntentionView) -> Unit,
    onOpenConversion: (IntentionConversion) -> Unit,
) {
    Text(view.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(
        view.estimatedValue?.format() ?: stringResource(R.string.intentions_no_value),
        style = MaterialTheme.typography.titleLarge,
    )
    view.desiredMonth?.let { Text(stringResource(R.string.intentions_when, it.label()), style = MaterialTheme.typography.bodyLarge) }
    view.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    Text(
        stringResource(R.string.intention_created_by, personName(view.createdBy, state.currentPersonId)) + ". " +
            stringResource(R.string.intention_updated_by, personName(view.updatedBy, state.currentPersonId)) + ". " + view.sync.label(),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = { onSimulate(view) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.intention_simulate))
    }
    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.intention_edit)) }
    // Assumir compromisso é uma ação à parte de Salvar intenção. Convertida: mostra o vínculo e não oferece converter de novo.
    val conversion = view.conversion
    if (conversion == null) {
        OutlinedButton(onClick = { onAssume(view) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.intention_assume))
        }
    } else {
        Text(stringResource(R.string.intention_converted_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        OutlinedButton(onClick = { onOpenConversion(conversion) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.intention_converted_open))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditForm(
    state: IntentionUiState,
    onName: (String) -> Unit,
    onValue: (String) -> Unit,
    onNote: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClearMonth: () -> Unit,
    onPickMonth: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onDiscardDraft: () -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    val valueFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val nameBring = remember { BringIntoViewRequester() }
    val valueBring = remember { BringIntoViewRequester() }
    val noteBring = remember { BringIntoViewRequester() }
    val form = state.form
    FormTextField(
        value = form.name, onChange = onName, label = stringResource(R.string.intention_name),
        error = state.errors[IntentionField.Name], focus = nameFocus, bring = nameBring,
    )
    FormTextField(
        value = form.valueText, onChange = onValue, label = stringResource(R.string.intention_value),
        error = state.errors[IntentionField.Value], focus = valueFocus, bring = valueBring,
        hint = stringResource(R.string.intention_value_hint), keyboard = KeyboardType.Decimal, emphasized = true,
    )
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.intention_when), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val month = form.month
        if (month == null) {
            Text(stringResource(R.string.intention_when_none), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onPickMonth, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.intention_when_pick)) }
        } else {
            MonthSelector(month, onPreviousMonth, onNextMonth)
            OutlinedButton(onClick = onClearMonth, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.intention_when_clear)) }
        }
        Text(stringResource(R.string.intention_when_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FormTextField(
        value = form.note, onChange = onNote, label = stringResource(R.string.intention_note),
        error = state.errors[IntentionField.Note], focus = noteFocus, bring = noteBring, hint = stringResource(R.string.intention_note_hint),
    )
    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(if (state.saving) R.string.intention_saving else R.string.intention_save))
    }
    if (!state.isNew) {
        OutlinedButton(onClick = onCancelEdit, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.intention_cancel_edit)) }
    }
    OutlinedButton(onClick = onDiscardDraft, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.intention_discard_draft))
    }
}
