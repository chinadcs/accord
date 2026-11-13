package app.accord.ui.detail

import app.accord.ui.components.AccordCard
import app.accord.ui.components.AccordSheet
import app.accord.ui.components.returnsFocus
import app.accord.ui.components.accordDialogColor
import app.accord.ui.categoryColor
import app.accord.ui.colorKey
import app.accord.ui.incomeCategoryLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.CancelState
import app.accord.core.space.Classification
import app.accord.core.space.EntrySituation
import app.accord.core.space.PaymentMethod
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.ui.components.LabelValue
import app.accord.ui.components.RowMenu
import app.accord.ui.components.RowMenuItem
import app.accord.ui.components.SectionHeader
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
    val ready = (content as? DetailContent.Ready)?.data
    var choosingIcon by remember { mutableStateOf(false) }
    val canChangeIcon = ready != null && ready.detail.kind != EntryKind.Income && ready.detail.kind != EntryKind.InvoicePayment &&
        LocalIconActions.current != null
    val menu = ready?.let { menuEntries(it, canChangeIcon, { choosingIcon = true }, onOpenAction, onRequestDelete, onRequestDeleteOccurrence) }.orEmpty()
    if (choosingIcon && ready != null) ChooseIconFlow(ready.detail.id, ready.detail.description) { choosingIcon = false }

    // Barra superior e inferior fixas; só o miolo rola (ADR 0089, item 6).
    Column(Modifier.fillMaxSize().padding(padding)) {
        Box(Modifier.fillMaxWidth().padding(start = AccordSpacing.s4, end = AccordSpacing.s4, top = AccordSpacing.s4, bottom = AccordSpacing.s2)) {
            val title = ready?.let { detailTitle(it.detail) } ?: stringResource(R.string.detail_title)
            DetailHeader(
                title, onBack,
                action = if (menu.isEmpty()) null else ({
                    RowMenu(title) { close ->
                        menu.forEach { entry -> RowMenuItem(entry.label, entry.destructive) { close(); entry.onClick() } }
                    }
                }),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
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
                    ReadyDetail(
                        content.data, menu, if (canChangeIcon) ({ choosingIcon = true }) else null,
                        onOpenAction, onOpenCommitment, onToggleSettle, onAdjustSettle, linked?.action,
                    )
                }
            }
        }
        if (ready != null && ready.canEdit) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = { onEdit(ready.detail.id) },
                modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s4).heightIn(min = AccordSpacing.touchMin),
            ) {
                Text(stringResource(if (ready.editsPurchase) R.string.detail_edit_purchase else R.string.detail_edit))
            }
        }
    }
    DeleteDialogs(delete, ready, onCancelDelete, onConfirmDelete, onConfirmDeleteOccurrence, onConfirmDeleteRealized)
    linked?.let { LinkedActionDialog(it, onLinkedChange, onLinkedSubmit, onLinkedClose) }
}

/** Título pelo tipo (spec 24); "Detalhe do lançamento" só enquanto não há lançamento (carregando, falha, não encontrado). */
@Composable
private fun detailTitle(d: EntryDetail): String = stringResource(
    when (d.kind) {
        EntryKind.DirectPayment -> R.string.detail_title_expense
        EntryKind.CardInstallment -> if ((d.purchase?.installments?.size ?: 1) > 1) R.string.detail_title_installment else R.string.detail_title_card_purchase
        EntryKind.CardPurchase -> R.string.detail_title_card_purchase
        EntryKind.Boleto -> R.string.detail_title_boleto
        EntryKind.Income -> R.string.detail_title_income
        EntryKind.RecurrenceOccurrence ->
            if (d.occurrence?.kind == RecurrenceKind.Income) R.string.detail_title_recurring_income else R.string.detail_title_recurring_expense
        EntryKind.Refund -> R.string.detail_title_refund
        EntryKind.InvoicePayment -> R.string.detail_title_invoice_payment
    },
)

/** Item do ⋮; as mesmas ações vão como ações personalizadas do herói para o TalkBack. */
private class MenuEntry(val label: String, val destructive: Boolean, val onClick: () -> Unit)

/** Ordem do Alvo: Trocar ícone, Registrar estorno, Corrigir fatura, Pedir cancelamento e, por último, a exclusão. */
@Composable
private fun menuEntries(
    data: DetailData,
    canChangeIcon: Boolean,
    onChangeIcon: () -> Unit,
    onOpenAction: (LinkedAction) -> Unit,
    onRequestDelete: () -> Unit,
    onRequestDeleteOccurrence: () -> Unit,
): List<MenuEntry> {
    val d = data.detail
    val installments = d.purchase?.installments?.size ?: 0
    return buildList {
        if (canChangeIcon) add(MenuEntry(stringResource(R.string.detail_menu_change_icon), false, onChangeIcon))
        data.actions.filterIsInstance<LinkedAction.NewRefund>().forEach { add(MenuEntry(stringResource(R.string.linked_refund), false) { onOpenAction(it) }) }
        data.actions.filterIsInstance<LinkedAction.OccurrenceInvoice>().filter { it.confirmed }
            .forEach { add(MenuEntry(stringResource(R.string.linked_invoice_correct), false) { onOpenAction(it) }) }
        data.actions.filterIsInstance<LinkedAction.Cancellation>().filter { !it.nonCharge }
            .forEach { add(MenuEntry(stringResource(R.string.linked_cancel_request), false) { onOpenAction(it) }) }
        if (data.canDelete) {
            val label = if (installments > 1) stringResource(R.string.detail_menu_delete_purchase, installments) else stringResource(R.string.detail_menu_delete)
            add(MenuEntry(label, true, onRequestDelete))
        }
        if (data.canDeleteOccurrence) add(MenuEntry(stringResource(R.string.detail_menu_delete_occurrence), true, onRequestDeleteOccurrence))
    }
}

@Composable
private fun ReadyDetail(
    data: DetailData,
    menu: List<MenuEntry>,
    onChangeIcon: (() -> Unit)?,
    onOpenAction: (LinkedAction) -> Unit,
    onOpenCommitment: (String) -> Unit,
    onToggleSettle: (Boolean) -> Unit,
    onAdjustSettle: () -> Unit,
    /** A ação vinculada com painel aberto: ao fechar, o foco volta ao botão que a abriu (spec 26). */
    openAction: LinkedAction? = null,
) {
    val d = data.detail
    Hero(data, menu, onChangeIcon)
    if (d.voided) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(AccordSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.detail_deleted), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }

    // Ação do momento, sob o herói: o interruptor, o que resta de pagamento, a fatura a conferir e a confirmação de não cobrança.
    data.settleSwitch?.let { SettleSwitchCard(it, d.amount, onToggleSettle, onAdjustSettle) }
    data.actions.filterIsInstance<LinkedAction.Settle>().forEach { action ->
        OutlinedButton(onClick = { onOpenAction(action) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).returnsFocus(openAction == action)) {
            Text(actionLabel(action))
        }
    }
    data.actions.filterIsInstance<LinkedAction.OccurrenceInvoice>().filter { !it.confirmed }.forEach { action ->
        Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.small) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(
                    stringResource(R.string.detail_invoice_estimated, action.estimated.format(DETAIL_INVOICE_MONTH)),
                    style = MaterialTheme.typography.bodyLarge, color = Accord.colors.onAttentionContainer,
                )
                TextButton(onClick = { onOpenAction(action) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin).returnsFocus(openAction == action)) {
                    Text(actionLabel(action))
                }
            }
        }
    }
    data.actions.filterIsInstance<LinkedAction.Cancellation>().filter { it.nonCharge }.forEach { action ->
        OutlinedButton(onClick = { onOpenAction(action) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).returnsFocus(openAction == action)) {
            Text(actionLabel(action))
        }
    }

    DetailsCard(data)

    d.purchase?.installments?.let { schedule ->
        val flags = schedule.flatMap { i ->
            listOfNotNull(
                EntrySituation.CancelRequested.takeIf { i.cancel == CancelState.CancelRequested },
                EntrySituation.Cancelled.takeIf { i.cancel == CancelState.Cancelled },
                EntrySituation.InvoiceLinkToConfirm.takeIf { i.assignment == AssignmentState.ToConfirm },
            )
        }.distinct()
        if (flags.isNotEmpty()) SituationTags(flags)
    }

    if (d.kind == EntryKind.CardInstallment || d.kind == EntryKind.CardPurchase) {
        InstallmentsSection(d, onSeeAll = { onOpenCommitment(d.id) })
    }
    if (d.refunds.isNotEmpty()) RefundsBlock(d.refunds, data.actions.filterIsInstance<LinkedAction.ConfirmRefundOf>().map { it.refund.id }.toSet()) { id ->
        data.actions.filterIsInstance<LinkedAction.ConfirmRefundOf>().firstOrNull { it.refund.id == id }?.let(onOpenAction)
    }

    if (!d.voided && d.kind == EntryKind.RecurrenceOccurrence) {
        OutlinedButton(onClick = { onOpenCommitment(d.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.detail_see_recurrence))
        }
    }
    if (!d.voided && d.kind == EntryKind.Boleto) {
        OutlinedButton(onClick = { onOpenCommitment(d.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.detail_see_boleto))
        }
    }

    // Antes da sincronização (S2), o item está "salvo no aparelho" e nunca "sincronizado".
    Text(stringResource(R.string.detail_status, d.sync.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Herói: o que o valor é ("Parcela 2 de 6", "Compra de R$ 1.200,00 em 6×", "Ocorrência de …") e a situação de pagamento. */
@Composable
private fun Hero(data: DetailData, menu: List<MenuEntry>, onChangeIcon: (() -> Unit)?) {
    val d = data.detail
    val amount = when {
        d.kind == EntryKind.InvoicePayment -> d.amount.format()
        data.isIncome || d.kind == EntryKind.Refund -> "+" + d.amount.format()
        d.amount.cents > 0 -> Money(-d.amount.cents).format()
        else -> d.amount.format()
    }
    val purchase = d.purchase
    val count = purchase?.installments?.size ?: 0
    val number = d.id.substringAfter('#', "").toIntOrNull()
    val lines = buildList {
        when (d.kind) {
            EntryKind.CardInstallment -> if (purchase != null && count > 1) {
                number?.let { add(stringResource(R.string.detail_hero_installment_of, it, count)) }
                add(stringResource(R.string.detail_hero_purchase_in, purchase.total.format(), count))
            }
            EntryKind.CardPurchase -> if (purchase != null && count > 1) {
                add(stringResource(R.string.detail_hero_installments_of, count, purchase.installments.first().amount.format()))
            }
            EntryKind.RecurrenceOccurrence -> (d.dueDate ?: d.occurrence?.budgetMonth?.atDay(1))?.let {
                add(stringResource(R.string.detail_hero_occurrence_of, it.fullLabel()))
            }
            else -> Unit
        }
    }
    val settled = d.settledDate
    val status = when {
        d.voided -> null
        d.kind == EntryKind.Boleto -> settled?.let { stringResource(R.string.detail_hero_paid_on, it.fullLabel()) } ?: stringResource(R.string.entries_status_to_pay)
        d.kind == EntryKind.Income -> settled?.let { stringResource(R.string.detail_hero_received_on, it.fullLabel()) } ?: stringResource(R.string.entries_status_expected)
        d.kind == EntryKind.RecurrenceOccurrence && d.occurrence?.form == RecurrenceForm.OffCard -> {
            val received = d.occurrence?.kind == RecurrenceKind.Income
            when {
                d.occurrence?.paid == true && settled != null ->
                    stringResource(if (received) R.string.detail_hero_received_on else R.string.detail_hero_paid_on, settled.fullLabel())
                else -> stringResource(if (received) R.string.entries_status_expected else R.string.entries_status_to_pay)
            }
        }
        else -> null
    }
    val actions = menu.map { entry -> CustomAccessibilityAction(entry.label) { entry.onClick(); true } }
    Surface(
        color = Accord.colors.financeSurface, contentColor = Accord.colors.onFinanceSurface, shape = MaterialTheme.shapes.large,
        modifier = Modifier.semantics { customActions = actions },
    ) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                EntryIdentityIcon(
                    d.description,
                    allowVendor = d.kind != EntryKind.Income && d.kind != EntryKind.InvoicePayment,
                    entryId = d.id,
                    onChange = onChangeIcon,
                )
                Text(d.description, style = MaterialTheme.typography.titleLarge, color = Accord.colors.onFinanceSurface, modifier = Modifier.weight(1f).semantics { heading() })
            }
            Text(amount, style = Accord.typography.amountHero, color = Accord.colors.onFinanceSurface)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Accord.colors.onFinanceSurface) }
            status?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Accord.colors.onFinanceSurface) }
        }
    }
}

/** Pares rótulo/valor por tipo (spec 24); cada par é lido como "rótulo, valor". */
@Composable
private fun DetailsCard(data: DetailData) {
    val d = data.detail
    val purchase = d.purchase
    val number = d.id.substringAfter('#', "").toIntOrNull()
    val installment = number?.let { n -> purchase?.installments?.firstOrNull { it.number == n } } ?: purchase?.installments?.firstOrNull()
    val receiving = data.isIncome
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            SectionHeader(stringResource(R.string.detail_section_details))
            when (d.kind) {
                EntryKind.CardInstallment, EntryKind.CardPurchase -> {
                    d.purchaseDate?.let { LabelValue(stringResource(R.string.detail_l_purchase_date), it.fullLabel()) }
                    installment?.let { LabelValue(stringResource(R.string.detail_l_invoice), it.invoiceMonth.format(DETAIL_INVOICE_MONTH)) }
                    d.dueDate?.let { LabelValue(stringResource(R.string.detail_l_due), it.fullLabel()) }
                }
                EntryKind.DirectPayment -> d.purchaseDate?.let { LabelValue(stringResource(R.string.detail_l_date), it.fullLabel()) }
                EntryKind.Boleto -> {
                    d.dueDate?.let { LabelValue(stringResource(R.string.detail_l_due), it.fullLabel()) }
                    d.settledDate?.let { LabelValue(stringResource(R.string.detail_l_paid), it.fullLabel()) }
                    d.boleto?.let { b ->
                        LabelValue(stringResource(R.string.detail_l_budget_month), b.budgetMonth.label())
                        if (b.paidAmount != null && b.paidAmount != b.expectedAmount) {
                            LabelValue(stringResource(R.string.detail_l_expected_value), b.expectedAmount.format())
                            LabelValue(stringResource(R.string.detail_l_effective_value), b.paidAmount!!.format())
                        }
                    }
                }
                EntryKind.Income -> {
                    d.dueDate?.let { LabelValue(stringResource(R.string.detail_l_expected_date), it.fullLabel()) }
                    d.settledDate?.let { LabelValue(stringResource(R.string.detail_l_received), it.fullLabel()) }
                    d.income?.let { i ->
                        if (i.receivedAmount != null && i.receivedAmount != i.expectedAmount) {
                            LabelValue(stringResource(R.string.detail_l_expected_value), i.expectedAmount.format())
                            LabelValue(stringResource(R.string.detail_l_effective_value), i.receivedAmount!!.format())
                        }
                    }
                }
                EntryKind.RecurrenceOccurrence -> {
                    d.dueDate?.let { LabelValue(stringResource(R.string.detail_l_due), it.fullLabel()) }
                    d.settledDate?.let { LabelValue(stringResource(if (receiving) R.string.detail_l_received else R.string.detail_l_paid), it.fullLabel()) }
                }
                EntryKind.Refund, EntryKind.InvoicePayment -> d.settledDate?.let { LabelValue(stringResource(R.string.detail_l_date), it.fullLabel()) }
            }

            if (d.kind != EntryKind.InvoicePayment) {
                if (receiving) {
                    val incomeCategoryId = d.income?.incomeCategoryId ?: d.occurrence?.incomeCategoryId
                    val incomeCategory = incomeCategoryId?.let { id -> data.incomeCategories.firstOrNull { it.id == id } }
                    LabelValue(
                        stringResource(R.string.detail_l_category),
                        incomeCategory?.let { incomeCategoryLabel(it, data.incomeCategories) } ?: stringResource(R.string.detail_category_none),
                    )
                } else {
                    val category = (d.classification as? Classification.Category)?.id?.let(data.categories::get)
                    LabelValue(stringResource(R.string.detail_l_category), category?.name ?: stringResource(R.string.situation_to_classify))
                }
            }
            if (d.method != null && d.method != PaymentMethod.Card) LabelValue(stringResource(R.string.detail_l_method), d.method!!.label())
            d.cardId?.let { id -> data.cards[id]?.let { LabelValue(stringResource(R.string.detail_l_card), it.nickname) } }
            if (d.method == PaymentMethod.Cash) LabelValue(stringResource(R.string.detail_l_account), stringResource(R.string.balance_wallet))
            d.accountId?.let { id ->
                data.accounts[id]?.let { account ->
                    val bank = when (val b = account.bank) {
                        is BankRef.Catalog -> b.name
                        is BankRef.Other -> b.name
                    }
                    val value = "$bank · ${account.nickname}"
                    val description = stringResource(R.string.detail_l_account) + ", " + value
                    Row(
                        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                    ) {
                        BankLogo(account.bank, size = 32.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                            Text(stringResource(R.string.detail_l_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            val who = if (d.authorId == data.currentPersonId) stringResource(R.string.who_you) else stringResource(R.string.who_other)
            d.origin?.let { LabelValue(stringResource(R.string.detail_l_registered_by), stringResource(R.string.detail_registered_value, who, it.label())) }
            d.spentBy?.let {
                val spender = if (it == data.currentPersonId) stringResource(R.string.who_you) else stringResource(R.string.who_other)
                LabelValue(stringResource(R.string.detail_l_spent_by), spender)
            }
        }
    }
}

/** Parcelas ao redor da atual (anterior, esta e próxima); a atual leva o texto "Esta", nunca só um marcador. */
@Composable
private fun InstallmentsSection(d: EntryDetail, onSeeAll: () -> Unit) {
    val schedule = d.purchase?.installments ?: return
    if (schedule.isEmpty()) return
    val current = d.id.substringAfter('#', "").toIntOrNull()
    val at = schedule.indexOfFirst { it.number == current }.coerceAtLeast(0)
    val first = (at - 1).coerceAtLeast(0).coerceAtMost((schedule.size - 3).coerceAtLeast(0))
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            SectionHeader(stringResource(R.string.detail_section_installments))
            schedule.drop(first).take(3).forEach { i ->
                val here = i.number == current
                val notes = listOfNotNull(
                    stringResource(R.string.detail_installment_here_tag).takeIf { here },
                    EntrySituation.Cancelled.label().takeIf { i.cancel == CancelState.Cancelled },
                    EntrySituation.CancelRequested.label().takeIf { i.cancel == CancelState.CancelRequested },
                    EntrySituation.InvoiceLinkToConfirm.label().takeIf { i.assignment == AssignmentState.ToConfirm },
                )
                val head = stringResource(R.string.detail_installment_row, i.number, schedule.size, i.invoiceMonth.format(DETAIL_INVOICE_MONTH))
                val line = (listOf(head, i.amount.format()) + notes).joinToString(", ")
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = line }) {
                    AdaptiveRow(
                        start = { Text(head, style = MaterialTheme.typography.bodyLarge) },
                        end = { Text(i.amount.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
                    )
                    if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!d.voided) {
                TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.detail_see_installments))
                }
            }
        }
    }
}

private val DETAIL_INVOICE_MONTH = DateTimeFormatter.ofPattern("MM/yy", Locale.forLanguageTag("pt-BR"))

@Composable
private fun RefundsBlock(refunds: List<RefundView>, confirmable: Set<String>, onConfirm: (String) -> Unit) {
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            SectionHeader(stringResource(R.string.detail_refunds))
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
                if (r.id in confirmable) {
                    TextButton(onClick = { onConfirm(r.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.detail_refund_confirm))
                    }
                }
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
            containerColor = accordDialogColor(),
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
            containerColor = accordDialogColor(),
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
            containerColor = accordDialogColor(),
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

/** Exclusão de ocorrência de série: o alcance é obrigatório e o botão só vale depois de escolhê-lo (painel inferior, spec 26). */
@Composable
private fun OccurrenceScopeDialog(onConfirm: (RecurrenceScope) -> Unit, onCancel: () -> Unit) {
    var chosen by remember { mutableStateOf<RecurrenceScope?>(null) }
    AccordSheet(
        title = stringResource(R.string.edit_delete_title), onRequestClose = onCancel,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.delete_keep))
                }
                androidx.compose.material3.Button(
                    onClick = { chosen?.let(onConfirm) }, enabled = chosen != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.edit_delete_confirm)) }
            }
        },
    ) {
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
}

@Composable
private fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.delete_keep))
            }
        },
    )
}
