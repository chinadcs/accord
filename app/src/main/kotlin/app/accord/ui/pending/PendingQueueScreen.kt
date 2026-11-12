package app.accord.ui.pending

import app.accord.ui.components.AccordCard
import app.accord.ui.components.accordDialogColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import app.accord.core.chat.QuestionLink
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.space.PendingItem
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.imports.label
import app.accord.ui.imports.movementAmount
import app.accord.ui.shortLabel
import app.accord.ui.sync.commandLabel
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.isCompactHeight
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

@Composable
fun PendingQueueRoute(
    viewModel: PendingQueueViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenImport: (batchId: String, ordinal: Int) -> Unit,
    onOpenConflict: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenInvoice: (YearMonth, String?) -> Unit,
    onOpenChat: () -> Unit,
    onOpenManual: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    var correction by remember { mutableStateOf<QueueItem.ChatCorrection?>(null) }
    var discarding by remember { mutableStateOf<QueueItem.BankNotification?>(null) }
    PendingQueueScreen(
        state, padding, onBack, viewModel::setFilter, viewModel::clearBatchFilter, viewModel::refresh, viewModel::accept,
        onDiscardNotification = { discarding = it },
        onOpen = { item ->
            when (item) {
                is QueueItem.ImportDoubt -> onOpenImport(item.movement.batchId, item.movement.ordinal)
                is QueueItem.Conflict -> onOpenConflict(item.conflict.operationId)
                is QueueItem.ChatCategory -> onOpenEntry((item.item.question.link as QuestionLink.Entry).entryId)
                is QueueItem.ChatDraft -> onOpenManual(viewModel.draftRoute(item))
                is QueueItem.ChatCorrection -> correction = item
                is QueueItem.BankNotification -> onOpenManual(viewModel.notificationRoute(item))
                is QueueItem.Record -> when (val p = item.item) {
                    is PendingItem.CategoryToClarify -> onOpenEntry(p.entryId)
                    is PendingItem.InvoiceLink -> onOpenInvoice(p.month, p.cardId)
                    is PendingItem.DivergentInvoicePayment -> onOpenInvoice(p.month, p.cardId)
                    is PendingItem.InvoiceTotalDifference -> onOpenInvoice(p.month, p.cardId)
                }
            }
        },
        onOpenChat = onOpenChat,
    )
    discarding?.let { item ->
        AlertDialog(containerColor = accordDialogColor(), onDismissRequest = { discarding = null }, title = { Text(stringResource(R.string.pending_notification_discard_title)) },
            text = { Text(stringResource(R.string.pending_notification_discard_body)) },
            confirmButton = { TextButton(onClick = { viewModel.discardNotification(item); discarding = null }) { Text(stringResource(R.string.pending_notification_discard)) } },
            dismissButton = { TextButton(onClick = { discarding = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    correction?.let { item ->
        AlertDialog(containerColor = accordDialogColor(), onDismissRequest = { correction = null }, title = { Text(stringResource(R.string.pending_correction)) },
            text = { Column {
                Text(stringResource(R.string.pending_correction_body))
                item.targets.forEach { (id, label) -> TextButton(onClick = { correction = null; onOpenEntry(id) }) { Text(label) } }
                if (item.targets.isEmpty()) Text(stringResource(R.string.pending_correction_missing))
            } },
            confirmButton = { TextButton(onClick = { viewModel.dismissCorrection(item); correction = null }) { Text(stringResource(R.string.pending_correction_done)) } },
            dismissButton = { TextButton(onClick = { correction = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
fun PendingQueueScreen(
    state: PendingQueueUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onFilter: (QueueFilter) -> Unit,
    onClearBatch: () -> Unit,
    onRetry: () -> Unit,
    onAccept: (app.accord.ui.review.CategoryReviewItem) -> Unit,
    onOpen: (QueueItem) -> Unit,
    onOpenChat: () -> Unit,
    onDiscardNotification: (QueueItem.BankNotification) -> Unit = {},
) {
    val shown = state.shown
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            item { DetailHeader(stringResource(R.string.pending_title), onBack) }
            item { Filters(state.filter, onFilter) }
            if (state.batchFilter != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Text(stringResource(R.string.pending_batch_filter), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearBatch, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.pending_batch_clear)) }
                    }
                }
            }
            // A quantidade acompanha o filtro, não o total de compras (spec 18).
            if (!state.loading && !state.failed) {
                item {
                    Text(pluralStringResource(R.plurals.pending_count, shown.size, shown.size), style = MaterialTheme.typography.titleMedium)
                }
            }
            when {
                state.loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
                state.failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        MessageBanner(ScreenMessage(MessageKind.Error, R.string.state_error))
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                shown.isEmpty() -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Text(stringResource(R.string.pending_empty_title), style = MaterialTheme.typography.titleMedium)
                        // Sem prometer cobertura total: nenhuma pendência conhecida não prova que está tudo registrado.
                        Text(stringResource(R.string.pending_empty_body), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> items(shown, key = { it.key }) { QueueRow(it, onAccept, onOpen, onOpenChat, onDiscardNotification) }
            }
        }
        if (!state.loading && !state.failed && shown.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                if (isCompactHeight()) {
                    // Paisagem (spec 18): frase à esquerda, ação à direita.
                    Row(Modifier.padding(AccordSpacing.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                        Text(stringResource(R.string.pending_continue_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Button(onClick = { onOpen(shown.first()) }) { Text(stringResource(R.string.pending_review_first)) }
                    }
                } else {
                    Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Button(onClick = { onOpen(shown.first()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pending_review_first)) }
                        Text(stringResource(R.string.pending_continue_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(selected: QueueFilter, onFilter: (QueueFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        QueueFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter, onClick = { onFilter(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                QueueFilter.All -> R.string.pending_filter_all
                                QueueFilter.Chat -> R.string.pending_filter_chat
                                QueueFilter.Import -> R.string.pending_filter_import
                                QueueFilter.Conflicts -> R.string.pending_filter_conflicts
                                QueueFilter.Records -> R.string.pending_filter_records
                                QueueFilter.Notifications -> R.string.pending_filter_notifications
                            },
                        ),
                    )
                },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem, onAccept: (app.accord.ui.review.CategoryReviewItem) -> Unit, onOpen: (QueueItem) -> Unit, onOpenChat: () -> Unit,
    onDiscardNotification: (QueueItem.BankNotification) -> Unit,
) {
    val (reason, subject, icon) = describe(item)
    val summary = "$reason. $subject"
    AccordCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .semantics(mergeDescendants = true) { contentDescription = summary }
            .clickable(role = Role.Button) { onOpen(item) },) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(icon)
                Text(reason, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
            }
            Text(subject, style = MaterialTheme.typography.bodyMedium)
            if (item is QueueItem.ImportDoubt && item.movement.ready) {
                Text(stringResource(R.string.pending_ready), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (item is QueueItem.BankNotification) {
                TextButton(onClick = { onDiscardNotification(item) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.pending_notification_discard))
                }
            }
            if (item is QueueItem.ChatCategory) {
                val suggestion = item.item.suggestion
                Text(
                    if (suggestion != null) stringResource(R.string.review_category_suggested, suggestion.label) else stringResource(R.string.review_category_no_suggestion),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                    if (suggestion != null) {
                        Button(onClick = { onAccept(item.item) }) { Text(stringResource(R.string.review_category_accept)) }
                    }
                    TextButton(onClick = { onOpen(item) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.pending_choose_category)) }
                }
            }
        }
    }
}

/** Motivo, assunto (com data e valor quando se sabe) e ícone da linha; valor que não se sabe não aparece como zero. */
@Composable
private fun describe(item: QueueItem): Triple<String, String, Int> = when (item) {
    is QueueItem.ChatDraft -> Triple(stringResource(R.string.pending_chat_draft), item.draft.fields["description"].orEmpty(), Lucide.drawable.lucide_ic_message_circle)
    is QueueItem.BankNotification -> {
        val f = item.draft.fields
        val amount = f["amount"]?.let { "R$ $it" }
        val income = f["kind"] == "Income"
        Triple(
            stringResource(R.string.pending_reason_notification),
            listOfNotNull(
                f["description"]?.ifBlank { null } ?: stringResource(R.string.pending_notification_no_subject),
                amount,
                stringResource(if (income) R.string.pending_notification_income else R.string.pending_notification_expense),
            ).joinToString(" · "),
            Lucide.drawable.lucide_ic_bell,
        )
    }
    is QueueItem.ChatCorrection -> Triple(stringResource(R.string.pending_correction), listOfNotNull(item.question.description, item.question.amountCents?.let { Money(it).format() }).joinToString(" · "), Lucide.drawable.lucide_ic_message_circle)
    is QueueItem.ImportDoubt -> {
        val m = item.movement
        val amount = movementAmount(m.cents, m.currency)
        val reason = m.doubts.firstOrNull()?.label(card = m.kind == app.accord.core.importing.DocumentKind.Card) ?: stringResource(R.string.pending_reason_import)
        Triple(
            when {
                m.postponed -> stringResource(R.string.pending_reason_postponed)
                m.ready -> stringResource(R.string.pending_reason_ready)
                else -> reason
            },
            listOfNotNull(m.description.ifBlank { null }, amount, m.date?.shortLabel()).joinToString(" · "),
            if (m.ready) Lucide.drawable.lucide_ic_circle_check else Lucide.drawable.lucide_ic_triangle_alert,
        )
    }
    is QueueItem.Conflict -> Triple(
        stringResource(R.string.pending_reason_conflict), stringResource(commandLabel(item.conflict.commandType)), Lucide.drawable.lucide_ic_git_compare,
    )
    is QueueItem.ChatCategory -> {
        val q = item.item.question
        Triple(
            stringResource(R.string.pending_reason_category),
            listOfNotNull(q.description, q.amountCents?.let { Money(it).format() }).joinToString(" · ").ifBlank { stringResource(R.string.review_category_item_unknown) },
            Lucide.drawable.lucide_ic_tag,
        )
    }
    is QueueItem.Record -> when (val p = item.item) {
        is PendingItem.CategoryToClarify -> Triple(stringResource(R.string.pending_reason_category), "${p.description} · ${p.amount.format()}", Lucide.drawable.lucide_ic_tag)
        is PendingItem.InvoiceLink -> Triple(stringResource(R.string.pending_reason_invoice_link), "${p.description} · ${p.amount.format()}", Lucide.drawable.lucide_ic_credit_card)
        is PendingItem.DivergentInvoicePayment -> Triple(stringResource(R.string.pending_reason_payment), "${p.amount.format()} · ${p.date.shortLabel()}", Lucide.drawable.lucide_ic_credit_card)
        is PendingItem.InvoiceTotalDifference -> Triple(stringResource(R.string.pending_reason_total), stringResource(R.string.pending_total_difference, p.difference.format()), Lucide.drawable.lucide_ic_credit_card)
    }
}
