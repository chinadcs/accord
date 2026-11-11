package app.accord.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import app.accord.AppContainer
import app.accord.R
import app.accord.core.analysis.ProductPhase
import app.accord.core.sync.AccessState
import app.accord.ui.alerts.AlertPreferencesViewModel
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.nav.ROUTE_ACCESS
import app.accord.ui.nav.ROUTE_ACCOUNTS
import app.accord.ui.nav.ROUTE_ACTIVITY
import app.accord.ui.nav.ROUTE_CARD
import app.accord.ui.nav.ROUTE_CATEGORIES
import app.accord.ui.nav.ROUTE_CHAT_SETTINGS
import app.accord.ui.nav.ROUTE_CLUES
import app.accord.ui.nav.ROUTE_COMMITMENTS
import app.accord.ui.nav.ROUTE_COMPARISON
import app.accord.ui.nav.ROUTE_FORECASTS
import app.accord.ui.nav.ROUTE_IMPORT_HUB
import app.accord.ui.nav.ROUTE_INCOMES
import app.accord.ui.nav.ROUTE_INTENTIONS
import app.accord.ui.nav.ROUTE_LIMITS
import app.accord.ui.nav.ROUTE_NOTIFICATIONS
import app.accord.ui.nav.ROUTE_PRIVACY_SECURITY
import app.accord.ui.nav.ROUTE_RECEIVABLES
import app.accord.ui.nav.ROUTE_REVIEW
import app.accord.ui.nav.ROUTE_SIMULATOR
import app.accord.ui.nav.ROUTE_SPACE
import app.accord.ui.nav.ROUTE_SUBSCRIPTIONS
import app.accord.ui.nav.ROUTE_SYNC
import app.accord.ui.nav.ROUTE_WEEKLY_REVIEW
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch

/**
 * Raiz da aba Mais: liga a tela às rotas, ao estado de acesso e aos seletores/diálogos do sistema. Sem chamada de rede ao abrir:
 * o cabeçalho usa só o espaço e a lista de participantes que o aparelho já guarda (ADR 0077).
 */
@Composable
internal fun MoreRoute(
    container: AppContainer,
    navController: NavHostController,
    padding: PaddingValues,
    reviewState: PendingQueueUiState,
    access: AccessState,
) {
    val scope = rememberCoroutineScope()
    val entriesCriterion by container.theme.entriesDateCriterion.collectAsStateWithLifecycle()
    val includeExpectedIncome by container.theme.includeExpectedIncomeInHomeSummary.collectAsStateWithLifecycle()
    val themePreference by container.theme.preference.collectAsStateWithLifecycle()
    val themePalette by container.theme.palette.collectAsStateWithLifecycle()
    var signOutPending by remember { mutableStateOf<Int?>(null) }
    val alertsViewModel: AlertPreferencesViewModel = viewModel(
        factory = viewModelFactory { initializer { AlertPreferencesViewModel(container, container.notifications) } },
    )
    val alertsState by alertsViewModel.state.collectAsStateWithLifecycle()
    // Voltar do hub Notificações (ou dos ajustes do sistema) atualiza o subtítulo da linha com a situação real.
    RefreshOnResume(alertsViewModel::refresh)
    val updatesState by container.updates.state.collectAsStateWithLifecycle()
    val apkState by container.apkUpdate.state.collectAsStateWithLifecycle()
    val p2 = container.phase.includes(ProductPhase.P2)
    MoreScreen(
        padding,
        actions = MoreActions(
            onSpace = { navController.navigate(ROUTE_SPACE) },
            onSync = { navController.navigate(ROUTE_SYNC) },
            onReview = { navController.navigate(ROUTE_REVIEW) },
            onImport = { navController.navigate(ROUTE_IMPORT_HUB) },
            onLimits = { navController.navigate(ROUTE_LIMITS) },
            onCategories = { navController.navigate(ROUTE_CATEGORIES) },
            onIncomes = { navController.navigate(ROUTE_INCOMES) },
            onCard = { navController.navigate(ROUTE_CARD) },
            onAccounts = { navController.navigate(ROUTE_ACCOUNTS) },
            onSavings = { navController.navigate("savings") },
            onCommitments = { navController.navigate(ROUTE_COMMITMENTS) },
            onReceivables = { navController.navigate(ROUTE_RECEIVABLES) },
            onSubscriptions = { navController.navigate(ROUTE_SUBSCRIPTIONS) },
            onForecasts = { navController.navigate(ROUTE_FORECASTS) },
            onIntentions = if (p2) ({ navController.navigate(ROUTE_INTENTIONS) }) else null,
            onSimulator = if (p2) ({ navController.navigate(ROUTE_SIMULATOR) }) else null,
            onWeeklyReview = { navController.navigate(ROUTE_WEEKLY_REVIEW) },
            onActivity = { navController.navigate(ROUTE_ACTIVITY) },
            onComparison = { navController.navigate(ROUTE_COMPARISON) },
            onClues = if (p2) ({ navController.navigate(ROUTE_CLUES) }) else null,
            onSignOut = {
                // Sempre uma decisão explícita, com o número de operações por enviar à vista.
                scope.launch {
                    val summary = container.access.runtime.value?.syncStore?.summary()
                    signOutPending = summary?.let { it.awaitingOperations + it.openConflicts + it.rejectedOperations } ?: 0
                }
            },
            onRenew = { navController.navigate(ROUTE_ACCESS) },
            onNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
            onChatSettings = { navController.navigate(ROUTE_CHAT_SETTINGS) },
            onAppearance = { navController.navigate("appearance") },
            onPrivacySecurity = { navController.navigate(ROUTE_PRIVACY_SECURITY) },
        ),
        state = MoreUiState(
            header = access.moreHeader(),
            review = reviewState,
            session = access.session,
            entriesCriterion = entriesCriterion,
            includeExpectedIncome = includeExpectedIncome,
            themePreference = themePreference,
            themePalette = themePalette,
            // Mesma conta do aviso ao sair: o que ainda não saiu do aparelho (por enviar, em conflito ou recusado).
            pendingOperations = app.accord.ui.sync.LocalSyncStatus.current?.summary
                ?.let { it.awaitingOperations + it.openConflicts + it.rejectedOperations } ?: 0,
            alerts = alertsState,
        ),
        onEntriesCriterionChange = container.theme::setEntriesDateCriterion,
        onIncludeExpectedIncomeChange = container.theme::setIncludeExpectedIncomeInHomeSummary,
        updates = updatesState,
        onCheckUpdates = { container.updates.check(manual = true) },
        installState = apkState.install,
        updateActions = container.updateActions,
    )
    signOutPending?.let { pending ->
        SignOutDialog(
            pending,
            onConfirm = { signOutPending = null; scope.launch { container.access.signOut(confirmPending = true) } },
            onDismiss = { signOutPending = null },
        )
    }
}

/**
 * Cabeçalho a partir do que o aparelho já sabe. Os participantes só contam quando a lista está em cache: sem ela o número é nulo,
 * nunca 1 nem 0. Sem conta, nulo (a tela mostra só o título).
 */
internal fun AccessState.moreHeader(): MoreHeaderState? = account?.let {
    MoreHeaderState(email = it.email, spaceName = space?.name, participants = participants?.items?.size)
}

@Composable
private fun SignOutDialog(pendingOperations: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.signout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.signout_body))
                if (pendingOperations > 0) {
                    Text(pluralStringResource(R.plurals.signout_pending, pendingOperations, pendingOperations), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.signout_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.signout_cancel)) } },
    )
}
