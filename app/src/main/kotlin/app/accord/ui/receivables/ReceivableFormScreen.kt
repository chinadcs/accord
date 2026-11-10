package app.accord.ui.receivables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.lucideRes
import app.accord.ui.incomeCategoryLabel
import app.accord.ui.register.AccountDialog
import app.accord.ui.register.CategoryDialog
import app.accord.ui.register.DatePickerSheet
import app.accord.ui.register.IncomeCategoryDialog
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private enum class Picking { Account, IncomeCategory, ExpenseCategory, Date, Start, Slot }

/**
 * Novo valor a receber (spec 39). [onBackToExpense] é o "Alguém me deve isso" desligado: volta ao registro de despesa
 * (nulo quando a tela foi aberta por Quem me deve, onde o switch só avisa que está ligado).
 */
@Composable
fun ReceivableFormRoute(
    vm: ReceivableFormViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onBackToExpense: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(done) { if (done) onDone() }
    val f = state.form
    var picking by remember { mutableStateOf<Picking?>(null) }
    var slotIndex by remember { mutableStateOf(0) }
    val total = state.total
    val sum = f.rows.sumOf { (Money.parse(it.amount) as? MoneyParse.Ok)?.money?.cents ?: 0 }

    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.receivable_form_title), onBack)
        OutlinedTextField(
            value = f.amount, onValueChange = vm::setAmount, singleLine = true, enabled = !state.working,
            label = { Text(stringResource(R.string.receivable_form_amount)) },
            supportingText = {
                Text(stringResource(if (f.counts) R.string.receivable_form_amount_note_counted else R.string.receivable_form_amount_note))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        PickRow(
            stringResource(R.string.receivable_form_account),
            state.accounts.firstOrNull { it.id == f.accountId }?.nickname ?: stringResource(R.string.receivable_form_account_pick),
        ) { picking = Picking.Account }
        OutlinedTextField(
            value = f.debtorName, onValueChange = vm::setName, singleLine = true, enabled = !state.working,
            label = { Text(stringResource(R.string.receivable_form_who)) },
            placeholder = { Text(stringResource(R.string.receivable_form_who_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        val suggestions = state.debtors.filter { it.name.contains(f.debtorName.trim(), ignoreCase = true) && !it.name.equals(f.debtorName.trim(), ignoreCase = true) }.take(6)
        if (suggestions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                suggestions.forEach { debtor ->
                    FilterChip(selected = false, onClick = { vm.setName(debtor.name) }, label = { Text(debtor.name) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin))
                }
            }
        }
        PickRow(stringResource(R.string.receivable_form_date), f.date.format(FULL_DATE)) { picking = Picking.Date }
        PickRow(
            stringResource(R.string.receivable_form_income_category),
            f.incomeCategoryId?.let { id -> state.incomeCategories.firstOrNull { it.id == id }?.let { incomeCategoryLabel(it, state.incomeCategories) } }
                ?: stringResource(R.string.register_income_category_pick),
        ) { picking = Picking.IncomeCategory }

        HorizontalDivider()
        Text(stringResource(R.string.receivable_form_schedule), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.receivable_form_schedule_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            listOf(
                ScheduleMode.WhenPossible to R.string.receivable_form_mode_when,
                ScheduleMode.Single to R.string.receivable_form_mode_single,
                ScheduleMode.Installments to R.string.receivable_form_mode_installments,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = f.mode == mode, onClick = { vm.setMode(mode) }, label = { Text(stringResource(label)) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { role = Role.RadioButton },
                )
            }
        }
        if (f.mode == ScheduleMode.Installments) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                IconButton(onClick = { vm.setInstallments(f.installments - 1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(lucideRes("minus")), contentDescription = stringResource(R.string.receivable_form_installments_less))
                }
                Text("${f.installments}×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "${f.installments}×" })
                IconButton(onClick = { vm.setInstallments(f.installments + 1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(lucideRes("plus")), contentDescription = stringResource(R.string.receivable_form_installments_more))
                }
                TextButton(onClick = { picking = Picking.Start }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.receivable_form_start, f.start.shortLabel()))
                }
                TextButton(onClick = vm::splitEvenly, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivable_form_split)) }
            }
        }
        if (f.mode == ScheduleMode.Single) {
            TextButton(onClick = { picking = Picking.Start }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.receivable_form_start, f.start.shortLabel()))
            }
        }
        f.rows.forEachIndexed { index, row ->
            val number = index + 1
            val dateLabel = stringResource(R.string.receivables_slot_date, number)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                TextButton(
                    onClick = { slotIndex = index; picking = Picking.Slot },
                    modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = "$dateLabel: ${row.date.format(FULL_DATE)}" },
                ) { Text("$number  ${row.date.shortLabel()}") }
                OutlinedTextField(
                    value = row.amount, onValueChange = { vm.setSlotAmount(index, it) }, singleLine = true, enabled = !state.working,
                    label = { Text(stringResource(R.string.receivables_slot_amount, number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1.3f),
                )
                IconButton(onClick = { vm.removeSlot(index) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(lucideRes("trash-2")), contentDescription = stringResource(R.string.receivables_remove_slot, number))
                }
            }
        }
        if (f.mode != ScheduleMode.WhenPossible) {
            TextButton(onClick = vm::addSlot, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivables_add_slot)) }
            if (total != null) SumLine(sum, total.cents)
        }

        HorizontalDivider()
        SwitchRow(R.string.receivable_form_no_outflow, R.string.receivable_form_no_outflow_body, f.noOutflow, !state.working, vm::setNoOutflow)
        SwitchRow(R.string.receivable_form_counts, R.string.receivable_form_counts_body, f.counts, !state.working, vm::setCounts)
        if (f.counts && !f.noOutflow) {
            PickRow(
                stringResource(R.string.receivable_form_expense_category),
                f.expenseCategoryId?.let { state.categories[it]?.name } ?: stringResource(R.string.receivable_form_expense_category_pick),
            ) { picking = Picking.ExpenseCategory }
        }
        SwitchRow(R.string.receivable_form_someone_owes, R.string.receivable_form_someone_owes_body, true, onBackToExpense != null && !state.working) { on ->
            if (!on) onBackToExpense?.invoke()
        }
        OutlinedTextField(
            value = f.note, onValueChange = vm::setNote, enabled = !state.working,
            label = { Text(stringResource(R.string.receivable_form_note)) }, modifier = Modifier.fillMaxWidth(),
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = { vm.save { id -> resources.getString(id) } }, enabled = !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
        ) { Text(stringResource(R.string.receivable_form_save)) }
    }

    when (picking) {
        Picking.Account -> AccountDialog(state.accounts, f.accountId, onPick = { vm.setAccount(it); picking = null }, onDismiss = { picking = null })
        Picking.IncomeCategory -> IncomeCategoryDialog(
            categories = state.incomeCategories, selected = f.incomeCategoryId,
            onPick = { vm.setIncomeCategory(it); picking = null }, onCreate = vm::createIncomeCategory, onDismiss = { picking = null },
        )
        Picking.ExpenseCategory -> CategoryDialog(state.categories, f.expenseCategoryId, onPick = { vm.setExpenseCategory(it); picking = null }, onDismiss = { picking = null })
        Picking.Date -> DatePickerSheet(f.date, onPick = { vm.setDate(it); picking = null }, onDismiss = { picking = null })
        Picking.Start -> DatePickerSheet(f.start, onPick = { vm.setStart(it); picking = null }, onDismiss = { picking = null })
        Picking.Slot -> f.rows.getOrNull(slotIndex)?.let { row ->
            DatePickerSheet(row.date, onPick = { vm.setSlotDate(slotIndex, it); picking = null }, onDismiss = { picking = null })
        }
        null -> Unit
    }
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
            .selectable(selected = false, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SumLine(sum: Long, total: Long) {
    val money = Money(sum).format()
    val totalText = Money(total).format()
    val status = when {
        sum == total -> stringResource(R.string.receivable_form_sum_ok)
        sum < total -> stringResource(R.string.receivable_form_sum_missing, Money(total - sum).format())
        else -> stringResource(R.string.receivable_form_sum_over, Money(sum - total).format())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.receivable_form_sum, money, totalText), style = MaterialTheme.typography.bodyMedium)
        Text(status, style = MaterialTheme.typography.bodyMedium, color = if (sum > total) MaterialTheme.colorScheme.error else Accord.colors.success)
    }
}

@Composable
private fun SwitchRow(title: Int, body: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
