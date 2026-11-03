package app.accord.ui.pending

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.PendingDetail
import app.accord.core.importing.ProposedEffect
import app.accord.core.importing.RefundCandidate
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.CategoryTag
import app.accord.ui.categoryName
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.imports.label
import app.accord.ui.imports.monthText
import app.accord.ui.imports.movementAmount
import app.accord.ui.register.CategoryDialog
import app.accord.ui.shortLabel
import app.accord.ui.theme.AccordSpacing

private val REPETITION_DOUBTS = setOf(DoubtReason.FitidDivergent, DoubtReason.PossibleRepetition, DoubtReason.RemovedPreviously)

private val INVOICE_DOUBTS = setOf(
    DoubtReason.InvoiceDiverges, DoubtReason.InstallmentInvoiceDiverges, DoubtReason.InstallmentShiftBlocked, DoubtReason.InstallmentValueDiverges,
    DoubtReason.InstallmentPurchaseNotRegistered, DoubtReason.InvoiceTotalNotConfirmed, DoubtReason.PaymentDivergent,
    DoubtReason.PossibleInvoicePayment, DoubtReason.PossibleBoleto,
)

@Composable
fun PendingResolveRoute(viewModel: PendingResolveViewModel, padding: PaddingValues, onBack: () -> Unit, onResolved: () -> Unit, onOpenCard: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.eventFlow.collect { onResolved() } }
    PendingResolveScreen(
        state, padding, onBack,
        onRetry = viewModel::load, onToggleOriginal = viewModel::toggleOriginal, onPick = viewModel::pick, onDismissPicker = viewModel::dismissPicker,
        onCategory = viewModel::chooseCategory, onIncome = viewModel::chooseIncomeCategory, onAcceptSuggestion = viewModel::acceptSuggestion,
        onConfirmNew = viewModel::confirmNew, onDismiss = viewModel::dismiss, onLink = viewModel::link, onSeparate = viewModel::keepSeparate,
        onReset = viewModel::reset, onApply = viewModel::apply, onOpenCard = onOpenCard,
        onCorrectInvoice = viewModel::correctInvoice, onShiftFirst = viewModel::shiftFirstInstallment, onRefund = viewModel::chooseRefundOrigin,
    )
}

@Composable
fun PendingResolveScreen(
    state: PendingResolveUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleOriginal: () -> Unit,
    onPick: (Picking) -> Unit,
    onDismissPicker: () -> Unit,
    onCategory: (String) -> Unit,
    onIncome: (String) -> Unit,
    onAcceptSuggestion: () -> Unit,
    onConfirmNew: () -> Unit,
    onDismiss: () -> Unit,
    onLink: (String) -> Unit,
    onSeparate: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onOpenCard: () -> Unit,
    onCorrectInvoice: (String) -> Unit = {},
    onShiftFirst: () -> Unit = {},
    onRefund: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        when (val content = state.content) {
            ResolveContent.Loading -> Plain(onBack) { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            ResolveContent.Failed -> Plain(onBack) {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            ResolveContent.Gone -> Plain(onBack) {
                Text(stringResource(R.string.pending_gone), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pending_gone_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_back_queue)) }
            }
            is ResolveContent.Ready -> {
                val detail = content.detail
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    DetailHeader(stringResource(R.string.pending_resolve_title), onBack, subtitle = detail.pending.fileName.ifBlank { null })
                    state.message?.let { MessageBanner(it) }
                    Evidence(detail, state.showOriginal, onToggleOriginal)
                    Options(detail, state, onPick, onAcceptSuggestion, onConfirmNew, onDismiss, onLink, onSeparate, onOpenCard, onCorrectInvoice, onShiftFirst)
                    Consequence(detail, state.categories, onReset, state.working)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Button(onClick = onApply, enabled = detail.pending.ready && !state.working, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pending_apply))
                        }
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                            Text(stringResource(R.string.pending_later))
                        }
                    }
                }
                when (state.picking) {
                    Picking.ExpenseCategory -> CategoryDialog(state.categories, selected = null, onPick = onCategory, onDismiss = onDismissPicker)
                    Picking.IncomeCategory -> IncomeChoiceDialog(state.incomeCategories, onIncome, onDismissPicker)
                    Picking.Refund -> RefundChoiceDialog(detail.refundCandidates, onRefund, onDismissPicker)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun Plain(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
        DetailHeader(stringResource(R.string.pending_resolve_title), onBack)
        content()
    }
}

@Composable
private fun Evidence(detail: PendingDetail, showOriginal: Boolean, onToggle: () -> Unit) {
    val m = detail.reconciled.movement
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.pending_imported), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(m.description.ifBlank { stringResource(R.string.import_no_description) }, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(m.date?.let { "${it.shortLabel()}/${it.year}" }, movementAmount(m.amountCents, m.currency) ?: stringResource(R.string.import_amount_unknown)).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (detail.pending.postponed) Text(stringResource(R.string.pending_postponed_note), style = MaterialTheme.typography.bodyMedium)
            detail.reconciled.doubts.forEach { Text(it.label(), style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (showOriginal) R.string.import_original_hide else R.string.import_original_show))
            }
            if (showOriginal) m.originalFields.forEach { (name, value) -> Text("$name: $value", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Opções por motivo. Nenhuma vem escolhida: cada toque grava uma decisão e a releitura diz se já dá para aplicar. */
@Composable
private fun Options(
    detail: PendingDetail,
    state: PendingResolveUiState,
    onPick: (Picking) -> Unit,
    onAcceptSuggestion: () -> Unit,
    onConfirmNew: () -> Unit,
    onDismiss: () -> Unit,
    onLink: (String) -> Unit,
    onSeparate: (String) -> Unit,
    onOpenCard: () -> Unit,
    onCorrectInvoice: (String) -> Unit,
    onShiftFirst: () -> Unit,
) {
    val item = detail.reconciled
    val doubts = item.doubts
    val enabled = !state.working
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        val candidates = item.candidateEntryIds.mapNotNull { id -> detail.linkableEntries.firstOrNull { it.id == id } }
        if (candidates.isNotEmpty()) {
            Text(stringResource(R.string.pending_existing), style = MaterialTheme.typography.titleSmall)
            val documentInvoice = detail.pending.documentInvoice
            candidates.forEach { entry ->
                Text(entryLine(entry), style = MaterialTheme.typography.bodyMedium)
                val invoice = entry.invoice
                if (DoubtReason.InvoiceDiverges in doubts && invoice != null && documentInvoice != null && invoice != documentInvoice) {
                    Text(
                        stringResource(R.string.pending_invoice_diverges_line, monthText(invoice), monthText(documentInvoice)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { onCorrectInvoice(entry.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pending_correct_invoice, monthText(documentInvoice)))
                    }
                    Text(stringResource(R.string.pending_correct_invoice_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedButton(onClick = { onLink(entry.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_same_link)) }
                }
                OutlinedButton(onClick = { onSeparate(entry.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_different)) }
            }
        }
        if (doubts.any { it in REPETITION_DOUBTS }) {
            Text(stringResource(R.string.pending_repetition_title), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onConfirmNew, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_confirm_new)) }
            OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_dismiss)) }
        }
        if (DoubtReason.CreditToClassify in doubts) {
            Text(stringResource(R.string.pending_credit_title), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { onPick(Picking.IncomeCategory) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_credit_income)) }
            OutlinedButton(onClick = { onPick(Picking.Refund) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_credit_refund)) }
            Text(stringResource(R.string.pending_credit_refund_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_dismiss)) }
        }
        if (DoubtReason.CategoryUnknown in doubts) {
            Text(stringResource(R.string.pending_category_title), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { onPick(Picking.ExpenseCategory) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_category)) }
            item.suggestedCategoryId?.let { suggested ->
                Button(onClick = onAcceptSuggestion, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_accept_suggestion, categoryName(state.categories, suggested).orEmpty()))
                }
            }
        }
        val suggestedFirst = item.suggestedFirstInstallment
        val line = item.movement.installment
        val documentInvoice = detail.pending.documentInvoice
        if (DoubtReason.InstallmentInvoiceDiverges in doubts && suggestedFirst != null && line != null && documentInvoice != null) {
            Text(stringResource(R.string.pending_shift_title, line.number, line.total, monthText(documentInvoice)), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.pending_shift_body, monthText(suggestedFirst)), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onShiftFirst, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pending_shift_apply, monthText(suggestedFirst)))
            }
        }
        if (doubts.any { it in INVOICE_DOUBTS }) {
            Text(stringResource(R.string.pending_invoice_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenCard, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_open_card)) }
        }
    }
}

/** O que acontece se a pessoa aplicar; sem dúvida resolvida, diz o que ainda falta e não habilita nada. */
@Composable
private fun Consequence(detail: PendingDetail, categories: Map<String, CategoryNode>, onReset: () -> Unit, working: Boolean) {
    val item = detail.reconciled
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.pending_consequence), style = MaterialTheme.typography.titleSmall)
            when {
                detail.pending.ready && item.dismissed -> Text(stringResource(R.string.pending_consequence_dismiss), style = MaterialTheme.typography.bodyMedium)
                detail.pending.ready -> {
                    item.effect?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium) }
                    (item.effect as? ProposedEffect.LinkInstallment)?.shift?.let { shift ->
                        Text(
                            stringResource(R.string.import_summary_shift, shift.before.joinToString(", ") { monthText(it) }, shift.after.joinToString(", ") { monthText(it) }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    (item.effect as? ProposedEffect.CreateRefund)?.let { refund ->
                        detail.refundCandidates.firstOrNull { it.id == refund.originId }?.let { Text(refundLine(it), style = MaterialTheme.typography.bodyMedium) }
                    }
                    CategoryTag(categories, item.categoryId)
                }
                else -> Text(stringResource(R.string.pending_consequence_missing), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onReset, enabled = !working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_reset)) }
        }
    }
}

@Composable
private fun refundLine(candidate: RefundCandidate): String = stringResource(
    R.string.pending_refund_line, candidate.description, "${candidate.date.shortLabel()}/${candidate.date.year}", Money(candidate.totalCents).format(),
)

@Composable
private fun RefundChoiceDialog(candidates: List<RefundCandidate>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pending_refund_pick)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isEmpty()) Text(stringResource(R.string.pending_refund_none), style = MaterialTheme.typography.bodyLarge)
                candidates.forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onPick(c.id) }.padding(vertical = AccordSpacing.s3),
                        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                    ) {
                        Text(refundLine(c), style = MaterialTheme.typography.bodyLarge)
                        if (c.remainingCents < c.totalCents) {
                            Text(stringResource(R.string.pending_refund_remaining, Money(c.remainingCents).format()), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_cancel)) } },
    )
}

@Composable
private fun entryLine(entry: LinkableEntry): String =
    stringResource(R.string.import_entry_line, entry.description, "${entry.date.shortLabel()}/${entry.date.year}", Money(-entry.cents).format())

@Composable
private fun IncomeChoiceDialog(categories: List<IncomeCategoryNode>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pending_income_pick)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    Text(
                        category.name, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onPick(category.id) }.padding(vertical = AccordSpacing.s3),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_cancel)) } },
    )
}
