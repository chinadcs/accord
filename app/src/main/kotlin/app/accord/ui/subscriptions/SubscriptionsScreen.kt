package app.accord.ui.subscriptions

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Frequency
import app.accord.core.analysis.ChargeComparison
import app.accord.core.analysis.formatSigned
import app.accord.core.space.FrequencySubtotal
import app.accord.core.space.SubscriptionRow
import app.accord.ui.categoryPath
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.fullLabel
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun SubscriptionsRoute(
    viewModel: SubscriptionsViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRegister: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SubscriptionsScreen(state, padding, onBack, viewModel::previousMonth, viewModel::nextMonth, viewModel::setFilter, viewModel::retry, onOpen, onRegister)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFilter: (SubscriptionFilter) -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onRegister: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item { DetailHeader(stringResource(R.string.subscriptions_title), onBack) }
        item { MonthSelector(state.month, onPrevious, onNext) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                SubscriptionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter, onClick = { onFilter(filter) },
                        label = { Text(stringResource(filter.label())) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    )
                }
            }
        }
        when (val content = state.content) {
            SubscriptionsContent.Loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            is SubscriptionsContent.Failed -> {
                item { FailureBanner(onRetry) }
                content.last?.let { subscriptionItems(it, state.filter, onOpen, onRegister) }
            }
            is SubscriptionsContent.Ready -> subscriptionItems(content.data, state.filter, onOpen, onRegister)
        }
        item {
            Text(stringResource(R.string.subscriptions_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun SubscriptionFilter.label() = when (this) {
    SubscriptionFilter.All -> R.string.subscriptions_filter_all
    SubscriptionFilter.Active -> R.string.subscriptions_filter_active
    SubscriptionFilter.Ended -> R.string.subscriptions_filter_ended
}

@Composable
private fun FailureBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.subscriptions_failed), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.subscriptionItems(
    data: SubscriptionsData,
    filter: SubscriptionFilter,
    onOpen: (String) -> Unit,
    onRegister: () -> Unit,
) {
    val list = data.list
    item {
        Text(
            stringResource(R.string.subscriptions_period, list.from.shortLabel(), list.untilExclusive.minusDays(1).fullLabel()),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (list.items.isEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.subscriptions_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.subscriptions_empty_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRegister, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.subscriptions_register)) }
            }
        }
        return
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(stringResource(R.string.subscriptions_due_title), style = MaterialTheme.typography.titleMedium) },
                end = { Text(list.dueInPeriod.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
            )
            Text(stringResource(R.string.subscriptions_due_count, list.dueCount), style = MaterialTheme.typography.bodySmall)
            list.subtotals.forEach { Text(it.line(), style = MaterialTheme.typography.bodyMedium) }
            if (list.subtotals.size > 1) {
                Text(stringResource(R.string.subscriptions_subtotals_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val visible = list.items.filter { filter.accepts(it.status) }
    if (visible.isEmpty()) {
        item { Text(stringResource(R.string.subscriptions_filtered_empty), style = MaterialTheme.typography.bodyLarge) }
    } else {
        items(visible, key = { it.recurrenceId }) { row -> SubscriptionLine(row, data, onOpen) }
    }
    item {
        OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.subscriptions_register))
        }
    }
}

@Composable
private fun FrequencySubtotal.line(): String = stringResource(
    when (frequency) {
        Frequency.Monthly -> R.string.subscriptions_subtotal_monthly
        Frequency.Annual -> R.string.subscriptions_subtotal_annual
    },
    count, expected.format(),
)

@Composable
internal fun Frequency.adjective(): String = stringResource(
    when (this) {
        Frequency.Monthly -> R.string.subscriptions_frequency_monthly
        Frequency.Annual -> R.string.subscriptions_frequency_annual
    },
)

@Composable
private fun SubscriptionLine(row: SubscriptionRow, data: SubscriptionsData, onOpen: (String) -> Unit) {
    val next = row.next
    val line = if (next != null) {
        stringResource(R.string.subscriptions_row_next, row.frequency.adjective(), next.scheduledDate.shortLabel())
    } else {
        stringResource(R.string.subscriptions_row_ended, row.frequency.adjective())
    }
    val observed = (row.comparison as? ChargeComparison.Observed)?.difference?.takeIf { it.cents != 0L }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onOpen(row.recurrenceId) },
    ) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(row.description, style = MaterialTheme.typography.titleSmall) },
                end = {
                    next?.let { Text(it.expectedAmount.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) }
                },
            )
            Text(line, style = MaterialTheme.typography.bodyMedium)
            categoryPath(data.categories, row.categoryId)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Só aponta o que os dados permitem; nunca "aumento confirmado" nem "cobrança indevida".
            observed?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_info)
                    Text(stringResource(R.string.subscriptions_observed_tag, it.formatSigned()), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
