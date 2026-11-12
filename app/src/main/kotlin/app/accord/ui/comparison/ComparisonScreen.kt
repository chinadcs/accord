package app.accord.ui.comparison

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.analysis.ComparisonGroupKey
import app.accord.core.analysis.ComparisonRow
import app.accord.core.analysis.Measure
import app.accord.core.analysis.PercentChange
import app.accord.core.analysis.PeriodChange
import app.accord.core.analysis.formatSigned
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonSide
import app.accord.core.space.DateCriterion
import app.accord.core.space.PeriodComparison
import app.accord.ui.categoryPath
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.MoneyChart
import app.accord.ui.components.MoneyChartValue
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

/** Destinos da comparação: Lançamentos no mês e critério do lado escolhido (07) e a lista de pendências (18/Revisar). */
data class ComparisonActions(
    val onBack: () -> Unit,
    /** Abre Lançamentos em [month], no [criterion] da comparação, com a categoria ou subcategoria do grupo, ou só "a classificar". */
    val onOpenEntries: (month: YearMonth, categoryId: String?, criterion: DateCriterion, toClassify: Boolean) -> Unit,
    val onOpenPendings: () -> Unit,
)

@Composable
fun ComparisonRoute(viewModel: ComparisonViewModel, padding: PaddingValues, actions: ComparisonActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    ComparisonScreen(
        state, padding, actions,
        ComparisonControls(
            viewModel::setCriterion, viewModel::setGrouping, viewModel::previousA, viewModel::nextA, viewModel::previousB, viewModel::nextB, viewModel::retry,
        ),
    )
}

data class ComparisonControls(
    val onCriterion: (DateCriterion) -> Unit,
    val onGrouping: (ComparisonGrouping) -> Unit,
    val onPreviousA: () -> Unit,
    val onNextA: () -> Unit,
    val onPreviousB: () -> Unit,
    val onNextB: () -> Unit,
    val onRetry: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComparisonScreen(state: ComparisonUiState, padding: PaddingValues, actions: ComparisonActions, controls: ComparisonControls) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item { DetailHeader(stringResource(R.string.comparison_title), actions.onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.comparison_criterion_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    DateCriterion.entries.forEach { criterion ->
                        FilterChip(
                            selected = state.criterion == criterion, onClick = { controls.onCriterion(criterion) },
                            label = { Text(stringResource(if (criterion == DateCriterion.BudgetMonth) R.string.comparison_criterion_budget else R.string.comparison_criterion_purchase)) },
                            modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                        )
                    }
                }
                // O critério fica sempre escrito: nunca se compara compra original de um lado e parcela exigível do outro.
                Text(
                    stringResource(if (state.criterion == DateCriterion.BudgetMonth) R.string.comparison_criterion_budget_note else R.string.comparison_criterion_purchase_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { PeriodPicker(R.string.comparison_period_a, state.monthA, controls.onPreviousA, controls.onNextA) }
        item { PeriodPicker(R.string.comparison_period_b, state.monthB, controls.onPreviousB, controls.onNextB) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.comparison_grouping_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    ComparisonGrouping.entries.forEach { grouping ->
                        FilterChip(
                            selected = state.grouping == grouping, onClick = { controls.onGrouping(grouping) },
                            label = { Text(stringResource(if (grouping == ComparisonGrouping.Category) R.string.comparison_grouping_category else R.string.comparison_grouping_subcategory)) },
                            modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                        )
                    }
                }
            }
        }
        when (val content = state.content) {
            ComparisonContent.Loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            is ComparisonContent.Failed -> {
                item { FailureBanner(controls.onRetry) }
                content.last?.let { comparisonItems(it, state, actions) }
            }
            is ComparisonContent.Ready -> comparisonItems(content.data, state, actions)
        }
    }
}

@Composable
private fun PeriodPicker(label: Int, month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        MonthSelector(month, onPrevious, onNext)
    }
}

@Composable
private fun FailureBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.comparison_failed), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private fun periodName(side: ComparisonSide) = YearMonth.from(side.period.from).label()

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.comparisonItems(data: ComparisonData, state: ComparisonUiState, actions: ComparisonActions) {
    val c = data.comparison
    item { LimitationsBlock(c, actions) }
    val nothing = c.rows.isEmpty() && c.totalA !is Measure.Value && c.totalB !is Measure.Value && !c.a.failed && !c.b.failed
    if (nothing) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.comparison_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.comparison_empty_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    item {
        MoneyChart(
            stringResource(R.string.chart_comparison_title),
            stringResource(if (c.criterion == DateCriterion.BudgetMonth) R.string.comparison_criterion_budget_note else R.string.comparison_criterion_purchase_note),
            listOf(
                MoneyChartValue(stringResource(R.string.chart_period_a, periodName(c.a)), (c.totalA as? Measure.Value)?.money, cellText(c.totalA),
                    onClick = { actions.onOpenEntries(YearMonth.from(c.a.period.from), null, c.criterion, false) }),
                MoneyChartValue(stringResource(R.string.chart_period_b, periodName(c.b)), (c.totalB as? Measure.Value)?.money, cellText(c.totalB),
                    onClick = { actions.onOpenEntries(YearMonth.from(c.b.period.from), null, c.criterion, false) }),
            ),
        )
    }
    item {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(
                    stringResource(R.string.comparison_row_values, stringResource(R.string.comparison_total), cellText(c.totalA), cellText(c.totalB)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.comparison_change, changeText(c.totalChange)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    item { Text(stringResource(R.string.comparison_list_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    val max = c.rows.flatMap { listOf(it.a, it.b) }.maxOfOrNull { (it as? Measure.Value)?.money?.cents?.let { v -> Math.abs(v) } ?: 0L } ?: 0L
    c.rows.forEach { row -> item(key = "${row.key.categoryId}/${row.key.subcategoryId}") { RowCard(row, c, data.categories, max, state, actions) } }
}

@Composable
private fun LimitationsBlock(c: PeriodComparison, actions: ComparisonActions) {
    val a = stringResource(R.string.comparison_period_a)
    val b = stringResource(R.string.comparison_period_b)
    AccordCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.comparison_before_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.comparison_duration, a, c.a.period.days, b, c.b.period.days), style = MaterialTheme.typography.bodyMedium)
            if (c.differentDuration) Text(stringResource(R.string.comparison_different_duration), style = MaterialTheme.typography.bodyMedium)
            listOf(a to c.a, b to c.b).forEach { (name, side) ->
                if (side.failed) Text(stringResource(R.string.comparison_side_failed, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                if (side.inProgressOrFuture) Text(stringResource(R.string.comparison_in_progress, periodName(side)), style = MaterialTheme.typography.bodyMedium)
                if (side.provisionalLinks > 0) {
                    Text(pluralStringResource(R.plurals.comparison_provisional, side.provisionalLinks, side.provisionalLinks, periodName(side)), style = MaterialTheme.typography.bodyMedium)
                }
                side.toClassify?.let { Text(stringResource(R.string.comparison_to_classify, periodName(side), it.format()), style = MaterialTheme.typography.bodyMedium) }
            }
            Text(stringResource(R.string.comparison_coverage_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.a.toClassify != null || c.b.toClassify != null) {
                OutlinedButton(onClick = actions.onOpenPendings, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.comparison_open_pendings))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowCard(
    row: ComparisonRow,
    c: PeriodComparison,
    categories: Map<String, CategoryNode>,
    max: Long,
    state: ComparisonUiState,
    actions: ComparisonActions,
) {
    val name = groupName(row.key, categories, c.grouping)
    val toClassify = row.key.categoryId == null
    val categoryId = row.key.subcategoryId ?: row.key.categoryId
    AccordCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.comparison_row_values, name, cellText(row.a), cellText(row.b)), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.comparison_change, changeText(row.change)), style = MaterialTheme.typography.bodyMedium)
            Bar(row.a, max)
            Bar(row.b, max)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                OutlinedButton(
                    onClick = { actions.onOpenEntries(state.monthA, categoryId.takeUnless { toClassify }, c.criterion, toClassify) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.comparison_open_period, periodName(c.a))) }
                OutlinedButton(
                    onClick = { actions.onOpenEntries(state.monthB, categoryId.takeUnless { toClassify }, c.criterion, toClassify) },
                    modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(R.string.comparison_open_period, periodName(c.b))) }
            }
        }
    }
}

@Composable
private fun groupName(key: ComparisonGroupKey, categories: Map<String, CategoryNode>, grouping: ComparisonGrouping): String = when {
    key.categoryId == null -> stringResource(R.string.comparison_group_to_classify)
    key.subcategoryId != null -> categoryPath(categories, key.subcategoryId) ?: key.subcategoryId!!
    grouping == ComparisonGrouping.Subcategory ->
        stringResource(R.string.comparison_group_no_subcategory, categoryPath(categories, key.categoryId) ?: key.categoryId!!)
    else -> categoryPath(categories, key.categoryId) ?: key.categoryId!!
}

@Composable
private fun cellText(m: Measure): String = when (m) {
    is Measure.Value -> m.money.format()
    Measure.NoData -> stringResource(R.string.comparison_cell_no_data)
    Measure.Failed -> stringResource(R.string.comparison_cell_failed)
}

/** Diferença e percentual no texto: "+R$ 180,00 (+22,5%)", ou o motivo de não haver percentual. Ausência e falha nunca viram zero. */
@Composable
private fun changeText(change: PeriodChange): String = when (change) {
    is PeriodChange.Computed -> {
        val diff = change.difference.formatSigned()
        when (val p = change.percent) {
            is PercentChange.Value -> stringResource(R.string.comparison_change_percent, diff, p.format())
            is PercentChange.NoBase -> when (p.reason) {
                PercentChange.Reason.ZeroBase -> stringResource(R.string.comparison_change_no_base, diff)
                PercentChange.Reason.NegativeBase -> stringResource(R.string.comparison_change_negative_base, diff)
                PercentChange.Reason.OutOfRange -> stringResource(R.string.comparison_change_out_of_range, diff)
            }
        }
    }
    is PeriodChange.Unavailable -> stringResource(
        R.string.comparison_change_unavailable,
        stringResource(
            when {
                change.a == Measure.Failed && change.b == Measure.Failed -> R.string.comparison_unavailable_both
                change.a == Measure.Failed -> R.string.comparison_unavailable_a_failed
                change.b == Measure.Failed -> R.string.comparison_unavailable_b_failed
                change.a == Measure.NoData && change.b == Measure.NoData -> R.string.comparison_unavailable_both
                change.a == Measure.NoData -> R.string.comparison_unavailable_a_no_data
                else -> R.string.comparison_unavailable_b_no_data
            },
        ),
    )
}

/** Barra de reforço visual; o valor exato está sempre no texto da linha. Valor ausente, falho ou nulo não ganha barra de zero. */
@Composable
private fun Bar(m: Measure, max: Long) {
    val cents = (m as? Measure.Value)?.money?.cents?.let { Math.abs(it) }
    Box(
        Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall).clearAndSetSemantics {},
    ) {
        if (cents != null && cents > 0 && max > 0) {
            Box(
                Modifier.fillMaxWidth((cents.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
            )
        }
    }
}
