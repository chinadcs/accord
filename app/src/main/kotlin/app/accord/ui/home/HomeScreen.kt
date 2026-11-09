package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.RootScaffold
import app.accord.ui.components.AccordButton
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.sync.SyncIndicator
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordSpacing

/** A mesma leitura local no Início e em Mais; falha ou carga nunca aparecem como zero. */
@Composable
fun pendingSummary(state: PendingQueueUiState): String = when {
    state.failed -> stringResource(R.string.home_pending_failed)
    state.loading -> stringResource(R.string.home_pending_loading)
    state.items.isEmpty() -> stringResource(R.string.home_pending_empty)
    else -> pluralStringResource(R.plurals.home_pending_count, state.items.size, state.items.size)
}

/** Base do Início (ADR 0067). Os painéis financeiros têm entregas próprias. */
@Composable
fun HomeScreen(
    padding: PaddingValues,
    pending: PendingQueueUiState,
    onRetry: () -> Unit,
    onChat: () -> Unit,
    onRegister: () -> Unit,
    onReview: () -> Unit,
    allowance: HomeAllowanceState = HomeAllowanceState.Loading,
    onAllowanceRetry: () -> Unit = {},
    onBudget: () -> Unit = {},
    upcoming: HomeUpcomingState = HomeUpcomingState.Loading,
    onUpcomingRetry: () -> Unit = {},
    onCommitments: () -> Unit = {},
    projection: HomeProjectionState = HomeProjectionState.Loading,
    onProjectionRetry: () -> Unit = {},
    onForecasts: () -> Unit = {},
) {
    RootScaffold(stringResource(R.string.tab_home), padding) {
        SyncIndicator()
        Text(stringResource(R.string.home_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AccordButton(onClick = onChat, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.home_open_chat))
        }
        AccordOutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.action_register_manually))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                when (allowance) {
                    HomeAllowanceState.Loading -> Text(stringResource(R.string.allowance_loading))
                    HomeAllowanceState.Failed -> {
                        Text(stringResource(R.string.allowance_failed))
                        AccordOutlinedButton(onClick = onAllowanceRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    is HomeAllowanceState.Ready -> AllowanceBody(allowance.value)
                }
                AccordOutlinedButton(onClick = onBudget) { Text(stringResource(R.string.allowance_budget)) }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                when (projection) {
                    HomeProjectionState.Loading -> Text(stringResource(R.string.projection_loading))
                    HomeProjectionState.Failed -> {
                        Text(stringResource(R.string.projection_failed))
                        AccordOutlinedButton(onClick = onProjectionRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    is HomeProjectionState.Ready -> ProjectionBody(projection.rows)
                }
                AccordOutlinedButton(onClick = onForecasts) { Text(stringResource(R.string.projection_open)) }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                when (upcoming) {
                    HomeUpcomingState.Loading -> Text(stringResource(R.string.upcoming_loading))
                    HomeUpcomingState.Failed -> {
                        Text(stringResource(R.string.upcoming_failed))
                        AccordOutlinedButton(onClick = onUpcomingRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    is HomeUpcomingState.Ready -> UpcomingBody(upcoming.events)
                }
                AccordOutlinedButton(onClick = onCommitments) { Text(stringResource(R.string.upcoming_open)) }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Text(stringResource(R.string.tab_review), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(pendingSummary(pending), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pending.failed) {
                    AccordOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                AccordOutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.home_open_review))
                }
            }
        }
    }
}
