package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.RootScaffold
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.components.SkeletonBlock
import app.accord.ui.components.SkeletonLines
import app.accord.ui.entries.TotalBalanceCard
import app.accord.ui.theme.Accord
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
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
    cashFlow: HomeCashFlowState = HomeCashFlowState.Loading,
    onCashFlowRetry: () -> Unit = {},
    balance: HomeBalanceState = HomeBalanceState.Loading,
    onBalanceRetry: () -> Unit = {},
) {
    RootScaffold(stringResource(R.string.tab_home), padding) {
        BalanceSection(balance, onBalanceRetry)
        AttentionStrip(
            pending, upcoming, projection,
            onReview = onReview, onRetryPending = onRetry,
            onCommitments = onCommitments, onRetryUpcoming = onUpcomingRetry,
            onForecasts = onForecasts, onRetryProjection = onProjectionRetry,
        )
        AllowanceSummary(allowance, onBudget = onBudget, onRetry = onAllowanceRetry)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                when (cashFlow) {
                    HomeCashFlowState.Loading -> SkeletonLines(stringResource(R.string.cashflow_loading), lines = 4)
                    HomeCashFlowState.Failed -> {
                        Text(stringResource(R.string.cashflow_failed))
                        AccordOutlinedButton(onClick = onCashFlowRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    is HomeCashFlowState.Ready -> CashFlowBody(cashFlow.value)
                }
                AccordOutlinedButton(onClick = onBudget) { Text(stringResource(R.string.cashflow_open)) }
            }
        }
        // O botão flutuante de registro fica sobre o canto inferior; este espaço deixa o último cartão acessível.
        Spacer(Modifier.height(AccordSpacing.fabClearance))
    }
}

/** Saldo total do mês: esqueleto enquanto carrega, mensagem com repetir em falha, nunca zero no lugar de "não sei". */
@Composable
private fun BalanceSection(state: HomeBalanceState, onRetry: () -> Unit) {
    when (state) {
        HomeBalanceState.Loading -> {
            val description = stringResource(R.string.balance_loading)
            Surface(color = Accord.colors.financeSurface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s5)
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
                ) {
                    SkeletonBlock(height = 18.dp, widthFraction = 0.35f)
                    SkeletonBlock(height = 36.dp, widthFraction = 0.6f)
                    SkeletonBlock(height = 18.dp, widthFraction = 0.8f)
                }
            }
        }
        HomeBalanceState.Failed -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Text(stringResource(R.string.balance_failed))
                AccordOutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        is HomeBalanceState.Ready -> TotalBalanceCard(state.value)
    }
}
