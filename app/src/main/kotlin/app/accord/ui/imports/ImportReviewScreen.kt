package app.accord.ui.imports

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.importing.BatchReview
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.Classification
import app.accord.core.importing.ConfirmationSummary
import app.accord.core.Money
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.ReconciledMovement
import app.accord.core.space.CategoryNode
import app.accord.ui.CategoryTag
import app.accord.ui.categoryName
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.MonthSelector
import app.accord.ui.register.CategoryDialog
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.isCompactHeight
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

@Composable
fun ImportReviewRoute(
    viewModel: ImportReviewViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenResult: (String) -> Unit,
    onClosed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect {
            when (it) {
                is ImportReviewEvent.OpenResult -> onOpenResult(it.batchId)
                ImportReviewEvent.Closed -> onClosed()
            }
        }
    }
    ImportReviewScreen(
        state, padding, onBack,
        onRetry = viewModel::retry,
        onFilter = viewModel::setFilter,
        onOpen = viewModel::open,
        onCloseMovement = viewModel::closeMovement,
        onPickCategory = viewModel::pickCategoryFor,
        onCategory = viewModel::chooseCategory,
        onDismissCategory = viewModel::dismissCategory,
        onClearDefault = viewModel::clearDefaultCategory,
        onAcceptSuggestion = viewModel::acceptSuggestion,
        onAcceptAll = viewModel::acceptAllSuggestions,
        onAcceptInstallments = viewModel::acceptInstallmentTotals,
        onInstallmentTotal = viewModel::installmentTotal,
        onLink = viewModel::link,
        onSeparate = viewModel::keepSeparate,
        onReset = viewModel::reset,
        onOpenSummary = viewModel::openSummary,
        onCloseSummary = viewModel::closeSummary,
        onConfirm = viewModel::confirm,
        onAskDiscard = viewModel::askDiscard,
        onCancelDiscard = viewModel::cancelDiscard,
        onDiscard = viewModel::discard,
        onPostpone = viewModel::postpone,
        onPickRepeat = viewModel::pickRepeatFor,
        onRepeat = viewModel::markRepeat,
        onDismissRepeat = viewModel::dismissRepeat,
        onOpenSource = viewModel::openSource,
        onCloseSource = viewModel::closeSource,
        onSourceKind = viewModel::chooseSourceKind,
        onSourceOrigin = viewModel::chooseSourceOrigin,
        onSourcePrevious = viewModel::previousSourceInvoice,
        onSourceNext = viewModel::nextSourceInvoice,
        onSourceInvoiceByDate = viewModel::chooseSourceInvoiceByDate,
        onApplySource = viewModel::applySource,
    )
}

@Composable
fun ImportReviewScreen(
    state: ImportReviewUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFilter: (ReviewFilter) -> Unit,
    onOpen: (Int) -> Unit,
    onCloseMovement: () -> Unit,
    onPickCategory: (CategoryTarget) -> Unit,
    onCategory: (String) -> Unit,
    onDismissCategory: () -> Unit,
    onClearDefault: () -> Unit,
    onAcceptSuggestion: (Int) -> Unit,
    onAcceptAll: () -> Unit,
    onLink: (Int, String) -> Unit,
    onSeparate: (Int, String) -> Unit,
    onReset: (Int) -> Unit,
    onOpenSummary: () -> Unit,
    onCloseSummary: () -> Unit,
    onConfirm: () -> Unit,
    onAskDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onDiscard: () -> Unit,
    onPostpone: (Int) -> Unit = {},
    onPickRepeat: (Int) -> Unit = {},
    onRepeat: (Int, Int) -> Unit = { _, _ -> },
    onDismissRepeat: () -> Unit = {},
    onOpenSource: () -> Unit = {},
    onCloseSource: () -> Unit = {},
    onSourceKind: (DocumentKind) -> Unit = {},
    onSourceOrigin: (String) -> Unit = {},
    onSourcePrevious: () -> Unit = {},
    onSourceNext: () -> Unit = {},
    onSourceInvoiceByDate: (Boolean) -> Unit = {},
    onApplySource: () -> Unit = {},
    onAcceptInstallments: () -> Unit = {},
    onInstallmentTotal: (Int, Long) -> Unit = { _, _ -> },
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        when (val content = state.content) {
            ReviewContent.Loading -> Centered(onBack) { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            ReviewContent.Failed -> Centered(onBack) {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            ReviewContent.Missing -> Centered(onBack) { Text(stringResource(R.string.import_review_missing), style = MaterialTheme.typography.bodyLarge) }
            is ReviewContent.Ready -> {
                val review = content.review
                LazyColumn(
                    Modifier.weight(1f), contentPadding = PaddingValues(AccordSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    item { DetailHeader(stringResource(R.string.import_review_title), onBack, subtitle = fileLine(review)) }
                    item { Summary(review, state.working, onOpenSource) }
                    if (review.info.source.kind == DocumentKind.Card) item { Text(state.cards.firstOrNull { it.id == review.info.source.originId }?.nickname.orEmpty(), style = MaterialTheme.typography.titleMedium) }
                    state.message?.let { message -> item { MessageBanner(message) } }
                    item { Filters(state.filter, onFilter) }
                    item { BulkActions(review, state, onAcceptAll, onAcceptInstallments, onPickCategory, onClearDefault) }
                    val shown = review.preview.items.filter { it.matches(state.filter) }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                stringResource(if (review.preview.items.isEmpty()) R.string.import_review_empty else R.string.import_review_filter_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    items(shown, key = { it.movement.ordinal }) { MovementRow(it, state.categories, card = review.info.source.kind == DocumentKind.Card) { onOpen(it.movement.ordinal) } }
                    item { OutlinedButton(onClick = onAskDiscard, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_discard)) } }
                }
                ConfirmBar(review, state.working, onOpenSummary)
                val editing = state.editing?.let { ordinal -> review.preview.items.firstOrNull { it.movement.ordinal == ordinal } }
                if (editing != null) {
                    MovementDialog(
                        item = editing, review = review, categories = state.categories, working = state.working,
                        onClose = onCloseMovement, onPickCategory = { onPickCategory(CategoryTarget.Movement(editing.movement.ordinal)) },
                        onAcceptSuggestion = { onAcceptSuggestion(editing.movement.ordinal) },
                        onLink = { onLink(editing.movement.ordinal, it) }, onSeparate = { onSeparate(editing.movement.ordinal, it) },
                        onReset = { onReset(editing.movement.ordinal) },
                        onPostpone = { onPostpone(editing.movement.ordinal) },
                        onPickRepeat = { onPickRepeat(editing.movement.ordinal) },
                        hasRepeatCandidates = repeatCandidates(review, editing).isNotEmpty(),
                        onInstallmentTotal = { onInstallmentTotal(editing.movement.ordinal, it) },
                    )
                }
                state.pickingRepeat?.let { ordinal ->
                    val of = review.preview.items.firstOrNull { it.movement.ordinal == ordinal }
                    if (of != null) RepeatDialog(repeatCandidates(review, of), { onRepeat(ordinal, it) }, onDismissRepeat)
                }
                state.pickingCategory?.let {
                    CategoryDialog(state.categories, selected = null, onPick = onCategory, onDismiss = onDismissCategory)
                }
                state.source?.let { SourceDialog(it, state, onSourceKind, onSourceOrigin, onSourcePrevious, onSourceNext, onSourceInvoiceByDate, onApplySource, onCloseSource) }
                if (state.summaryOpen) SummaryDialog(review.summary, state.working, onConfirm, onCloseSummary,
                    state.cards.firstOrNull { review.info.source.kind == DocumentKind.Card && it.id == review.info.source.originId }?.nickname, review.token)
                if (state.discardOpen) DiscardDialog(onDiscard, onCancelDiscard)
            }
        }
    }
}

@Composable
private fun Centered(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
        DetailHeader(stringResource(R.string.import_review_title), onBack)
        content()
    }
}

@Composable
private fun fileLine(review: BatchReview): String {
    val info = review.info
    return listOfNotNull(info.fileName.ifBlank { null }, period(info.periodStart, info.periodEnd)).joinToString(" · ")
}

@Composable
private fun Summary(review: BatchReview, working: Boolean, onOpenSource: () -> Unit) {
    val c = review.preview.counts
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        // A moeda original fica à vista; EUR nunca é mostrado como reais nem convertido.
        review.info.currency?.takeIf { it != "BRL" }?.let {
            Text(stringResource(R.string.import_file_currency, it), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            pluralStringResource(R.plurals.import_count_movements, c.total, c.total) + ": " +
                listOf(
                    pluralStringResource(R.plurals.import_count_new, c.new, c.new),
                    pluralStringResource(R.plurals.import_count_links, c.safeLinks, c.safeLinks),
                    pluralStringResource(R.plurals.import_count_doubts, c.doubts + c.blocked, c.doubts + c.blocked),
                ).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (c.alreadyKnown > 0) {
            Text(pluralStringResource(R.plurals.import_review_known_count, c.alreadyKnown, c.alreadyKnown), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        review.info.source.kind?.let {
            Text(
                stringResource(if (it == DocumentKind.Card) R.string.import_kind_card else R.string.import_kind_account),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (review.info.source.kind == DocumentKind.Card) {
            val invoice = review.info.source.documentInvoice
            Text(
                if (invoice != null) stringResource(R.string.import_source_invoice_line, monthText(invoice)) else stringResource(R.string.import_source_invoice_by_date),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (review.info.status == BatchStatus.InReview) {
            OutlinedButton(onClick = onOpenSource, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_source_change)) }
        }
        if (review.info.unrecognizedSegments > 0) {
            MessageBanner(app.accord.ui.access.ScreenMessage(app.accord.ui.access.MessageKind.Attention, R.string.import_partial_coverage, listOf(review.info.unrecognizedSegments)))
        }
    }
}

@Composable
private fun Filters(selected: ReviewFilter, onFilter: (ReviewFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        ReviewFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter, onClick = { onFilter(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                ReviewFilter.All -> R.string.import_filter_all
                                ReviewFilter.New -> R.string.import_filter_new
                                ReviewFilter.Links -> R.string.import_filter_links
                                ReviewFilter.Doubts -> R.string.import_filter_doubts
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
private fun BulkActions(
    review: BatchReview,
    state: ImportReviewUiState,
    onAcceptAll: () -> Unit,
    onAcceptInstallments: () -> Unit,
    onPickCategory: (CategoryTarget) -> Unit,
    onClearDefault: () -> Unit,
) {
    val suggestions = review.preview.items.count { it.suggestedCategoryId != null }
    val needCategory = review.preview.items.any { Classification.Doubt == it.classification && it.doubts.contains(app.accord.core.importing.DoubtReason.CategoryUnknown) }
    // Cada compra parcelada que o arquivo mostra por várias linhas, sem total declarado, conta uma vez.
    val purchases = review.preview.items
        .filter { it.installmentSeries?.acceptable == true && app.accord.core.importing.DoubtReason.InstallmentPurchaseNotRegistered in it.doubts }
        .distinctBy { it.installmentSeries!!.leaderOrdinal }.size
    if (suggestions == 0 && !needCategory && purchases == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        if (purchases > 0) {
            OutlinedButton(onClick = onAcceptInstallments, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.import_accept_installments, purchases, purchases))
            }
            Text(stringResource(R.string.import_accept_installments_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (suggestions > 0) {
            OutlinedButton(onClick = onAcceptAll, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.import_accept_suggestions, suggestions, suggestions))
            }
        }
        if (needCategory) {
            OutlinedButton(onClick = { onPickCategory(CategoryTarget.Default) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_default_category))
            }
        }
    }
}

@Composable
private fun MovementRow(item: ReconciledMovement, categories: Map<String, CategoryNode>, card: Boolean = false, onClick: () -> Unit) {
    val m = item.movement
    val amount = movementAmount(m.amountCents, m.currency) ?: stringResource(R.string.import_amount_unknown)
    val state = item.classification.label()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .semantics(mergeDescendants = true) { contentDescription = "${m.description}, $amount, $state" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(m.description.ifBlank { stringResource(R.string.import_no_description) }, style = MaterialTheme.typography.titleSmall) },
                end = { Text(amount, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                DecorativeIcon(classIcon(item.classification))
                Text(
                    listOfNotNull(m.date?.shortLabel(), state).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (item.doubts.isNotEmpty()) {
                Text(item.doubts.map { it.label(card) }.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.effect?.let { Text(it.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val shownCategory = item.categoryId ?: item.suggestedCategoryId
            if (categoryName(categories, shownCategory) != null) {
                // Escolhida: só o ícone colorido e o nome. Sugerida: o mesmo, com a origem da sugestão antes (só vale se aceita).
                val prefix = when {
                    item.categoryId != null -> null
                    item.suggestionSource == app.accord.core.importing.SuggestionSource.BankLabel -> R.string.import_category_prefix_bank
                    item.suggestionSource == app.accord.core.importing.SuggestionSource.History -> R.string.import_category_prefix_history
                    else -> R.string.import_category_prefix_suggested
                }
                prefix?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                CategoryTag(categories, shownCategory, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun classIcon(c: Classification): Int = when (c) {
    Classification.New -> Lucide.drawable.lucide_ic_circle_plus
    Classification.SafeLink -> Lucide.drawable.lucide_ic_link
    Classification.AlreadyKnown -> Lucide.drawable.lucide_ic_circle_check
    Classification.Doubt -> Lucide.drawable.lucide_ic_triangle_alert
    Classification.BlockedByCurrency -> Lucide.drawable.lucide_ic_circle_alert
}

@Composable
private fun ConfirmBar(review: BatchReview, working: Boolean, onOpenSummary: () -> Unit) {
    val c = review.preview.counts
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        val line: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                stringResource(
                    R.string.import_confirm_line,
                    pluralStringResource(R.plurals.import_confirm_checked, c.checked, c.checked),
                    pluralStringResource(R.plurals.import_confirm_preserved, c.doubts + c.blocked, c.doubts + c.blocked),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier,
            )
        }
        val enabled = review.blockers.isEmpty() && !working
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            if (isCompactHeight()) {
                // Paisagem (spec 16): resumo à esquerda, Confirmar à direita; o aviso de bloqueio continua em largura total.
                if (review.blockers.isNotEmpty()) MessageBanner(ImportReviewViewModel.blockedMessage(review.blockers))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                    line(Modifier.weight(1f))
                    Button(onClick = onOpenSummary, enabled = enabled) { Text(stringResource(R.string.import_confirm)) }
                }
            } else {
                line(Modifier)
                if (review.blockers.isNotEmpty()) {
                    MessageBanner(ImportReviewViewModel.blockedMessage(review.blockers))
                }
                Button(onClick = onOpenSummary, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_confirm))
                }
            }
        }
    }
}

@Composable
private fun MovementDialog(
    item: ReconciledMovement,
    review: BatchReview,
    categories: Map<String, CategoryNode>,
    working: Boolean,
    onClose: () -> Unit,
    onPickCategory: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onLink: (String) -> Unit,
    onSeparate: (String) -> Unit,
    onReset: () -> Unit,
    onPostpone: () -> Unit,
    onPickRepeat: () -> Unit,
    hasRepeatCandidates: Boolean,
    onInstallmentTotal: (Long) -> Unit = {},
) {
    val m = item.movement
    var showOriginal by remember { mutableStateOf(false) }
    val candidates = item.candidateEntryIds.mapNotNull { id -> review.linkableEntries.firstOrNull { it.id == id } }
    val editable = item.classification == Classification.New || item.classification == Classification.Doubt
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(m.description.ifBlank { stringResource(R.string.import_no_description) }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(listOfNotNull(m.date?.let { "${it.shortLabel()}/${it.year}" }, movementAmount(m.amountCents, m.currency)).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                Text(item.classification.label(), style = MaterialTheme.typography.titleSmall)
                item.doubts.forEach { Text(it.label(review.info.source.kind == DocumentKind.Card), style = MaterialTheme.typography.bodyMedium) }
                item.effect?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium) }
                CategoryTag(categories, item.categoryId)
                item.installmentSeries?.let { series ->
                    Text(
                        stringResource(if (series.complete) R.string.import_series_complete else R.string.import_series_partial, series.size, series.total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (app.accord.core.importing.DoubtReason.InstallmentPurchaseNotRegistered in item.doubts) {
                        Button(onClick = { onInstallmentTotal(series.suggestedTotalCents) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.import_series_register, Money(series.suggestedTotalCents).format()))
                        }
                    }
                }

                if (editable) {
                    OutlinedButton(onClick = onPickCategory, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_category)) }
                }
                item.suggestedCategoryId?.let { suggested ->
                    Button(onClick = onAcceptSuggestion, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_accept_suggestion, categoryName(categories, suggested).orEmpty()))
                    }
                }
                candidates.forEach { entry ->
                    Text(entryLine(entry), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onLink(entry.id) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_link_to)) }
                    OutlinedButton(onClick = { onSeparate(entry.id) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_keep_separate)) }
                }
                item.linkedEntryId?.let { linked ->
                    review.linkableEntries.firstOrNull { it.id == linked }?.let { Text(entryLine(it), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = { onSeparate(linked) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_not_same)) }
                }
                if (item.classification != Classification.AlreadyKnown && item.classification != Classification.BlockedByCurrency) {
                    OutlinedButton(onClick = onPostpone, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_review_later)) }
                    if (hasRepeatCandidates) {
                        OutlinedButton(onClick = onPickRepeat, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_repeat_of)) }
                    }
                    TextButton(onClick = onReset, enabled = !working, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_reset)) }
                }
                TextButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(if (showOriginal) R.string.import_original_hide else R.string.import_original_show))
                }
                if (showOriginal) {
                    m.originalFields.forEach { (name, value) -> Text("$name: $value", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_close)) } },
    )
}

/** Corrige o tipo, a conta/cartão e a fatura do documento. Nada muda até "Aplicar"; decisões que apontavam para a origem anterior voltam a ser dúvida. */
@Composable
private fun SourceDialog(
    draft: SourceDraft,
    state: ImportReviewUiState,
    onKind: (DocumentKind) -> Unit,
    onOrigin: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onInvoiceByDate: (Boolean) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val enabled = !state.working
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.import_source_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Text(stringResource(R.string.import_source_note), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    Text(stringResource(R.string.import_kind_title), style = MaterialTheme.typography.titleMedium)
                    ChoiceRow(stringResource(R.string.import_kind_card), draft.kind == DocumentKind.Card, enabled) { onKind(DocumentKind.Card) }
                    ChoiceRow(stringResource(R.string.import_kind_account), draft.kind == DocumentKind.Account, enabled) { onKind(DocumentKind.Account) }
                }
                when (draft.kind) {
                    null -> Unit
                    DocumentKind.Account -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                        Text(stringResource(R.string.import_origin_account), style = MaterialTheme.typography.titleMedium)
                        if (state.accounts.isEmpty()) Text(stringResource(R.string.import_origin_no_accounts), style = MaterialTheme.typography.bodyMedium)
                        state.accounts.forEach { account -> ChoiceRow(accountLabel(account), draft.originId == account.id, enabled) { onOrigin(account.id) } }
                    }
                    DocumentKind.Card -> Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        Text(stringResource(R.string.import_origin_card), style = MaterialTheme.typography.titleMedium)
                        state.cards.filter { it.configured }.forEach { card -> ChoiceRow(card.nickname, draft.originId == card.id, enabled) { onOrigin(card.id) } }
                        InvoiceChoice(draft.invoice, enabled, onInvoiceByDate, onPrevious, onNext)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onApply, enabled = enabled && draft.canApply) { Text(stringResource(R.string.import_source_apply)) } },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_cancel)) } },
    )
}

/** Outros movimentos do arquivo com o mesmo valor que ainda não foram descartados: só um deles pode ser o original de uma repetição. */
private fun repeatCandidates(review: BatchReview, of: ReconciledMovement): List<ReconciledMovement> =
    review.preview.items.filter { it.movement.ordinal != of.movement.ordinal && !it.dismissed && it.movement.amountCents != null && it.movement.amountCents == of.movement.amountCents }

@Composable
private fun RepeatDialog(candidates: List<ReconciledMovement>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_repeat_pick_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEach { c ->
                    val m = c.movement
                    Text(
                        stringResource(R.string.import_repeat_line, m.ordinal, m.description.ifBlank { stringResource(R.string.import_no_description) }, m.date?.shortLabel().orEmpty()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onPick(m.ordinal) }.padding(vertical = AccordSpacing.s3),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_cancel)) } },
    )
}

@Composable
private fun entryLine(entry: LinkableEntry): String =
    stringResource(
        R.string.import_entry_line, entry.description, "${entry.date.shortLabel()}/${entry.date.year}",
        app.accord.core.Money(-entry.cents).format(),
    )

@Composable
private fun SummaryDialog(summary: ConfirmationSummary, working: Boolean, onConfirm: () -> Unit, onClose: () -> Unit, cardName: String?, token: String) {
    var cardConfirmed by remember(token) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.import_summary_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                if (cardName != null) {
                    Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).toggleable(value = cardConfirmed, role = Role.Checkbox, onValueChange = { cardConfirmed = it }), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = cardConfirmed, onCheckedChange = null)
                        Text(stringResource(R.string.cards_import_confirm, cardName), modifier = Modifier.weight(1f))
                    }
                }
                summaryLine(R.plurals.import_summary_creates, summary.creates)
                summaryLine(R.plurals.import_summary_links, summary.links)
                summaryLine(R.plurals.import_summary_corrections, summary.invoiceCorrections)
                summaryLine(R.plurals.import_summary_installments, summary.installmentLinks)
                summaryLine(R.plurals.import_summary_invoices, summary.invoicePayments)
                summaryLine(R.plurals.import_summary_boletos, summary.boletoPayments)
                summaryLine(R.plurals.import_summary_incomes, summary.incomes)
                summaryLine(R.plurals.import_summary_refunds, summary.refunds)
                summaryLine(R.plurals.import_summary_known, summary.alreadyKnown)
                summaryLine(R.plurals.import_summary_dismissed, summary.dismissed)
                summaryLine(R.plurals.import_summary_pending, summary.pendingDoubts)
                summary.installmentShifts.forEach { shift ->
                    Text(
                        stringResource(R.string.import_summary_shift, shift.before.joinToString(", ") { monthText(it) }, shift.after.joinToString(", ") { monthText(it) }),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(stringResource(R.string.import_summary_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !working && (cardName == null || cardConfirmed)) { Text(stringResource(R.string.import_summary_confirm)) } },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_cancel)) } },
    )
}

@Composable
private fun summaryLine(plural: Int, count: Int) {
    if (count > 0) Text(pluralStringResource(plural, count, count), style = MaterialTheme.typography.bodyLarge)
}

@Composable
internal fun DiscardDialog(onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_discard_title)) },
        text = { Text(stringResource(R.string.import_discard_body)) },
        confirmButton = { Button(onClick = onDiscard) { Text(stringResource(R.string.import_discard_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.import_cancel)) } },
    )
}
