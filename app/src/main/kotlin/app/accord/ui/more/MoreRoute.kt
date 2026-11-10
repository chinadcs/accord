package app.accord.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import app.accord.AppContainer
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.core.alerts.NotificationAccess
import app.accord.core.analysis.ProductPhase
import app.accord.core.chat.ChatImportResult
import app.accord.core.sync.AccessState
import app.accord.ui.alerts.AlertPreferencesViewModel
import app.accord.ui.alerts.rememberAllowNotifications
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.nav.ROUTE_ACCESS
import app.accord.ui.nav.ROUTE_ACCOUNTS
import app.accord.ui.nav.ROUTE_ACTIVITY
import app.accord.ui.nav.ROUTE_ALERT_PREFS
import app.accord.ui.nav.ROUTE_BANK_NOTIFICATIONS
import app.accord.ui.nav.ROUTE_CARD
import app.accord.ui.nav.ROUTE_CATEGORIES
import app.accord.ui.nav.ROUTE_CLUES
import app.accord.ui.nav.ROUTE_COMMITMENTS
import app.accord.ui.nav.ROUTE_COMPARISON
import app.accord.ui.nav.ROUTE_FORECASTS
import app.accord.ui.nav.ROUTE_IMPORT_HUB
import app.accord.ui.nav.ROUTE_INCOMES
import app.accord.ui.nav.ROUTE_INTENTIONS
import app.accord.ui.nav.ROUTE_LIMITS
import app.accord.ui.nav.ROUTE_MODEL
import app.accord.ui.nav.ROUTE_RECEIVABLES
import app.accord.ui.nav.ROUTE_REVIEW
import app.accord.ui.nav.ROUTE_SIMULATOR
import app.accord.ui.nav.ROUTE_SPACE
import app.accord.ui.nav.ROUTE_SUBSCRIPTIONS
import app.accord.ui.nav.ROUTE_SYNC
import app.accord.ui.nav.ROUTE_WEEKLY_REVIEW
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onRegister: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val entriesCriterion by container.theme.entriesDateCriterion.collectAsStateWithLifecycle()
    val themePreference by container.theme.preference.collectAsStateWithLifecycle()
    val themePalette by container.theme.palette.collectAsStateWithLifecycle()
    var signOutPending by remember { mutableStateOf<Int?>(null) }
    var chatDialog by remember { mutableStateOf<ChatHistoryDialog?>(null) }
    val resolver = LocalContext.current.contentResolver
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = container.exportChat()
            val saved = text != null && runCatching {
                withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("sem saída") }
            }.isSuccess
            container.feedback.post(FeedbackEvent(if (saved) R.string.chat_exported else R.string.chat_export_failed))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } } }.getOrNull()
            val result = text?.let { container.importChat(it) } ?: ChatImportResult.Unreadable
            container.feedback.post(
                when (result) {
                    is ChatImportResult.Imported ->
                        if (result.messages == 0) FeedbackEvent(R.string.chat_imported_none) else FeedbackEvent(R.string.chat_imported_count, listOf(result.messages))
                    ChatImportResult.OtherOwner -> FeedbackEvent(R.string.chat_import_other_owner)
                    ChatImportResult.Unreadable -> FeedbackEvent(R.string.chat_import_unreadable)
                },
            )
        }
    }
    val alertsViewModel: AlertPreferencesViewModel = viewModel(
        factory = viewModelFactory { initializer { AlertPreferencesViewModel(container, container.notifications) } },
    )
    val alertsState by alertsViewModel.state.collectAsStateWithLifecycle()
    // Voltar dos ajustes do sistema atualiza a situação real das notificações.
    RefreshOnResume(alertsViewModel::refresh)
    val allowNotifications = rememberAllowNotifications(container.notifications, onDone = alertsViewModel::refresh)
    val updatesState by container.updates.state.collectAsStateWithLifecycle()
    val apkState by container.apkUpdate.state.collectAsStateWithLifecycle()
    val p2 = container.phase.includes(ProductPhase.P2)
    MoreScreen(
        padding,
        actions = MoreActions(
            onSpace = { navController.navigate(ROUTE_SPACE) },
            onRegister = onRegister,
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
            onAlertsToggle = { enabled ->
                alertsViewModel.toggleNow(enabled)
                // Ligar sem permissão leva direto ao pedido do sistema (ou aos ajustes), sem esconder o que falta.
                if (enabled && alertsState.access != NotificationAccess.Granted) allowNotifications()
            },
            onAlertsAllow = allowNotifications,
            onAlertPreferences = { navController.navigate(ROUTE_ALERT_PREFS) },
            onBankNotifications = { navController.navigate(ROUTE_BANK_NOTIFICATIONS) },
            onArchivedChats = if (access.archivedSpaces.isNotEmpty()) ({ navController.navigate("archived_chats") }) else null,
            onChatModel = { navController.navigate(ROUTE_MODEL) },
            onChatExport = { chatDialog = ChatHistoryDialog.Export },
            onChatImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onChatClear = { chatDialog = ChatHistoryDialog.Clear },
            onAppearance = { navController.navigate("appearance") },
            onSecurity = { navController.navigate("security") },
            onPrivacy = { navController.navigate("privacy") },
        ),
        state = MoreUiState(
            header = access.moreHeader(),
            review = reviewState,
            session = access.session,
            entriesCriterion = entriesCriterion,
            themePreference = themePreference,
            themePalette = themePalette,
            // Mesma conta do aviso ao sair: o que ainda não saiu do aparelho (por enviar, em conflito ou recusado).
            pendingOperations = app.accord.ui.sync.LocalSyncStatus.current?.summary
                ?.let { it.awaitingOperations + it.openConflicts + it.rejectedOperations } ?: 0,
            alerts = alertsState,
        ),
        onEntriesCriterionChange = container.theme::setEntriesDateCriterion,
        updates = updatesState,
        onCheckUpdates = { container.updates.check(manual = true) },
        installState = apkState.install,
        updateActions = container.updateActions,
    )
    chatDialog?.let { dialog ->
        ChatHistoryDialogView(
            dialog,
            onConfirm = {
                chatDialog = null
                when (dialog) {
                    ChatHistoryDialog.Export -> exportLauncher.launch("historico-do-chat.json")
                    ChatHistoryDialog.Clear -> scope.launch {
                        container.clearChat()
                        container.feedback.post(FeedbackEvent(R.string.chat_cleared))
                    }
                }
            },
            onDismiss = { chatDialog = null },
        )
    }
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

private enum class ChatHistoryDialog { Export, Clear }

@Composable
private fun ChatHistoryDialogView(dialog: ChatHistoryDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val export = dialog == ChatHistoryDialog.Export
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (export) R.string.chat_export_title else R.string.chat_clear_title)) },
        text = { Text(stringResource(if (export) R.string.chat_export_body else R.string.chat_clear_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (export) R.string.chat_export_confirm else R.string.chat_clear_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.chat_dialog_cancel)) } },
    )
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
