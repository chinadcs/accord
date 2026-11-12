package app.accord.ui.activity

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.space.ActivityGroup
import app.accord.core.space.ActivityLine
import app.accord.ui.categoryPath
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

/** Destinos que a atividade abre: Lançamentos pela data da compra (07) e o orçamento mensal (05). */
data class ActivityActions(
    val onBack: () -> Unit,
    /** Abre Lançamentos no [month], critério data da compra, com a categoria opcional e, se for "a classificar", essa situação. */
    val onOpenEntries: (month: YearMonth, categoryId: String?, toClassify: Boolean) -> Unit,
    val onOpenBudget: () -> Unit,
    val onOpenComparison: () -> Unit = {},
)

@Composable
fun PurchaseActivityRoute(viewModel: PurchaseActivityViewModel, padding: PaddingValues, actions: ActivityActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    PurchaseActivityScreen(state, padding, actions, viewModel::previousMonth, viewModel::nextMonth, viewModel::setCategory, viewModel::retry)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurchaseActivityScreen(
    state: ActivityUiState,
    padding: PaddingValues,
    actions: ActivityActions,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCategory: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item { DetailHeader(stringResource(R.string.activity_title), actions.onBack) }
        item { MonthSelector(state.month, onPrevious, onNext) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(stringResource(R.string.activity_period, state.month.label()), style = MaterialTheme.typography.bodyMedium)
                // O critério fica sempre visível para a tela nunca ser lida como o orçamento do mês.
                Text(stringResource(R.string.activity_criterion), style = MaterialTheme.typography.titleSmall)
            }
        }
        when (val content = state.content) {
            ActivityContent.Loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            is ActivityContent.Failed -> {
                item { FailureBanner(onRetry) }
                content.last?.let { activityItems(it, state, actions, onCategory) }
            }
            is ActivityContent.Ready -> activityItems(content.data, state, actions, onCategory)
        }
        item {
            Button(
                onClick = { actions.onOpenEntries(state.month, state.categoryId, false) },
                modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
            ) { Text(stringResource(R.string.activity_check)) }
        }
        item {
            OutlinedButton(onClick = actions.onOpenBudget, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.activity_see_budget))
            }
        }
        item {
            OutlinedButton(onClick = actions.onOpenComparison, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.activity_compare))
            }
        }
    }
}

@Composable
private fun FailureBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.activity_failed), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.activityItems(
    data: ActivityData,
    state: ActivityUiState,
    actions: ActivityActions,
    onCategory: (String?) -> Unit,
) {
    val a = data.activity
    item {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(stringResource(R.string.activity_total_title), style = MaterialTheme.typography.titleMedium) },
                end = { Text(a.total.total.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
            )
            Text(pluralStringResource(R.plurals.activity_purchases, a.total.count, a.total.count), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.activity_total_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            FilterChip(
                selected = state.categoryId == null, onClick = { onCategory(null) },
                label = { Text(stringResource(R.string.activity_filter_all)) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
            )
            data.categories.values.filter { it.parentId == null }.sortedBy { it.order }.forEach { category ->
                FilterChip(
                    selected = state.categoryId == category.id, onClick = { onCategory(category.id) },
                    label = { Text(category.name) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                )
            }
        }
    }
    if (a.total.count == 0 && a.toClassify == null) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.activity_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.activity_empty_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        item { Text(stringResource(R.string.activity_categories), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        val max = maxOf(a.groups.maxOfOrNull { it.line.total.cents } ?: 0L, a.toClassify?.total?.cents ?: 0L)
        a.groups.forEach { group ->
            item(key = "g-${group.categoryId}") { GroupRow(group, max, data, state, actions) }
        }
        a.toClassify?.let { line ->
            item(key = "classify") {
                LineRow(
                    stringResource(R.string.activity_to_classify), line, max,
                    onClick = { actions.onOpenEntries(state.month, null, true) },
                )
            }
        }
        if (a.weeks.isNotEmpty()) {
            item { Text(stringResource(R.string.activity_weeks), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
            val weekMax = a.weeks.maxOf { it.line.total.cents }
            a.weeks.forEach { week ->
                item(key = "w-${week.weekStart}") {
                    LineRow(stringResource(R.string.activity_week_line, week.weekStart.shortLabel()), week.line, weekMax, onClick = null)
                }
            }
        }
    }
    a.credits?.let { credits ->
        item {
            Text(
                stringResource(R.string.activity_credits, credits.total.format(), pluralStringResource(R.plurals.activity_credit_count, credits.count, credits.count)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    a.gaps?.let { gaps ->
        item { Text(pluralStringResource(R.plurals.activity_gaps, gaps.count, gaps.count, gaps.total.format()), style = MaterialTheme.typography.bodyMedium) }
    }
    if (a.withCancellation > 0) {
        item { Text(pluralStringResource(R.plurals.activity_cancellations, a.withCancellation, a.withCancellation), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun GroupRow(group: ActivityGroup, max: Long, data: ActivityData, state: ActivityUiState, actions: ActivityActions) {
    val name = categoryPath(data.categories, group.categoryId) ?: group.categoryId
    LineRow(name, group.line, max, onClick = { actions.onOpenEntries(state.month, group.categoryId, false) }) {
        // Só mostra subcategorias quando há mais de um grupo: com uma só, a linha da categoria já diz tudo.
        if (group.subgroups.size > 1 || group.subgroups.any { it.subcategoryId == null }) {
            group.subgroups.forEach { sub ->
                val label = sub.subcategoryId?.let { categoryPath(data.categories, it) } ?: stringResource(R.string.activity_no_subcategory)
                Text(
                    "$label · ${sub.line.total.format()}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Linha com valor exato em texto; a barra é só reforço visual e não carrega significado sozinha. */
@Composable
private fun LineRow(
    title: String,
    line: ActivityLine,
    max: Long,
    onClick: (() -> Unit)?,
    extra: @Composable () -> Unit = {},
) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
        .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
    AccordCard(modifier = modifier) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(title, style = MaterialTheme.typography.titleSmall) },
                end = { Text(line.total.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Text(pluralStringResource(R.plurals.activity_purchases, line.count, line.count), style = MaterialTheme.typography.bodySmall)
            val fraction = if (max > 0) (line.total.cents.toFloat() / max).coerceIn(0.02f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                    .clearAndSetSemantics {},
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
            }
            extra()
        }
    }
}
