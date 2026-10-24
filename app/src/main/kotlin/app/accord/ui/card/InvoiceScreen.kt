package app.accord.ui.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.space.CardInfo
import app.accord.core.space.InvoiceStatus
import app.accord.core.space.InvoiceView
import app.accord.core.space.InvoiceTotalSource
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.fullLabel
import app.accord.ui.register.DatePickerSheet
import app.accord.ui.register.PickerField
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

@Composable
fun InvoiceRoute(
    viewModel: InvoiceViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onSeeEntries: (YearMonth) -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    InvoiceScreen(
        viewModel.month, content, dialog, padding, onBack, onRetry = { viewModel.load() }, onSeeEntries = onSeeEntries,
        onOpenTotal = viewModel::openTotal, onOpenPay = viewModel::openPay, onChange = viewModel::change,
        onSubmit = viewModel::submit, onClose = viewModel::close,
    )
}

@Composable
fun InvoiceScreen(
    month: YearMonth,
    content: InvoiceContent,
    dialog: InvoiceDialogState?,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSeeEntries: (YearMonth) -> Unit,
    onOpenTotal: () -> Unit,
    onOpenPay: () -> Unit,
    onChange: (amountText: String?, date: java.time.LocalDate?) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.invoice_title, month.label()), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (content) {
            InvoiceContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            InvoiceContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            is InvoiceContent.Ready -> InvoiceBody(content.invoice, content.card, onSeeEntries, onOpenTotal, onOpenPay)
        }
    }
    dialog?.let { InvoiceDialogs(it, onChange, onSubmit, onClose) }
}

@Composable
private fun InvoiceBody(
    invoice: InvoiceView,
    card: CardInfo,
    onSeeEntries: (YearMonth) -> Unit,
    onOpenTotal: () -> Unit,
    onOpenPay: () -> Unit,
) {
    val body = MaterialTheme.typography.bodyLarge
    val due = invoice.dueMonth.atDay(card.dueDay.coerceAtMost(invoice.dueMonth.lengthOfMonth()))
    Text(stringResource(R.string.invoice_due_line, due.fullLabel(), invoiceStatusLine(invoice)), style = body)

    // Total conferido só existe depois que alguém o informou; sem ele, a composição é só o que o app conhece.
    val checked = invoice.checkedTotal
    if (checked != null) {
        AdaptiveRow(
            start = { Text(stringResource(R.string.invoice_checked_total), style = MaterialTheme.typography.titleMedium) },
            end = { Text(checked.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
        )
        Text(
            stringResource(if (invoice.checkedSource == InvoiceTotalSource.Import) R.string.invoice_source_import else R.string.invoice_source_person),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    invoice.totalDifference?.takeIf { !invoice.conferred }?.let {
        Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(AccordSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.onAttentionContainer)
                Text(stringResource(R.string.invoice_difference, app.accord.core.Money(kotlin.math.abs(it.cents)).format()), color = Accord.colors.onAttentionContainer)
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.invoice_composition), style = MaterialTheme.typography.titleMedium)
            Line(R.string.invoice_purchases, invoice.purchases.format())
            Line(R.string.invoice_charges, invoice.charges.format())
            Line(R.string.invoice_credits, "-" + invoice.credits.format())
            Line(R.string.invoice_known_total, invoice.knownTotal.format(), strong = true)
        }
    }

    if (invoice.paid) {
        Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(Lucide.drawable.lucide_ic_circle_check)
            Text(stringResource(R.string.invoice_paid_on, invoice.paidDate?.fullLabel().orEmpty()), style = body)
        }
    }
    if (invoice.linksToConfirm > 0) {
        Text(stringResource(R.string.invoice_links_to_confirm, invoice.linksToConfirm), style = MaterialTheme.typography.bodyMedium, color = Accord.colors.attention)
    }
    invoice.divergentPayments.forEach {
        Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.small) {
            Text(
                stringResource(R.string.invoice_divergent_payment, it.amount.format(), it.date.fullLabel()),
                color = Accord.colors.onAttentionContainer, modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s3),
            )
        }
    }
    if (invoice.divergentPayments.isNotEmpty() && !invoice.paid) {
        Text(stringResource(R.string.invoice_review_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    OutlinedButton(onClick = { onSeeEntries(invoice.dueMonth) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.invoice_see_entries))
    }
    if (!invoice.paid) {
        Button(onClick = onOpenPay, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.invoice_pay))
        }
        if (!invoice.conferred) Text(stringResource(R.string.invoice_pay_needs_total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(onClick = onOpenTotal, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(if (checked == null) R.string.invoice_enter_total else R.string.invoice_correct_total))
    }
}

@Composable
private fun Line(label: Int, value: String, strong: Boolean = false) {
    val style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge
    AdaptiveRow(
        start = { Text(stringResource(label), style = style) },
        end = { Text(value, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InvoiceDialogs(
    state: InvoiceDialogState,
    onChange: (String?, java.time.LocalDate?) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val paying = state.dialog == InvoiceDialog.Pay
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(if (paying) R.string.invoice_pay_title else R.string.invoice_total_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                OutlinedTextField(
                    value = state.amountText, onValueChange = { onChange(it, null) },
                    label = { Text(stringResource(if (paying) R.string.invoice_pay_amount else R.string.invoice_total_amount)) },
                    singleLine = true, isError = state.error != null, supportingText = state.error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                if (paying) {
                    PickerField(label = stringResource(R.string.invoice_pay_date), value = state.date.fullLabel(), error = null, bring = bring) { pickingDate = true }
                    Text(stringResource(R.string.invoice_pay_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.invoice_total_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        DatePickerSheet(initial = state.date, onPick = { onChange(null, it); pickingDate = false }, onDismiss = { pickingDate = false })
    }
}
