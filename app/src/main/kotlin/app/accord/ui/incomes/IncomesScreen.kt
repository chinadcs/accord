package app.accord.ui.incomes

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.IncomeCategoryTag
import app.accord.core.space.IncomeView
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.detail.LinkedAction
import app.accord.ui.detail.LinkedActionDialog
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun IncomesRoute(
    viewModel: IncomesViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    IncomesScreen(
        state, padding, onBack, viewModel::previousMonth, viewModel::nextMonth, viewModel::retry, onOpen, onAdd,
        onReceive = viewModel::receive,
    )
    linked?.let { LinkedActionDialog(it, viewModel::updateLinked, viewModel::submitLinked, viewModel::closeLinked) }
}

@Composable
fun IncomesScreen(
    state: IncomesUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onReceive: (IncomeView) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item {
            DetailHeader(stringResource(R.string.incomes_title), onBack)
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.incomes_add))
            }
        }
        item { MonthSelector(state.month, onPrevious, onNext) }
        when (val content = state.content) {
            IncomesContent.Loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            IncomesContent.Failed -> item {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
                }
            }
            is IncomesContent.Ready -> {
                val summary = content.summary
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                        Text(
                            stringResource(R.string.incomes_received_and_pending, summary.received.format(), summary.toReceive.format()),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        AdaptiveRow(
                            start = { Text(stringResource(R.string.incomes_expected), style = MaterialTheme.typography.titleMedium) },
                            end = { Text(summary.expected.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
                        )
                        Text(stringResource(R.string.incomes_not_balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (summary.items.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                            Text(stringResource(R.string.incomes_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.incomes_empty_body), style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = onAdd, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.incomes_add)) }
                        }
                    }
                } else {
                    items(summary.items, key = { it.id }) { IncomeRow(it, state.incomeCategories[it.incomeCategoryId], state.incomeCategories.values.toList(), onOpen, onReceive) }
                }
            }
        }
    }
}

@Composable
private fun IncomeRow(income: IncomeView, category: IncomeCategoryNode?, allCategories: List<IncomeCategoryNode>, onOpen: (String) -> Unit, onReceive: (IncomeView) -> Unit) {
    val received = income.receivedDate != null
    val line = if (received) {
        stringResource(R.string.incomes_line_received, income.receivedDate?.shortLabel().orEmpty())
    } else {
        stringResource(R.string.incomes_line_expected, income.expectedDate.shortLabel())
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onOpen(income.id) },
    ) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(income.description, style = MaterialTheme.typography.titleSmall) },
                end = { Text((income.receivedAmount ?: income.expectedAmount).format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                DecorativeIcon(if (received) Lucide.drawable.lucide_ic_circle_check else Lucide.drawable.lucide_ic_clock)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            IncomeCategoryTag(category, style = MaterialTheme.typography.bodySmall, categories = allCategories)
            // Valor efetivo e previsto aparecem rotulados, sem pendência pela diferença.
            if (received && income.receivedAmount != null && income.receivedAmount != income.expectedAmount) {
                Text(
                    stringResource(R.string.incomes_expected_was, income.expectedAmount.format()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!received) {
                OutlinedButton(onClick = { onReceive(income) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.incomes_receive))
                }
            }
        }
    }
}

/** Ação de recebimento da previsão: a mesma do detalhe, para a renda escolhida na lista. */
fun incomeReceiveAction(income: IncomeView) =
    LinkedAction.Settle(LinkedAction.Settle.Kind.Income, income.id, income.expectedAmount, revision = income.revision)
