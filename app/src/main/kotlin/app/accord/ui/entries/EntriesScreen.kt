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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import app.accord.core.BudgetClock
import app.accord.core.analysis.formatSigned
import app.accord.R
import app.accord.ui.vendors.EntryIdentityIcon
import app.accord.core.Money
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.AccountInfo
import app.accord.core.space.CardInfo
import app.accord.core.space.EntrySummary
import app.accord.core.space.EntryTypeFilter
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.colorKey
import app.accord.ui.incomeCategoryLabel
import app.accord.core.space.PaymentMethod
import app.accord.ui.categoryPath
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.RemovableChip
import app.accord.ui.register.CategoryDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import app.accord.ui.components.ScreenTitle
import app.accord.ui.components.lucideRes
import app.accord.core.label
import app.accord.ui.label
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.isCompactHeight
import com.composables.icons.lucide.R as Lucide
import java.time.LocalDate
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
    onImport: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::reload)
    EntriesScreen(
        state, padding,
        onSearch = viewModel::setSearch, onApplyFilter = viewModel::applyFilter, onClear = viewModel::clearFilters,
        onRetry = viewModel::retry, onLoadMore = viewModel::loadMore, onOpenEntry = onOpenEntry, onNew = onNew, onBack = onBack,
        onImport = onImport, countFor = viewModel::count,
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
    onImport: (() -> Unit)? = null,
    countFor: suspend (EntriesFilterState) -> Int? = { null },
) {
    val fontScale = LocalDensity.current.fontScale
    val enlarged = fontScale >= 1.5f
    // Topo fixo: a barra de mês sempre; busca e filtros também, exceto com fonte a partir de 1,3 ou altura compacta (spec 22), quando rolam com a lista.
    val fixedSearch = fontScale < 1.3f && !isCompactHeight()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var showCriterion by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable(state.filter.search) { mutableStateOf(state.filter.search) }
    val today = remember { LocalDate.now(BudgetClock.ZONE) }

    // Carregamento progressivo: ao chegar perto do fim pede a página seguinte, sem mexer na ordem.
    LaunchedEffect(listState, state.rows.size, state.nextCursor) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (state.nextCursor != null && last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_DISTANCE) onLoadMore()
        }
    }
    val chips: @Composable () -> Unit = {
        EntriesChips(state, onApplyFilter, onCriterion = { showCriterion = true })
    }
    val search: @Composable (Modifier) -> Unit = { modifier ->
        SearchField(searchText, onChange = { searchText = it; onSearch(it) }, onClear = { searchText = ""; onSearch("") }, modifier)
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier.fillMaxWidth().padding(start = AccordSpacing.s4, end = AccordSpacing.s4, top = AccordSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            // O mês é o título da tela; a linha com título só existe onde há Voltar ou "Novo" (lista aberta de outra tela, paisagem).
            if (onBack != null || (showInlineNew && !enlarged)) {
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
            if (fixedSearch) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    search(Modifier.weight(1f))
                    EntriesFiltersButton(state.filter, onClick = { showFilters = true })
                }
                chips()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = AccordSpacing.s4, end = AccordSpacing.s4,
                top = AccordSpacing.s2,
                bottom = AccordSpacing.s4 + if (!showInlineNew) 80.dp else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            if (showInlineNew && enlarged) item {
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("entries-inline-new")) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_plus, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.action_register_manually), modifier = Modifier.padding(start = AccordSpacing.s1))
                }
            }
            if (!fixedSearch) {
                item { search(Modifier.fillMaxWidth()) }
                item { EntriesFiltersButton(state.filter, onClick = { showFilters = true }, modifier = Modifier.fillMaxWidth()) }
                item { chips() }
            }

            when {
                state.status == EntriesStatus.Failed -> item { ErrorBanner(state.rows.isNotEmpty(), onRetry) }
            }
            if (state.rows.isNotEmpty()) {
                item { SummaryBlock(state.summary, state.total) }
                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                    Column {
                        if (index == 0 || state.rows[index - 1].date != row.date) {
                            Text(
                                dayLabel(row.date, today),
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
                item { EmptyState(state.filter.isFiltered, onClear, onNew, showInlineNew, onImport) }
            } else {
                // Sem linhas ainda (carregando ou falha): o resumo mostra "—", nunca zero.
                item { SummaryBlock(null, 0) }
                if (state.status == EntriesStatus.Loading) item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(containerColor = accordSheetColor(), onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FilterPanel(
                initial = state.filter,
                categories = state.categories,
                onApply = { onApplyFilter(it); showFilters = false },
                onCancel = { showFilters = false },
                incomeCategories = state.incomeCategories, cards = state.cards, accounts = state.accounts, countFor = countFor,
            )
        }
    }
    if (showCriterion) {
        CriterionSheet(
            selected = state.filter.criterion,
            onPick = { onApplyFilter(state.filter.copy(criterion = it)); showCriterion = false },
            onDismiss = { showCriterion = false },
        )
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
private val ENTRY_DAY = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", Locale.forLanguageTag("pt-BR"))
private val ENTRY_DAY_YEAR = DateTimeFormatter.ofPattern("EEE, d 'de' MMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
private val ENTRY_INVOICE_MONTH = DateTimeFormatter.ofPattern("MM/yy", Locale.forLanguageTag("pt-BR"))

/** "Hoje", "Ontem" ou "sex., 6 de nov." (com o ano só quando não é o corrente); sem total por dia. */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> stringResource(R.string.entries_day_today)
    date == today.minusDays(1) -> stringResource(R.string.entries_day_yesterday)
    date.year == today.year -> date.format(ENTRY_DAY)
    else -> date.format(ENTRY_DAY_YEAR)
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClear: () -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.entries_search_label), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        leadingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_search) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.entries_search_clear))
                }
            }
        },
        modifier = modifier,
    )
}

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
private fun EntryTypeFilter.chipLabel(): String = stringResource(
    when (this) {
        EntryTypeFilter.All -> R.string.entries_chip_type_all
        EntryTypeFilter.Expenses -> R.string.entries_chip_type_expenses
        EntryTypeFilter.Incomes -> R.string.entries_chip_type_incomes
    },
)

@Composable
private fun DateCriterion.chipLabel(): String = stringResource(
    if (this == DateCriterion.PurchaseDate) R.string.entries_chip_criterion_purchase else R.string.entries_chip_criterion_budget,
)

/**
 * Filtro rápido de tipo, critério de data (só desta lista) e um chip removível por filtro ativo, com "Limpar" (spec 23). Remover um chip
 * não abre o painel; a busca não vira chip, e período e critério sobrevivem ao "Limpar".
 */
@Composable
private fun EntriesChips(state: EntriesUiState, onApply: (EntriesFilterState) -> Unit, onCriterion: () -> Unit) {
    val f = state.filter
    val incomeList = remember(state.incomeCategories) { state.incomeCategories.values.toList() }
    val active = buildList<Pair<String, EntriesFilterState>> {
        f.categoryId?.let { add((categoryPath(state.categories, it) ?: stringResource(R.string.filter_category)) to f.copy(categoryId = null)) }
        f.incomeCategoryId?.let { id ->
            add((state.incomeCategories[id]?.let { incomeCategoryLabel(it, incomeList) } ?: stringResource(R.string.filter_income_category)) to f.copy(incomeCategoryId = null))
        }
        f.methods.sortedBy { it.ordinal }.forEach { add(it.label() to f.copy(methods = f.methods - it)) }
        f.cardId?.let { id -> add((state.cards.firstOrNull { it.id == id }?.nickname ?: stringResource(R.string.filter_card)) to f.copy(cardId = null)) }
        f.accountId?.let { id -> add((state.accounts.firstOrNull { it.id == id }?.nickname ?: stringResource(R.string.filter_account)) to f.copy(accountId = null)) }
        f.situations.sortedBy { it.ordinal }.forEach { add(it.label() to f.copy(situations = f.situations - it)) }
    }
    val criterionLabel = f.criterion.chipLabel()
    val criterionDescription = stringResource(R.string.entries_chip_criterion_description, criterionLabel)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
            EntryTypeFilter.entries.forEach { type ->
                FilterChip(
                    selected = f.type == type,
                    onClick = { onApply(f.copy(type = type)) },
                    label = { Text(type.chipLabel()) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
            }
            AssistChip(
                onClick = onCriterion,
                label = { Text(criterionLabel) },
                trailingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_chevron_down, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = criterionDescription },
            )
        }
        if (active.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                active.forEach { (text, next) -> RemovableChip(text, onRemove = { onApply(next) }) }
                TextButton(onClick = { onApply(f.cleared(keepSearch = true)) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.entries_chips_clear))
                }
            }
        }
    }
}

/** Troca o critério só desta lista; o padrão continua em Mais e o Início não muda (ADR 0089, item 5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CriterionSheet(selected: DateCriterion, onPick: (DateCriterion) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(containerColor = accordSheetColor(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4).padding(bottom = AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.entries_criterion_sheet_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.entries_criterion_sheet_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DateCriterion.entries.forEach { criterion ->
                val purchase = criterion == DateCriterion.PurchaseDate
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                        .selectable(selected = criterion == selected, onClick = { onPick(criterion) }, role = Role.RadioButton)
                        .padding(vertical = AccordSpacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = criterion == selected, onClick = null)
                    Column(Modifier.padding(start = AccordSpacing.s2)) {
                        Text(criterion.chipLabel(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (purchase) R.string.entries_criterion_purchase_hint else R.string.entries_criterion_budget_hint),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
private fun EmptyState(filtered: Boolean, onClear: () -> Unit, onNew: () -> Unit, showInlineNew: Boolean, onImport: (() -> Unit)?) {
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
        } else {
            if (showInlineNew) Button(onClick = onNew, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.action_register_manually))
            }
            if (onImport != null) OutlinedButton(onClick = onImport, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.more_import))
            }
        }
    }
}

/**
 * Receitas, Despesas e Resultado da lista (ADR 0089, item 2). Receitas e Despesas usam os tokens `income` e `expense` com sinal, ícone de direção e rótulo;
 * o Resultado é neutro, com sinal. A despesa daqui é bruta e segue o critério da lista: não abate estornos, ao contrário da do Início. Sem resultado ainda, "—".
 */
@Composable
private fun SummaryBlock(summary: EntrySummary?, total: Int) {
    val absent = "—"
    val toReceive = summary?.let { it.incomes - it.receivedIncomes }?.takeIf { it.cents > 0 }
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            SummaryLine(
                stringResource(R.string.entries_summary_incomes_label), summary?.incomes?.formatSigned() ?: absent,
                Accord.colors.income, Lucide.drawable.lucide_ic_arrow_down_left,
                note = toReceive?.let { stringResource(R.string.entries_summary_to_receive, it.format()) },
            )
            SummaryLine(
                stringResource(R.string.entries_summary_expenses_label),
                summary?.expenses?.let { if (it.cents > 0) Money(-it.cents).format() else it.format() } ?: absent,
                Accord.colors.expense, Lucide.drawable.lucide_ic_arrow_up_right,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryLine(stringResource(R.string.entries_summary_result_label), summary?.listResult?.formatSigned() ?: absent, MaterialTheme.colorScheme.onSurface, null)
            if (summary != null) {
                Text(pluralStringResource(R.plurals.entries_total, total, total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.entries_summary_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Rótulo e valor lidos juntos pelo TalkBack; o ícone de direção é decorativo, o rótulo diz a mesma coisa. */
@Composable
private fun SummaryLine(label: String, value: String, color: Color, @DrawableRes icon: Int?, note: String? = null) {
    val spoken = listOfNotNull(label, value, note).joinToString(", ")
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        AdaptiveRow(
            start = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    if (icon != null) DecorativeIcon(icon, modifier = Modifier.size(18.dp), tint = color)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            },
            end = { Text(value, style = Accord.typography.amountRow, color = color, textAlign = TextAlign.End) },
        )
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Pago e a pagar só aparecem onde dizem algo: pagamento direto já nasce pago e seria ruído. */
private val STATUS_KINDS = setOf(
    EntryKind.Boleto, EntryKind.Income, EntryKind.RecurrenceOccurrence, EntryKind.InvoicePayment, EntryKind.CardInstallment,
)

/** Situações que pedem atenção ou valem como etiqueta; pago e não pago vão sob o valor, não como etiqueta. */
private val TAG_SITUATIONS = setOf(
    EntrySituation.ToClassify, EntrySituation.InvoiceLinkToConfirm, EntrySituation.Overdue,
    EntrySituation.CancelRequested, EntrySituation.DivergentPayment, EntrySituation.Cancelled,
)

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
    val installmentNumber = row.installmentNumber
    val installmentCount = row.installmentCount
    val installment = if (installmentNumber != null && installmentCount != null && installmentCount > 1) {
        stringResource(R.string.detail_installment_of, installmentNumber, installmentCount)
    } else {
        null
    }
    val received = row.isIncome
    val status = when {
        row.kind !in STATUS_KINDS || EntrySituation.Overdue in row.situations -> null
        EntrySituation.Paid in row.situations -> stringResource(if (received) R.string.entries_status_received else R.string.entries_status_paid)
        EntrySituation.NotPaid in row.situations -> stringResource(if (received) R.string.entries_status_expected else R.string.entries_status_to_pay)
        else -> null
    }
    val tags = row.situations.filter { it in TAG_SITUATIONS }.sortedBy { it.ordinal }
    val invoiceMonth = row.dueDate?.let { YearMonth.from(it).format(ENTRY_INVOICE_MONTH) }
        ?.takeIf { row.kind == EntryKind.CardInstallment }
        ?.let { stringResource(R.string.entries_invoice_month, it) }
    val neutralTags = listOfNotNull(invoiceMonth, row.kind.label().takeIf { row.kind == EntryKind.Refund || row.kind == EntryKind.InvoicePayment })
    val subtitle = listOfNotNull(categoryName, row.method?.label(), installment).joinToString(" · ")
    // Compra parcelada: o valor da linha é o da parcela exibida ("Parcela 3/10"), nunca o total da compra.
    val amount = row.installmentAmount ?: row.amount
    val signedAmount = when {
        row.kind == EntryKind.InvoicePayment -> amount.format()
        received || row.kind == EntryKind.Refund -> "+" + amount.format()
        amount.cents > 0 -> Money(-amount.cents).format()
        else -> amount.format()
    }
    val type = when {
        row.kind == EntryKind.Refund || row.kind == EntryKind.InvoicePayment -> row.kind.label()
        received -> stringResource(R.string.entries_type_income)
        else -> stringResource(R.string.entries_type_expense)
    }
    // Sinal e cor não bastam para o TalkBack: o tipo vem por extenso e o valor sem sinal.
    val description = (listOfNotNull(type, row.description, amount.format(), subtitle.takeIf(String::isNotBlank), status) + neutralTags + tags.map { it.label() })
        .joinToString(". ")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        onClick = { onOpen(row.id) },
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = AccordSpacing.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            ) {
                EntryIdentityIcon(
                    description = row.description,
                    allowVendor = row.kind != EntryKind.Income && row.kind != EntryKind.InvoicePayment && !received,
                    entryId = row.id,
                    size = 40.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.description, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(signedAmount, style = Accord.typography.amountRow, textAlign = TextAlign.End, modifier = Modifier.padding(start = AccordSpacing.s2))
                    }
                    if (subtitle.isNotBlank() || status != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                                if (expenseCategory != null || incomeCategory != null) {
                                    DecorativeIcon(
                                        lucideRes(expenseCategory?.iconKey ?: incomeCategory!!.iconKey),
                                        modifier = Modifier.size(16.dp),
                                        tint = if (expenseCategory != null) categoryColor(categories, expenseCategory.id)
                                        else categoryColor(incomeCategory!!.colorKey(incomeCategoryList)),
                                    )
                                }
                                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            status?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = AccordSpacing.s2))
                            }
                        }
                    }
                    if (tags.isNotEmpty() || neutralTags.isNotEmpty()) SituationTags(tags, neutralTags)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Marcas de situação: ícone e texto juntos, nunca só cor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SituationTags(situations: List<EntrySituation>, neutral: List<String> = emptyList()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        neutral.forEach { text ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                Text(
                    text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AccordSpacing.s2, vertical = AccordSpacing.s1),
                )
            }
        }
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

private val ALL_METHODS = PaymentMethod.entries
private val ATTENTION_SITUATIONS = listOf(
    EntrySituation.ToClassify, EntrySituation.InvoiceLinkToConfirm, EntrySituation.Overdue,
    EntrySituation.CancelRequested, EntrySituation.DivergentPayment,
)
private val PAYMENT_SITUATIONS = listOf(EntrySituation.Paid, EntrySituation.NotPaid, EntrySituation.Cancelled)

/**
 * Painel de filtros: o que se escolhe aqui só vale ao aplicar; ✕ e arrastar para baixo descartam o rascunho. "Ver N lançamentos" mostra a
 * contagem do rascunho e vira "Aplicar" se [countFor] falhar ou demorar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    initial: EntriesFilterState,
    categories: Map<String, CategoryNode>,
    onApply: (EntriesFilterState) -> Unit,
    onCancel: () -> Unit,
    incomeCategories: Map<String, IncomeCategoryNode> = emptyMap(),
    cards: List<CardInfo> = emptyList(),
    accounts: List<AccountInfo> = emptyList(),
    countFor: suspend (EntriesFilterState) -> Int? = { null },
) {
    var draft by remember { mutableStateOf(initial) }
    var range by remember { mutableStateOf(initial.from != initial.to) }
    var pickingCategory by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf<Int?>(null) }
    val currentCount by rememberUpdatedState(countFor)
    LaunchedEffect(draft) {
        count = null
        delay(COUNT_DEBOUNCE_MS)
        count = withTimeoutOrNull(COUNT_TIMEOUT_MS) { currentCount(draft) }
    }
    val incomeList = remember(incomeCategories) { incomeCategories.values.sortedBy { it.order } }

    Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4).padding(bottom = AccordSpacing.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onCancel, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_x), contentDescription = stringResource(R.string.register_picker_close))
            }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Text(stringResource(R.string.filter_period), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                FilterChip(
                    selected = !range,
                    onClick = { range = false; draft = draft.copy(to = draft.from) },
                    label = { Text(stringResource(R.string.filter_period_month)) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
                FilterChip(
                    selected = range,
                    onClick = { range = true },
                    label = { Text(stringResource(R.string.filter_period_range)) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
            }
            if (range) {
                MonthStepper(stringResource(R.string.filter_from), draft.from) { draft = draft.copy(from = it, to = maxOf(draft.to, it)) }
                MonthStepper(stringResource(R.string.filter_to), draft.to) { draft = draft.copy(to = it, from = minOf(draft.from, it)) }
            } else {
                MonthStepper(stringResource(R.string.filter_month), draft.from) { draft = draft.copy(from = it, to = it) }
            }

            Text(stringResource(R.string.filter_category), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                FilterChip(
                    selected = draft.categoryId == null,
                    onClick = { draft = draft.copy(categoryId = null) },
                    label = { Text(stringResource(R.string.filter_category_all)) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
                FilterChip(
                    selected = draft.categoryId != null,
                    onClick = { pickingCategory = true },
                    label = { Text(categoryPath(categories, draft.categoryId) ?: stringResource(R.string.filter_category_pick)) },
                    trailingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_chevron_down, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
            }

            if (incomeList.isNotEmpty()) {
                Text(stringResource(R.string.filter_income_category), style = MaterialTheme.typography.titleMedium)
                ChoiceChips(
                    options = incomeList.map { it.id to incomeCategoryLabel(it, incomeList) },
                    selected = draft.incomeCategoryId, allLabel = stringResource(R.string.filter_income_category_all),
                ) { draft = draft.copy(incomeCategoryId = it) }
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

            // Um cartão só não precisa de filtro: todo cartão é ele.
            if (cards.size >= 2) {
                Text(stringResource(R.string.filter_card), style = MaterialTheme.typography.titleMedium)
                ChoiceChips(cards.map { it.id to it.nickname }, draft.cardId, allLabel = null) { draft = draft.copy(cardId = it) }
            }

            if (accounts.isNotEmpty()) {
                Text(stringResource(R.string.filter_account), style = MaterialTheme.typography.titleMedium)
                ChoiceChips(accounts.map { it.id to it.nickname }, draft.accountId, allLabel = null) { draft = draft.copy(accountId = it) }
            }

            Text(stringResource(R.string.filter_situation), style = MaterialTheme.typography.titleMedium)
            SituationGroup(stringResource(R.string.filter_situation_attention), ATTENTION_SITUATIONS, draft.situations) { draft = draft.copy(situations = it) }
            SituationGroup(stringResource(R.string.filter_situation_payment), PAYMENT_SITUATIONS, draft.situations) { draft = draft.copy(situations = it) }
        }

        // Ações fixas: nunca ficam escondidas sob a rolagem nem sob o teclado.
        Row(
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            modifier = Modifier.fillMaxWidth().padding(top = AccordSpacing.s3),
        ) {
            OutlinedButton(onClick = { draft = draft.cleared(keepSearch = true) }, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.filter_clear))
            }
            Button(onClick = { onApply(draft) }, modifier = Modifier.weight(2f).heightIn(min = AccordSpacing.touchMin)) {
                Text(count?.let { pluralStringResource(R.plurals.filter_see_count, it, it) } ?: stringResource(R.string.filter_apply))
            }
        }
    }
    if (pickingCategory) {
        CategoryDialog(categories, draft.categoryId, onPick = { draft = draft.copy(categoryId = it); pickingCategory = false }, onDismiss = { pickingCategory = false })
    }
}

/** Escolha única entre [options] (id, rótulo); tocar de novo na escolhida a remove. [allLabel] vira a opção "todas", marcada sem escolha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(options: List<Pair<String, String>>, selected: String?, allLabel: String?, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        if (allLabel != null) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin))
        }
        options.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(if (selected == id) null else id) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SituationGroup(title: String, items: List<EntrySituation>, selected: Set<EntrySituation>, onChange: (Set<EntrySituation>) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        items.forEach { situation ->
            val on = situation in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - situation else selected + situation) },
                label = { Text(situation.label()) },
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
        }
    }
}

private const val COUNT_DEBOUNCE_MS = 200L
private const val COUNT_TIMEOUT_MS = 2_000L

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
