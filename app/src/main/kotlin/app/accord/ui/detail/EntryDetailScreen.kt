package app.accord.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.ExtraReason
import app.accord.core.space.RefundStatus
import app.accord.core.space.RefundView
import app.accord.ui.categoryPath
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.entries.SituationTags
import app.accord.ui.fullLabel
import app.accord.ui.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun EntryDetailRoute(
    viewModel: EntryDetailViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenCommitment: (String) -> Unit = {},
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    // Depois de editar, o detalhe volta com os valores novos.
    RefreshOnResume(onRefresh = viewModel::reload)
    EntryDetailScreen(
        content, delete, padding, onBack, onEdit,
        onRetry = viewModel::load, onRequestDelete = viewModel::requestDelete, onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onOpenCommitment = onOpenCommitment,
        linked = linked, onOpenAction = viewModel::openAction, onLinkedChange = viewModel::updateLinked,
        onLinkedSubmit = viewModel::submitLinked, onLinkedClose = viewModel::closeLinked,
    )
}

@Composable
fun EntryDetailScreen(
    content: DetailContent,
    delete: DeletePhase,
    padding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onRetry: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (extraConfirmed: Boolean) -> Unit,
    onOpenCommitment: (String) -> Unit = {},
    linked: LinkedState? = null,
    onOpenAction: (LinkedAction) -> Unit = {},
    onLinkedChange: ((LinkedForm) -> LinkedForm) -> Unit = {},
    onLinkedSubmit: () -> Unit = {},
    onLinkedClose: () -> Unit = {},
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
                stringResource(R.string.detail_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (content) {
            DetailContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            DetailContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            DetailContent.NotFound -> {
                Text(stringResource(R.string.detail_not_found_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.detail_not_found_body), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.action_back))
                }
            }
            is DetailContent.Ready -> ReadyDetail(content.data, onEdit, onRequestDelete, onOpenAction, onOpenCommitment)
        }
    }
    DeleteDialogs(delete, (content as? DetailContent.Ready)?.data, onCancelDelete, onConfirmDelete)
    linked?.let { LinkedActionDialog(it, onLinkedChange, onLinkedSubmit, onLinkedClose) }
}

@Composable
private fun ReadyDetail(
    data: DetailData,
    onEdit: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onOpenAction: (LinkedAction) -> Unit,
    onOpenCommitment: (String) -> Unit,
) {
    val d = data.detail
    AdaptiveRow(
        start = { Text(d.description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        end = { Text(d.amount.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
    )
    if (d.voided) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(AccordSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.detail_deleted), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
    DatesAndAmounts(d)

    val path = categoryPath(data.categories, (d.classification as? app.accord.core.space.Classification.Category)?.id)
    Text(path ?: stringResource(R.string.detail_category_unset), style = MaterialTheme.typography.bodyLarge)
    d.method?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    d.accountId?.let { id ->
        data.accounts[id]?.let { account ->
            val bank = when (val b = account.bank) {
                is app.accord.core.space.BankRef.Catalog -> b.name
                is app.accord.core.space.BankRef.Other -> b.name
            }
            Text(stringResource(R.string.detail_account, "$bank · ${account.nickname}"), style = MaterialTheme.typography.bodyMedium)
        }
    }

    val who = if (d.authorId == data.currentPersonId) stringResource(R.string.who_you) else stringResource(R.string.who_other)
    d.origin?.let { Text(stringResource(R.string.detail_registered_by, who, it.label()), style = MaterialTheme.typography.bodyMedium) }
    d.spentBy?.let {
        val spender = if (it == data.currentPersonId) stringResource(R.string.who_you) else stringResource(R.string.who_other)
        Text(stringResource(R.string.detail_spent_by, spender), style = MaterialTheme.typography.bodyMedium)
    }
    // Antes da sincronização (S2), o item está "salvo no aparelho" e nunca "sincronizado".
    Text(stringResource(R.string.detail_status, d.sync.label()), style = MaterialTheme.typography.bodyMedium)

    d.purchase?.installments?.let { schedule ->
        val flags = schedule.flatMap { i ->
            listOfNotNull(
                app.accord.core.space.EntrySituation.CancelRequested.takeIf { i.cancel == app.accord.core.space.CancelState.CancelRequested },
                app.accord.core.space.EntrySituation.Cancelled.takeIf { i.cancel == app.accord.core.space.CancelState.Cancelled },
                app.accord.core.space.EntrySituation.InvoiceLinkToConfirm.takeIf { i.assignment == app.accord.core.space.AssignmentState.ToConfirm },
            )
        }.distinct()
        if (flags.isNotEmpty()) SituationTags(flags)
    }

    if (d.refunds.isNotEmpty()) RefundsBlock(d.refunds)

    LinkedActionsBlock(data.actions, onOpenAction)

    // Parcela, ocorrência de série e boleto previsto são compromissos com cronograma próprio.
    if (!d.voided && d.kind in setOf(EntryKind.CardInstallment, EntryKind.RecurrenceOccurrence, EntryKind.Boleto)) {
        OutlinedButton(onClick = { onOpenCommitment(d.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.detail_see_commitment))
        }
    }

    if (!d.voided) {
        if (data.canEdit) {
            Button(onClick = { onEdit(d.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.detail_edit))
            }
        }
        if (data.canDelete) {
            OutlinedButton(onClick = onRequestDelete, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Data da compra, vencimento/fatura e pagamento são campos diferentes, conforme o tipo (spec 08 §4). */
@Composable
private fun DatesAndAmounts(d: EntryDetail) {
    val body = MaterialTheme.typography.bodyMedium
    val purchase = d.purchase
    when (d.kind) {
        EntryKind.CardInstallment -> {
            val number = d.id.substringAfter('#').toIntOrNull()
            val count = purchase?.installments?.size
            if (purchase != null && count != null) {
                val each = purchase.installments.first().amount
                Text(stringResource(R.string.detail_total_and_installments, d.purchaseDate?.fullLabel().orEmpty(), count, each.format()), style = body)
                Text(stringResource(R.string.detail_purchase_total, purchase.total.format()), style = body)
                if (number != null) {
                    Text(stringResource(R.string.detail_installment_here, number, count, d.amount.format()), style = MaterialTheme.typography.bodyLarge)
                    purchase.installments.firstOrNull { it.number == number }?.let {
                        Text(stringResource(R.string.detail_invoice, it.invoiceMonth.label()), style = body)
                    }
                }
            }
            d.dueDate?.let { Text(stringResource(R.string.detail_due_on, it.fullLabel()), style = body) }
        }
        EntryKind.CardPurchase -> {
            d.purchaseDate?.let { Text(stringResource(R.string.detail_purchase_on, it.fullLabel()), style = body) }
            purchase?.let { p ->
                p.installments.firstOrNull()?.let { first ->
                    Text(stringResource(R.string.detail_total_and_installments, d.purchaseDate?.fullLabel().orEmpty(), p.installments.size, first.amount.format()), style = body)
                    Text(stringResource(R.string.detail_invoice, first.invoiceMonth.label()), style = body)
                }
            }
        }
        EntryKind.DirectPayment ->
            d.purchaseDate?.let { Text(stringResource(R.string.detail_movement_on, it.fullLabel()), style = body) }
        EntryKind.Boleto -> {
            d.dueDate?.let { Text(stringResource(R.string.detail_due_on, it.fullLabel()), style = body) }
            d.settledDate?.let { Text(stringResource(R.string.detail_paid_on, it.fullLabel()), style = body) }
            d.boleto?.let { b ->
                Text(stringResource(R.string.detail_budget_month, b.budgetMonth.label()), style = body)
                if (b.paidAmount != null && b.paidAmount != b.expectedAmount) {
                    Text(stringResource(R.string.detail_expected_value, b.expectedAmount.format()), style = body)
                    Text(stringResource(R.string.detail_effective_value, b.paidAmount!!.format()), style = body)
                }
            }
        }
        EntryKind.Income -> {
            d.dueDate?.let { Text(stringResource(R.string.detail_expected_on, it.fullLabel()), style = body) }
            d.settledDate?.let { Text(stringResource(R.string.detail_received_on, it.fullLabel()), style = body) }
            d.income?.let { i ->
                if (i.receivedAmount != null && i.receivedAmount != i.expectedAmount) {
                    Text(stringResource(R.string.detail_expected_value, i.expectedAmount.format()), style = body)
                    Text(stringResource(R.string.detail_effective_value, i.receivedAmount!!.format()), style = body)
                }
            }
        }
        EntryKind.RecurrenceOccurrence -> {
            d.dueDate?.let { Text(stringResource(R.string.detail_due_on, it.fullLabel()), style = body) }
            d.settledDate?.let { Text(stringResource(R.string.detail_paid_on, it.fullLabel()), style = body) }
        }
        EntryKind.Refund, EntryKind.InvoicePayment ->
            d.settledDate?.let { Text(stringResource(R.string.detail_paid_on, it.fullLabel()), style = body) }
    }
}

@Composable
private fun RefundsBlock(refunds: List<RefundView>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.detail_refunds), style = MaterialTheme.typography.titleMedium)
            refunds.forEach { r ->
                val state = if (r.status == RefundStatus.Confirmed) {
                    stringResource(R.string.refund_confirmed, r.date?.fullLabel().orEmpty())
                } else {
                    stringResource(R.string.refund_requested)
                }
                AdaptiveRow(
                    start = { Text(state, style = MaterialTheme.typography.bodyMedium) },
                    end = { Text(r.amount.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
                )
            }
        }
    }
}

@Composable
private fun DeleteDialogs(
    phase: DeletePhase,
    data: DetailData?,
    onCancel: () -> Unit,
    onConfirm: (extraConfirmed: Boolean) -> Unit,
) {
    when (phase) {
        DeletePhase.Idle -> Unit
        DeletePhase.Confirming, DeletePhase.Working -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Text(stringResource(R.string.delete_effect))
                    val installments = data?.detail?.purchase?.installments?.size
                    if (installments != null && installments > 1) {
                        Text(stringResource(R.string.delete_effect_installments, installments))
                    }
                    if (data?.detail?.refunds?.isNotEmpty() == true) Text(stringResource(R.string.delete_effect_refunds))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(false) },
                    enabled = phase == DeletePhase.Confirming,
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.delete_keep))
                }
            },
        )
        is DeletePhase.ConfirmingExtra -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.delete_extra_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    phase.reasons.forEach {
                        Text(
                            stringResource(
                                if (it == ExtraReason.PaidInvoice) R.string.delete_extra_paid_invoice else R.string.delete_extra_imported,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(true) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.delete_keep))
                }
            },
        )
        DeletePhase.Stale -> MessageDialog(stringResource(R.string.delete_stale), onCancel)
        DeletePhase.Failed -> MessageDialog(stringResource(R.string.delete_failed), onCancel)
    }
}

@Composable
private fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.delete_keep))
            }
        },
    )
}
