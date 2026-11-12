package app.accord.ui.entries

import app.accord.ui.components.AccordCard
import app.accord.ui.components.accordSheetColor
import app.accord.ui.categoryColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.vendors.EntryIdentityIcon
import app.accord.core.Money
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntrySummary
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.colorKey
import app.accord.ui.incomeCategoryLabel
import app.accord.core.space.PaymentMethod
import app.accord.ui.categoryPath
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.ScreenTitle
import app.accord.ui.components.lucideRes
import app.accord.core.label
import app.accord.ui.label
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.isCompactHeight
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EntriesRoute(
    viewModel: EntriesViewModel,
    padding: PaddingValues,
    onOpenEntry: (String) -> Unit,
    onNew: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::reload)
    EntriesScreen(
        state, padding,
        onSearch = viewModel::setSearch, onApplyFilter = viewModel::applyFilter, onClear = viewModel::clearFilters,
        onRetry = viewModel::retry, onLoadMore = viewModel::loadMore, onOpenEntry = onOpenEntry, onNew = onNew, onBack = onBack,
        showInlineNew = onBack != null || LocalDensity.current.fontScale >= 1.5f || isCompactHeight(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntriesScreen(
    state: EntriesUiState,
    padding: PaddingValues,
    onSearch: (String) -> Unit,
    onApplyFilter: (EntriesFilterState) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onNew: () -> Unit,
    onBack: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    showInlineNew: Boolean = true,
) {
    val enlarged = LocalDensity.current.fontScale >= 1.5f
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable(state.filter.search) { mutableStateOf(state.filter.search) }

    // Carregamento progressivo: ao chegar perto do fim pede a página seguinte, sem mexer na ordem.
    LaunchedEffect(listState, state.rows.size, state.nextCursor) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (state.nextCursor != null && last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_DISTANCE) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(
            start = AccordSpacing.s4, end = AccordSpacing.s4,
            top = AccordSpacing.s4,
            bottom = AccordSpacing.s4 + if (!showInlineNew) 80.dp else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                        Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
                    }
                }
                ScreenTitle(stringResource(R.string.entries_title), Modifier.weight(1f))
                if (showInlineNew && !enlarged) Button(onClick = onNew, modifier = Modifier.heightIn(min = AccordSpacing.touchMin).testTag("entries-title-new")) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_plus, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.entries_new), modifier = Modifier.padding(start = AccordSpacing.s1))
                }
            }
        }
        if (showInlineNew && enlarged) item {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("entries-inline-new")) {
                DecorativeIcon(Lucide.drawable.lucide_ic_plus, tint = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(R.string.action_register_manually), modifier = Modifier.padding(start = AccordSpacing.s1))
            }
        }
        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; onSearch(it) },
                label = { Text(stringResource(R.string.entries_search_label), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_search) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = ""; onSearch("") }) {
                            Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.entries_search_clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { EntriesFiltersButton(state.filter, onClick = { showFilters = true }, modifier = Modifier.fillMaxWidth()) }
        item {
            FilterBar(
                state.filter, state.currentMonth,
                onPickMonth = { showMonthPicker = true },
                onChangeMonth = { delta ->
                    onApplyFilter(
                        state.filter.copy(from = state.filter.from.plusMonths(delta), to = state.filter.to.plusMonths(delta)),
                    )
                },
                onBackToCurrent = { onApplyFilter(state.filter.copy(from = state.currentMonth, to = state.currentMonth)) },
            )
        }

        when {
            state.status == EntriesStatus.Failed -> item { ErrorBanner(state.rows.isNotEmpty(), onRetry) }
        }
        if (state.rows.isNotEmpty()) {
            state.summary?.let { summary -> item { SummaryBlock(summary, state.total) } }
            itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                Column {
                    if (index == 0 || state.rows[index - 1].date != row.date) {
                        Text(
                            row.date.format(ENTRY_GROUP_DATE),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AccordSpacing.s2, bottom = AccordSpacing.s1).semantics { heading() },
                        )
                    }
                    EntryItem(row, state.categories, state.incomeCategories, onOpenEntry)
                }
            }
            if (state.status == EntriesStatus.Loading && state.nextCursor != null) {
                item { Text(stringResource(R.string.entries_loading_more), style = MaterialTheme.typography.bodyMedium) }
            }
            if (state.loadMoreFailed) item { ErrorBanner(true, onLoadMore) }
        } else if (state.status == EntriesStatus.Ready) {
            item { EmptyState(state.filter.isFiltered, onClear, onNew, showInlineNew) }
        } else if (state.status == EntriesStatus.Loading) {
            item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
        }
    }

    if (showFilters) {
        ModalBottomSheet(containerColor = accordSheetColor(), onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FilterPanel(
                initial = state.filter,
                categories = state.categories,
                onApply = { onApplyFilter(it); showFilters = false },
                onCancel = { showFilters = false },
            )
        }
    }
    if (showMonthPicker) {
        MonthPickerSheet(
            selected = state.filter.from,
            onSelect = { month ->
                onApplyFilter(state.filter.copy(from = month, to = month))
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }
}

private const val LOAD_MORE_DISTANCE = 4
private val ENTRY_GROUP_DATE = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
private val ENTRY_INVOICE_MONTH = DateTimeFormatter.ofPattern("MM/yy", Locale.forLanguageTag("pt-BR"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    filter: EntriesFilterState,
    currentMonth: YearMonth,
    onPickMonth: () -> Unit,
    onChangeMonth: (Long) -> Unit,
    onBackToCurrent: () -> Unit,
) {
    val monthPickerDescription = stringResource(R.string.entries_pick_month)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChangeMonth(-1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.entries_previous_month))
            }
            AccordCard(
                modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onPickMonth)
                    .semantics { contentDescription = monthPickerDescription },
                shape = MaterialTheme.shapes.small,) {
                Row(
                    Modifier.heightIn(min = AccordSpacing.touchMin).padding(horizontal = AccordSpacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_calendar, modifier = Modifier.size(20.dp))
                    Text(
                        if (filter.from == filter.to) filter.from.label() else stringResource(R.string.entries_period, filter.from.label(), filter.to.label()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = AccordSpacing.s2),
                    )
                }
            }
            if (filter.from != currentMonth || filter.to != currentMonth) {
                FilledTonalIconButton(onClick = onBackToCurrent, modifier = Modifier.size(AccordSpacing.touchMin).testTag("entries-back-to-current")) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_calendar_check), contentDescription = stringResource(R.string.entries_back_to_current))
                }
            }
            IconButton(onClick = { onChangeMonth(1) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.entries_next_month))
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MonthPickerSheet(selected: YearMonth, onSelect: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by rememberSaveable { mutableStateOf(selected.year) }
    ModalBottomSheet(containerColor = accordSheetColor(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { year-- }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.entries_previous_year))
                }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                IconButton(onClick = { year++ }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.entries_next_year))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                (1..12).forEach { monthNumber ->
                    val month = YearMonth.of(year, monthNumber)
                    FilterChip(
                        selected = month == selected,
                        onClick = { onSelect(month) },
                        label = { Text(month.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("pt-BR"))).replaceFirstChar(Char::uppercase)) },
                        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(keepsPrevious: Boolean, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.entries_error), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (keepsPrevious) {
                Text(stringResource(R.string.entries_error_keep), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onClear: () -> Unit, onNew: () -> Unit, showInlineNew: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = AccordSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (filtered) R.string.entries_empty_filtered_title else R.string.entries_empty_title),
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        if (!filtered) {
            Text(
                stringResource(R.string.entries_empty_body),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        if (filtered) {
            OutlinedButton(onClick = onClear, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.entries_clear_filters))
            }
        } else if (showInlineNew) {
            Button(onClick = onNew, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.action_register_manually))
            }
        }
    }
}

@Composable
private fun SummaryBlock(summary: EntrySummary, total: Int) {
    val incomeColor = Accord.colors.income
    val expenseColor = Accord.colors.expense
    val result = summary.listResult
    val resultColor = when {
        result.cents > 0 -> incomeColor
        result.cents < 0 -> expenseColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val count = pluralStringResource(R.plurals.entries_total, total, total)
    val description = pluralStringResource(R.plurals.entries_total, total, total) + ", " +
        pluralStringResource(R.plurals.entries_summary_incomes, summary.incomeCount, summary.incomeCount) + " e " +
        pluralStringResource(R.plurals.entries_summary_expenses, summary.expenseCount, summary.expenseCount)
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(
                buildAnnotatedString {
                    append("$count (")
                    withStyle(SpanStyle(color = incomeColor)) { append(summary.incomeCount.toString()) }
                    append("/")
                    withStyle(SpanStyle(color = expenseColor)) { append(summary.expenseCount.toString()) }
                    append(")")
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = description },
            )
            Text(result.format(), style = Accord.typography.amountRow, color = resultColor)
        }
    }
}

private val NOISY_SITUATIONS = setOf(EntrySituation.Paid, EntrySituation.NotPaid)

/** Pago e não pago só aparecem onde dizem algo: pagamento direto já nasce pago e seria ruído. */
private fun EntryRow.visibleSituations(): List<EntrySituation> {
    val showPaid = kind in setOf(
        EntryKind.Boleto, EntryKind.Income, EntryKind.RecurrenceOccurrence, EntryKind.InvoicePayment, EntryKind.CardInstallment,
    )
    return situations.filter { showPaid || it !in NOISY_SITUATIONS }
        .filterNot { it == EntrySituation.NotPaid && EntrySituation.Overdue in situations }
        .sortedBy { it.ordinal }
}

@Composable
private fun EntryItem(
    row: EntryRow,
    categories: Map<String, CategoryNode>,
    incomeCategories: Map<String, IncomeCategoryNode>,
    onOpen: (String) -> Unit,
) {
    val expenseCategory = row.categoryId?.let(categories::get)
    val incomeCategory = row.incomeCategoryId?.let(incomeCategories::get)
    val incomeCategoryList = remember(incomeCategories) { incomeCategories.values.toList() }
    val categoryName = expenseCategory?.name ?: incomeCategory?.let { incomeCategoryLabel(it, incomeCategoryList) }
    val situations = row.visibleSituations()
    val installmentNumber = row.installmentNumber
    val installmentCount = row.installmentCount
    val installment = if (installmentNumber != null && installmentCount != null && installmentCount > 1) {
        stringResource(R.string.detail_installment_of, installmentNumber, installmentCount)
    } else {
        null
    }
    val invoiceMonth = row.dueDate?.let { YearMonth.from(it).format(ENTRY_INVOICE_MONTH) }
        ?.takeIf { row.kind == EntryKind.CardInstallment }
        ?.let { stringResource(R.string.entries_invoice_month, it) }
    val situationLabels = situations.map { it.label() }
    val subtitle = listOfNotNull(
        row.date.shortLabel(), installment, invoiceMonth, row.method?.label(),
        row.kind.label().takeIf { row.kind in setOf(EntryKind.Refund, EntryKind.InvoicePayment, EntryKind.Income) },
    ).joinToString(" · ")
    // Compra parcelada: o valor da linha é o da parcela exibida ("Parcela 3/10"), nunca o total da compra.
    val shownAmount = (row.installmentAmount ?: row.amount).format()
    val description = (listOfNotNull(row.description, shownAmount, categoryName, subtitle.takeIf(String::isNotBlank)) + situationLabels).joinToString(". ")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        onClick = { onOpen(row.id) },
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).padding(vertical = AccordSpacing.s3),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                EntryIdentityIcon(
                    description = row.description,
                    allowVendor = row.kind != EntryKind.Income && row.kind != EntryKind.InvoicePayment,
                    entryId = row.id,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    AdaptiveRow(
                        start = { Text(row.description, style = MaterialTheme.typography.titleMedium) },
                        end = { Text(shownAmount, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
                    )
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expenseCategory != null || incomeCategory != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                        ) {
                            DecorativeIcon(
                                lucideRes(expenseCategory?.iconKey ?: incomeCategory!!.iconKey),
                                modifier = Modifier.size(16.dp),
                                tint = if (expenseCategory != null) categoryColor(categories, expenseCategory.id)
                                else categoryColor(incomeCategory!!.colorKey(incomeCategoryList)),
                            )
                            Text(categoryName.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (situations.isNotEmpty()) SituationTags(situations)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Marcas de situação: ícone e texto juntos, nunca só cor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SituationTags(situations: List<EntrySituation>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        situations.forEach { s ->
            val attention = s in setOf(
                EntrySituation.ToClassify, EntrySituation.InvoiceLinkToConfirm, EntrySituation.Overdue,
                EntrySituation.CancelRequested, EntrySituation.DivergentPayment,
            )
            Surface(
                color = if (attention) Accord.colors.attentionContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    Modifier.padding(horizontal = AccordSpacing.s2, vertical = AccordSpacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                ) {
                    val tint = if (attention) Accord.colors.onAttentionContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    DecorativeIcon(
                        when (s) {
                            EntrySituation.Paid -> Lucide.drawable.lucide_ic_circle_check
                            EntrySituation.Cancelled -> Lucide.drawable.lucide_ic_circle_x
                            EntrySituation.NotPaid -> Lucide.drawable.lucide_ic_clock
                            else -> Lucide.drawable.lucide_ic_triangle_alert
                        },
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(s.label(), style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}

private val ALL_SITUATIONS = EntrySituation.entries
private val ALL_METHODS = PaymentMethod.entries

/** Painel de filtros: o que se escolhe aqui só vale ao Aplicar; Cancelar não muda nada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    initial: EntriesFilterState,
    categories: Map<String, CategoryNode>,
    onApply: (EntriesFilterState) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val mains = categories.values.filter { it.parentId == null }.sortedBy { it.order }

    Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4).padding(bottom = AccordSpacing.s4)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            ScreenTitle(stringResource(R.string.filter_title))

            Text(stringResource(R.string.filter_period), style = MaterialTheme.typography.titleMedium)
            MonthStepper(stringResource(R.string.filter_from), draft.from) { draft = draft.copy(from = it, to = maxOf(draft.to, it)) }
            MonthStepper(stringResource(R.string.filter_to), draft.to) { draft = draft.copy(to = it, from = minOf(draft.from, it)) }

            Text(stringResource(R.string.filter_category), style = MaterialTheme.typography.titleMedium)
            CategoryChoice(stringResource(R.string.filter_category_all), draft.categoryId == null, indent = false) { draft = draft.copy(categoryId = null) }
            mains.forEach { main ->
                CategoryChoice(main.name, draft.categoryId == main.id, indent = false) { draft = draft.copy(categoryId = main.id) }
                categories.values.filter { it.parentId == main.id }.sortedBy { it.order }.forEach { sub ->
                    CategoryChoice(sub.name, draft.categoryId == sub.id, indent = true) { draft = draft.copy(categoryId = sub.id) }
                }
            }

            Text(stringResource(R.string.filter_method), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                ALL_METHODS.forEach { method ->
                    val on = method in draft.methods
                    FilterChip(
                        selected = on,
                        onClick = { draft = draft.copy(methods = if (on) draft.methods - method else draft.methods + method) },
                        label = { Text(method.label()) },
                        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }

            Text(stringResource(R.string.filter_situation), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                ALL_SITUATIONS.forEach { situation ->
                    val on = situation in draft.situations
                    FilterChip(
                        selected = on,
                        onClick = { draft = draft.copy(situations = if (on) draft.situations - situation else draft.situations + situation) },
                        label = { Text(situation.label()) },
                        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
        }

        // Ações fixas: nunca ficam escondidas sob a rolagem nem sob o teclado.
        Row(
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            modifier = Modifier.fillMaxWidth().padding(top = AccordSpacing.s3),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.filter_cancel))
            }
            Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.filter_apply))
            }
        }
    }
}

@Composable
private fun MonthStepper(label: String, month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(month.minusMonths(1)) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.month_previous))
            }
            Text(month.label(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange(month.plusMonths(1)) }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.month_next))
            }
        }
    }
}

/** A linha inteira é o alvo de toque (não só o botão de opção) e se anuncia como seleção única. */
@Composable
private fun CategoryChoice(name: String, selected: Boolean, indent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = if (indent) AccordSpacing.s5 else AccordSpacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}
