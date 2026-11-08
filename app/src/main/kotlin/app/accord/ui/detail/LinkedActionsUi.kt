package app.accord.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.accord.core.Money
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.core.label
import app.accord.core.space.RefundStatus
import app.accord.ui.fullLabel
import app.accord.ui.register.DatePickerSheet
import app.accord.ui.register.PickerField
import app.accord.ui.theme.AccordSpacing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.selection.toggleable

/** Botões das ações vinculadas: pagar, receber, estornar, pedir cancelamento. Só as que valem para este lançamento. */
@Composable
fun LinkedActionsBlock(actions: List<LinkedAction>, onOpen: (LinkedAction) -> Unit) {
    if (actions.isEmpty()) return
    Text(stringResource(R.string.linked_actions_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    actions.forEach { action ->
        OutlinedButton(
            onClick = { onOpen(action) },
            modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
        ) { Text(actionLabel(action)) }
    }
}

@Composable
private fun actionLabel(action: LinkedAction): String = when (action) {
    is LinkedAction.Settle -> stringResource(if (action.receiving) R.string.linked_receive else R.string.linked_pay)
    is LinkedAction.NewRefund -> stringResource(R.string.linked_refund)
    is LinkedAction.ConfirmRefundOf -> stringResource(R.string.linked_refund_confirm, action.refund.amount.format())
    is LinkedAction.OccurrenceInvoice -> stringResource(if (action.confirmed) R.string.linked_invoice_correct else R.string.linked_invoice_confirm)
    is LinkedAction.Cancellation -> stringResource(if (action.nonCharge) R.string.linked_non_charge else R.string.linked_cancel_request)
}

@Composable
private fun actionTitle(action: LinkedAction): String = when (action) {
    is LinkedAction.Settle -> stringResource(if (action.receiving) R.string.linked_receive_title else R.string.linked_pay_title)
    is LinkedAction.NewRefund -> stringResource(R.string.linked_refund_title)
    is LinkedAction.ConfirmRefundOf -> stringResource(R.string.linked_refund_confirm_title)
    is LinkedAction.OccurrenceInvoice -> stringResource(R.string.linked_invoice_title)
    is LinkedAction.Cancellation -> stringResource(if (action.nonCharge) R.string.linked_non_charge_title else R.string.linked_cancel_title)
}

@Composable
private fun errorText(error: LinkedError?): String? = when (error) {
    null -> null
    LinkedError.AmountEmpty -> stringResource(R.string.error_amount_empty)
    LinkedError.AmountInvalid -> stringResource(R.string.error_amount_invalid)
    LinkedError.AmountAmbiguous -> stringResource(R.string.error_amount_ambiguous)
    LinkedError.MustBePositive -> stringResource(R.string.error_must_be_positive)
    LinkedError.StatusRequired -> stringResource(R.string.error_refund_status)
    LinkedError.NothingSelected -> stringResource(R.string.error_installments_pick)
    LinkedError.InvoiceRequired -> stringResource(R.string.error_invoice_pick)
}

/** Formulário curto da ação vinculada; a recusa da fachada aparece dentro dele, sem fechar nem apagar o que foi digitado. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkedActionDialog(
    state: LinkedState,
    onChange: ((LinkedForm) -> LinkedForm) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val action = state.action
    val form = state.form
    var pickingDate by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val bodyTopPadding = if (LocalDensity.current.fontScale >= 1.5f) AccordSpacing.s4 else 0.dp

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(actionTitle(action)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(top = bodyTopPadding),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                when (action) {
                    is LinkedAction.Settle -> {
                        DateField(
                            if (action.receiving) R.string.linked_date_received else R.string.linked_date_paid, form, bring,
                        ) { pickingDate = true }
                        AmountField(
                            label = stringResource(R.string.linked_amount_effective),
                            hint = stringResource(R.string.linked_amount_effective_hint, action.expectedAmount.format()),
                            value = form.amountText, error = errorText(state.errors[LinkedField.Amount]),
                        ) { v -> onChange { it.copy(amountText = v) } }
                    }
                    is LinkedAction.NewRefund -> {
                        AmountField(
                            label = stringResource(R.string.linked_amount_refund), hint = null, value = form.amountText,
                            error = errorText(state.errors[LinkedField.Amount]),
                        ) { v -> onChange { it.copy(amountText = v) } }
                        Text(stringResource(R.string.linked_refund_status), style = MaterialTheme.typography.titleMedium)
                        listOf(
                            RefundStatus.Confirmed to R.string.linked_refund_status_confirmed,
                            RefundStatus.Requested to R.string.linked_refund_status_requested,
                        ).forEach { (status, label) ->
                            ChoiceRow(stringResource(label), form.refundStatus == status) { onChange { it.copy(refundStatus = status) } }
                        }
                        errorText(state.errors[LinkedField.Status])?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (form.refundStatus == RefundStatus.Confirmed) DateField(R.string.linked_date_refund, form, bring) { pickingDate = true }
                        Text(stringResource(R.string.linked_refund_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is LinkedAction.ConfirmRefundOf -> {
                        AmountField(
                            label = stringResource(R.string.linked_amount_returned), hint = null, value = form.amountText,
                            error = errorText(state.errors[LinkedField.Amount]),
                        ) { v -> onChange { it.copy(amountText = v) } }
                        DateField(R.string.linked_date_refund, form, bring) { pickingDate = true }
                    }
                    is LinkedAction.OccurrenceInvoice -> {
                        Text(stringResource(R.string.linked_invoice_pick), style = MaterialTheme.typography.titleMedium)
                        action.options.forEach { month ->
                            val label = when (month) {
                                action.estimated -> stringResource(R.string.linked_invoice_option_estimated, month.label())
                                else -> month.label()
                            }
                            ChoiceRow(label, form.invoice == month) { onChange { it.copy(invoice = month) } }
                        }
                        errorText(state.errors[LinkedField.Invoice])?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            stringResource(R.string.linked_invoice_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is LinkedAction.Cancellation -> {
                        Text(stringResource(R.string.linked_cancel_pick), style = MaterialTheme.typography.titleMedium)
                        action.candidates.forEach { number ->
                            val checked = number in form.selected
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                                    .toggleable(value = checked, role = Role.Checkbox) { on ->
                                        onChange { it.copy(selected = if (on) it.selected + number else it.selected - number) }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(stringResource(R.string.linked_installment_line, number), modifier = Modifier.padding(start = AccordSpacing.s2))
                            }
                        }
                        errorText(state.errors[LinkedField.Installments])?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            stringResource(if (action.nonCharge) R.string.linked_non_charge_note else R.string.linked_cancel_note),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (state.stale) Text(stringResource(R.string.linked_stale), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (state.failed) Text(stringResource(R.string.linked_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !state.working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (state.working) R.string.linked_working else R.string.linked_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.edit_cancel)) }
        },
    )

    if (pickingDate) {
        DatePickerSheet(
            initial = form.date,
            onPick = { picked -> onChange { it.copy(date = picked) }; pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateField(label: Int, form: LinkedForm, bring: BringIntoViewRequester, onClick: () -> Unit) {
    PickerField(label = stringResource(label), value = form.date.fullLabel(), error = null, bring = bring, onClick = onClick)
}

@Composable
private fun AmountField(label: String, hint: String?, value: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, placeholder = hint?.let { { Text(it) } },
        isError = error != null, supportingText = error?.let { { Text(it) } }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}

/**
 * Interruptor Recebido/Pago. A linha inteira é o alvo (48 dp) e o TalkBack lê rótulo e estado juntos; o texto abaixo diz o que
 * acontece ao ligar ou o que foi registrado, para o estado nunca depender só do interruptor.
 */
@Composable
fun SettleSwitchCard(settle: SettleSwitch, amount: Money, onToggle: (Boolean) -> Unit, onAdjust: () -> Unit) {
    val label = stringResource(if (settle.receiving) R.string.settle_switch_received else R.string.settle_switch_paid)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                    .toggleable(value = settle.on, role = Role.Switch, onValueChange = onToggle)
                    .testTag("settle-switch"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = settle.on, onCheckedChange = null)
            }
            if (settle.on) {
                val date = settle.doneDate?.fullLabel().orEmpty()
                val res = if (settle.receiving) R.string.settle_switch_received_on else R.string.settle_switch_paid_on
                Text(
                    stringResource(res, date, (settle.doneAmount ?: amount).format()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.settle_switch_off_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(R.string.settle_switch_off_summary, settle.expectedAmount.format()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAdjust, modifier = Modifier.heightIn(min = AccordSpacing.touchMin).testTag("settle-adjust")) {
                    Text(stringResource(R.string.settle_switch_adjust))
                }
            }
        }
    }
}
