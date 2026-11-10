package app.accord.ui.commitments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentKind
import app.accord.core.space.CommitmentNature
import app.accord.ui.categoryPath
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.entries.SituationTags
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun CommitmentsRoute(
    viewModel: CommitmentsViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRegister: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenSubscriptions: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CommitmentsScreen(
        state, padding, onBack, viewModel::previousMonth, viewModel::nextMonth, viewModel::setKind, viewModel::retry,
        onOpen, onRegister, onOpenCard, onOpenSubscriptions, viewModel::retryUpcoming,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommitmentsScreen(
    state: CommitmentsUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onKind: (CommitmentKind) -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onRegister: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenSubscriptions: (() -> Unit)? = null,
    onRetryUpcoming: () -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            DetailHeader(stringResource(R.string.commitments_title), onBack)
        }
        item { MonthSelector(state.month, onPrevious, onNext) }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                CommitmentKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind, onClick = { onKind(kind) },
                        label = { Text(stringResource(kind.label())) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
        }
        state.scenario?.let { scenario -> item { ScenarioLayer(scenario, state) } }
        when (val content = state.content) {
            CommitmentsContent.Loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            is CommitmentsContent.Failed -> {
                item { FailureBanner(onRetry) }
                content.last?.let { commitmentItems(it, onOpen, onRegister, onOpenCard) }
            }
            is CommitmentsContent.Ready -> commitmentItems(content.data, onOpen, onRegister, onOpenCard)
        }
        // Depois do bloco do mês: seletor, total e lista não mudam de lugar ao navegar entre meses; esta seção é de hoje.
        item(key = "upcoming") { CommitmentsUpcomingSection(state.upcoming, onRetryUpcoming, onOpen, onOpenCard) }
        onOpenSubscriptions?.let { open ->
            item {
                OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.commitments_subscriptions_link))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.commitments_variable_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun CommitmentKind.label() = when (this) {
    CommitmentKind.All -> R.string.commitments_kind_all
    CommitmentKind.Installments -> R.string.commitments_kind_installments
    CommitmentKind.Recurrences -> R.string.commitments_kind_recurrences
    CommitmentKind.Bills -> R.string.commitments_kind_bills
}

@Composable
private fun FailureBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.commitments_failed), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.commitmentItems(
    data: CommitmentsData,
    onOpen: (String) -> Unit,
    onRegister: () -> Unit,
    onOpenCard: () -> Unit,
) {
    val month = data.commitments
    item {
        AdaptiveRow(
            start = { Text(stringResource(R.string.commitments_known_total), style = MaterialTheme.typography.titleMedium) },
            end = { Text(month.knownTotal.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
        )
    }
    item {
        OutlinedButton(onClick = onOpenCard, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.commitments_card_link))
        }
    }
    if (month.items.isEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.commitments_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.commitments_empty_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRegister, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.commitments_register))
                }
            }
        }
    } else {
        items(month.items, key = { it.id }) { item -> CommitmentRow(item, data, onOpen) }
        item {
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.commitments_register))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommitmentRow(item: Commitment, data: CommitmentsData, onOpen: (String) -> Unit) {
    val line = when (item.nature) {
        CommitmentNature.Finite -> stringResource(R.string.commitments_line_finite, item.number ?: 0, item.count ?: 0, item.dueDate.shortLabel())
        CommitmentNature.Recurring -> stringResource(R.string.commitments_line_recurring, item.dueDate.shortLabel())
        CommitmentNature.Bill -> stringResource(R.string.commitments_line_bill, item.dueDate.shortLabel())
    }
    val category = categoryPath(data.categories, item.categoryId)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onOpen(item.id) },
    ) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(item.description, style = MaterialTheme.typography.titleSmall) },
                end = { Text(item.amount.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Text(line, style = MaterialTheme.typography.bodyMedium)
            category?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (item.situations.isNotEmpty()) SituationTags(item.situations.toList())
        }
    }
}

/**
 * Camada de cenário (S7): rotulada, separada e sem somar aos totais reais, que continuam sendo só o que foi registrado.
 * O texto e o ícone dizem que é simulação; a leitura do TalkBack é uma frase única.
 */
@Composable
private fun ScenarioLayer(scenario: app.accord.core.simulation.Scenario, state: CommitmentsUiState) {
    val part = app.accord.core.simulation.Simulation.partIn(scenario, state.month)
    val known = ((state.content as? CommitmentsContent.Ready)?.data ?: (state.content as? CommitmentsContent.Failed)?.last)?.commitments?.knownTotal
    val spoken = if (part == null) {
        stringResource(R.string.commitments_scenario_none)
    } else {
        stringResource(
            R.string.commitments_scenario_a11y, part.number, scenario.installments, part.amount.format(),
            known?.let { (it + part.amount).format() } ?: part.amount.format(),
        )
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AccordSpacing.s3).semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_info)
                Text(stringResource(R.string.commitments_scenario_title), style = MaterialTheme.typography.titleSmall)
            }
            if (part == null) {
                Text(stringResource(R.string.commitments_scenario_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.commitments_scenario_line, part.number, scenario.installments, part.amount.format()), style = MaterialTheme.typography.bodyMedium)
                known?.let { Text(stringResource(R.string.commitments_scenario_total, (it + part.amount).format()), style = MaterialTheme.typography.bodyMedium) }
            }
            Text(stringResource(R.string.commitments_scenario_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
