package app.accord.ui.budget

import app.accord.ui.components.AccordCard
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.core.space.GeneralBudgetView
import app.accord.core.MonthCashFlow
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.CollapsibleSection
import app.accord.ui.components.SkeletonBlock
import app.accord.ui.components.SkeletonLines
import app.accord.ui.components.chartInk
import app.accord.ui.components.textInk
import app.accord.ui.components.BudgetBar
import app.accord.ui.components.StateBadge
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.home.AllowanceMethodText
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
    onOpenForecasts: (YearMonth) -> Unit = {},
    countExpectedIncome: Boolean = false,
    onEditLimits: (YearMonth) -> Unit = {},
    onConfirmLinks: (YearMonth) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    BudgetScreen(
        state, padding,
        onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth, onRetry = viewModel::retry,
        onRetryFlow = viewModel::retryFlow,
        onOpenCategory = onOpenCategory, onClassify = onClassify, onRegister = onRegister,
        onOpenCommitments = onOpenCommitments, onOpenIncomes = onOpenIncomes, onOpenCard = onOpenCard,
        hasFloatingAction = true, onOpenForecasts = onOpenForecasts, countExpectedIncome = countExpectedIncome,
        onEditLimits = onEditLimits, onConfirmLinks = onConfirmLinks, onPickMonth = viewModel::selectMonth,
        onCurrentMonth = viewModel::goToCurrentMonth.takeIf { !viewModel.isCurrentMonth(state.month) },
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
    hasFloatingAction: Boolean = false,
    onOpenForecasts: (YearMonth) -> Unit = {},
    onRetryFlow: () -> Unit = {},
    onCurrentMonth: (() -> Unit)? = null,
    countExpectedIncome: Boolean = false,
    onEditLimits: (YearMonth) -> Unit = {},
    onConfirmLinks: (YearMonth) -> Unit = {},
    onPickMonth: ((YearMonth) -> Unit)? = null,
) {
    // A barra inferior sobrepõe o conteúdo do Scaffold. O espaço dela vai no recorte da lista (não em contentPadding):
    // assim a área visível termina onde a barra começa e um item levado à tela (rolagem por acessibilidade, foco,
    // teclado) nunca fica escondido sob ela, o que contentPadding não impede porque só vale no fim da rolagem.
    // O aberto/fechado de "Mais detalhes do mês" fica fora da lista: sobrevive à rolagem e a voltar de um detalhe.
    var flowOpen by rememberSaveable { mutableStateOf(false) }
    var chartOpen by rememberSaveable { mutableStateOf(false) }
    var methodOpen by rememberSaveable { mutableStateOf(false) }
    val details = DetailsState(
        flowOpen, chartOpen, methodOpen,
        onToggle = { when (it) { DetailSection.Flow -> flowOpen = !flowOpen; DetailSection.Chart -> chartOpen = !chartOpen; DetailSection.Method -> methodOpen = !methodOpen } },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
        contentPadding = PaddingValues(
            start = AccordSpacing.s4, end = AccordSpacing.s4,
            top = padding.calculateTopPadding() + AccordSpacing.s4,
            bottom = AccordSpacing.s4 + if (hasFloatingAction) AccordSpacing.fabClearance else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                ScreenTitle(stringResource(R.string.tab_budget))
                MonthSelector(state.month, onPrevious, onNext, onCurrent = onCurrentMonth, onPick = onPickMonth)
            }
        }
        when (val content = state.content) {
            BudgetContent.Loading -> item { BudgetSkeleton() }
            is BudgetContent.Failed -> {
                item { FailureBanner(content.last?.month, onRetry) }
                content.last?.let { budgetContent(it, onOpenCategory, onClassify, onRegister, onOpenCommitments, onOpenIncomes, onOpenCard, !hasFloatingAction, onOpenForecasts, state.flow.takeIf { _ -> it.month == state.month }, onRetryFlow, countExpectedIncome, details, onEditLimits, onConfirmLinks) }
            }
            is BudgetContent.Ready -> budgetContent(content.data, onOpenCategory, onClassify, onRegister, onOpenCommitments, onOpenIncomes, onOpenCard, !hasFloatingAction, onOpenForecasts, state.flow.takeIf { content.data.month == state.month }, onRetryFlow, countExpectedIncome, details, onEditLimits, onConfirmLinks)
        }
    }
}

private enum class DetailSection { Flow, Chart, Method }

private class DetailsState(val flow: Boolean, val chart: Boolean, val method: Boolean, val onToggle: (DetailSection) -> Unit)

/** Esqueleto das formas finais (resumo e linhas de categoria): nunca mostra valor, e o leitor de tela recebe um só nó. */
@Composable
private fun BudgetSkeleton() {
    val description = stringResource(R.string.state_loading)
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        SkeletonBlock(height = 140.dp)
        repeat(4) {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                SkeletonBlock(height = 20.dp, widthFraction = 0.6f)
                SkeletonBlock(height = 8.dp)
                SkeletonBlock(height = 16.dp, widthFraction = 0.8f)
            }
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
    countExpectedIncome: Boolean,
    details: DetailsState,
    onEditLimits: (YearMonth) -> Unit,
    onConfirmLinks: (YearMonth) -> Unit,
) {
    if (data.isEmpty) {
        item { EmptyMonth(onRegister, showInlineManual) }
        item { BudgetCriteria() }
        return
    }
    item { SummaryCard(data) { onEditLimits(data.month) } }
    if (data.noActivity) item { EmptyMonth(onRegister, showInlineManual) }
    // Aviso único: "A classificar" e os vínculos de fatura a conferir ficam juntos, uma vez só na tela.
    if (data.budget.toClassify.count > 0 || data.budget.invoiceLinksToConfirm > 0) item { NoticeCard(data, { onClassify(data.month) }, { onConfirmLinks(data.month) }) }
    item {
        val hasAnyLimit = data.hasLimits || data.budget.general.isDefined
        AdaptiveRow(
            start = {
                Text(
                    stringResource(R.string.categories_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = AccordSpacing.s2).semantics { heading() },
                )
            },
            end = {
                // Sem limite algum, a ação "Definir limites" fica no resumo (spec 06); aqui só quando há o que editar.
                if (hasAnyLimit) {
                    val description = stringResource(R.string.budget_edit_limits_description, data.month.label())
                    TextButton(
                        onClick = { onEditLimits(data.month) },
                        modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = description },
                    ) { Text(stringResource(R.string.budget_edit_limits)) }
                }
            },
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
    item(key = "details-title") {
        Text(
            stringResource(R.string.budget_details_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = AccordSpacing.s2).semantics { heading() },
        )
    }
    // O fluxo é do mês escolhido e só aparece junto de uma visão desse mesmo mês (nunca com a do mês anterior).
    item(key = "cash-flow") { BudgetFlowSection(flow ?: BudgetFlowState.Loading, details.flow, { details.onToggle(DetailSection.Flow) }, onRetryFlow, countExpectedIncome) }
    item(key = "category-chart") {
        // "A classificar" fica fora do gráfico: é o aviso acima, e as barras são só categorias classificadas.
        val values = data.budget.categories.map { category ->
            MoneyChartValue(
                data.categories[category.categoryId]?.name.orEmpty(), category.net,
                onClick = { onOpenCategory(category.categoryId, data.month) },
            )
        }
        CollapsibleSection(stringResource(R.string.chart_budget_title), details.chart, { details.onToggle(DetailSection.Chart) }) {
            MoneyChart(
                null, stringResource(R.string.chart_budget_criterion, data.month.label()), values,
                background = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }
    }
    // Texto fixo de "Como é calculado" (ADR 0078): a margem é a de hoje, então não lê dados do mês escolhido.
    item(key = "allowance-method") { AllowanceMethodSection(details.method) { details.onToggle(DetailSection.Method) } }
    item(key = "month-links") { MonthLinks({ onOpenCommitments(data.month) }, { onOpenIncomes(data.month) }, onOpenCard, { onOpenForecasts(data.month) }) }
}

/**
 * Fluxo do mês escolhido: renda contra o comprometido e o ritmo estimado dos gastos variáveis (leitura; nada é gravado).
 * O cabeçalho mostra o resultado (com ícone e palavra), a carga ou a falha, nunca zero; só a falha oferece "Tentar de novo",
 * que relê apenas o fluxo.
 */
@Composable
private fun BudgetFlowSection(flow: BudgetFlowState, open: Boolean, onToggle: () -> Unit, onRetry: () -> Unit, countExpectedIncome: Boolean) {
    val retryDescription = stringResource(R.string.budget_flow_retry_description)
    CollapsibleSection(
        title = stringResource(R.string.budget_flow_title), expanded = open, onToggle = onToggle,
        summary = {
            when (flow) {
                BudgetFlowState.Loading -> Text(stringResource(R.string.cashflow_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BudgetFlowState.Failed -> Text(stringResource(R.string.cashflow_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is BudgetFlowState.Ready -> FlowResultSummary(flow.value, countExpectedIncome)
            }
        },
    ) {
        when (flow) {
            BudgetFlowState.Loading -> SkeletonLines(stringResource(R.string.cashflow_loading), lines = 4)
            BudgetFlowState.Failed -> OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = retryDescription },
            ) { Text(stringResource(R.string.action_retry)) }
            is BudgetFlowState.Ready -> CashFlowBody(flow.value, countExpectedIncome)
        }
    }
}

/** Resultado do mês no cabeçalho recolhido: ícone e palavra sobra/falta; a cor nunca é o único sinal. */
@Composable
private fun FlowResultSummary(value: MonthCashFlow, countExpectedIncome: Boolean) {
    val cents = if (countExpectedIncome) value.resultWithExpectedCents else value.resultWithReceivedCents
    val amount = Money(kotlin.math.abs(cents)).format()
    val outcome = when {
        cents > 0 -> stringResource(R.string.cashflow_outcome_surplus, amount)
        cents < 0 -> stringResource(R.string.cashflow_outcome_shortfall, amount)
        else -> stringResource(R.string.cashflow_outcome_even, amount)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        DecorativeIcon(if (cents < 0) Lucide.drawable.lucide_ic_circle_alert else Lucide.drawable.lucide_ic_circle_check)
        Text(outcome.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Como é calculado": critério de data e o texto da margem para gastar. Sem números: a margem é a de hoje e não
 * depende do mês escolhido. Recolhida por padrão; o aberto/fechado é anunciado pelo leitor de tela.
 */
@Composable
private fun AllowanceMethodSection(open: Boolean, onToggle: () -> Unit) {
    CollapsibleSection(stringResource(R.string.budget_method_title), open, onToggle) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(stringResource(R.string.budget_criteria), style = MaterialTheme.typography.bodyMedium)
            AllowanceMethodText(value = null)
            Text(
                stringResource(R.string.budget_allowance_method_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun SummaryCard(data: BudgetData, onDefineLimits: () -> Unit) {
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
                // Só com Orçamento geral (sem limite por categoria) o resumo não diz "Nenhum limite definido": o geral tem o seu cartão.
                val limitsText = if (data.hasLimits) budget.totalLimits.format() else if (budget.general.isDefined) "—" else stringResource(R.string.summary_no_limits)
                if (stackedMetrics) {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                        SummaryMetric(stringResource(R.string.summary_limits), limitsText)
                        SummaryMetric(stringResource(R.string.summary_paid), budget.paid.format(), stringResource(R.string.summary_paid_note))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
                        SummaryMetric(stringResource(R.string.summary_limits), limitsText, modifier = Modifier.weight(1f))
                        // Pago integra o comprometido; a legenda impede somar os dois novamente.
                        SummaryMetric(stringResource(R.string.summary_paid), budget.paid.format(), stringResource(R.string.summary_paid_note), Modifier.weight(1f))
                    }
                }
            }
        }
        if (budget.general.isDefined) GeneralBudgetCard(budget.general)
        if (!data.hasLimits && !budget.general.isDefined) OutlinedButton(onClick = onDefineLimits, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.budget_define_limits))
        }
        if (budget.credits.cents != 0L) LabelledAmount(stringResource(R.string.summary_credits), budget.credits.format())
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
    AccordCard {
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

/** Aviso único do mês: A classificar abre Lançamentos filtrados; vínculos de fatura a conferir são a segunda linha (Conferir abre Lançamentos filtrados, no mês exibido). */
@Composable
private fun NoticeCard(data: BudgetData, onClassify: () -> Unit, onConfirmLinks: () -> Unit) {
    val line = data.budget.toClassify
    val links = data.budget.invoiceLinksToConfirm
    val ink = Accord.colors.onAttentionContainer
    Surface(color = Accord.colors.attentionContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth()) {
            if (line.count > 0) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.to_classify_action), onClick = onClassify)
                        .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_tag, tint = ink)
                    Text(
                        pluralStringResource(R.plurals.budget_notice_to_classify, line.count, line.count, line.amount.format()),
                        style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.weight(1f),
                    )
                    DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right, tint = ink)
                }
            }
            if (links > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                ) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = ink)
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.links_to_confirm, links, links), style = MaterialTheme.typography.bodyMedium, color = ink)
                        Text(stringResource(R.string.links_to_confirm_note), style = MaterialTheme.typography.bodySmall, color = ink)
                    }
                    TextButton(onClick = onConfirmLinks, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.links_to_confirm_action), color = ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryBudget, name: String, iconKey: String, tint: Color, onClick: () -> Unit) {
    val limit = category.limit
    val state = barState(limit)
    val money = { m: Money -> m.format() }
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val netCredit = if (category.net.isNegative) stringResource(R.string.category_net_credit, money(-category.net)) else null
    val status = netCredit ?: when (state) {
        BarState.NoLimit -> stringResource(R.string.category_limit_undefined)
        BarState.Excess -> stringResource(R.string.category_excess, money(limit.excess ?: Money.ZERO))
        else -> stringResource(R.string.category_remaining, money(limit.remaining ?: Money.ZERO))
    }
    // O estado vai para o selo (atenção e excesso); o texto de "Faltam" fica neutro e o crédito líquido segue em `success`.
    val statusColor = if (netCredit != null) textInk(Accord.colors.success, surface, onSurface) else MaterialTheme.colorScheme.onSurfaceVariant
    val barLabel = when (state) {
        BarState.WithinLimit -> stringResource(R.string.bar_within)
        BarState.Attention -> stringResource(R.string.bar_attention)
        BarState.Excess -> stringResource(R.string.bar_excess)
        BarState.NoLimit -> stringResource(R.string.bar_none)
    }
    val percent = percentLabel(limit)
    val ofLimit = limit.total?.let { stringResource(R.string.category_of_limit_short, money(it)) }
    // Despesas brutas e créditos ficam na tela da categoria, mas o leitor de tela recebe tudo na leitura da linha.
    val breakdown = if (category.credits.cents != 0L) {
        stringResource(R.string.category_credits_breakdown, money(category.gross), money(category.credits), money(category.net))
    } else {
        null
    }
    val description = listOfNotNull(
        name, money(category.net), ofLimit, status, barLabel, percent?.let { stringResource(R.string.percent_value, it) }, breakdown,
    ).joinToString(". ")

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                .clickable(onClickLabel = stringResource(R.string.category_open, name), role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = AccordSpacing.s1, vertical = AccordSpacing.s3),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            // Linha 1: ícone, nome e valor líquido.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Surface(color = surface, shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.size(AccordSpacing.touchMin), contentAlignment = Alignment.Center) {
                        DecorativeIcon(lucideRes(iconKey), tint = tint)
                    }
                }
                if (LocalDensity.current.fontScale >= 1.3f) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(money(category.net), style = Accord.typography.amountRow)
                    }
                } else {
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(money(category.net), style = Accord.typography.amountRow, textAlign = TextAlign.End)
                }
            }
            // Linha 2: barra. Sem limite definido não há barra: "Limite não definido" é o estado.
            if (state != BarState.NoLimit) BudgetBar(barFill(limit), state, excessMarker(limit, category.net), categoryColor = tint)
            // Linha 3: estado (ícone e texto), "de R$ limite" e percentual.
            AdaptiveRow(
                start = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                        when {
                            netCredit != null -> Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                            // Atenção: selo "perto do limite" e, ao lado, quanto falta; excesso: o selo já diz quanto passou.
                            state == BarState.Attention -> {
                                StateBadge(state, stringResource(R.string.bar_attention))
                                Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                            }
                            state == BarState.Excess -> StateBadge(state, status)
                            else -> Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                        }
                    }
                },
                end = {
                    val tail = listOfNotNull(ofLimit, percent?.let { stringResource(R.string.percent_value, it) }).joinToString(" · ")
                    if (tail.isNotEmpty()) Text(tail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Caminhos do mês para o que já ocupa os próximos meses, as rendas e o cartão; cada um volta ao Orçamento. */
@Composable
private fun MonthLinks(onCommitments: () -> Unit, onIncomes: () -> Unit, onCard: () -> Unit, onForecasts: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.budget_month_links_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = AccordSpacing.s1, vertical = AccordSpacing.s2).semantics { heading() },
        )
        MonthLink(stringResource(R.string.budget_link_forecasts), Lucide.drawable.lucide_ic_trending_up, onForecasts)
        MonthLink(stringResource(R.string.budget_link_commitments), Lucide.drawable.lucide_ic_calendar, onCommitments)
        MonthLink(stringResource(R.string.budget_link_incomes), Lucide.drawable.lucide_ic_banknote, onIncomes)
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
