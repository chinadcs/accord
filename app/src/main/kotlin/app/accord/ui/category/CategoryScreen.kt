package app.accord.ui.category

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
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.components.DetailHeader
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.ui.budget.barFill
import app.accord.ui.budget.barState
import app.accord.ui.budget.excessMarker
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.BudgetBar
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.lucideRes
import app.accord.core.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing

@Composable
fun CategoryRoute(
    viewModel: CategoryViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onCheckEntries: (categoryId: String, subcategoryId: String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CategoryScreen(state, viewModel.month, padding, onBack, { viewModel.load() }, onCheckEntries)
}

@Composable
fun CategoryScreen(
    state: CategoryContent,
    month: java.time.YearMonth,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCheckEntries: (String, String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AccordSpacing.s4, end = AccordSpacing.s4,
            top = padding.calculateTopPadding() + AccordSpacing.s2,
            bottom = padding.calculateBottomPadding() + AccordSpacing.s4,
        ),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            DetailHeader(
                title = (state as? CategoryContent.Ready)?.value?.node?.name.orEmpty(),
                onBack = onBack,
                subtitle = month.label(),
            )
        }
        when (state) {
            CategoryContent.Loading -> item { Text(stringResource(R.string.state_loading)) }
            CategoryContent.Failed -> item {
                // Falha não vira zero fictício: diz o que houve e oferece tentar de novo.
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Text(stringResource(R.string.state_error))
                    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            is CategoryContent.Ready -> categoryContent(state.value, onCheckEntries)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryContent(
    value: CategoryData,
    onCheckEntries: (String, String?) -> Unit,
) {
    item { CategorySummary(value.category, value.node.iconKey) }
    if (value.subcategories.isEmpty()) {
        item { Text(stringResource(R.string.empty_category), style = MaterialTheme.typography.bodyLarge) }
    }
    items(value.subcategories, key = { it ?: "none" }) { id ->
        SubcategoryRow(value, id) { onCheckEntries(value.category.categoryId, id) }
    }
    val limit = value.category.limit
    if (limit.subs.values.any { it.part != null }) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Text(
                    stringResource(R.string.split_summary, limit.distributed.format(), (limit.undistributed ?: Money.ZERO).format()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                limit.remaining?.let {
                    Text(stringResource(R.string.remaining_after_spending, it.format()), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    item {
        Button(
            onClick = { onCheckEntries(value.category.categoryId, null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
        ) { Text(stringResource(R.string.action_check_entries)) }
    }
}

@Composable
private fun CategorySummary(category: CategoryBudget, iconKey: String) {
    val limit = category.limit
    val state = barState(limit)
    val headline = limit.total?.let {
        stringResource(R.string.category_committed_of_limit, category.net.format(), it.format())
    } ?: stringResource(R.string.category_committed_no_limit, category.net.format())
    val paid = category.paid.format()
    val detail = when {
        limit.excess != null -> stringResource(R.string.category_excess_paid, limit.excess!!.format(), paid)
        limit.remaining != null -> stringResource(R.string.category_remaining_paid, limit.remaining!!.format(), paid)
        else -> stringResource(R.string.category_paid_only, paid)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(AccordSpacing.s2), contentAlignment = Alignment.Center) {
                        DecorativeIcon(lucideRes(iconKey))
                    }
                }
                Text(headline, style = Accord.typography.amountRow)
            }
            BudgetBar(barFill(limit), state, excessMarker(limit, category.net))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (category.credits.cents != 0L) {
                Text(
                    stringResource(
                        R.string.category_credits_breakdown, category.gross.format(), category.credits.format(), category.net.format(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubcategoryRow(value: CategoryData, id: String?, onClick: () -> Unit) {
    val node = id?.let { value.data.categories[it] }
    val name = node?.name ?: stringResource(R.string.sub_none)
    val spent = value.category.bySubcategory[id] ?: Money.ZERO
    val view = id?.let { value.category.limit.sub(it) }
    val part = view?.part
    val lines = buildList {
        add(stringResource(R.string.sub_spent, spent.format()))
        if (id == null) return@buildList
        if (part != null) {
            add(stringResource(R.string.sub_part, part.format()))
            val remaining = view.remainingOfPart ?: Money.ZERO
            add(
                if (remaining.isNegative) stringResource(R.string.sub_over, (-remaining).format())
                else stringResource(R.string.sub_remaining, remaining.format()),
            )
        } else if (value.category.limit.subs.values.any { it.part != null }) {
            // Só mostra "sem divisão definida" quando a categoria tem divisão; sem ela não há o que comparar.
            add(stringResource(R.string.sub_no_split))
        }
    }
    val description = (listOf(name) + lines).joinToString(". ")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).padding(AccordSpacing.s4),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DecorativeIcon(lucideRes(node?.iconKey ?: "tag"))
            AdaptiveRow(
                start = {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        lines.drop(1).forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                end = { Text(spent.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
        }
    }
}
