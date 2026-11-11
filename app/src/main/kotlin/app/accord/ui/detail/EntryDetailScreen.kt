package app.accord.ui.detail

import app.accord.ui.categoryColor
import app.accord.ui.colorKey
import app.accord.ui.incomeCategoryLabel
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.ui.vendors.ChooseIconFlow
import app.accord.ui.vendors.EntryIdentityIcon
import app.accord.ui.vendors.LocalIconActions
import app.accord.ui.banks.BankLogo
import app.accord.core.RecurrenceScope
import app.accord.core.label
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.ExtraReason
import app.accord.core.space.RefundStatus
import app.accord.core.space.RefundView
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.entries.SituationTags
import app.accord.ui.fullLabel
import app.accord.ui.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EntryDetailRoute(
    viewModel: EntryDetailViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenCommitment: (String) -> Unit = {},
    onOpenIntention: (String) -> Unit = {},
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
        onRequestDeleteOccurrence = viewModel::requestDeleteOccurrence,
        onConfirmDeleteOccurrence = { viewModel.confirmDeleteOccurrence(it) },
        onConfirmDeleteRealized = viewModel::confirmDeleteRealized,
        onOpenCommitment = onOpenCommitment, onOpenIntention = onOpenIntention,
        linked = linked, onOpenAction = viewModel::openAction, onToggleSettle = viewModel::toggleSettle, onAdjustSettle = viewModel::adjustSettle,
        onLinkedChange = viewModel::updateLinked,
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
    onRequestDeleteOccurrence: () -> Unit = {},
    onConfirmDeleteOccurrence: (RecurrenceScope) -> Unit = {},
    onConfirmDeleteRealized: () -> Unit = {},
    onOpenCommitment: (String) -> Unit = {},
    onOpenIntention: (String) -> Unit = {},
    linked: LinkedState? = null,
    onOpenAction: (LinkedAction) -> Unit = {},
    onToggleSettle: (Boolean) -> Unit = {},
    onAdjustSettle: () -> Unit = {},
    onLinkedChange: ((LinkedForm) -> LinkedForm) -> Unit = {},
    onLinkedSubmit: () -> Unit = {},
    onLinkedClose: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.detail_title), onBack)
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
            is DetailContent.Ready -> {
                app.accord.ui.intentions.OriginIntentionLink(content.data.origin, onOpenIntention)
                ReadyDetail(content.data, onEdit, onRequestDelete, onRequestDeleteOccurrence, onOpenAction, onOpenCommitment, onToggleSettle, onAdjustSettle)
            }
        }
    }
    DeleteDialogs(delete, (content as? DetailContent.Ready)?.data, onCancelDelete, onConfirmDelete, onConfirmDeleteOccurrence, onConfirmDeleteRealized)
    linked?.let { LinkedActionDialog(it, onLinkedChange, onLinkedSubmit, onLinkedClose) }
}

@Composable
private fun ReadyDetail(
    data: DetailData,
    onEdit: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onRequestDeleteOccurrence: () -> Unit,
    onOpenAction: (LinkedAction) -> Unit,
    onOpenCommitment: (String) -> Unit,
    onToggleSettle: (Boolean) -> Unit,
    onAdjustSettle: () -> Unit,
) {
    val d = data.detail
    var choosingIcon by remember { mutableStateOf(false) }
    if (choosingIcon) ChooseIconFlow(d.id, d.description) { choosingIcon = false }
    Surface(color = Accord.colors.financeSurface, contentColor = Accord.colors.onFinanceSurface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                EntryIdentityIcon(
                    d.description,
                    allowVendor = d.kind != EntryKind.Income && d.kind != EntryKind.InvoicePayment,
                    entryId = d.id,
                    onChange = if (LocalIconActions.current != null) ({ choosingIcon = true }) else null,
                )
                Text(d.description, style = MaterialTheme.typography.titleLarge,
                    color = Accord.colors.onFinanceSurface, modifier = Modifier.weight(1f).semantics { heading() })
            }
            Text(d.amount.format(), style = Accord.typography.amountHero,
                color = Accord.colors.onFinanceSurface)
        }
    }
    if (d.voided) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(AccordSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.detail_deleted), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            DatesAndAmounts(d)

            val category = (d.classification as? app.accord.core.space.Classification.Category)?.id?.let(data.categories::get)
            val incomeCategoryId = d.income?.incomeCategoryId ?: d.occurrence?.incomeCategoryId
            val incomeCategory = incomeCategoryId?.let { id -> data.incomeCategories.firstOrNull { it.id == id } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                if (d.income != null || d.occurrence?.kind == app.accord.core.space.RecurrenceKind.Income) {
                    // Renda tem categoria própria (ADR 0038); sem escolha, "a classificar" segue valendo.
                    DecorativeIcon(lucideRes(incomeCategory?.iconKey ?: "tag"), tint = incomeCategory?.let { categoryColor(it.colorKey(data.incomeCategories)) } ?: categoryColor(data.categories, null))
                    Text(
                        incomeCategory?.let { incomeCategoryLabel(it, data.incomeCategories) } ?: stringResource(R.string.detail_category_unset),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    DecorativeIcon(lucideRes(category?.iconKey ?: "tag"), tint = categoryColor(data.categories, category?.id))
                    Text(category?.name ?: stringResource(R.string.detail_category_unset), style = MaterialTheme.typography.bodyLarge)
                }
            }
            d.method?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (d.method == app.accord.core.space.PaymentMethod.Cash) {
                Text(stringResource(R.string.balance_wallet), style = MaterialTheme.typography.bodyMedium)
            }
            d.accountId?.let { id ->
                data.accounts[id]?.let { account ->
                    val bank = when (val b = account.bank) {
                        is app.accord.core.space.BankRef.Catalog -> b.name
                        is app.accord.core.space.BankRef.Other -> b.name
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        BankLogo(account.bank, size = 32.dp)
                        Text(stringResource(R.string.detail_account, "$bank · ${account.nickname}"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val who = if (d.authorId == data.currentPersonId) stringResource(R.string.who_you) else stringResource(R.string.who_other)
            d.origin?.let { Text(stringResource(R.string.detail_registered_by, who, it.label()), style = MaterialTheme.typography.bodyMedium) }
            d.spentBy?.let {
                val spender = if (it == data.currentPersonId) stringResource(R.string.who_you) else stringResource(R.string.who_other)
                Text(stringResource(R.string.detail_spent_by, spender), style = MaterialTheme.typography.bodyMedium)
            }
            // Antes da sincronização (S2), o item está "salvo no aparelho" e nunca "sincronizado".
            Text(stringResource(R.string.detail_status, d.sync.label()), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

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

    data.settleSwitch?.let { SettleSwitchCard(it, data.detail.amount, onToggleSettle, onAdjustSettle) }

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
        if (data.canDeleteOccurrence) {
            OutlinedButton(onClick = onRequestDeleteOccurrence, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.edit_delete_occurrence), color = MaterialTheme.colorScheme.error)
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
                if (count > 1) {
                    Text(stringResource(R.string.detail_total_and_installments, d.purchaseDate?.fullLabel().orEmpty(), count, each.format()), style = body)
                } else {
                    d.purchaseDate?.let { Text(stringResource(R.string.detail_purchase_on, it.fullLabel()), style = body) }
                }
                Text(stringResource(R.string.detail_purchase_total, purchase.total.format()), style = body)
                val installment = number?.let { installmentNumber -> purchase.installments.firstOrNull { it.number == installmentNumber } }
                if (count > 1 && number != null) {
                    Text(stringResource(R.string.detail_installment_here, number, count, d.amount.format()), style = MaterialTheme.typography.bodyLarge)
                }
                installment?.let { InvoiceIndicator(it.invoiceMonth) }
            }
            d.dueDate?.let { Text(stringResource(R.string.detail_due_on, it.fullLabel()), style = body) }
        }
        EntryKind.CardPurchase -> {
            d.purchaseDate?.let { Text(stringResource(R.string.detail_purchase_on, it.fullLabel()), style = body) }
            purchase?.let { p ->
                if (p.installments.size > 1) {
                    p.installments.firstOrNull()?.let { first ->
                        Text(stringResource(R.string.detail_installment_count, p.installments.size, first.amount.format()), style = body)
                    }
                }
                p.installments.firstOrNull()?.let { InvoiceIndicator(it.invoiceMonth) }
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

private val DETAIL_INVOICE_MONTH = DateTimeFormatter.ofPattern("MM/yy", Locale.forLanguageTag("pt-BR"))

@Composable
private fun InvoiceIndicator(month: YearMonth) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = AccordSpacing.s2, vertical = AccordSpacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            DecorativeIcon(Lucide.drawable.lucide_ic_credit_card)
            Text(stringResource(R.string.detail_invoice, month.format(DETAIL_INVOICE_MONTH)), style = MaterialTheme.typography.labelMedium)
        }
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
    onConfirmOccurrence: (RecurrenceScope) -> Unit,
    onConfirmRealized: () -> Unit,
) {
    when (phase) {
        DeletePhase.Idle -> Unit
        DeletePhase.ChoosingScope -> OccurrenceScopeDialog(onConfirmOccurrence, onCancel)
        is DeletePhase.ConfirmingRealized -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.edit_realized_title)) },
            text = { Text(stringResource(R.string.edit_realized_body, phase.numbers.joinToString(", "))) },
            confirmButton = {
                TextButton(onClick = onConfirmRealized, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.edit_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.delete_keep))
                }
            },
        )
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

/** Exclusão de ocorrência de série: o alcance é obrigatório e o botão só vale depois de escolhê-lo. */
@Composable
private fun OccurrenceScopeDialog(onConfirm: (RecurrenceScope) -> Unit, onCancel: () -> Unit) {
    var chosen by remember { mutableStateOf<RecurrenceScope?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.edit_delete_title)) },
        text = {
            Column {
                listOf(
                    RecurrenceScope.OnlyThis to R.string.edit_scope_only_this,
                    RecurrenceScope.ThisAndNext to R.string.edit_scope_this_and_next,
                ).forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                            .selectable(selected = chosen == value, onClick = { chosen = value }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen == value, onClick = null)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
                    }
                }
                chosen?.let {
                    Text(
                        stringResource(if (it == RecurrenceScope.OnlyThis) R.string.edit_delete_note_only_this else R.string.edit_delete_note_next),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let(onConfirm) }, enabled = chosen != null, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.edit_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.delete_keep)) }
        },
    )
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
