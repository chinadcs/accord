package app.accord.ui.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.label
import app.accord.core.Money
import app.accord.core.space.InvoiceStatus
import app.accord.core.space.InvoiceView
import app.accord.ui.banks.BankLogo
import app.accord.ui.banks.BankPickerDialog
import app.accord.ui.banks.displayName
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

@Composable
fun CardRoute(
    viewModel: CardViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenInvoice: (YearMonth) -> Unit,
    onToConfirm: () -> Unit,
    initialSetup: Boolean = false,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CardScreen(
        content, edit, padding, onBack, onRetry = { viewModel.load() }, onOpenInvoice = onOpenInvoice, onToConfirm = onToConfirm,
        onStartEdit = viewModel::startEdit, onCancelEdit = viewModel::cancelEdit, onChange = viewModel::change, onSave = viewModel::save,
        initialSetup = initialSetup,
    )
}

@Composable
fun CardScreen(
    content: CardContent,
    edit: CardEditState?,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenInvoice: (YearMonth) -> Unit,
    onToConfirm: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onChange: ((CardForm) -> CardForm) -> Unit,
    onSave: () -> Unit,
    initialSetup: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(if (initialSetup) R.string.card_setup_title else R.string.card_title), onBack)
        when (content) {
            CardContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            CardContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            is CardContent.Ready -> {
                if (edit != null) {
                    CardEditor(edit, onChange, onSave, onCancelEdit, initialSetup)
                } else if (content.data.card.configured) {
                    CardSummary(content.data, onStartEdit)
                } else {
                    OutlinedButton(onClick = onStartEdit, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.steps_card_action_pending))
                    }
                }
                if (!initialSetup && content.data.card.configured) {
                    if (content.data.linksToConfirm > 0) ToConfirmRow(content.data.linksToConfirm, onToConfirm)
                    Text(stringResource(R.string.card_invoices), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (content.data.invoices.isEmpty()) {
                        Text(stringResource(R.string.card_no_invoices), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                            Column {
                                content.data.invoices.forEachIndexed { index, invoice ->
                                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = AccordSpacing.s4),
                                        color = MaterialTheme.colorScheme.outlineVariant)
                                    InvoiceRow(invoice) { onOpenInvoice(invoice.dueMonth) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSummary(data: CardData, onStartEdit: () -> Unit) {
    val card = data.card
    Surface(color = Accord.colors.financeSurface, contentColor = Accord.colors.onFinanceSurface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                BankLogo(card.bank)
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                    Text(card.nickname, style = MaterialTheme.typography.titleLarge, color = Accord.colors.onFinanceSurface)
                    Text(
                        card.bank?.displayName() ?: stringResource(R.string.card_bank_none),
                        style = MaterialTheme.typography.bodyMedium, color = Accord.colors.onFinanceSurface,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                    Text(stringResource(R.string.card_closing_day), style = MaterialTheme.typography.labelMedium,
                        color = Accord.colors.onFinanceSurface)
                    Text(card.closingDay.toString(), style = MaterialTheme.typography.headlineSmall,
                        color = Accord.colors.onFinanceSurface)
                }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                    Text(stringResource(R.string.card_due_day), style = MaterialTheme.typography.labelMedium,
                        color = Accord.colors.onFinanceSurface)
                    Text(card.dueDay.toString(), style = MaterialTheme.typography.headlineSmall,
                        color = Accord.colors.onFinanceSurface)
                }
            }
            card.creditLimit?.let { limit ->
                Text(stringResource(R.string.card_credit_limit_value, limit.format()), style = MaterialTheme.typography.bodyLarge,
                    color = Accord.colors.onFinanceSurface)
                val used = data.invoices.filterNot { it.paid }.fold(Money.ZERO) { total, invoice -> total + invoice.knownTotal }
                if (used > limit) {
                    Text(stringResource(R.string.card_credit_limit_exceeded, used.format()),
                        style = MaterialTheme.typography.bodyMedium, color = Accord.colors.onFinanceSurface)
                }
            }
        }
    }
    OutlinedButton(onClick = onStartEdit, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.card_edit))
    }
}

@Composable
private fun CardEditor(edit: CardEditState, onChange: ((CardForm) -> CardForm) -> Unit, onSave: () -> Unit, onCancel: () -> Unit, initialSetup: Boolean) {
    var pickingBank by remember { mutableStateOf(false) }
    val form = edit.form
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(stringResource(R.string.card_edit_title), style = MaterialTheme.typography.titleMedium)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.control).clickable(role = Role.Button) { pickingBank = true },
            ) {
                Row(
                    Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    BankLogo(form.bank)
                    Column {
                        Text(stringResource(R.string.card_bank), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(form.bank?.displayName() ?: stringResource(R.string.bank_none), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Field(stringResource(R.string.card_nickname), form.nickname, edit.errors[CardField.Nickname], KeyboardType.Text) { v -> onChange { it.copy(nickname = v) } }
            Text(stringResource(R.string.nickname_optional_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(stringResource(R.string.card_closing_day), form.closingText, edit.errors[CardField.Closing], KeyboardType.Number) { v ->
                onChange { it.copy(closingText = v.filter(Char::isDigit).take(2)) }
            }
            Field(stringResource(R.string.card_due_day), form.dueText, edit.errors[CardField.Due], KeyboardType.Number) { v ->
                onChange { it.copy(dueText = v.filter(Char::isDigit).take(2)) }
            }
            Field(stringResource(R.string.card_credit_limit), form.creditLimitText, edit.errors[CardField.CreditLimit], KeyboardType.Decimal) { v ->
                onChange { it.copy(creditLimitText = v.filter { char -> char.isDigit() || char == ',' || char == '.' }) }
            }
            Text(stringResource(if (initialSetup) R.string.card_setup_note else R.string.card_edit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            edit.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_cancel)) }
                Button(onClick = onSave, enabled = !edit.saving, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(if (edit.saving) R.string.edit_saving else R.string.card_save))
                }
            }
        }
    }
    if (pickingBank) {
        BankPickerDialog(
            initial = form.bank,
            onPick = { bank -> onChange { it.copy(bank = bank) }; pickingBank = false },
            onDismiss = { pickingBank = false },
        )
    }
}

@Composable
private fun Field(label: String, value: String, error: String?, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToConfirmRow(count: Int, onClick: () -> Unit) {
    Surface(
        color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.onAttentionContainer)
            Text(stringResource(R.string.card_to_confirm, count), color = Accord.colors.onAttentionContainer, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun InvoiceRow(invoice: InvoiceView, onClick: () -> Unit) {
    val total = invoice.checkedTotal ?: invoice.knownTotal
    Column(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowTall).clickable(role = Role.Button, onClick = onClick).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
    ) {
            AdaptiveRow(
                start = { Text(invoice.dueMonth.label(), style = MaterialTheme.typography.titleSmall) },
                end = { Text(total.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Text(invoiceStatusLine(invoice), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Fechada (estimada)" e "Fechada" são coisas diferentes; "conferida" e "paga" vêm separadas do fechamento. */
@Composable
fun invoiceStatusLine(invoice: InvoiceView): String {
    val phase = stringResource(
        when (invoice.status) {
            InvoiceStatus.Open -> R.string.invoice_status_open
            InvoiceStatus.ClosedEstimated -> R.string.invoice_status_closed_estimated
            InvoiceStatus.Closed -> R.string.invoice_status_closed
        },
    )
    val extras = buildList {
        if (invoice.conferred) add(stringResource(R.string.invoice_conferred))
        if (invoice.paid) add(stringResource(R.string.invoice_paid))
        if (invoice.divergentPayments.isNotEmpty() && !invoice.paid) add(stringResource(R.string.invoice_payment_in_review))
    }
    return (listOf(phase) + extras).joinToString(" · ")
}
