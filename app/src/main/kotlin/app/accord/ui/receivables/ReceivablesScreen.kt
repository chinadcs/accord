package app.accord.ui.receivables

import app.accord.ui.components.AccordCard
import app.accord.ui.components.accordDialogColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.analysis.formatSigned
import app.accord.core.space.DebtorSummary
import app.accord.core.space.IncomeView
import app.accord.core.space.ReceivableView
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.lucideRes
import app.accord.ui.register.DatePickerSheet
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun LocalDate.full(): String = format(FULL_DATE)

/** Iniciais para o avatar: a primeira letra de até duas palavras do nome. */
internal fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
fun ReceivablesRoute(vm: ReceivablesViewModel, padding: PaddingValues, onBack: () -> Unit, onNew: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    RefreshOnResume(vm::refresh)
    val detail = state.selected
    BackHandler(enabled = state.selectedDebtorId != null) { vm.select(null) }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(detail?.debtor?.name ?: stringResource(R.string.receivables_title), if (state.selectedDebtorId != null) ({ vm.select(null) }) else onBack)
        when {
            state.loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            state.failed && state.overview.debtors.isEmpty() -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = vm::refresh, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            state.selectedDebtorId != null && detail == null -> Text(stringResource(R.string.receivables_missing), style = MaterialTheme.typography.bodyLarge)
            detail != null -> DebtorDetail(detail, state, vm)
            else -> DebtorList(state, vm::select, onNew)
        }
    }
    dialog?.let { ReceivableDialogs(it, vm) }
}

// ---- lista ----

@Composable
private fun DebtorList(state: ReceivablesUiState, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val overview = state.overview
    if (overview.debtors.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.receivables_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.receivables_empty_body), style = MaterialTheme.typography.bodyLarge)
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivables_new)) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.receivables_to_receive), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(overview.toReceive.formatSigned(), style = MaterialTheme.typography.headlineMedium)
        Text(
            pluralStringResource(R.plurals.receivables_people, overview.debtors.size, overview.debtors.size),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivables_new)) }
    val (open, settled) = overview.debtors.partition { it.open.cents > 0 }
    open.forEach { DebtorRow(it, onOpen) }
    if (settled.isNotEmpty()) {
        Text(stringResource(R.string.receivables_settled_group), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        settled.forEach { DebtorRow(it, onOpen) }
    }
}

@Composable
private fun Avatar(name: String, size: Int = 44) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            Text(initialsOf(name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun agreedLabel(next: LocalDate?): String =
    if (next == null) stringResource(R.string.receivables_when_possible) else stringResource(R.string.receivables_agreed_for, next.shortLabel())

@Composable
private fun DebtorRow(summary: DebtorSummary, onOpen: (String) -> Unit) {
    val agreed = agreedLabel(summary.nextDate)
    val description = "${summary.debtor.name}. ${stringResource(R.string.receivables_open_label)} ${summary.open.format()}. $agreed"
    AccordCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .clickable(role = Role.Button) { onOpen(summary.debtor.id) }
                .padding(AccordSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Avatar(summary.debtor.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(summary.debtor.name, style = MaterialTheme.typography.titleMedium)
                if (summary.open.cents > 0) Text(agreed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(summary.open.formatSigned(), style = Accord.typography.amountRow, textAlign = TextAlign.End)
        }
    }
}

// ---- detalhe ----

@Composable
private fun DebtorDetail(summary: DebtorSummary, state: ReceivablesUiState, vm: ReceivablesViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Avatar(summary.debtor.name, size = 56)
        Column(Modifier.weight(1f)) {
            Text(summary.debtor.name, style = MaterialTheme.typography.titleLarge)
            val first = summary.receivables.firstOrNull()
            if (first != null) {
                Text(
                    stringResource(R.string.receivables_registered_on, first.registeredDate.full(), state.accountName(first.accountId)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { vm.openRename(summary.debtor) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.receivables_rename))
        }
    }
    summary.receivables.forEach { ReceivableCard(summary.debtor.name, it, state, vm) }
}

@Composable
private fun ReceivableCard(debtorName: String, r: ReceivableView, state: ReceivablesUiState, vm: ReceivablesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        AccordCard(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                AdaptiveRow(
                    start = {
                        Column {
                            Text(stringResource(R.string.receivables_open_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.open.formatSigned(), style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    end = {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                            Text(agreedLabel(r.scheduled.firstOrNull()?.expectedDate), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = AccordSpacing.s3, vertical = AccordSpacing.s1))
                        }
                    },
                )
                val progressText = stringResource(R.string.receivables_progress_description, r.resolved.format(), r.total.format())
                LinearProgressIndicator(
                    progress = { if (r.total.cents == 0L) 0f else (r.resolved.cents.toDouble() / r.total.cents).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressText },
                    color = Accord.colors.income,
                )
                AdaptiveRow(
                    start = { Text(stringResource(R.string.receivables_resolved_of, r.resolved.formatSigned()), style = MaterialTheme.typography.bodyMedium, color = Accord.colors.income) },
                    end = { Text(stringResource(R.string.receivables_of_total, r.total.format()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                if (r.countsInBudget) {
                    Text(stringResource(R.string.receivables_counts_badge), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.openReceive(r) }, enabled = r.open.cents > 0, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                        DecorativeIcon(Lucide.drawable.lucide_ic_arrow_down_left, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(AccordSpacing.s1))
                        Text(stringResource(R.string.receivables_received))
                    }
                    OutlinedButton(onClick = { vm.openReschedule(r) }, enabled = r.scheduled.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                        DecorativeIcon(Lucide.drawable.lucide_ic_calendar)
                        Spacer(Modifier.width(AccordSpacing.s1))
                        Text(stringResource(R.string.receivables_reschedule))
                    }
                }
            }
        }
        ScheduleCard(r, vm)
        EntriesSection(debtorName, r, state)
        HistorySection(r, vm)
        if (r.receipts.isEmpty()) {
            TextButton(onClick = { vm.openCancel(r) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.receivables_cancel_receivable))
            }
        }
    }
}

@Composable
private fun ScheduleCard(r: ReceivableView, vm: ReceivablesViewModel) {
    AccordCard(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            AdaptiveRow(
                start = { Text(stringResource(R.string.receivables_schedule_title), style = MaterialTheme.typography.titleMedium) },
                end = {
                    TextButton(onClick = { vm.openEditSchedule(r) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.receivables_schedule_edit))
                    }
                },
            )
            if (r.scheduled.isEmpty()) Text(stringResource(R.string.receivables_schedule_empty), style = MaterialTheme.typography.bodyMedium)
            r.scheduled.forEachIndexed { index, slot ->
                AdaptiveRow(
                    start = { Text("${index + 1}   ${slot.expectedDate.full()}", style = MaterialTheme.typography.bodyLarge) },
                    end = { Text(slot.expectedAmount.formatSigned(), style = Accord.typography.amountRow, color = Accord.colors.income, textAlign = TextAlign.End) },
                )
            }
            if (r.unscheduled.cents > 0 && r.scheduled.isNotEmpty()) {
                Text(stringResource(R.string.receivables_unscheduled, r.unscheduled.format()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.receivables_schedule_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntriesSection(debtorName: String, r: ReceivableView, state: ReceivablesUiState) {
    Text(stringResource(R.string.receivables_entries_title), style = MaterialTheme.typography.titleMedium)
    val account = state.accountName(r.accountId)
    if (r.outflow) {
        EntryLine(stringResource(R.string.receivables_entry_lent, debtorName), stringResource(R.string.receivables_entry_lent_sub, account), r.registeredDate, r.total.format(), Accord.colors.expense, "arrow-up-right")
    }
    r.receipts.forEach {
        EntryLine(
            stringResource(R.string.receivables_entry_receipt, debtorName), stringResource(R.string.receivables_entry_receipt_sub, account),
            it.receivedDate ?: it.expectedDate, (it.receivedAmount ?: it.expectedAmount).format(), Accord.colors.income, "arrow-down-left",
        )
    }
    r.scheduled.forEach {
        EntryLine(
            stringResource(R.string.receivables_entry_receipt, debtorName), stringResource(R.string.receivables_entry_agreed_sub, account),
            it.expectedDate, it.expectedAmount.format(), Accord.colors.income, "arrow-down-left",
        )
    }
}

@Composable
private fun EntryLine(title: String, subtitle: String, date: LocalDate, amount: String, amountColor: androidx.compose.ui.graphics.Color, direction: String) {
    val description = "$title. $subtitle. ${date.full()}. $amount"
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { DecorativeIcon(lucideRes(direction), tint = amountColor) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(date.full(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = Accord.typography.amountRow, color = amountColor)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HistorySection(r: ReceivableView, vm: ReceivablesViewModel) {
    AccordCard(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.receivables_history_title), style = MaterialTheme.typography.titleMedium)
            if (r.receipts.isEmpty()) Text(stringResource(R.string.receivables_history_empty), style = MaterialTheme.typography.bodyMedium)
            var running = r.total.cents
            // "Saldo após": o que faltava logo depois de cada recebimento, do mais antigo ao mais novo.
            val lines = r.receipts.map { income ->
                running -= (income.receivedAmount ?: income.expectedAmount).cents
                income to Money(running.coerceAtLeast(0))
            }
            lines.reversed().forEach { (income, after) -> HistoryLine(income, after, vm) }
        }
    }
}

@Composable
private fun HistoryLine(income: IncomeView, after: Money, vm: ReceivablesViewModel) {
    val date = (income.receivedDate ?: income.expectedDate).full()
    val removeLabel = stringResource(R.string.receivables_remove_receipt_description, date)
    Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.receivables_history_line, date), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.receivables_history_after, after.format()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text((income.receivedAmount ?: income.expectedAmount).format(), style = Accord.typography.amountRow, color = Accord.colors.income)
        IconButton(onClick = { vm.openRemoveReceipt(income) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
            Icon(painterResource(lucideRes("trash-2")), contentDescription = removeLabel)
        }
    }
}

// ---- painéis de ação ----

@Composable
private fun ReceivableDialogs(dialog: ReceivableDialog, vm: ReceivablesViewModel) {
    when (dialog) {
        is ReceivableDialog.Receive -> ReceiveDialog(dialog, vm)
        is ReceivableDialog.Reschedule -> RescheduleDialog(dialog, vm)
        is ReceivableDialog.EditSchedule -> EditScheduleDialog(dialog, vm)
        is ReceivableDialog.RemoveReceipt -> ConfirmDialog(
            title = R.string.receivables_remove_title,
            body = stringResource(
                R.string.receivables_remove_body,
                (dialog.receipt.receivedAmount ?: dialog.receipt.expectedAmount).format(), (dialog.receipt.receivedDate ?: dialog.receipt.expectedDate).full(),
            ),
            confirm = R.string.receivables_confirm_remove, dialog = dialog, vm = vm,
        )
        is ReceivableDialog.Cancel -> ConfirmDialog(
            title = R.string.receivables_cancel_title, body = stringResource(R.string.receivables_cancel_body),
            confirm = R.string.receivables_confirm_cancel, dialog = dialog, vm = vm,
        )
        is ReceivableDialog.Rename -> AlertDialog(
            containerColor = accordDialogColor(),
            onDismissRequest = vm::close,
            title = { Text(stringResource(R.string.receivables_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    OutlinedTextField(
                        value = dialog.name, onValueChange = vm::setName, singleLine = true, enabled = !dialog.working,
                        label = { Text(stringResource(R.string.receivables_rename_label)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    dialog.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = vm::confirm, enabled = !dialog.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = vm::close, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: String, confirm: Int, dialog: ReceivableDialog, vm: ReceivablesViewModel) {
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = vm::close,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(body, style = MaterialTheme.typography.bodyLarge)
                dialog.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = vm::confirm, enabled = !dialog.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = vm::close, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivables_keep)) } },
    )
}

@Composable
private fun ReceiveDialog(d: ReceivableDialog.Receive, vm: ReceivablesViewModel) {
    var pickingDate by remember { mutableStateOf(false) }
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = vm::close,
        title = { Text(stringResource(R.string.receivables_receive_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.receivables_receive_hint, d.receivable.open.format()), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = d.amount, onValueChange = vm::setAmount, singleLine = true, enabled = !d.working,
                    label = { Text(stringResource(R.string.receivables_receive_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                DateButton(stringResource(R.string.receivables_receive_date), d.date) { pickingDate = true }
                d.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = vm::confirm, enabled = !d.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivables_receive_confirm)) } },
        dismissButton = { TextButton(onClick = vm::close, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingDate) DatePickerSheet(d.date, onPick = { vm.setDate(it); pickingDate = false }, onDismiss = { pickingDate = false })
}

@Composable
private fun DateButton(label: String, date: LocalDate, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = "$label: ${date.full()}" },
    ) {
        Text("$label: ${date.full()}")
    }
}

@Composable
private fun RescheduleDialog(d: ReceivableDialog.Reschedule, vm: ReceivablesViewModel) {
    var pickingDate by remember { mutableStateOf(false) }
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = vm::close,
        title = { Text(stringResource(R.string.receivables_reschedule_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                val income = d.income
                if (income == null) {
                    Text(stringResource(R.string.receivables_reschedule_none), style = MaterialTheme.typography.bodyLarge)
                } else {
                    d.receivable.scheduled.forEach { slot ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                                .selectable(selected = slot.id == income.id, role = Role.RadioButton) { vm.pickIncome(slot) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = slot.id == income.id, onClick = null)
                            Text(
                                stringResource(R.string.receivables_reschedule_which, slot.expectedAmount.format(), slot.expectedDate.full()),
                                modifier = Modifier.padding(start = AccordSpacing.s2), style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    DateButton(stringResource(R.string.receivables_reschedule_new_date), d.date) { pickingDate = true }
                    listOf(false to R.string.receivables_reschedule_only, true to R.string.receivables_reschedule_following).forEach { (shift, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(selected = d.shiftFollowing == shift, role = Role.RadioButton) { vm.setShift(shift) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = d.shiftFollowing == shift, onClick = null)
                            Text(stringResource(label), modifier = Modifier.padding(start = AccordSpacing.s2), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                d.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::confirm, enabled = !d.working && d.income != null, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.receivables_reschedule_confirm))
            }
        },
        dismissButton = { TextButton(onClick = vm::close, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingDate) DatePickerSheet(d.date, onPick = { vm.setDate(it); pickingDate = false }, onDismiss = { pickingDate = false })
}

@Composable
private fun EditScheduleDialog(d: ReceivableDialog.EditSchedule, vm: ReceivablesViewModel) {
    var pickingIndex by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = vm::close,
        title = { Text(stringResource(R.string.receivables_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.receivables_edit_hint, d.receivable.open.format()), style = MaterialTheme.typography.bodyMedium)
                d.rows.forEachIndexed { index, row ->
                    val number = index + 1
                    val dateLabel = stringResource(R.string.receivables_slot_date, number)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        OutlinedButton(
                            onClick = { pickingIndex = index },
                            modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = "$dateLabel: ${row.date.full()}" },
                        ) { Text(row.date.shortLabel()) }
                        OutlinedTextField(
                            value = row.amount, onValueChange = { vm.setSlotAmount(index, it) }, singleLine = true, enabled = !d.working,
                            label = { Text(stringResource(R.string.receivables_slot_amount, number)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1.3f),
                        )
                        IconButton(onClick = { vm.removeSlot(index) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                            Icon(painterResource(lucideRes("trash-2")), contentDescription = stringResource(R.string.receivables_remove_slot, number))
                        }
                    }
                }
                TextButton(onClick = vm::addSlot, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.receivables_add_slot)) }
                d.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = vm::confirm, enabled = !d.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = vm::close, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_cancel)) } },
    )
    pickingIndex?.let { index ->
        d.rows.getOrNull(index)?.let { row ->
            DatePickerSheet(row.date, onPick = { vm.setSlotDate(index, it); pickingIndex = null }, onDismiss = { pickingIndex = null })
        }
    }
}
