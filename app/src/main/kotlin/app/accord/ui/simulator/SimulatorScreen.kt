package app.accord.ui.simulator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.label
import app.accord.core.simulation.SimulatedMonth
import app.accord.ui.categoryPath
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MonthSelector
import app.accord.ui.register.CategoryDialog
import app.accord.ui.register.FormTextField
import app.accord.ui.register.PickerField
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun SimulatorRoute(
    viewModel: SimulatorViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenMonth: (java.time.YearMonth, app.accord.core.simulation.Scenario) -> Unit,
    onSaveAsIntention: (app.accord.core.simulation.Scenario) -> Unit,
    onAssume: (app.accord.core.simulation.Scenario) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SimulatorScreen(
        state, padding, onBack,
        onTotal = viewModel::setTotal, onInstallments = viewModel::setInstallments, onCategory = viewModel::setCategory,
        onPreviousMonth = { viewModel.shiftFirstMonth(-1) }, onNextMonth = { viewModel.shiftFirstMonth(1) },
        onUseSuggestion = viewModel::useSuggestedMonth, onSimulate = viewModel::simulate, onOpenMonth = onOpenMonth,
        onSaveAsIntention = onSaveAsIntention, onAssume = onAssume,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimulatorScreen(
    state: SimulatorUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onTotal: (String) -> Unit,
    onInstallments: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onUseSuggestion: () -> Unit,
    onSimulate: () -> Unit,
    onOpenMonth: (java.time.YearMonth, app.accord.core.simulation.Scenario) -> Unit = { _, _ -> },
    onSaveAsIntention: (app.accord.core.simulation.Scenario) -> Unit = {},
    onAssume: (app.accord.core.simulation.Scenario) -> Unit = {},
) {
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    val totalFocus = remember { FocusRequester() }
    val installmentsFocus = remember { FocusRequester() }
    val totalBring = remember { BringIntoViewRequester() }
    val installmentsBring = remember { BringIntoViewRequester() }
    val categoryBring = remember { BringIntoViewRequester() }

    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.simulator_title), onBack)
        Text(stringResource(R.string.simulator_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        FormTextField(
            value = state.form.totalText, onChange = onTotal, label = stringResource(R.string.simulator_total),
            error = state.errors[SimulatorField.Total], focus = totalFocus, bring = totalBring,
            hint = stringResource(R.string.simulator_total_hint), keyboard = KeyboardType.Decimal, emphasized = true,
        )
        FormTextField(
            value = state.form.installmentsText, onChange = onInstallments, label = stringResource(R.string.simulator_installments),
            error = state.errors[SimulatorField.Installments], focus = installmentsFocus, bring = installmentsBring,
            hint = stringResource(R.string.simulator_installments_hint), keyboard = KeyboardType.Number,
        )
        // Só o cartão tem fatura por parcela; parcelamento fora do cartão fica fora desta entrega (ADR 0048).
        PickerField(
            label = stringResource(R.string.simulator_payment), value = stringResource(R.string.simulator_payment_card),
            error = null, bring = remember { BringIntoViewRequester() }, onClick = {},
        )
        PickerField(
            label = stringResource(R.string.simulator_category),
            value = categoryPath(state.categories, state.form.categoryId) ?: stringResource(R.string.simulator_category_pick),
            error = null, bring = categoryBring,
        ) { pickingCategory = true }
        if (state.form.categoryId != null) {
            OutlinedButton(onClick = { onCategory(null) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.simulator_category_clear))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.simulator_first_invoice), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            state.firstMonth?.let { month -> MonthSelector(month, onPreviousMonth, onNextMonth) }
            Text(
                stringResource(if (state.firstMonthIsEstimate) R.string.simulator_first_invoice_estimate else R.string.simulator_first_invoice_chosen),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.firstMonthIsEstimate && state.suggestedMonth != null) {
                OutlinedButton(onClick = onUseSuggestion, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.simulator_use_suggestion))
                }
            }
        }

        val calculating = state.outcome is SimulatorOutcome.Calculating
        Button(
            onClick = onSimulate, enabled = !calculating && state.firstMonth != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
        ) { Text(stringResource(if (calculating) R.string.simulator_calculating else R.string.simulator_action_simulate)) }

        when (val outcome = state.outcome) {
            SimulatorOutcome.Idle, SimulatorOutcome.Calculating -> Unit
            SimulatorOutcome.Unavailable -> UnavailableBanner(onSimulate)
            is SimulatorOutcome.Ready -> ResultSection(outcome, state, onOpenMonth, onSaveAsIntention, onBack, onAssume)
        }
    }

    if (pickingCategory) {
        CategoryDialog(state.categories, state.form.categoryId, onPick = { id -> onCategory(id); pickingCategory = false }, onDismiss = { pickingCategory = false })
    }
}

@Composable
private fun UnavailableBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.simulator_unavailable), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.simulator_retry)) }
        }
    }
}

@Composable
private fun ResultSection(
    outcome: SimulatorOutcome.Ready,
    state: SimulatorUiState,
    onOpenMonth: (java.time.YearMonth, app.accord.core.simulation.Scenario) -> Unit,
    onSaveAsIntention: (app.accord.core.simulation.Scenario) -> Unit,
    onBackToIntention: () -> Unit,
    onAssume: (app.accord.core.simulation.Scenario) -> Unit,
) {
    val months = outcome.result.months
    val count = months.size
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        // Rótulo persistente: ícone + texto, nunca só cor (spec 36 §8).
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                DecorativeIcon(Lucide.drawable.lucide_ic_info)
                Text(stringResource(R.string.simulator_label_scenario), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
        }
        val first = months.first()
        val last = months.last()
        val even = months.all { it.scenario == first.scenario }
        Text(
            if (even) stringResource(R.string.simulator_summary, first.month.label(), last.month.label(), first.scenario.format())
            else stringResource(R.string.simulator_summary_uneven, first.month.label(), last.month.label(), outcome.result.scenarioTotal.format(), count),
            style = MaterialTheme.typography.titleMedium,
        )
        months.forEach { m -> MonthCard(m, count, state) { onOpenMonth(m.month, outcome.scenario) } }
        // Assumir compromisso é uma ação separada: abre o registro manual preenchido, e só salvar cria o compromisso.
        Button(onClick = { onAssume(outcome.scenario) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.intention_assume))
        }
        // Cenário avulso vira intenção pela tela 35; vindo de uma intenção, volta a ela sem alterar o valor estimado.
        if (state.originIntentionId == null) {
            OutlinedButton(onClick = { onSaveAsIntention(outcome.scenario) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.simulator_save_intention))
            }
        } else {
            OutlinedButton(onClick = onBackToIntention, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.simulator_back_to_intention))
            }
        }
        Text(stringResource(R.string.simulator_stale_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.simulator_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Um mês simulado. A leitura do TalkBack é uma frase única com o rótulo de simulação, o valor e a origem de cada parte. */
@Composable
private fun MonthCard(month: SimulatedMonth, count: Int, state: SimulatorUiState, onOpen: () -> Unit) {
    val base = stringResource(
        R.string.simulator_a11y_month, month.month.label(), month.installmentNumber, count,
        month.scenario.format(), month.known.format(), month.totalWithScenario.format(),
    )
    // O limite e o possível excesso também são falados: a leitura única não pode ficar atrás do que se vê.
    val limitSpoken = month.category?.let { c ->
        val name = categoryPath(state.categories, c.categoryId) ?: ""
        val limit = c.limit
        when {
            limit == null -> stringResource(R.string.simulator_a11y_no_limit, name)
            else -> stringResource(R.string.simulator_a11y_limit, name, limit.format(), c.totalWithScenario.format()) +
                (c.scenarioExcess?.takeIf { it.cents > 0 }?.let { " " + stringResource(R.string.simulator_a11y_excess, it.format()) } ?: "")
        }
    }
    val spoken = listOfNotNull(base, limitSpoken).joinToString(" ")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AccordSpacing.s3).semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                AdaptiveRow(
                    start = { Text(stringResource(R.string.simulator_month_title, month.month.label(), month.installmentNumber, count), style = MaterialTheme.typography.titleSmall) },
                    end = { Text(month.totalWithScenario.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
                )
                Text(stringResource(R.string.simulator_known, month.known.format()), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.simulator_scenario_line, month.scenario.format()), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.simulator_total_with, month.totalWithScenario.format()), style = MaterialTheme.typography.bodyMedium)
                month.variableEstimate?.let {
                    Text(stringResource(R.string.simulator_estimate_line, it.format()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                month.category?.let { c ->
                    val name = categoryPath(state.categories, c.categoryId) ?: ""
                    val limit = c.limit
                    if (limit == null) {
                        Text(stringResource(R.string.simulator_limit_none, name), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            stringResource(R.string.simulator_limit_line, name, limit.format(), c.known.format(), c.totalWithScenario.format()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val excess = c.scenarioExcess
                        if (excess != null && excess.cents > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                                DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.onAttentionContainer)
                                Text(stringResource(R.string.simulator_excess_scenario, excess.format()), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text(stringResource(R.string.simulator_within_limit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.simulator_open_month, month.month.label()))
            }
        }
    }
}
