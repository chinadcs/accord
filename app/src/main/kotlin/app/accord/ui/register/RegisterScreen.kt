package app.accord.ui.register

import app.accord.ui.components.accordSheetColor
import app.accord.ui.components.AccordCard
import app.accord.ui.components.AccordSheet
import app.accord.ui.components.accordDialogColor
import app.accord.ui.colorKey
import app.accord.ui.categoryColor
import app.accord.ui.incomeCategoryLabel
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.ui.banks.BankLogo
import app.accord.R
import app.accord.core.Frequency
import app.accord.core.label
import app.accord.core.space.AccountInfo
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryNode
import app.accord.ui.categoryPath
import app.accord.ui.components.DateChoices
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.FormTextField
import app.accord.ui.components.PickerField
import app.accord.ui.components.errorText
import app.accord.ui.components.lucideRes
import app.accord.ui.fullLabel
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun RegisterRoute(
    viewModel: RegisterViewModel,
    padding: PaddingValues,
    onExit: () -> Unit,
    /** "Alguém me deve isso" (ADR 0083): leva valor, conta e data ao registro de valor a receber; nulo esconde o switch. */
    onSomeoneOwesMe: ((amount: String, accountId: String?, date: LocalDate) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit) { if (exit) onExit() }
    BackHandler(enabled = !exit) { viewModel.requestBack() }
    RegisterScreen(
        state, padding,
        onChange = viewModel::update, onSave = viewModel::save, onBack = viewModel::requestBack,
        onKeepEditing = viewModel::keepEditing, onSaveDraft = viewModel::saveDraftAndExit, onDiscard = viewModel::discardAndExit,
        onDismissRecovered = viewModel::dismissRecoveredDraft,
        onCreateIncomeCategory = viewModel::createIncomeCategory,
        onSomeoneOwesMe = onSomeoneOwesMe,
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
    onCreateIncomeCategory: (String, String?, (String?) -> Unit) -> Unit = { _, _, done -> done(null) },
    onSomeoneOwesMe: ((amount: String, accountId: String?, date: LocalDate) -> Unit)? = null,
) {
    val form = state.form
    val focus = remember { FormField.entries.associateWith { FocusRequester() } }
    val bring = remember { FormField.entries.associateWith { BringIntoViewRequester() } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Depois de salvar com erro, o foco vai para o primeiro campo errado e os demais dados ficam como estão.
    LaunchedEffect(state.focusTick) {
        val field = state.focusField ?: return@LaunchedEffect
        scope.launch { bring.getValue(field).bringIntoView() }
        runCatching { focus.getValue(field).requestFocus() }
    }

    LaunchedEffect(state.loading, state.autoFocusAmount, state.recoveredDraft) {
        if (!state.loading && state.autoFocusAmount && !state.recoveredDraft &&
            form.description.isBlank() && form.amountText.isBlank()
        ) {
            focus.getValue(FormField.Amount).requestFocus()
            keyboard?.show()
        }
    }

    var picking by remember { mutableStateOf<Picker?>(null) }

    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DetailHeader(
                stringResource(
                    when {
                        form.kind == FormKind.Income -> R.string.register_title_income
                        form.form == ExpenseForm.Card -> R.string.register_title_card
                        else -> R.string.register_title_expense
                    },
                ),
                onBack,
            )

            if (state.loadFailed) {
                Text(stringResource(R.string.register_load_failed), color = MaterialTheme.colorScheme.error)
            }
            if (state.recoveredDraft) RecoveredBanner(onDismissRecovered)
            if (state.replacing) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                    Text(
                        stringResource(R.string.register_replacing_banner), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s3),
                    )
                }
            }
            state.origin?.let { ConversionBanner(it, state.preview.firstOrNull()?.invoiceMonth) }
            state.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            FormTextField(
                value = form.amountText, onChange = { v -> onChange { it.copy(amountText = v) } },
                label = stringResource(R.string.register_amount), hint = stringResource(R.string.register_amount_hint),
                error = state.errors[FormField.Amount], keyboard = KeyboardType.Decimal,
                focus = focus.getValue(FormField.Amount), bring = bring.getValue(FormField.Amount),
                emphasized = true, leadingIcon = Lucide.drawable.lucide_ic_banknote,
            )

            FormTextField(
                value = form.description, onChange = { v -> onChange { it.copy(description = v) } },
                label = stringResource(R.string.register_description), error = state.errors[FormField.Description],
                focus = focus.getValue(FormField.Description), bring = bring.getValue(FormField.Description),
                leadingIcon = Lucide.drawable.lucide_ic_file_text,
            )

            val selectedCategoryIcon = form.categoryId?.let { state.categories[it]?.iconKey }?.let { lucideRes(it) }
            val selectedCategoryColor = categoryColor(state.categories, form.categoryId)
            if (form.kind == FormKind.Expense) {
                PickerField(
                    label = stringResource(R.string.register_category),
                    value = categoryPath(state.categories, form.categoryId) ?: stringResource(R.string.register_category_pick),
                    error = state.errors[FormField.Category], bring = bring.getValue(FormField.Category),
                    leadingContent = selectedCategoryIcon?.let { icon -> { DecorativeIcon(icon, tint = selectedCategoryColor) } },
                    open = picking == Picker.Category,
                ) { keyboard?.hide(); picking = Picker.Category }
            }

            if (form.kind == FormKind.Income) {
                val chosen = state.incomeCategories.firstOrNull { it.id == form.incomeCategoryId }
                PickerField(
                    label = stringResource(R.string.register_income_category),
                    value = chosen?.let { incomeCategoryLabel(it, state.incomeCategories) } ?: stringResource(R.string.register_income_category_pick),
                    error = null, bring = remember { BringIntoViewRequester() },
                    leadingContent = chosen?.let { node -> { DecorativeIcon(lucideRes(node.iconKey), tint = categoryColor(node.colorKey(state.incomeCategories))) } },
                    open = picking == Picker.IncomeCategory,
                ) { keyboard?.hide(); picking = Picker.IncomeCategory }
            }

            val dateLabel = when {
                form.kind == FormKind.Income -> R.string.register_date_expected_receipt
                form.form == ExpenseForm.Boleto -> R.string.register_date_due
                form.form == ExpenseForm.Card -> R.string.register_date_purchase
                else -> R.string.register_date
            }
            DateChoices(
                label = stringResource(dateLabel), date = form.date, today = state.today,
                error = state.errors[FormField.Date], bring = bring.getValue(FormField.Date),
                onPickOther = { picking = Picker.Date(DateTarget.Main) },
                onSelectDate = { date -> onChange { it.copy(date = date) } },
            )

            if (form.kind == FormKind.Expense && form.form == ExpenseForm.Card) {
                Text(stringResource(R.string.import_origin_card), style = MaterialTheme.typography.titleMedium)
                state.cards.filter { it.configured }.forEach { card ->
                    FilterChip(selected = form.cardId == card.id, onClick = { onChange { it.copy(cardId = card.id) } },
                        label = { Text(card.nickname) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin))
                }
            }
            if (form.kind == FormKind.Expense && form.form != ExpenseForm.Card) {
                Text(stringResource(R.string.register_payment), style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    ExpenseForm.entries.filter { it != ExpenseForm.Card }.forEach { option ->
                        FilterChip(
                            selected = form.form == option,
                            onClick = { onChange { it.copy(form = option) } },
                            label = { Text(stringResource(option.label())) },
                            leadingIcon = { DecorativeIcon(expenseFormIcon(option), modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                        )
                    }
                }
            }

            if (form.allowsAccount) {
                val account = state.accounts.firstOrNull { it.id == form.accountId }
                val accountLogo: (@Composable () -> Unit)? = account?.let { selected ->
                    { BankLogo(selected.bank, size = 32.dp) }
                }
                PickerField(
                    label = stringResource(R.string.register_account),
                    value = account?.let { accountLabel(it) }
                        ?: stringResource(if (state.accounts.isEmpty()) R.string.register_account_none else R.string.register_account_pick),
                    error = state.errors[FormField.Account], bring = bring.getValue(FormField.Account),
                    leadingContent = accountLogo,
                    open = picking == Picker.Account,
                ) { picking = Picker.Account }
            }

            MoreOptionsToggle(expanded = form.moreOptions) { onChange { it.copy(moreOptions = !it.moreOptions) } }
            if (form.moreOptions) MoreOptions(state, onChange, focus, bring, onSomeoneOwesMe) { picking = it }

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
        Picker.IncomeCategory -> IncomeCategoryDialog(
            categories = state.incomeCategories, selected = form.incomeCategoryId,
            onPick = { id -> onChange { it.copy(incomeCategoryId = id) }; picking = null },
            onCreate = onCreateIncomeCategory,
            onDismiss = { picking = null },
        )
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
            containerColor = accordDialogColor(),
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
    data object IncomeCategory : Picker
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

@Composable
private fun expenseFormIcon(form: ExpenseForm): Int = when (form) {
    ExpenseForm.Card -> Lucide.drawable.lucide_ic_credit_card
    ExpenseForm.Pix -> lucideRes("qr-code")
    ExpenseForm.Debit -> Lucide.drawable.lucide_ic_credit_card
    ExpenseForm.Cash -> Lucide.drawable.lucide_ic_banknote
    ExpenseForm.Boleto -> Lucide.drawable.lucide_ic_receipt
}

@Composable
private fun MoreOptionsToggle(expanded: Boolean, onClick: () -> Unit) {
    AccordCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowTall),
        shape = MaterialTheme.shapes.large,) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_settings_2, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (expanded) R.string.register_less_options else R.string.register_more_options_short),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.register_more_options_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DecorativeIcon(if (expanded) Lucide.drawable.lucide_ic_chevron_up else Lucide.drawable.lucide_ic_chevron_down)
        }
    }
}

@Composable
private fun OptionHeading(label: String, icon: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        DecorativeIcon(icon, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun accountLabel(account: AccountInfo): String {
    val bank = when (val b = account.bank) {
        is BankRef.Catalog -> b.name
        is BankRef.Other -> b.name
    }
    return "$bank · ${account.nickname}"
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
    onSomeoneOwesMe: ((String, String?, LocalDate) -> Unit)?,
    onPick: (Picker) -> Unit,
) {
    val form = state.form
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            // "Alguém me deve isso": só em Pix e débito (o cartão e o dinheiro não têm conta que receba); ligar leva ao valor a receber.
            if (onSomeoneOwesMe != null && form.kind == FormKind.Expense && (form.form == ExpenseForm.Pix || form.form == ExpenseForm.Debit)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                        .toggleable(value = false, role = Role.Switch) { on -> if (on) onSomeoneOwesMe(form.amountText, form.accountId, form.date) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                        Text(stringResource(R.string.receivable_form_someone_owes), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.receivable_form_someone_owes_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = false, onCheckedChange = null)
                }
            }
            if (form.allowsInstallments) {
                OptionHeading(stringResource(R.string.register_installments), Lucide.drawable.lucide_ic_layers)
                val installmentCount = form.installmentsText.toIntOrNull() ?: 1
                val decreaseLabel = stringResource(R.string.register_installments_decrease)
                val increaseLabel = stringResource(R.string.register_installments_increase)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    IconButton(
                        onClick = {
                            onChange { it.copy(installmentsText = ((it.installmentsText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString()) }
                        },
                        enabled = installmentCount > 1,
                        modifier = Modifier.size(AccordSpacing.touchMin).semantics { contentDescription = decreaseLabel },
                    ) { Icon(painterResource(Lucide.drawable.lucide_ic_minus), contentDescription = null) }
                    FormTextField(
                        value = form.installmentsText, onChange = { v -> onChange { it.copy(installmentsText = v.filter(Char::isDigit)) } },
                        label = stringResource(R.string.register_installments), hint = stringResource(R.string.register_installments_hint),
                        error = state.errors[FormField.Installments], keyboard = KeyboardType.Number,
                        focus = focus.getValue(FormField.Installments), bring = bring.getValue(FormField.Installments),
                        leadingIcon = Lucide.drawable.lucide_ic_layers,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onChange {
                                val current = it.installmentsText.toIntOrNull() ?: 1
                                it.copy(installmentsText = (if (current < Int.MAX_VALUE) current + 1 else current).toString())
                            }
                        },
                        enabled = installmentCount < Int.MAX_VALUE,
                        modifier = Modifier.size(AccordSpacing.touchMin).semantics { contentDescription = increaseLabel },
                    ) { Icon(painterResource(Lucide.drawable.lucide_ic_plus), contentDescription = null) }
                }
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

            OptionHeading(stringResource(R.string.register_repeat), Lucide.drawable.lucide_ic_repeat)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
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
                    DateChoices(
                        label = stringResource(if (boleto) R.string.register_settled_date_boleto else R.string.register_settled_date_income),
                        date = form.settledDate, today = state.today, error = state.errors[FormField.SettledDate],
                        bring = bring.getValue(FormField.SettledDate),
                        onPickOther = { onPick(Picker.Date(DateTarget.Settled)) },
                        onSelectDate = { date -> onChange { it.copy(settledDate = date) } },
                    )
                    FormTextField(
                        value = form.settledAmountText, onChange = { v -> onChange { it.copy(settledAmountText = v) } },
                        label = stringResource(R.string.register_settled_amount), hint = stringResource(R.string.register_settled_amount_hint),
                        error = state.errors[FormField.SettledAmount], keyboard = KeyboardType.Decimal,
                        focus = focus.getValue(FormField.SettledAmount), bring = bring.getValue(FormField.SettledAmount),
                        leadingIcon = Lucide.drawable.lucide_ic_banknote,
                    )
                }
            }
        }
    }
}

/** Fatura estimada (com "a conferir" quando cai na janela do fechamento) e cronograma das parcelas. */
/**
 * Modo conversão (S7): diz de qual intenção nasce o compromisso, que só passa a existir ao salvar e que a intenção fica vinculada a ele. Se a fatura
 * atribuída pelas regras do aplicativo for outra que a simulada, mostra a diferença antes de salvar. Nenhuma data de "quando pretende" vira data da compra.
 */
@Composable
private fun ConversionBanner(origin: ConversionOrigin, assignedInvoice: java.time.YearMonth?) {
    androidx.compose.material3.Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.onAttentionContainer)
                Text(
                    stringResource(R.string.conversion_title, origin.name.orEmpty()),
                    style = MaterialTheme.typography.titleSmall, color = Accord.colors.onAttentionContainer,
                    modifier = Modifier.semantics { heading() },
                )
            }
            when (origin.problem) {
                OriginProblem.NotFound -> Text(stringResource(R.string.conversion_not_found), color = Accord.colors.onAttentionContainer)
                OriginProblem.AlreadyConverted -> Text(stringResource(R.string.conversion_already), color = Accord.colors.onAttentionContainer)
                OriginProblem.None -> {
                    Text(stringResource(R.string.conversion_impact), color = Accord.colors.onAttentionContainer)
                    Text(stringResource(R.string.conversion_other_phone), style = MaterialTheme.typography.bodySmall, color = Accord.colors.onAttentionContainer)
                }
            }
            val simulated = origin.simulatedInvoice
            if (simulated != null && assignedInvoice != null && simulated != assignedInvoice) {
                Text(
                    stringResource(R.string.conversion_invoice_differs, assignedInvoice.label(), simulated.label()),
                    style = MaterialTheme.typography.bodyMedium, color = Accord.colors.onAttentionContainer,
                )
            }
        }
    }
}

@Composable
private fun InvoiceAndSchedule(state: RegisterUiState, onCorrectInvoice: () -> Unit, onUseEstimate: () -> Unit) {
    val first = state.preview.firstOrNull() ?: return
    val form = state.form
    AccordCard {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDialog(categories: Map<String, CategoryNode>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val mains = categories.values.filter { it.parentId == null }.sortedBy { it.order }
    val selectedNode = selected?.let(categories::get)
    var parentId by remember(selected) { mutableStateOf(selectedNode?.parentId ?: selectedNode?.id) }
    val parent = parentId?.let(categories::get)
    val children = parent?.let { node -> categories.values.filter { it.parentId == node.id }.sortedBy { it.order } }.orEmpty()

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = accordSheetColor(),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.register_category), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(if (parent == null) R.string.register_category_choose_parent else R.string.register_category_choose_child),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.register_picker_close))
                }
            }

            if (parent == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    items(mains, key = { it.id }) { main ->
                        val subcategories = categories.values.any { it.parentId == main.id }
                        CategoryTile(
                            name = main.name, iconKey = main.iconKey, color = categoryColor(main.colorKey(categories)),
                            selected = selected == main.id || selectedNode?.parentId == main.id,
                            onClick = {
                                if (subcategories) parentId = main.id else onPick(main.id)
                            },
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    IconButton(onClick = { parentId = null }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                        Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
                    }
                    DecorativeIcon(lucideRes(parent.iconKey), tint = categoryColor(parent.colorKey(categories)))
                    Text(parent.name, style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = { onPick(parent.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.register_category_use_parent, parent.name), style = MaterialTheme.typography.bodyLarge) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    items(children, key = { it.id }) { child ->
                        CategoryTile(child.name, child.iconKey, categoryColor(child.colorKey(categories)), selected == child.id) { onPick(child.id) }
                    }
                }
            }
        }
    }
}

/**
 * Quadro de uma categoria: ícone na cor da categoria principal (a subcategoria herda a do pai) e nome. O mesmo quadro
 * serve ao seletor do registro manual e às sugestões do Chat; [caption] identifica o pai de uma subcategoria.
 */
@Composable
internal fun CategoryTile(
    name: String,
    iconKey: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 96.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) AccordSpacing.s1 else AccordSpacing.s2, vertical = AccordSpacing.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            // Selecionado, o ícone fica sobre um disco sólido da superfície: a cor da categoria mantém contraste sobre o destaque.
            Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.surface else color.copy(alpha = 0.16f)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    DecorativeIcon(lucideRes(iconKey), modifier = Modifier.size(28.dp), tint = color)
                }
            }
            Text(
                name, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, maxLines = 2,
            )
            if (caption != null) {
                Text(
                    caption, style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun AccountDialog(accounts: List<AccountInfo>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AccordSheet(title = stringResource(R.string.register_account), onRequestClose = onDismiss, footer = {}) {
        if (accounts.isEmpty()) Text(stringResource(R.string.register_account_none))
        accounts.forEach { account ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                    .selectable(selected = selected == account.id, onClick = { onPick(account.id) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                RadioButton(selected = selected == account.id, onClick = null)
                BankLogo(account.bank, size = 32.dp)
                Text(accountLabel(account), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            }
        }
    }
}

/** Escolhe o mês de vencimento da fatura, quando a estimada não é a certa. */
@Composable
private fun InvoiceDialog(initial: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(initial) }
    AccordSheet(
        title = stringResource(R.string.register_invoice_title), onRequestClose = onDismiss,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_date_pick_cancel))
                }
                Button(onClick = { onPick(month) }, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.register_date_pick_ok))
                }
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.month_previous))
            }
            Text(month.label(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.month_next))
            }
        }
    }
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
