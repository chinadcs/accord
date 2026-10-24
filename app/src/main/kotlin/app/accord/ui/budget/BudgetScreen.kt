package app.accord.ui.budget

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.BudgetBar
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.ScreenTitle
import app.accord.ui.components.lucideRes
import app.accord.core.label
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.YearMonth

@Composable
fun BudgetRoute(
    viewModel: BudgetViewModel,
    padding: PaddingValues,
    onOpenCategory: (categoryId: String, month: YearMonth) -> Unit,
    onClassify: (YearMonth) -> Unit,
    onRegister: () -> Unit,
    onOpenCommitments: (YearMonth) -> Unit = {},
    onOpenIncomes: (YearMonth) -> Unit = {},
    onOpenCard: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    BudgetScreen(
        state, padding,
        onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth, onRetry = viewModel::retry,
        onOpenCategory = onOpenCategory, onClassify = onClassify, onRegister = onRegister,
        onOpenCommitments = onOpenCommitments, onOpenIncomes = onOpenIncomes, onOpenCard = onOpenCard,
    )
}

@Composable
fun BudgetScreen(
    state: BudgetUiState,
    padding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenCategory: (String, YearMonth) -> Unit,
    onClassify: (YearMonth) -> Unit,
    onRegister: () -> Unit,
    onOpenCommitments: (YearMonth) -> Unit = {},
    onOpenIncomes: (YearMonth) -> Unit = {},
    onOpenCard: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AccordSpacing.s4, end = AccordSpacing.s4,
            top = padding.calculateTopPadding() + AccordSpacing.s4,
            bottom = padding.calculateBottomPadding() + AccordSpacing.s4,
        ),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                ScreenTitle(stringResource(R.string.tab_budget))
                MonthSelector(state.month, onPrevious, onNext)
                Text(
                    stringResource(R.string.budget_criteria),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Antes da sincronização (S2), nada é apresentado como sincronizado.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_smartphone, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.saved_on_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when (val content = state.content) {
            BudgetContent.Loading -> item {
                Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            }
            is BudgetContent.Failed -> {
                item { FailureBanner(content.last?.month, onRetry) }
                content.last?.let { budgetContent(it, onOpenCategory, onClassify, onRegister, onOpenCommitments, onOpenIncomes, onOpenCard) }
            }
            is BudgetContent.Ready -> budgetContent(content.data, onOpenCategory, onClassify, onRegister, onOpenCommitments, onOpenIncomes, onOpenCard)
        }
    }
}

@Composable
private fun FailureBanner(lastMonth: YearMonth?, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.state_error), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            lastMonth?.let {
                Text(
                    stringResource(R.string.failed_last_view, it.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

private fun LazyListScope.budgetContent(
    data: BudgetData,
    onOpenCategory: (String, YearMonth) -> Unit,
    onClassify: (YearMonth) -> Unit,
    onRegister: () -> Unit,
    onOpenCommitments: (YearMonth) -> Unit,
    onOpenIncomes: (YearMonth) -> Unit,
    onOpenCard: () -> Unit,
) {
    if (data.isEmpty) {
        item { EmptyMonth(onRegister) }
        return
    }
    item { SummaryCard(data) }
    item { IncomeBlock(data) }
    if (data.budget.toClassify.count > 0) item { ToClassifyRow(data) { onClassify(data.month) } }
    item {
        Text(
            stringResource(R.string.categories_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = AccordSpacing.s2),
        )
    }
    items(data.budget.categories, key = { it.categoryId }) { category ->
        val node = data.categories[category.categoryId]
        CategoryRow(
            category = category,
            name = node?.name.orEmpty(),
            iconKey = node?.iconKey ?: "tag",
            onClick = { onOpenCategory(category.categoryId, data.month) },
        )
    }
    item { MonthLinks({ onOpenCommitments(data.month) }, { onOpenIncomes(data.month) }, onOpenCard) }
}

@Composable
private fun EmptyMonth(onRegister: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = AccordSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_month_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.empty_month_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRegister, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.action_register_manually))
        }
    }
}

@Composable
private fun LabelledAmount(label: String, amount: String, note: String? = null) {
    AdaptiveRow(
        start = {
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        end = { Text(amount, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
    )
}

@Composable
private fun SummaryCard(data: BudgetData) {
    val budget = data.budget
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            LabelledAmount(stringResource(R.string.summary_committed), budget.committed.format())
            LabelledAmount(
                stringResource(R.string.summary_limits),
                if (data.hasLimits) budget.totalLimits.format() else stringResource(R.string.summary_no_limits),
            )
            // "Pago" é parte do comprometido, nunca uma parcela a somar de novo.
            LabelledAmount(stringResource(R.string.summary_paid), budget.paid.format(), stringResource(R.string.summary_paid_note))
            if (budget.credits.cents != 0L) {
                LabelledAmount(stringResource(R.string.summary_credits), budget.credits.format())
            }
            if (budget.invoiceLinksToConfirm > 0) LinksToConfirm(budget.invoiceLinksToConfirm)
        }
    }
}

@Composable
private fun LinksToConfirm(count: Int) {
    Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.fillMaxWidth().padding(AccordSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.onAttentionContainer)
            Column {
                Text(
                    pluralStringResource(R.plurals.links_to_confirm, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accord.colors.onAttentionContainer,
                )
                Text(
                    stringResource(R.string.links_to_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accord.colors.onAttentionContainer,
                )
            }
        }
    }
}

@Composable
private fun IncomeBlock(data: BudgetData) {
    val income = data.budget.income
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            // Renda recebida e a receber ficam em linhas distintas; prevista não é saldo disponível.
            LabelledAmount(stringResource(R.string.income_received), income.received.format())
            LabelledAmount(stringResource(R.string.income_to_receive), income.toReceive.format())
        }
    }
}

@Composable
private fun ToClassifyRow(data: BudgetData, onClick: () -> Unit) {
    val line = data.budget.toClassify
    val detail = pluralStringResource(R.plurals.to_classify_detail, line.count, line.amount.format(), line.count)
    Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_tag, tint = Accord.colors.onAttentionContainer)
                Column {
                    Text(stringResource(R.string.to_classify_title), style = MaterialTheme.typography.titleMedium, color = Accord.colors.onAttentionContainer)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = Accord.colors.onAttentionContainer)
                }
            }
            OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.to_classify_action))
            }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryBudget, name: String, iconKey: String, onClick: () -> Unit) {
    val limit = category.limit
    val state = barState(limit)
    val money = { m: Money -> m.format() }
    val status = when (state) {
        BarState.NoLimit -> stringResource(R.string.category_limit_undefined)
        BarState.Excess -> stringResource(R.string.category_excess, money(limit.excess ?: Money.ZERO))
        else -> stringResource(R.string.category_remaining, money(limit.remaining ?: Money.ZERO))
    }
    val amounts = limit.total?.let { stringResource(R.string.category_of_limit, money(category.net), money(it)) } ?: money(category.net)
    val barLabel = when (state) {
        BarState.WithinLimit -> stringResource(R.string.bar_within)
        BarState.Attention -> stringResource(R.string.bar_attention)
        BarState.Excess -> stringResource(R.string.bar_excess)
        BarState.NoLimit -> stringResource(R.string.bar_none)
    }
    val hasCredits = category.credits.cents != 0L
    val breakdown = if (hasCredits) {
        stringResource(R.string.category_credits_breakdown, money(category.gross), money(category.credits), money(category.net))
    } else {
        null
    }
    val netCredit = if (category.net.isNegative) stringResource(R.string.category_net_credit, money(-category.net)) else null
    val percent = percentLabel(limit)
    val description = listOfNotNull(name, amounts, status, barLabel, percent?.let { "$it%" }, breakdown, netCredit).joinToString(". ")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.category_open, name), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                DecorativeIcon(lucideRes(iconKey))
                // O nome ocupa a largura toda; os valores descem para a linha de baixo e nunca o espremem.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(amounts, style = Accord.typography.amountRow)
                }
            }
            // Sem limite definido não há barra: o texto "Limite não definido" é o estado.
            if (state != BarState.NoLimit) BudgetBar(barFill(limit), state, excessMarker(limit, category.net))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                when (state) {
                    BarState.Attention -> DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.attention)
                    BarState.Excess -> DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state) {
                        BarState.Excess -> MaterialTheme.colorScheme.error
                        BarState.Attention -> Accord.colors.attention
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                percent?.let { Text("$it%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            breakdown?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            netCredit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Accord.colors.success) }
        }
    }
}

/** Caminhos do mês para o que já ocupa os próximos meses, as rendas e o cartão; cada um volta ao Orçamento. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MonthLinks(onCommitments: () -> Unit, onIncomes: () -> Unit, onCard: () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        OutlinedButton(onClick = onCommitments, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.budget_link_commitments)) }
        OutlinedButton(onClick = onIncomes, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.budget_link_incomes)) }
        OutlinedButton(onClick = onCard, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.budget_link_card)) }
    }
}
