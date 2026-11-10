package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as Lucide
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
import app.accord.ui.components.SkeletonBlock
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

/** Início (ADR 0067, remodelado no ADR 0078): banner de falha, Saldo, Pede atenção, Margem, Previsão e rodapé. */
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
    val failures = homeFailures(pending, balance, upcoming, projection, allowance, cashFlow)
    RootScaffold(stringResource(R.string.tab_home), padding) {
        // Um só "Tentar de novo" para a tela: refaz apenas as leituras que falharam, sem recarregar o que já está pronto.
        HomeFailureBanner(failures, onRetry = {
            failures.forEach { block ->
                when (block) {
                    HomeBlock.Balance -> onBalanceRetry()
                    HomeBlock.Review -> onRetry()
                    HomeBlock.Dates -> onUpcomingRetry()
                    HomeBlock.Exceed -> onProjectionRetry()
                    HomeBlock.Allowance -> onAllowanceRetry()
                    HomeBlock.Forecast -> onCashFlowRetry()
                }
            }
        })
        BalanceSection(balance)
        AttentionStrip(
            pending, upcoming, projection,
            onReview = onReview, onCommitments = onCommitments, onForecasts = onForecasts,
        )
        AllowanceSummary(allowance, onBudget = onBudget)
        ForecastSummary(cashFlow, onBudget = onBudget)
        HomeFooter()
        // O botão flutuante de registro fica sobre o canto inferior; este espaço deixa o último bloco acessível.
        Spacer(Modifier.height(AccordSpacing.fabClearance))
    }
}

/** Rodapé único da Início (ADR 0078, decisão 5): substitui as notas repetidas dentro dos cartões. Ícone decorativo. */
@Composable
private fun HomeFooter() {
    Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(Lucide.drawable.lucide_ic_info), contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.home_footer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Saldo total do mês: esqueleto enquanto carrega, frase curta em falha (o "Tentar de novo" é do banner), nunca zero no lugar de "não sei". */
@Composable
private fun BalanceSection(state: HomeBalanceState) {
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
        HomeBalanceState.Failed -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.balance_failed), Modifier.fillMaxWidth().padding(AccordSpacing.s4))
        }
        is HomeBalanceState.Ready -> TotalBalanceCard(state.value)
    }
}
