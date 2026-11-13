package app.accord.ui.edit

import app.accord.ui.components.accordDialogColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.BudgetClock
import app.accord.ui.banks.BankLogo
import app.accord.ui.categoryColor
import app.accord.ui.colorKey
import app.accord.ui.incomeCategoryLabel
import app.accord.ui.components.AccordSheet
import app.accord.ui.components.DateChoices
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.core.RecurrenceScope
import app.accord.core.label
import app.accord.core.space.ExtraReason
import app.accord.core.space.PaymentMethod
import app.accord.ui.categoryPath
import app.accord.ui.fullLabel
import app.accord.ui.label
import app.accord.ui.register.AccountDialog
import app.accord.ui.register.CategoryDialog
import app.accord.ui.register.DatePickerSheet
import app.accord.ui.register.IncomeCategoryDialog
import app.accord.ui.components.FormTextField
import app.accord.ui.components.PickerField
import app.accord.ui.register.RegisterForm
import app.accord.ui.register.accountLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun EditEntryRoute(
    viewModel: EditEntryViewModel,
    padding: PaddingValues,
    onExit: () -> Unit,
    /** "Excluir e registrar de novo": abre o registro preenchido; a compra original só é excluída quando a nova for salva. */
    onReRegister: (form: RegisterForm, replaces: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit) { if (exit) onExit() }
    BackHandler(enabled = !exit) { viewModel.requestBack() }
    EditEntryScreen(
        state, padding,
        onChange = viewModel::update, onSave = { viewModel.save() }, onBack = viewModel::requestBack,
        onConfirmPrompt = viewModel::confirmPrompt, onDismissPrompt = viewModel::dismissPrompt,
        onRetry = viewModel::load,
        onCreateIncomeCategory = viewModel::createIncomeCategory,
        onSaveWithScope = viewModel::saveWithScope, onDismissScope = viewModel::dismissScopePanel,
        // O id a excluir é o da compra (a rota pode ser o de uma parcela, "compra#n").
        onReRegister = { viewModel.reRegisterForm()?.let { form -> viewModel.state.value.original?.let { onReRegister(form, it.entityId) } } },
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
    onRetry: () -> Unit,
    onCreateIncomeCategory: (String, String?, (String?) -> Unit) -> Unit = { _, _, _ -> },
    onSaveWithScope: (RecurrenceScope) -> Unit = {},
    onDismissScope: () -> Unit = {},
    onReRegister: () -> Unit = {},
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
            DetailHeader(original?.let { stringResource(editTitle(it)) } ?: stringResource(R.string.register_edit_title), onBack)

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
                    ContextNotice(original)
                    Fields(state, original, form, onChange, focus, bring, picking) { picking = it }
                    Notes(state, original, form)
                    if (original.target == EditTarget.CardPurchase && original.installments > 1) ReRegister(onReRegister)
                }
            }
        }

        if (original != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    if (state.nothingChanged) {
                        Text(stringResource(R.string.edit_nothing_changed), style = MaterialTheme.typography.bodyMedium)
                    }
                    val saveLabel = when {
                        state.saving -> stringResource(R.string.edit_saving)
                        original.target == EditTarget.CardPurchase && original.installments > 1 -> stringResource(R.string.edit_save_installments, original.installments)
                        else -> stringResource(R.string.edit_save)
                    }
                    Button(
                        onClick = onSave, enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
                    ) { Text(saveLabel) }
                }
            }
        }
    }

    if (form != null) {
        when (picking) {
            null -> Unit
            Picker.Date -> DatePickerSheet(
                initial = form.date ?: LocalDate.now(),
                onPick = { picked -> onChange { it.copy(date = picked) }; picking = null },
                onDismiss = { picking = null },
            )
            Picker.Category -> CategoryDialog(state.categories, form.categoryId, onPick = { id ->
                onChange { it.copy(categoryId = id) }; picking = null
            }, onDismiss = { picking = null })
            Picker.IncomeCategory -> IncomeCategoryDialog(
                categories = state.incomeCategories, selected = form.incomeCategoryId,
                onPick = { id -> onChange { it.copy(incomeCategoryId = id) }; picking = null },
                onCreate = onCreateIncomeCategory, onDismiss = { picking = null },
                allowNone = original?.incomeCategoryId == null,
            )
            Picker.Account -> AccountDialog(state.accounts, form.accountId, onPick = { id ->
                onChange { it.copy(accountId = id) }; picking = null
            }, onDismiss = { picking = null })
        }
    }

    if (state.scopePanel && original != null && form != null) {
        ScopePanel(original, changedNames(original, form), onSave = onSaveWithScope, onCancel = onDismissScope)
    }

    PromptDialogs(state, onConfirmPrompt, onDismissPrompt)
}

private enum class Picker { Date, Category, IncomeCategory, Account }

/** Título pelo tipo (spec 25). */
private fun editTitle(o: EditOriginal): Int = when (o.target) {
    EditTarget.Direct -> R.string.edit_title_expense
    EditTarget.CardPurchase -> if (o.installments > 1) R.string.edit_title_installment else R.string.edit_title_card
    EditTarget.Boleto -> R.string.edit_title_boleto
    EditTarget.Income -> R.string.edit_title_income
    EditTarget.Occurrence -> if (o.incomeSeries) R.string.edit_title_recurring_income else R.string.edit_title_recurring_expense
}

/** Aviso do alto: o que esta edição alcança, antes de qualquer campo. */
@Composable
private fun ContextNotice(original: EditOriginal) {
    val text = when {
        original.target == EditTarget.Occurrence ->
            stringResource(R.string.edit_notice_occurrence, original.scheduledDate?.fullLabel().orEmpty())
        original.target == EditTarget.CardPurchase && original.installments > 1 ->
            stringResource(R.string.edit_notice_installments, original.installments)
        else -> return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s3))
    }
}

/** Mesma ordem do registro: Valor, Descrição, Categoria, Data, Pagamento, Conta (spec 25). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Fields(
    state: EditUiState,
    original: EditOriginal,
    form: EditForm,
    onChange: ((EditForm) -> EditForm) -> Unit,
    focus: Map<EditField, FocusRequester>,
    bring: Map<EditField, BringIntoViewRequester>,
    picking: Picker?,
    onPick: (Picker) -> Unit,
) {
    if (original.amountEditable) {
        FormTextField(
            value = form.amountText, onChange = { v -> onChange { it.copy(amountText = v) } },
            label = stringResource(R.string.register_amount), hint = stringResource(R.string.register_amount_hint),
            error = state.errors[EditField.Amount], keyboard = KeyboardType.Decimal,
            focus = focus.getValue(EditField.Amount), bring = bring.getValue(EditField.Amount),
            emphasized = true, leadingIcon = com.composables.icons.lucide.R.drawable.lucide_ic_banknote,
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
    FormTextField(
        value = form.description, onChange = { v -> onChange { it.copy(description = v) } },
        label = stringResource(R.string.register_description), error = state.errors[EditField.Description],
        focus = focus.getValue(EditField.Description), bring = bring.getValue(EditField.Description),
        leadingIcon = com.composables.icons.lucide.R.drawable.lucide_ic_file_text,
    )
    if (original.categoryEditable) {
        PickerField(
            label = stringResource(R.string.register_category),
            value = categoryPath(state.categories, form.categoryId) ?: stringResource(R.string.register_category_pick),
            error = state.errors[EditField.Category], bring = bring.getValue(EditField.Category),
            leadingContent = form.categoryId?.let { state.categories[it]?.iconKey }?.let { key ->
                { DecorativeIcon(lucideRes(key), tint = categoryColor(state.categories, form.categoryId)) }
            },
            open = picking == Picker.Category,
        ) { onPick(Picker.Category) }
    }
    if (original.incomeCategoryEditable) {
        val chosen = state.incomeCategories.firstOrNull { it.id == form.incomeCategoryId }
        PickerField(
            label = stringResource(R.string.register_income_category),
            value = chosen?.let { incomeCategoryLabel(it, state.incomeCategories) } ?: stringResource(R.string.register_income_category_pick),
            error = state.errors[EditField.IncomeCategory], bring = bring.getValue(EditField.IncomeCategory),
            leadingContent = chosen?.let { node -> { DecorativeIcon(lucideRes(node.iconKey), tint = categoryColor(node.colorKey(state.incomeCategories))) } },
            open = picking == Picker.IncomeCategory,
        ) { onPick(Picker.IncomeCategory) }
    }
    if (original.dateEditable) {
        val label = when (original.target) {
            EditTarget.Boleto -> R.string.register_date_due
            EditTarget.Income -> R.string.register_date_expected_receipt
            EditTarget.CardPurchase -> R.string.register_date_purchase
            else -> R.string.register_date
        }
        val today = LocalDate.now(BudgetClock.ZONE)
        DateChoices(
            label = stringResource(label), date = form.date ?: today, today = today,
            error = state.errors[EditField.Date], bring = bring.getValue(EditField.Date),
            onPickOther = { onPick(Picker.Date) }, onSelectDate = { date -> onChange { it.copy(date = date) } },
        )
    } else if (original.target == EditTarget.Occurrence && original.scheduledDate != null) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.register_date_due), style = MaterialTheme.typography.titleSmall)
            Text(original.scheduledDate.fullLabel(), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.edit_date_by_recurrence), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (original.methodEditable && form.method != null) {
        Text(stringResource(R.string.register_payment), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            listOf(PaymentMethod.Pix, PaymentMethod.Debit, PaymentMethod.Cash).forEach { option ->
                FilterChip(
                    selected = form.method == option,
                    onClick = { onChange { it.copy(method = option) } },
                    label = { Text(option.label()) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
            }
        }
    }
    if (original.target == EditTarget.CardPurchase) {
        state.cards.firstOrNull { it.id == original.cardId }?.let { card ->
            Text(stringResource(R.string.edit_card_locked, card.nickname), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (original.accountEditableFor(form.method ?: original.method) && original.target != EditTarget.CardPurchase) {
        val account = state.accounts.firstOrNull { it.id == form.accountId }
        PickerField(
            label = stringResource(R.string.register_account),
            value = account?.let { accountLabel(it) } ?: stringResource(R.string.register_account_pick),
            error = state.errors[EditField.Account], bring = bring.getValue(EditField.Account),
            leadingContent = account?.let { selected -> { BankLogo(selected.bank, size = 32.dp) } },
            open = picking == Picker.Account,
        ) { onPick(Picker.Account) }
    }
}

/** Nomes, em minúsculas, do que mudou: "Você mudou: valor e categoria". */
@Composable
private fun changedNames(original: EditOriginal, form: EditForm): String {
    val names = buildList {
        if (form.description.trim() != original.description) add(stringResource(R.string.edit_changed_description))
        if (original.amountEditable && form.amountText.trim() != original.amount.formatPlain()) add(stringResource(R.string.edit_changed_amount))
        if (original.categoryEditable && form.categoryId != original.categoryId) add(stringResource(R.string.edit_changed_category))
        if (original.incomeCategoryEditable && form.incomeCategoryId != original.incomeCategoryId) add(stringResource(R.string.edit_changed_income_category))
        if (original.accountEditable && form.accountId != original.accountId) add(stringResource(R.string.edit_changed_account))
    }
    return names.joinToString(", ")
}

/**
 * Painel "Aplicar a quais ocorrências?": nenhuma opção marcada, Salvar inativo até escolher, e cada opção diz a data de corte.
 * Nasce já como painel inferior (spec 25); a escolha fica aqui e só vira alcance do formulário ao salvar.
 */
@Composable
private fun ScopePanel(original: EditOriginal, changed: String, onSave: (RecurrenceScope) -> Unit, onCancel: () -> Unit) {
    var chosen by remember { mutableStateOf<RecurrenceScope?>(null) }
    val date = original.scheduledDate?.fullLabel().orEmpty()
    AccordSheet(
        title = stringResource(R.string.edit_scope_title), onRequestClose = onCancel,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.edit_cancel))
                }
                Button(
                    onClick = { chosen?.let(onSave) }, enabled = chosen != null,
                    modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.edit_scope_save)) }
            }
        },
    ) {
        if (changed.isNotBlank()) Text(stringResource(R.string.edit_scope_changed, changed), style = MaterialTheme.typography.bodyLarge)
        listOf(
            RecurrenceScope.OnlyThis to stringResource(R.string.edit_scope_only_this_dated, date),
            RecurrenceScope.ThisAndNext to stringResource(R.string.edit_scope_this_and_next_dated, date),
        ).forEach { (value, label) -> ScopeRow(label, chosen == value) { chosen = value } }
        Text(stringResource(R.string.edit_scope_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** "Excluir e registrar de novo": a compra parcelada não muda valor nem parcelas, então o caminho é registrar outra (spec 25). */
@Composable
private fun ReRegister(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.edit_reregister))
        }
        Text(stringResource(R.string.edit_reregister_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** O que a edição não muda, o que ela vai tocar e o que a data indicaria: tudo antes de gravar. */
@Composable
private fun Notes(state: EditUiState, original: EditOriginal, form: EditForm) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (original.target == EditTarget.CardPurchase && original.installments > 1) {
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
private fun PromptDialogs(state: EditUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
            confirm = R.string.edit_realized_confirm, onConfirm = onConfirm, onDismiss = onDismiss,
        )
        EditPrompt.Stale -> ConfirmDialog(
            title = R.string.edit_stale_title, body = { Text(stringResource(R.string.edit_stale_body)) },
            confirm = R.string.edit_stale_confirm, onConfirm = onConfirm, onDismiss = onDismiss,
        )
        EditPrompt.Discard -> ConfirmDialog(
            title = R.string.edit_discard_title, body = { Text(stringResource(R.string.edit_discard_body)) },
            confirm = R.string.edit_discard_confirm, dismiss = R.string.edit_discard_keep, onConfirm = onConfirm, onDismiss = onDismiss,
        )
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
        containerColor = accordDialogColor(),
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
