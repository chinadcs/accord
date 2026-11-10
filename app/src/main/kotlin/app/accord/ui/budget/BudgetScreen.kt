package app.accord.ui.budget

import androidx.compose.ui.graphics.Color
import app.accord.ui.categoryColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.core.space.GeneralBudgetView
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.BudgetBar
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.HomeSectionCard
import app.accord.ui.components.SectionStatus
import app.accord.ui.home.CashFlowBody
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.MoneyChart
import app.accord.ui.components.MoneyChartValue
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
    onSearch: (() -> Unit)? = null,
    onOpenForecasts: (YearMonth) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    BudgetScreen(
        state, padding,
        onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth, onRetry = viewModel::retry,
        onRetryFlow = viewModel::retryFlow,
        onOpenCategory = onOpenCategory, onClassify = onClassify, onRegister = onRegister,
        onOpenCommitments = onOpenCommitments, onOpenIncomes = onOpenIncomes, onOpenCard = onOpenCard, onSearch = onSearch,
        hasFloatingAction = true, onOpenForecasts = onOpenForecasts,
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
    onSearch: (() -> Unit)? = null,
    hasFloatingAction: Boolean = false,
    onOpenForecasts: (YearMonth) -> Unit = {},
    onRetryFlow: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AccordSpacing.s4, end = AccordSpacing.s4,
            top = padding.calculateTopPadding() + AccordSpacing.s4,
            bottom = padding.calculateBottomPadding() + AccordSpacing.s4 + if (hasFloatingAction) 80.dp else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle(stringResource(R.string.tab_budget), Modifier.weight(1f))
                    onSearch?.let { search ->
                        IconButton(onClick = search, modifier = Modifier.size(AccordSpacing.touchMin)) {
                            Icon(painterResource(Lucide.drawable.lucide_ic_search), contentDescription = stringResource(R.string.search_entries))
                        }
                    }
                }
                MonthSelector(state.month, onPrevious, onNext)
            }
        }
        when (val content = state.content) {
            BudgetContent.Loading -> item {
                Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            }
            is BudgetContent.Failed -> {
                item { FailureBanner(content.last?.month, onRetry) }
                content.last?.let { budgetContent(it, onOpenCategory, onClassify, onRegister, onOpenCommitments, onOpenIncomes, onOpenCard, !hasFloatingAction, onOpenForecasts, state.flow.takeIf { _ -> it.month == state.month }, onRetryFlow) }
            }
            is BudgetContent.Ready -> budgetContent(content.data, onOpenCategory, onClassify, onRegister, onOpenCommitments, onOpenIncomes, onOpenCard, !hasFloatingAction, onOpenForecasts, state.flow.takeIf { content.data.month == state.month }, onRetryFlow)
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
    showInlineManual: Boolean,
    onOpenForecasts: (YearMonth) -> Unit,
    flow: BudgetFlowState?,
    onRetryFlow: () -> Unit,
) {
    if (data.isEmpty) {
        item { EmptyMonth(onRegister, showInlineManual) }
        item { BudgetCriteria() }
        return
    }
    item { SummaryCard(data) }
    item { BudgetCriteria() }
    item { IncomeBlock(data) }
    if (data.budget.toClassify.count > 0) item { ToClassifyRow(data) { onClassify(data.month) } }
    // O fluxo é do mês escolhido e só aparece junto de uma visão desse mesmo mês (nunca com a do mês anterior).
    if (flow != null) item(key = "cash-flow") { BudgetFlowSection(flow, onRetryFlow) }
    item {
        val values = data.budget.categories.map { category ->
            MoneyChartValue(
                data.categories[category.categoryId]?.name.orEmpty(), category.net,
                onClick = { onOpenCategory(category.categoryId, data.month) },
            )
        } + if (data.budget.toClassify.count > 0) listOf(
            MoneyChartValue(stringResource(R.string.chart_to_classify), data.budget.toClassify.amount, onClick = { onClassify(data.month) }),
        ) else emptyList()
        MoneyChart(stringResource(R.string.chart_budget_title), stringResource(R.string.chart_budget_criterion, data.month.label()), values)
    }
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
            tint = categoryColor(data.categories, category.categoryId),
            onClick = { onOpenCategory(category.categoryId, data.month) },
        )
    }
    item { MonthLinks({ onOpenCommitments(data.month) }, { onOpenIncomes(data.month) }, onOpenCard, { onOpenForecasts(data.month) }) }
}

/**
 * Fluxo do mês escolhido: renda contra o comprometido e o ritmo estimado dos gastos variáveis (leitura; nada é gravado).
 * Carga, falha e pronto têm cada um o seu corpo e a mesma altura mínima; só a falha oferece "Tentar de novo", que
 * relê apenas o fluxo.
 */
@Composable
private fun BudgetFlowSection(flow: BudgetFlowState, onRetry: () -> Unit) {
    val retryDescription = stringResource(R.string.budget_flow_retry_description)
    HomeSectionCard(
        title = stringResource(R.string.budget_flow_title),
        status = when (flow) {
            BudgetFlowState.Loading -> SectionStatus.Loading
            BudgetFlowState.Failed -> SectionStatus.Failed
            is BudgetFlowState.Ready -> SectionStatus.Ready
        },
        loadingDescription = stringResource(R.string.cashflow_loading),
        failedMessage = stringResource(R.string.cashflow_failed),
        skeletonLines = 4,
        failedAction = {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = retryDescription },
            ) { Text(stringResource(R.string.action_retry)) }
        },
    ) {
        if (flow is BudgetFlowState.Ready) CashFlowBody(flow.value, showTitle = false, detailed = true)
    }
}

@Composable
private fun BudgetCriteria() {
    Text(
        stringResource(R.string.budget_criteria),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AccordSpacing.s1),
    )
}

@Composable
private fun EmptyMonth(onRegister: () -> Unit, showInlineManual: Boolean) {
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
        if (showInlineManual) Button(onClick = onRegister, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
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
    val stackedMetrics = LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Surface(color = Accord.colors.financeSurface, contentColor = Accord.colors.onFinanceSurface, shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth().padding(AccordSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    Text(stringResource(R.string.summary_committed), style = MaterialTheme.typography.bodyMedium)
                    Text(budget.committed.format(), style = Accord.typography.amountHero, textAlign = TextAlign.Center)
                }
                HorizontalDivider(color = Accord.colors.onFinanceSurface.copy(alpha = 0.2f))
                if (stackedMetrics) {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                        SummaryMetric(stringResource(R.string.summary_limits), if (data.hasLimits) budget.totalLimits.format() else stringResource(R.string.summary_no_limits))
                        SummaryMetric(stringResource(R.string.summary_paid), budget.paid.format(), stringResource(R.string.summary_paid_note))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
                        SummaryMetric(stringResource(R.string.summary_limits), if (data.hasLimits) budget.totalLimits.format() else stringResource(R.string.summary_no_limits), modifier = Modifier.weight(1f))
                        // Pago integra o comprometido; a legenda impede somar os dois novamente.
                        SummaryMetric(stringResource(R.string.summary_paid), budget.paid.format(), stringResource(R.string.summary_paid_note), Modifier.weight(1f))
                    }
                }
            }
        }
        if (budget.general.isDefined) GeneralBudgetCard(budget.general)
        if (budget.credits.cents != 0L) LabelledAmount(stringResource(R.string.summary_credits), budget.credits.format())
        if (budget.invoiceLinksToConfirm > 0) LinksToConfirm(budget.invoiceLinksToConfirm)
    }
}

/** Orçamento geral do mês: quanto do total já foi gasto em qualquer categoria; excesso aparece em texto, não só em cor. */
@Composable
private fun GeneralBudgetCard(general: GeneralBudgetView) {
    val total = general.view.total ?: return
    val excess = general.view.excess
    val status = if (excess != null) stringResource(R.string.general_budget_excess, excess.format())
    else stringResource(R.string.general_budget_remaining, (general.view.remaining ?: Money.ZERO).format())
    val description = stringResource(R.string.general_budget_description, general.spent.format(), total.format(), status)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().padding(AccordSpacing.s4).semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            AdaptiveRow(
                start = { Text(stringResource(R.string.general_budget_title), style = MaterialTheme.typography.titleMedium) },
                end = {
                    Text(
                        "${general.spent.format()} ${stringResource(R.string.general_budget_of, total.format())}",
                        style = Accord.typography.amountRow, textAlign = TextAlign.End,
                    )
                },
            )
            LinearProgressIndicator(
                progress = { (general.view.usedFraction ?: if (general.spent.cents > 0) 1.0 else 0.0).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (excess != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                status, style = MaterialTheme.typography.bodyMedium,
                color = if (excess != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryMetric(label: String, amount: String, note: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(amount, style = Accord.typography.amountRow)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s1), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
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
private fun CategoryRow(category: CategoryBudget, name: String, iconKey: String, tint: Color, onClick: () -> Unit) {
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
    val description = listOfNotNull(name, amounts, status, barLabel, percent?.let { stringResource(R.string.percent_value, it) }, breakdown, netCredit).joinToString(". ")

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                .clickable(onClickLabel = stringResource(R.string.category_open, name), role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = AccordSpacing.s1, vertical = AccordSpacing.s3),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.size(AccordSpacing.touchMin), contentAlignment = Alignment.Center) {
                        DecorativeIcon(lucideRes(iconKey), tint = tint)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    if (LocalDensity.current.fontScale >= 1.3f) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(money(category.net), style = Accord.typography.amountRow)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(money(category.net), style = Accord.typography.amountRow, textAlign = TextAlign.End)
                        }
                    }
                    limit.total?.let {
                        Text(
                            stringResource(R.string.category_limit_value, money(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Sem limite definido não há barra: o texto "Limite não definido" é o estado.
            if (state != BarState.NoLimit) BudgetBar(barFill(limit), state, excessMarker(limit, category.net))
            AdaptiveRow(
                start = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                        when (state) {
                            BarState.Attention -> DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.attention)
                            BarState.Excess -> DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = when (state) {
                            BarState.Excess -> MaterialTheme.colorScheme.error
                            BarState.Attention -> Accord.colors.attention
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                    }
                },
                end = { percent?.let { Text(stringResource(R.string.percent_value, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            )
            breakdown?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            netCredit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Accord.colors.success) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Caminhos do mês para o que já ocupa os próximos meses, as rendas e o cartão; cada um volta ao Orçamento. */
@Composable
private fun MonthLinks(onCommitments: () -> Unit, onIncomes: () -> Unit, onCard: () -> Unit, onForecasts: () -> Unit) {
    Column {
        MonthLink(stringResource(R.string.budget_link_forecasts), Lucide.drawable.lucide_ic_trending_up, onForecasts)
        MonthLink(stringResource(R.string.budget_link_commitments), Lucide.drawable.lucide_ic_calendar, onCommitments)
        MonthLink(stringResource(R.string.budget_link_incomes), Lucide.drawable.lucide_ic_wallet, onIncomes)
        MonthLink(stringResource(R.string.budget_link_card), Lucide.drawable.lucide_ic_credit_card, onCard)
    }
}

@Composable
private fun MonthLink(label: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = AccordSpacing.s1, vertical = AccordSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DecorativeIcon(icon, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
    }
}
