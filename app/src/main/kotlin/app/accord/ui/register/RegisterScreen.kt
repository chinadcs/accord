package app.accord.ui.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.Frequency
import app.accord.core.label
import app.accord.core.space.AccountInfo
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryNode
import app.accord.ui.categoryPath
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.fullLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun RegisterRoute(viewModel: RegisterViewModel, padding: PaddingValues, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit) { if (exit) onExit() }
    BackHandler(enabled = !exit) { viewModel.requestBack() }
    RegisterScreen(
        state, padding,
        onChange = viewModel::update, onSave = viewModel::save, onBack = viewModel::requestBack,
        onKeepEditing = viewModel::keepEditing, onSaveDraft = viewModel::saveDraftAndExit, onDiscard = viewModel::discardAndExit,
        onDismissRecovered = viewModel::dismissRecoveredDraft,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    padding: PaddingValues,
    onChange: ((RegisterForm) -> RegisterForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onKeepEditing: () -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
    onDismissRecovered: () -> Unit,
) {
    val form = state.form
    val focus = remember { FormField.entries.associateWith { FocusRequester() } }
    val bring = remember { FormField.entries.associateWith { BringIntoViewRequester() } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Depois de salvar com erro, o foco vai para o primeiro campo errado e os demais dados ficam como estão.
    LaunchedEffect(state.focusTick) {
        val field = state.focusField ?: return@LaunchedEffect
        scope.launch { bring.getValue(field).bringIntoView() }
        runCatching { focus.getValue(field).requestFocus() }
    }

    var picking by remember { mutableStateOf<Picker?>(null) }

    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DetailHeader(stringResource(R.string.register_title), onBack)

            if (state.loadFailed) {
                Text(stringResource(R.string.register_load_failed), color = MaterialTheme.colorScheme.error)
            }
            if (state.recoveredDraft) RecoveredBanner(onDismissRecovered)
            state.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            // Tipo: despesa ou renda
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(FormKind.Expense to R.string.register_kind_expense, FormKind.Income to R.string.register_kind_income)
                    .forEachIndexed { index, (kind, label) ->
                        SegmentedButton(
                            selected = form.kind == kind,
                            onClick = { onChange { it.copy(kind = kind) } },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                        ) { Text(stringResource(label)) }
                    }
            }

            if (form.kind == FormKind.Expense) {
                Text(stringResource(R.string.register_payment), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    ExpenseForm.entries.forEach { option ->
                        FilterChip(
                            selected = form.form == option,
                            onClick = { onChange { it.copy(form = option) } },
                            label = { Text(stringResource(option.label())) },
                            modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                        )
                    }
                }
            }

            FormTextField(
                value = form.description, onChange = { v -> onChange { it.copy(description = v) } },
                label = stringResource(R.string.register_description), error = state.errors[FormField.Description],
                focus = focus.getValue(FormField.Description), bring = bring.getValue(FormField.Description),
            )
            FormTextField(
                value = form.amountText, onChange = { v -> onChange { it.copy(amountText = v) } },
                label = stringResource(R.string.register_amount), hint = stringResource(R.string.register_amount_hint),
                error = state.errors[FormField.Amount], keyboard = KeyboardType.Decimal,
                focus = focus.getValue(FormField.Amount), bring = bring.getValue(FormField.Amount),
                emphasized = true,
            )

            val dateLabel = when {
                form.kind == FormKind.Income -> R.string.register_date_expected
                form.form == ExpenseForm.Boleto -> R.string.register_date_due
                else -> R.string.register_date
            }
            PickerField(
                label = stringResource(dateLabel), value = form.date.fullLabel(), error = state.errors[FormField.Date],
                bring = bring.getValue(FormField.Date),
            ) { picking = Picker.Date(DateTarget.Main) }

            if (form.kind == FormKind.Expense) {
                PickerField(
                    label = stringResource(R.string.register_category),
                    value = categoryPath(state.categories, form.categoryId) ?: stringResource(R.string.register_category_pick),
                    error = state.errors[FormField.Category], bring = bring.getValue(FormField.Category),
                ) { picking = Picker.Category }
            }

            if (form.allowsAccount) {
                val account = state.accounts.firstOrNull { it.id == form.accountId }
                PickerField(
                    label = stringResource(R.string.register_account),
                    value = account?.let { accountLabel(it) }
                        ?: stringResource(if (state.accounts.isEmpty()) R.string.register_account_none else R.string.register_account_pick),
                    error = state.errors[FormField.Account], bring = bring.getValue(FormField.Account),
                ) { picking = Picker.Account }
            }

            OutlinedButton(
                onClick = { onChange { it.copy(moreOptions = !it.moreOptions) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
            ) {
                Text(stringResource(if (form.moreOptions) R.string.register_less_options else R.string.register_more_options))
            }
            if (form.moreOptions) MoreOptions(state, onChange, focus, bring) { picking = it }

            InvoiceAndSchedule(state, onCorrectInvoice = { picking = Picker.Invoice }, onUseEstimate = { onChange { it.copy(invoice = null) } })
            MonthChange(form)
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Button(
                onClick = onSave,
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s4).heightIn(min = AccordSpacing.touchMin),
            ) { Text(stringResource(if (state.saving) R.string.register_saving else R.string.register_save)) }
        }
    }

    when (val p = picking) {
        null -> Unit
        is Picker.Date -> DatePickerSheet(
            initial = when (p.target) { DateTarget.Main -> form.date; DateTarget.Settled -> form.settledDate ?: form.date },
            onPick = { picked ->
                onChange { f -> if (p.target == DateTarget.Main) f.copy(date = picked) else f.copy(settledDate = picked) }
                picking = null
            },
            onDismiss = { picking = null },
        )
        Picker.Category -> CategoryDialog(state.categories, form.categoryId, onPick = { id ->
            onChange { it.copy(categoryId = id) }
            picking = null
        }, onDismiss = { picking = null })
        Picker.Account -> AccountDialog(state.accounts, form.accountId, onPick = { id ->
            onChange { it.copy(accountId = id) }
            picking = null
        }, onDismiss = { picking = null })
        Picker.Invoice -> InvoiceDialog(
            initial = form.invoice ?: state.preview.firstOrNull()?.invoiceMonth ?: YearMonth.from(form.date),
            onPick = { month -> onChange { it.copy(invoice = month) }; picking = null },
            onDismiss = { picking = null },
        )
    }

    if (state.askKeepOrDiscard) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text(stringResource(R.string.register_back_title)) },
            text = { Text(stringResource(R.string.register_back_body)) },
            confirmButton = {
                TextButton(onClick = onSaveDraft, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_back_draft))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.register_back_discard), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onKeepEditing, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.register_back_keep))
                    }
                }
            },
        )
    }
}

private sealed interface Picker {
    data class Date(val target: DateTarget) : Picker
    data object Category : Picker
    data object Account : Picker
    data object Invoice : Picker
}

private enum class DateTarget { Main, Settled }

private fun ExpenseForm.label() = when (this) {
    ExpenseForm.Card -> R.string.register_form_card
    ExpenseForm.Pix -> R.string.register_form_pix
    ExpenseForm.Debit -> R.string.register_form_debit
    ExpenseForm.Cash -> R.string.register_form_cash
    ExpenseForm.Boleto -> R.string.register_form_boleto
}

internal fun accountLabel(account: AccountInfo): String {
    val bank = when (val b = account.bank) {
        is BankRef.Catalog -> b.name
        is BankRef.Other -> b.name
    }
    return "$bank · ${account.nickname}"
}

@Composable
internal fun errorText(error: FormError?): String? = when (error) {
    null -> null
    FormError.Required -> stringResource(R.string.error_required)
    FormError.AmountEmpty -> stringResource(R.string.error_amount_empty)
    FormError.AmountNegative -> stringResource(R.string.error_amount_negative)
    FormError.AmountAmbiguous -> stringResource(R.string.error_amount_ambiguous)
    FormError.AmountInvalid -> stringResource(R.string.error_amount_invalid)
    FormError.AmountTooLarge -> stringResource(R.string.error_amount_too_large)
    FormError.MustBePositive -> stringResource(R.string.error_must_be_positive)
    FormError.InstallmentsInvalid -> stringResource(R.string.error_installments_invalid)
    FormError.MeaningRequired -> stringResource(R.string.error_meaning_required)
    FormError.AccountRequired -> stringResource(R.string.error_account_required)
    FormError.AccountNotAllowed -> stringResource(R.string.error_account_not_allowed)
    FormError.CategoryRequired -> stringResource(R.string.error_category_required)
    FormError.ScopeRequired -> stringResource(R.string.error_scope_required)
    is FormError.FromFacade -> error.message
}

/** Campo de texto com o erro junto dele; o erro também chega ao TalkBack como parte do campo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: FormError?,
    focus: FocusRequester,
    bring: BringIntoViewRequester,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    emphasized: Boolean = false,
) {
    val message = errorText(error)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        isError = message != null,
        supportingText = message?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = if (emphasized) Accord.typography.amountHero else MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring).focusRequester(focus),
    )
}

/** Campo que abre um seletor; a linha inteira é o alvo de toque e anuncia rótulo e valor. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickerField(label: String, value: String, error: FormError?, bring: BringIntoViewRequester, onClick: () -> Unit) {
    val message = errorText(error)
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, value, message).joinToString(". ") },
        ) {
            Column(Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2), verticalArrangement = Arrangement.Center) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = AccordSpacing.s4, top = AccordSpacing.s1)) }
    }
}

@Composable
private fun RecoveredBanner(onDismiss: () -> Unit) {
    Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.onAttentionContainer)
                Text(stringResource(R.string.register_draft_recovered), color = Accord.colors.onAttentionContainer)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.register_draft_discard))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MoreOptions(
    state: RegisterUiState,
    onChange: ((RegisterForm) -> RegisterForm) -> Unit,
    focus: Map<FormField, FocusRequester>,
    bring: Map<FormField, BringIntoViewRequester>,
    onPick: (Picker) -> Unit,
) {
    val form = state.form
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            if (form.allowsInstallments) {
                FormTextField(
                    value = form.installmentsText, onChange = { v -> onChange { it.copy(installmentsText = v.filter(Char::isDigit)) } },
                    label = stringResource(R.string.register_installments), hint = stringResource(R.string.register_installments_hint),
                    error = state.errors[FormField.Installments], keyboard = KeyboardType.Number,
                    focus = focus.getValue(FormField.Installments), bring = bring.getValue(FormField.Installments),
                )
                if (form.asksAmountMeaning) {
                    Text(stringResource(R.string.register_meaning_title), style = MaterialTheme.typography.titleMedium)
                    listOf(AmountMeaning.Total to R.string.register_meaning_total, AmountMeaning.PerInstallment to R.string.register_meaning_each)
                        .forEach { (meaning, label) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                                    .selectable(selected = form.amountMeaning == meaning, role = Role.RadioButton) { onChange { it.copy(amountMeaning = meaning) } },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = form.amountMeaning == meaning, onClick = null)
                                Text(stringResource(label), modifier = Modifier.padding(start = AccordSpacing.s2))
                            }
                        }
                    state.errors[FormField.AmountMeaning]?.let {
                        Text(errorText(it).orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.bringIntoViewRequester(bring.getValue(FormField.AmountMeaning)))
                    }
                }
            }

            Text(stringResource(R.string.register_repeat), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                listOf<Pair<Frequency?, Int>>(
                    null to R.string.register_repeat_none, Frequency.Monthly to R.string.register_repeat_monthly,
                    Frequency.Annual to R.string.register_repeat_annual,
                ).forEach { (freq, label) ->
                    FilterChip(
                        selected = form.frequency == freq, onClick = { onChange { it.copy(frequency = freq) } },
                        label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
            if (form.frequency != null) {
                Text(stringResource(R.string.register_repeat_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (form.kind == FormKind.Income || form.form == ExpenseForm.Boleto) {
                val boleto = form.kind == FormKind.Expense
                Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (boleto) R.string.register_settled_boleto else R.string.register_settled_income),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = form.settledDate != null,
                        onCheckedChange = { on -> onChange { it.copy(settledDate = if (on) it.date else null, settledAmountText = if (on) it.settledAmountText else "") } },
                    )
                }
                if (form.settledDate != null) {
                    PickerField(
                        label = stringResource(if (boleto) R.string.register_settled_date_boleto else R.string.register_settled_date_income),
                        value = form.settledDate.fullLabel(), error = state.errors[FormField.SettledDate], bring = bring.getValue(FormField.SettledDate),
                    ) { onPick(Picker.Date(DateTarget.Settled)) }
                    FormTextField(
                        value = form.settledAmountText, onChange = { v -> onChange { it.copy(settledAmountText = v) } },
                        label = stringResource(R.string.register_settled_amount), hint = stringResource(R.string.register_settled_amount_hint),
                        error = state.errors[FormField.SettledAmount], keyboard = KeyboardType.Decimal,
                        focus = focus.getValue(FormField.SettledAmount), bring = bring.getValue(FormField.SettledAmount),
                    )
                }
            }
        }
    }
}

/** Fatura estimada (com "a conferir" quando cai na janela do fechamento) e cronograma das parcelas. */
@Composable
private fun InvoiceAndSchedule(state: RegisterUiState, onCorrectInvoice: () -> Unit, onUseEstimate: () -> Unit) {
    val first = state.preview.firstOrNull() ?: return
    val form = state.form
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (form.invoice != null) {
                Text(stringResource(R.string.register_invoice_informed, first.invoiceMonth.label()), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onUseEstimate, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_invoice_estimate))
                }
            } else {
                val check = state.previewToConfirm
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    if (check) DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.attention)
                    Text(
                        stringResource(
                            if (check) R.string.register_invoice_estimated_check else R.string.register_invoice_estimated,
                            first.invoiceMonth.label(),
                        ),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        color = if (check) Accord.colors.attention else MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = onCorrectInvoice, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_invoice_correct))
                }
            }
            if (state.preview.size > 1) {
                Text(stringResource(R.string.register_schedule_title), style = MaterialTheme.typography.titleMedium)
                state.preview.forEach { i ->
                    Text(
                        stringResource(R.string.register_schedule_line, i.number, state.preview.size, i.amount.format(), i.invoiceMonth.label()),
                        style = Accord.typography.amountRow,
                    )
                }
            }
        }
    }
}

/** Boleto pago ou renda recebida noutro mês: mostra a mudança de competência antes de salvar. */
@Composable
private fun MonthChange(form: RegisterForm) {
    val settled = form.settledDate ?: return
    val from = YearMonth.from(form.date)
    val to = YearMonth.from(settled)
    if (from == to) return
    val verb = stringResource(if (form.kind == FormKind.Income) R.string.register_month_change_received else R.string.register_month_change_paid)
    Text(
        stringResource(R.string.register_month_change, from.label(), verb, to.label()),
        style = MaterialTheme.typography.bodyMedium, color = Accord.colors.attention,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerSheet(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            ) { Text(stringResource(R.string.register_date_pick_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.register_date_pick_cancel))
            }
        },
    ) { DatePicker(state = pickerState) }
}

@Composable
internal fun CategoryDialog(categories: Map<String, CategoryNode>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val mains = categories.values.filter { it.parentId == null }.sortedBy { it.order }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.register_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                mains.forEach { main ->
                    ChoiceRow(main.name, selected == main.id, indent = false) { onPick(main.id) }
                    categories.values.filter { it.parentId == main.id }.sortedBy { it.order }.forEach { sub ->
                        ChoiceRow(sub.name, selected == sub.id, indent = true) { onPick(sub.id) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.register_picker_close))
            }
        },
    )
}

@Composable
internal fun AccountDialog(accounts: List<AccountInfo>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.register_account)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (accounts.isEmpty()) Text(stringResource(R.string.register_account_none))
                accounts.forEach { ChoiceRow(accountLabel(it), selected == it.id, indent = false) { onPick(it.id) } }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.register_picker_close))
            }
        },
    )
}

/** Escolhe o mês de vencimento da fatura, quando a estimada não é a certa. */
@Composable
private fun InvoiceDialog(initial: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.register_invoice_title)) },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.month_previous))
                }
                Text(month.label(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.month_next))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(month) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.register_date_pick_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.register_date_pick_cancel))
            }
        },
    )
}

@Composable
internal fun ChoiceRow(name: String, selected: Boolean, indent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = if (indent) AccordSpacing.s5 else AccordSpacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}
