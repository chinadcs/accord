package app.accord.ui

import app.accord.ui.components.accordDialogColor
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.accord.AppContainer
import app.accord.UndoOutcome
import app.accord.R
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntrySituation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import app.accord.core.sync.EntryStage
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncOutcome
import app.accord.ui.access.AccessRoute
import app.accord.ui.access.AccessViewModel
import app.accord.ui.alerts.AlertPreferencesRoute
import app.accord.ui.alerts.AlertPreferencesViewModel
import app.accord.ui.alerts.rememberAllowNotifications
import app.accord.core.alerts.NotificationAccess
import app.accord.ui.components.LocalPullSync
import app.accord.ui.components.PullToRefresh
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.firststeps.FirstStepsRoute
import app.accord.ui.firststeps.FirstStepsViewModel
import app.accord.ui.firststeps.StepActions
import app.accord.ui.nav.ROUTE_ACCESS
import app.accord.ui.nav.ROUTE_CONFLICT
import app.accord.ui.nav.ROUTE_SYNC
import app.accord.ui.imports.AndroidPdfTextExtractor
import app.accord.ui.imports.ContentResolverOpener
import app.accord.ui.imports.ImportFileRoute
import app.accord.ui.imports.ImportFileViewModel
import app.accord.ui.imports.ImportHub
import app.accord.ui.imports.ImportHubViewModel
import app.accord.ui.imports.ImportResultRoute
import app.accord.ui.imports.ImportResultViewModel
import app.accord.ui.imports.ImportReviewRoute
import app.accord.ui.imports.ImportReviewViewModel
import app.accord.ui.pending.PendingHubCard
import app.accord.ui.pending.PendingQueueRoute
import app.accord.ui.pending.PendingQueueViewModel
import app.accord.ui.pending.PendingResolveRoute
import app.accord.ui.pending.PendingResolveViewModel
import app.accord.ui.review.ReviewCategories
import app.accord.ui.sync.ConflictRoute
import app.accord.ui.sync.ConflictViewModel
import app.accord.ui.sync.LocalOpenSync
import app.accord.ui.sync.LocalSyncStatus
import app.accord.ui.sync.ReviewConflicts
import app.accord.ui.sync.ReviewViewModel
import app.accord.ui.sync.SyncRoute
import app.accord.ui.sync.SyncViewModel
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.components.RootNavigationBar
import androidx.compose.runtime.CompositionLocalProvider
import app.accord.core.sync.ConnectionState
import app.accord.ui.forecast.ForecastDetailActions
import app.accord.ui.forecast.ForecastDetailRoute
import app.accord.ui.forecast.ForecastDetailViewModel
import app.accord.ui.forecast.ForecastsActions
import app.accord.ui.forecast.ForecastsRoute
import app.accord.ui.forecast.ForecastsViewModel
import app.accord.ui.forecast.ReasonDestination
import app.accord.ui.nav.ROUTE_ALERT_PREFS
import app.accord.ui.nav.ROUTE_BANK_NOTIFICATIONS
import app.accord.ui.nav.ROUTE_NOTIFICATIONS
import app.accord.ui.banknotifications.BankNotificationsRoute
import app.accord.ui.banknotifications.BankNotificationsViewModel
import app.accord.ui.nav.ROUTE_FORECAST
import app.accord.ui.nav.ROUTE_FORECASTS
import app.accord.ui.nav.ROUTE_FIRST_STEPS
import app.accord.ui.nav.ROUTE_SPACE
import app.accord.ui.space.SpaceRoute
import app.accord.ui.space.SpaceViewModel
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.isCompactHeight
import app.accord.ui.budget.BudgetRoute
import app.accord.ui.budget.BudgetViewModel
import app.accord.ui.category.CategoryRoute
import app.accord.ui.category.CategoryViewModel
import app.accord.ui.detail.EntryDetailRoute
import app.accord.ui.detail.EntryDetailViewModel
import app.accord.ui.card.CardRoute
import app.accord.ui.card.CardViewModel
import app.accord.ui.card.InvoiceRoute
import app.accord.ui.card.InvoiceViewModel
import app.accord.ui.commitments.CommitmentDetailRoute
import app.accord.ui.commitments.CommitmentDetailViewModel
import app.accord.ui.commitments.CommitmentsRoute
import app.accord.ui.commitments.CommitmentsViewModel
import app.accord.ui.edit.EditEntryRoute
import app.accord.ui.edit.EditEntryViewModel
import app.accord.ui.entries.EntriesFilterState
import app.accord.ui.entries.EntriesRoute
import app.accord.ui.entries.EntriesViewModel
import app.accord.ui.nav.ARG_FILTER_CATEGORY
import app.accord.ui.nav.ARG_FILTER_MONTH
import app.accord.ui.nav.ARG_FILTER_SITUATION
import app.accord.ui.nav.ROUTE_CATEGORY
import app.accord.ui.nav.ROUTE_ENTRIES_FILTERED
import app.accord.ui.nav.ROUTE_ENTRY
import app.accord.ui.nav.ARG_MONTH
import app.accord.ui.nav.ROUTE_ACCOUNTS
import app.accord.ui.nav.ROUTE_CATEGORIES
import app.accord.ui.nav.ROUTE_CATEGORY_MOVE
import app.accord.ui.categories.CategoryMoveRoute
import app.accord.ui.categories.CategoryMoveViewModel
import app.accord.ui.nav.ROUTE_LIMITS
import app.accord.ui.nav.ARG_REGISTER_CHAT
import app.accord.ui.nav.ARG_REGISTER_FORM
import app.accord.ui.nav.ARG_REGISTER_KIND
import app.accord.ui.nav.ROUTE_CHAT_SETTINGS
import app.accord.ui.nav.ROUTE_MODEL
import app.accord.ui.nav.ROUTE_PRIVACY_SECURITY
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.accord.FeedbackEvent
import app.accord.core.chat.ChatImportResult
import app.accord.ui.model.ModelActions
import app.accord.ui.model.ModelRoute
import app.accord.ui.model.ModelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatRoute
import app.accord.ui.chat.ChatViewModel
import app.accord.ui.nav.ROUTE_CARD
import app.accord.ui.nav.ROUTE_COMMITMENT
import app.accord.ui.nav.ROUTE_COMMITMENTS
import app.accord.ui.nav.ROUTE_SIMULATOR
import app.accord.ui.nav.ROUTE_INTENTIONS
import app.accord.ui.nav.ROUTE_CLUES
import app.accord.ui.nav.ROUTE_CLUE
import app.accord.ui.clues.CluesViewModel
import app.accord.ui.clues.CluesRoute
import app.accord.ui.clues.ClueDetailRoute
import app.accord.ui.nav.ROUTE_INTENTION
import app.accord.ui.intentions.IntentionRoute
import app.accord.ui.intentions.IntentionViewModel
import app.accord.ui.intentions.IntentionsRoute
import app.accord.ui.intentions.IntentionsViewModel
import app.accord.ui.simulator.SimulatorRoute
import app.accord.core.simulation.ScenarioArg
import app.accord.ui.simulator.SimulatorViewModel
import app.accord.core.analysis.ProductPhase
import app.accord.ui.nav.ARG_FILTER_CRITERION
import app.accord.ui.nav.ROUTE_ACTIVITY
import app.accord.ui.nav.ROUTE_COMPARISON
import app.accord.ui.nav.ROUTE_WEEKLY_REVIEW
import app.accord.ui.weekly.PrefsWeeklyReviewStore
import app.accord.ui.weekly.WeeklyReviewActions
import app.accord.ui.weekly.WeeklyReviewRoute
import app.accord.ui.weekly.WeeklyReviewViewModel
import app.accord.ui.comparison.ComparisonActions
import app.accord.ui.comparison.ComparisonRoute
import app.accord.ui.comparison.ComparisonViewModel
import app.accord.ui.nav.ROUTE_SUBSCRIPTION
import app.accord.ui.activity.ActivityActions
import app.accord.ui.activity.PurchaseActivityRoute
import app.accord.ui.activity.PurchaseActivityViewModel
import app.accord.ui.nav.ROUTE_SUBSCRIPTIONS
import app.accord.ui.subscriptions.SubscriptionDetailRoute
import app.accord.ui.subscriptions.SubscriptionDetailViewModel
import app.accord.ui.subscriptions.SubscriptionsRoute
import app.accord.ui.subscriptions.SubscriptionsViewModel
import app.accord.ui.nav.ROUTE_EDIT
import app.accord.ui.nav.ROUTE_PENDING
import app.accord.ui.nav.ROUTE_PENDING_ITEM
import app.accord.ui.nav.ROUTE_IMPORT
import app.accord.ui.nav.ROUTE_IMPORT_HUB
import app.accord.ui.nav.ROUTE_IMPORT_RESULT
import app.accord.ui.nav.ROUTE_IMPORT_REVIEW
import app.accord.ui.nav.ROUTE_INCOMES
import app.accord.ui.nav.ROUTE_RECEIVABLES
import app.accord.ui.nav.ROUTE_RECEIVABLE_NEW
import app.accord.ui.nav.ROUTE_INVOICE
import app.accord.ui.nav.ROUTE_REGISTER
import app.accord.ui.nav.Root
import app.accord.ui.nav.ROUTE_REVIEW
import app.accord.ui.categories.CategoriesRoute
import app.accord.ui.categories.CategoriesViewModel
import app.accord.ui.incomes.IncomesRoute
import app.accord.ui.incomes.IncomesViewModel
import app.accord.ui.limits.LimitsRoute
import app.accord.ui.limits.LimitsViewModel
import app.accord.ui.more.AccountsRoute
import app.accord.ui.more.AccountsViewModel
import app.accord.ui.more.MoreRoute
import app.accord.ui.more.AppearanceScreen
import app.accord.ui.theme.Accord
import com.composables.icons.lucide.R as Lucide
import app.accord.ui.register.RegisterRoute
import app.accord.ui.register.RegisterViewModel
import app.accord.ui.register.RegisterForm
import app.accord.ui.register.FormKind
import app.accord.ui.register.ExpenseForm
import app.accord.ui.register.FormCodec
import app.accord.core.space.AmountMeaning
import app.accord.core.space.EntityType
import java.time.YearMonth

/**
 * Raiz do aplicativo: o estágio de entrada decide a tela. Sem conta, Acesso (01); com conta e sem
 * espaço, Espaço da casa (02); com espaço novo, Primeiros passos (03); depois, o aplicativo.
 */
@Composable
fun AccordApp(container: AppContainer) {
    val access by container.access.status.collectAsStateWithLifecycle()
    var utility by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(access.account?.accountId) { utility = null }
    // O fluxo de espaço continua na tela 02 depois de criar ou entrar, até a pessoa seguir adiante.
    var spaceIntro by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(access.stage) {
        when (access.stage) {
            EntryStage.NoSpace -> spaceIntro = true
            EntryStage.SignedOut -> spaceIntro = false
            else -> Unit
        }
    }
    when {
        access.stage == EntryStage.Loading -> LoadingGate()
        access.accountDeletionPending || access.accountDeletionCompleted -> app.accord.privacy.AccountDeletionScreen(container, access.accountDeletionCompleted)
        access.stage == EntryStage.SignedOut -> AccessGate(container)
        access.session == SessionState.NeedsRenewal -> AccessGate(container, renewal = true)
        utility != null && access.account != null -> Scaffold { padding ->
            if (utility == "archive") app.accord.privacy.ArchivedChatsScreen(padding, container) { utility = null }
            else {
                val vm: app.accord.privacy.PrivacyViewModel = viewModel(key = "privacy-${access.account?.accountId}", factory = viewModelFactory {
                    initializer { app.accord.privacy.PrivacyViewModel(container) }
                })
                app.accord.privacy.PrivacyScreen(padding, vm) { utility = null }
            }
        }
        access.stage == EntryStage.NoSpace || spaceIntro -> SpaceGate(container, onContinue = { spaceIntro = false },
            onPrivacy = { utility = "privacy" }, onArchive = if (access.archivedSpaces.isNotEmpty()) ({ utility = "archive" }) else null)
        else -> {
            // Fixada na primeira composição: trocar a rota inicial depois recriaria o grafo de navegação.
            val startRoute = remember { if (access.stage == EntryStage.FirstSteps) ROUTE_FIRST_STEPS else Root.Home.graph }
            MainApp(container, startRoute)
        }
    }
}

@Composable
private fun LoadingGate() {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.access_loading), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = AccordSpacing.s3))
        }
    }
}

@Composable
private fun AccessGate(container: AppContainer, renewal: Boolean = false) {
    Scaffold { padding ->
        val viewModel: AccessViewModel = viewModel(key = "access-gate-$renewal", factory = viewModelFactory { initializer { AccessViewModel(container.access, renewal = renewal) } })
        AccessRoute(viewModel, padding)
    }
}

@Composable
private fun SpaceGate(container: AppContainer, onContinue: () -> Unit, onPrivacy: () -> Unit, onArchive: (() -> Unit)?) {
    Scaffold { padding ->
        val viewModel: SpaceViewModel = viewModel(factory = viewModelFactory { initializer { SpaceViewModel(container.access) } })
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_title)) }
            onArchive?.let { action -> TextButton(onClick = action) { Text(stringResource(R.string.privacy_archived_chats)) } }
            Box(Modifier.weight(1f)) { SpaceRoute(viewModel, androidx.compose.foundation.layout.PaddingValues(), firstRun = true, onContinue = onContinue) }
        }
    }
}

@Composable
private fun MainApp(container: AppContainer, startRoute: String) {
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openRoot: (Root) -> Unit = { root ->
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        navController.openRoot(root)
    }
    val access by container.access.status.collectAsStateWithLifecycle()
    val syncStatus by container.sync.status.collectAsStateWithLifecycle()
    val reviewSummary: PendingQueueViewModel = viewModel(key = "home-pendings", factory = viewModelFactory {
        initializer { PendingQueueViewModel(container, container.sync, container.chat, androidx.lifecycle.SavedStateHandle()) }
    })
    val reviewState by reviewSummary.state.collectAsStateWithLifecycle()
    RefreshOnResume(reviewSummary::refresh)
    val coordinator by container.chat.collectAsStateWithLifecycle()
    LaunchedEffect(coordinator) {
        coordinator?.snapshot?.map { it.questions }?.distinctUntilChanged()?.collect { reviewSummary.refresh() }
    }
    val pendingAlert by container.pendingAlertEvent.collectAsStateWithLifecycle()
    // Aviso tocado: pilha Orçamento, Previsões, Detalhe. Evento de outro espaço ou conta é ignorado e abre só as Previsões; com a sessão encerrada, espera entrar.
    LaunchedEffect(pendingAlert, access.stage) {
        val id = pendingAlert ?: return@LaunchedEffect
        if (access.stage != EntryStage.Ready) return@LaunchedEffect
        container.consumeAlertEvent()
        val event = (container.space().alertEvent(id) as? app.accord.core.space.QueryResult.Data)?.value
        openRoot(Root.Budget)
        navController.navigate("$ROUTE_FORECASTS?$ARG_MONTH=${event?.month ?: container.clock.currentMonth()}")
        if (event != null) {
            navController.navigate("$ROUTE_FORECAST/${Uri.encode(event.categoryId)}/${event.month}?${ForecastDetailViewModel.ARG_EVENT}=${Uri.encode(id)}")
        }
    }
    val pendingShare by container.pendingShare.collectAsStateWithLifecycle()
    // "Compartilhar" de outro app (accord-jcd9): só depois de entrar. Texto vai para o campo do Chat, que o toma e o deixa sem enviar;
    // arquivo vai para a leitura da importação, que o consome. Nenhum dos dois grava nada por aqui.
    LaunchedEffect(pendingShare, access.stage) {
        val share = pendingShare ?: return@LaunchedEffect
        if (access.stage != EntryStage.Ready) return@LaunchedEffect
        when (share) {
            is app.accord.share.SharedContent.Text -> openRoot(Root.Chat)
            is app.accord.share.SharedContent.File ->
                if (navController.currentDestination?.route != ROUTE_IMPORT) navController.navigate(ROUTE_IMPORT) { launchSingleTop = true }
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    LaunchedEffect(entry, syncStatus) { reviewSummary.refresh() }
    // A barra inferior só aparece nas raízes; detalhes e formulários escondem-na.
    val onRoot = Root.entries.any { destination?.route == it.start }
    val selectedRoot = Root.entries.firstOrNull { root -> destination?.hierarchy?.any { it.route == root.graph } == true }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // O Chat já oferece registro manual abaixo do campo; ali um FAB cobriria o compositor.
    // Fonte grande ou paisagem (altura < 480 dp): o registro vai para o fluxo da raiz Lançamentos, sem FAB (spec 07).
    val largeFont = LocalDensity.current.fontScale >= 1.5f || isCompactHeight()
    val showAdd = !imeVisible && onRoot && selectedRoot in setOf(Root.Home, Root.Budget, Root.Entries) &&
        !(selectedRoot == Root.Entries && largeFont)
    var registerSpeedDialOpen by rememberSaveable { mutableStateOf(false) }
    var registerChooserOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedRoot) {
        registerSpeedDialOpen = false
        registerChooserOpen = false
    }

    fun openManualRegistration(kind: String, form: String? = null) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        registerSpeedDialOpen = false
        registerChooserOpen = false
        val route = buildString {
            append("$ROUTE_REGISTER?$ARG_REGISTER_KIND=$kind")
            form?.let { append("&$ARG_REGISTER_FORM=$it") }
        }
        navController.navigate(route)
    }
    /** "Assumir compromisso" (S7): abre o registro manual já preenchido, com a intenção de origem e a fatura simulada; só salvar cria o compromisso. */
    fun conversionRoute(form: RegisterForm, simulatedInvoice: java.time.YearMonth?): String = buildString {
        append("$ROUTE_REGISTER?$ARG_REGISTER_FORM=${Uri.encode(FormCodec.encode(form))}")
        simulatedInvoice?.let { append("&${RegisterViewModel.ARG_SIM_INVOICE}=$it") }
    }
    val openEntriesRegistration: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        registerChooserOpen = true
    }

    // Avisos depois de salvar, com Desfazer; ficam aqui para sobreviver à troca de tela.
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    // O aviso da ação anterior não cobre o botão Salvar do formulário que abre em seguida.
    val formRoute = destination?.route?.substringBefore('?')?.substringBefore('/')
    val inForm = formRoute in setOf(ROUTE_REGISTER, ROUTE_EDIT, ROUTE_CARD, ROUTE_ACCOUNTS, ROUTE_CATEGORIES, ROUTE_CATEGORY_MOVE, ROUTE_LIMITS, ROUTE_FIRST_STEPS, ROUTE_ALERT_PREFS, ROUTE_SIMULATOR, ROUTE_INTENTION, ROUTE_RECEIVABLE_NEW)
    // Ao abrir um formulário novo (de fora ou de outro formulário) o aviso some; voltar de uma tela de edição para a lista
    // (também formulário, já aberta antes) não apaga o aviso da ação que acabou de salvar.
    var cameFromForm by remember { mutableStateOf(false) }
    val seenEntries = remember { mutableSetOf<String>() }
    LaunchedEffect(entry?.id) {
        val opened = entry?.id?.let(seenEntries::add) == true
        if (inForm && (!cameFromForm || opened)) snackbar.currentSnackbarData?.dismiss()
        cameFromForm = inForm
    }
    LaunchedEffect(container) {
        container.feedback.events.collect { event ->
            launch {
                // O recibo diz só o que é verdade: salvo neste aparelho e, se der para enviar, aguardando envio.
                val messageRes = if (event.messageRes == R.string.feedback_saved) {
                    val status = container.sync.status.value
                    if (status != null && status.session == SessionState.Valid && status.connection != ConnectionState.Offline) {
                        R.string.feedback_saved_awaiting
                    } else {
                        R.string.feedback_saved_local
                    }
                } else {
                    event.messageRes
                }
                val message = resources.getString(messageRes, *event.args.toTypedArray())
                val undoLabel = event.undoOperationId?.let { resources.getString(R.string.action_undo) }
                // O Desfazer visível é sempre o da última ação: o aviso antigo não fica esperando na fila.
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(message, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
                val operationId = event.undoOperationId
                if (result == SnackbarResult.ActionPerformed && operationId != null) {
                    val text = when (container.undo(operationId)) {
                        UndoOutcome.Done -> R.string.undo_done
                        UndoOutcome.ChangedAfter -> R.string.undo_changed_after
                        UndoOutcome.Failed -> R.string.undo_failed
                    }
                    snackbar.showSnackbar(resources.getString(text))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (showAdd) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    if (registerSpeedDialOpen) {
                        SpeedDialAction(
                            stringResource(R.string.register_speed_dial_income), Lucide.drawable.lucide_ic_banknote_arrow_up,
                        ) { openManualRegistration("income") }
                        SpeedDialAction(
                            stringResource(R.string.register_speed_dial_expense), Lucide.drawable.lucide_ic_arrow_down_left,
                        ) { openManualRegistration("expense", "pix") }
                        SpeedDialAction(
                            stringResource(R.string.register_speed_dial_card_expense), Lucide.drawable.lucide_ic_credit_card,
                        ) { openManualRegistration("expense", "card") }
                    }
                    FloatingActionButton(
                        onClick = { registerSpeedDialOpen = !registerSpeedDialOpen },
                        modifier = Modifier.testTag("root-register-fab").semantics {
                            contentDescription = resources.getString(
                                if (registerSpeedDialOpen) R.string.register_speed_dial_close else R.string.register_speed_dial_open,
                            )
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            painterResource(if (registerSpeedDialOpen) Lucide.drawable.lucide_ic_x else Lucide.drawable.lucide_ic_plus),
                            contentDescription = null,
                        )
                    }
                }
            }
        },
        topBar = {
            // Sessão vencida: os dados e a fila ficam; só a pessoa pode entrar de novo, com a mesma conta.
            if (access.session == SessionState.NeedsRenewal && destination?.route != ROUTE_ACCESS) {
                RenewalBanner(onRenew = { navController.navigate(ROUTE_ACCESS) })
            }
        },
        bottomBar = {
            if (onRoot) {
                Box(Modifier.imePadding()) { RootNavigationBar(selectedRoot, openRoot, reviewState.items.isNotEmpty()) }
            }
        },
    ) { padding ->
        CompositionLocalProvider(
            LocalSyncStatus provides syncStatus,
            LocalOpenSync provides { navController.navigate(ROUTE_SYNC) },
            LocalPullSync provides {
                // Sem rede ou sem sessão a leitura local segue valendo: só se diz que ficou no aparelho.
                if (container.sync.syncNow() is SyncOutcome.Stopped) container.feedback.post(FeedbackEvent(R.string.pull_refresh_offline))
            },
        ) {
        val transitionMs = Accord.motion.navigation
        NavHost(
            navController, startDestination = startRoute,
            enterTransition = { fadeIn(tween(transitionMs)) },
            exitTransition = { fadeOut(tween(transitionMs)) },
            popEnterTransition = { fadeIn(tween(transitionMs)) },
            popExitTransition = { fadeOut(tween(transitionMs)) },
        ) {
            val register = { navController.navigate(ROUTE_REGISTER) }
            val openEntry = { id: String -> navController.navigate("$ROUTE_ENTRY/${Uri.encode(id)}") }

            navigation(startDestination = Root.Home.start, route = Root.Home.graph) {
                composable(Root.Home.start) {
                    val home: app.accord.ui.home.HomeAllowanceViewModel = viewModel(factory = viewModelFactory {
                        initializer { app.accord.ui.home.HomeAllowanceViewModel(container) }
                    })
                    val upcomingModel: app.accord.ui.home.HomeUpcomingViewModel = viewModel(factory = viewModelFactory {
                        initializer { app.accord.ui.home.HomeUpcomingViewModel(container) }
                    })
                    val projectionModel: app.accord.ui.home.HomeProjectionViewModel = viewModel(factory = viewModelFactory {
                        initializer { app.accord.ui.home.HomeProjectionViewModel(container) }
                    })
                    val cashFlowModel: app.accord.ui.home.HomeCashFlowViewModel = viewModel(factory = viewModelFactory {
                        initializer { app.accord.ui.home.HomeCashFlowViewModel(container) }
                    })
                    val balanceModel: app.accord.ui.home.HomeBalanceViewModel = viewModel(factory = viewModelFactory {
                        initializer { app.accord.ui.home.HomeBalanceViewModel(container) { container.theme.entriesDateCriterion.value } }
                    })
                    val balance by balanceModel.state.collectAsStateWithLifecycle()
                    val balanceMonth by balanceModel.month.collectAsStateWithLifecycle()
                    val includeExpectedIncome by container.theme.includeExpectedIncomeInHomeSummary.collectAsStateWithLifecycle()
                    val allowance by home.state.collectAsStateWithLifecycle()
                    val upcoming by upcomingModel.state.collectAsStateWithLifecycle()
                    val projection by projectionModel.state.collectAsStateWithLifecycle()
                    val cashFlow by cashFlowModel.state.collectAsStateWithLifecycle()
                    val refreshHome = { home.refresh(); upcomingModel.refresh(); projectionModel.refresh(); cashFlowModel.refresh(); balanceModel.refresh() }
                    RefreshOnResume(refreshHome)
                    LaunchedEffect(syncStatus) { refreshHome() }
                    LaunchedEffect(home) {
                        kotlinx.coroutines.flow.merge(container.feedback.changed, container.feedback.undone).collect { refreshHome() }
                    }
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    LaunchedEffect(home, lifecycleOwner) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                            var date = container.clock.today()
                            while (true) {
                                kotlinx.coroutines.delay(30_000)
                                val now = container.clock.today()
                                if (now != date) { date = now; refreshHome() }
                            }
                        }
                    }
                    PullToRefresh({ reviewSummary.refresh(); refreshHome() }) {
                        app.accord.ui.home.HomeScreen(padding, reviewState, reviewSummary::refresh,
                            onReview = { navController.navigate(ROUTE_REVIEW) }, allowance = allowance,
                            onAllowanceRetry = home::refresh, onBudget = { openRoot(Root.Budget) },
                            upcoming = upcoming, onUpcomingRetry = upcomingModel::refresh,
                            onCommitments = { navController.navigate(ROUTE_COMMITMENTS) },
                            cashFlow = cashFlow, onCashFlowRetry = cashFlowModel::refresh,
                            balance = balance, onBalanceRetry = balanceModel::refresh,
                            includeExpectedIncome = includeExpectedIncome,
                            balanceMonth = balanceMonth, currentMonth = container.clock.currentMonth(), onBalanceMonthShift = balanceModel::shiftMonth,
                            onRefreshed = { container.feedback.post(FeedbackEvent(R.string.home_refreshed)) },
                            projection = projection, onProjectionRetry = projectionModel::refresh,
                            onForecasts = { navController.navigate("$ROUTE_FORECASTS?$ARG_MONTH=${container.clock.currentMonth()}") })
                    }
                }
            }
            navigation(startDestination = Root.Chat.start, route = Root.Chat.graph) {
                composable(Root.Chat.start) {
                    val viewModel: ChatViewModel = viewModel(
                        factory = viewModelFactory { initializer { ChatViewModel(container, createSavedStateHandle()) } },
                    )
                    ChatRoute(
                        viewModel, padding, container.clock.today(),
                        ChatActions(
                            onOpenModel = { navController.navigate(ROUTE_MODEL) },
                            onOpenEntry = openEntry,
                            onEditEntry = { id -> navController.navigate("$ROUTE_EDIT/${Uri.encode(id)}") },
                            onManual = { route -> navController.navigate(route) },
                            onOpenBudget = { openRoot(Root.Budget) },
                            onOpenEntries = { openRoot(Root.Entries) },
                            onOpenCard = { navController.navigate(ROUTE_CARD) },
                            onOpenFilteredEntries = { month, categoryId, criterion -> navController.openFilteredEntries(month, categoryId, null, criterion) },
                            onOpenCommitments = { month -> navController.navigate("$ROUTE_COMMITMENTS?$ARG_MONTH=$month") },
                            onOpenCommitment = { id -> navController.navigate("$ROUTE_COMMITMENT/${Uri.encode(id)}") },
                            onRefreshQuery = viewModel::refreshQuery,
                            onSendQuestion = viewModel::sendQuestion,
                        ),
                    )
                }
            }
            navigation(startDestination = Root.Budget.start, route = Root.Budget.graph) {
                composable(Root.Budget.start) {
                    val viewModel: BudgetViewModel = viewModel(
                        factory = viewModelFactory { initializer { BudgetViewModel(container, createSavedStateHandle()) } },
                    )
                    val countExpectedIncome by container.theme.includeExpectedIncomeInHomeSummary.collectAsStateWithLifecycle()
                    PullToRefresh(viewModel::refresh) {
                        BudgetRoute(
                            viewModel, padding, countExpectedIncome = countExpectedIncome,
                            onOpenCategory = { id, month -> navController.navigate("$ROUTE_CATEGORY/$id/$month") },
                            onClassify = { month -> navController.openFilteredEntries(month, null, EntrySituation.ToClassify) },
                            onRegister = register,
                            onOpenCommitments = { month -> navController.navigate("$ROUTE_COMMITMENTS?$ARG_MONTH=$month") },
                            onOpenIncomes = { month -> navController.navigate("$ROUTE_INCOMES?$ARG_MONTH=$month") },
                            onOpenCard = { navController.navigate(ROUTE_CARD) },
                            onOpenForecasts = { month -> navController.navigate("$ROUTE_FORECASTS?$ARG_MONTH=$month") },
                            onEditLimits = { month -> navController.navigate("$ROUTE_LIMITS?$ARG_MONTH=$month") },
                            onConfirmLinks = { month -> navController.openFilteredEntries(month, null, EntrySituation.InvoiceLinkToConfirm) },
                        )
                    }
                }
                composable(
                    "$ROUTE_CATEGORY/{${CategoryViewModel.ARG_CATEGORY}}/{${CategoryViewModel.ARG_MONTH}}",
                    arguments = listOf(
                        navArgument(CategoryViewModel.ARG_CATEGORY) { type = NavType.StringType },
                        navArgument(CategoryViewModel.ARG_MONTH) { type = NavType.StringType },
                    ),
                ) {
                    val viewModel: CategoryViewModel = viewModel(
                        factory = viewModelFactory { initializer { CategoryViewModel(container, createSavedStateHandle()) } },
                    )
                    CategoryRoute(
                        viewModel, padding,
                        onBack = { navController.popBackStack() },
                        // "Sem subcategoria" não tem filtro próprio: abre a categoria inteira.
                        onCheckEntries = { categoryId, subcategoryId ->
                            navController.openFilteredEntries(viewModel.month, subcategoryId ?: categoryId, null)
                        },
                        onOpenForecast = { navController.navigate("$ROUTE_FORECAST/${Uri.encode(viewModel.categoryId)}/${viewModel.month}") },
                        onEditLimit = { navController.navigate("$ROUTE_LIMITS?$ARG_MONTH=${viewModel.month}") },
                    )
                }
            }
            navigation(startDestination = Root.Entries.start, route = Root.Entries.graph) {
                composable(Root.Entries.start) {
                    val viewModel: EntriesViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                val month = container.clock.currentMonth()
                                EntriesViewModel(container, createSavedStateHandle(), EntriesFilterState(month, month, container.theme.entriesDateCriterion.value))
                            }
                        },
                    )
                    PullToRefresh(viewModel::reload) {
                        EntriesRoute(viewModel, padding, onOpenEntry = openEntry,
                            onNew = if (largeFont) openEntriesRegistration else register)
                    }
                }
            }
            composable(ROUTE_REVIEW) {
                val viewModel: ReviewViewModel = viewModel(factory = viewModelFactory { initializer { ReviewViewModel(container.sync, container.chat) } })
                val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                RefreshOnResume(viewModel::refresh)
                val queue: PendingQueueViewModel = viewModel(
                    factory = viewModelFactory { initializer { PendingQueueViewModel(container, container.sync, container.chat, createSavedStateHandle()) } },
                )
                val queueState by queue.state.collectAsStateWithLifecycle()
                RefreshOnResume(queue::refresh)
                PullToRefresh({ viewModel.refresh(); queue.refresh() }) {
                    ReviewRoot(
                        stringResource(R.string.review_title), padding, register,
                        onBack = { navController.popBackStack() },
                        extra = {
                            if (container.phase.includes(ProductPhase.P2)) {
                                app.accord.ui.components.AccordOutlinedButton(
                                    onClick = { navController.navigate(ROUTE_CLUES) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.clues_title)) }
                            }
                            PendingHubCard(queueState.items.size, queueState.loading) { navController.navigate(ROUTE_PENDING) }
                            ReviewConflicts(conflicts) { id -> navController.navigate("$ROUTE_CONFLICT/${Uri.encode(id)}") }
                            ReviewCategories(categories, onAccept = viewModel::accept, onChoose = { navController.navigate(ROUTE_PENDING) })
                        },
                    )
                }
            }
            navigation(startDestination = Root.More.start, route = Root.More.graph) {
                composable(Root.More.start) {
                    MoreRoute(container, navController, padding, reviewState, access)
                }
            }

            // Lista aberta por outra tela: filtros já aplicados e voltar retorna à origem; a aba mantém a sua lista.
            composable(
                "$ROUTE_ENTRIES_FILTERED?$ARG_FILTER_MONTH={$ARG_FILTER_MONTH}&$ARG_FILTER_CATEGORY={$ARG_FILTER_CATEGORY}&$ARG_FILTER_SITUATION={$ARG_FILTER_SITUATION}&$ARG_FILTER_CRITERION={$ARG_FILTER_CRITERION}&filterCardId={filterCardId}",
                arguments = listOf(
                    navArgument("filterCardId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_FILTER_CRITERION) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_FILTER_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_FILTER_CATEGORY) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_FILTER_SITUATION) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStackEntry ->
                val viewModel: EntriesViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { EntriesViewModel(container, createSavedStateHandle(), backStackEntry.initialFilter(container.clock.currentMonth())) }
                    },
                )
                EntriesRoute(viewModel, padding, onOpenEntry = openEntry, onNew = register, onBack = { navController.popBackStack() })
            }
            composable(
                "$ROUTE_ENTRY/{${EntryDetailViewModel.ARG_ID}}",
                arguments = listOf(navArgument(EntryDetailViewModel.ARG_ID) { type = NavType.StringType }),
            ) {
                val viewModel: EntryDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { EntryDetailViewModel(container, createSavedStateHandle(), container.personId) }
                    },
                )
                EntryDetailRoute(
                    viewModel, padding,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("$ROUTE_EDIT/${Uri.encode(id)}") },
                    onOpenCommitment = { id -> navController.navigate("$ROUTE_COMMITMENT/${Uri.encode(id)}") },
                    onOpenIntention = { id -> navController.navigate("$ROUTE_INTENTION/${Uri.encode(id)}") },
                )
            }
            composable(
                "$ROUTE_EDIT/{${EditEntryViewModel.ARG_ID}}",
                arguments = listOf(navArgument(EditEntryViewModel.ARG_ID) { type = NavType.StringType }),
            ) {
                val viewModel: EditEntryViewModel = viewModel(
                    factory = viewModelFactory { initializer { EditEntryViewModel(container, createSavedStateHandle()) } },
                )
                EditEntryRoute(viewModel, padding, onExit = { navController.popBackStack() })
            }
            composable(
                "$ROUTE_FORECASTS?$ARG_MONTH={$ARG_MONTH}",
                arguments = listOf(navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: ForecastsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ForecastsViewModel(container, createSavedStateHandle()) } },
                )
                ForecastsRoute(
                    viewModel, padding,
                    ForecastsActions(
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { id -> navController.navigate("$ROUTE_FORECAST/${Uri.encode(id)}/${viewModel.month}") },
                        onOpenPreferences = { navController.navigate(ROUTE_ALERT_PREFS) },
                        onImproveCoverage = { navController.navigate(ROUTE_REVIEW) },
                        onClassify = { navController.openFilteredEntries(viewModel.month, null, EntrySituation.ToClassify) },
                    ),
                )
            }
            composable(
                "$ROUTE_FORECAST/{${ForecastDetailViewModel.ARG_CATEGORY}}/{${ForecastDetailViewModel.ARG_MONTH}}?${ForecastDetailViewModel.ARG_EVENT}={${ForecastDetailViewModel.ARG_EVENT}}",
                arguments = listOf(
                    navArgument(ForecastDetailViewModel.ARG_CATEGORY) { type = NavType.StringType },
                    navArgument(ForecastDetailViewModel.ARG_MONTH) { type = NavType.StringType },
                    navArgument(ForecastDetailViewModel.ARG_EVENT) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                val viewModel: ForecastDetailViewModel = viewModel(
                    factory = viewModelFactory { initializer { ForecastDetailViewModel(container, createSavedStateHandle()) } },
                )
                ForecastDetailRoute(
                    viewModel, padding,
                    ForecastDetailActions(
                        onBack = { navController.popBackStack() },
                        onCheckEntries = { navController.openFilteredEntries(viewModel.month, viewModel.categoryId, null) },
                        onBackToForecasts = {
                            if (!navController.popBackStack("$ROUTE_FORECASTS?$ARG_MONTH={$ARG_MONTH}", inclusive = false)) {
                                navController.navigate("$ROUTE_FORECASTS?$ARG_MONTH=${viewModel.month}")
                            }
                        },
                        onOpenCommitments = { navController.navigate("$ROUTE_COMMITMENTS?$ARG_MONTH=${viewModel.month}") },
                        onDestination = { destination ->
                            when (destination) {
                                ReasonDestination.Review -> navController.navigate(ROUTE_REVIEW)
                                ReasonDestination.Pendings -> navController.navigate(ROUTE_PENDING)
                                ReasonDestination.Card -> navController.navigate(ROUTE_CARD)
                                ReasonDestination.Sync -> navController.navigate(ROUTE_SYNC)
                            }
                        },
                        onOpenPreferences = { navController.navigate(ROUTE_ALERT_PREFS) },
                    ),
                )
            }
            composable(
                "$ROUTE_COMMITMENTS?$ARG_MONTH={$ARG_MONTH}&${CommitmentsViewModel.ARG_SCENARIO}={${CommitmentsViewModel.ARG_SCENARIO}}",
                arguments = listOf(
                    navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(CommitmentsViewModel.ARG_SCENARIO) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                val viewModel: CommitmentsViewModel = viewModel(
                    factory = viewModelFactory { initializer { CommitmentsViewModel(container, createSavedStateHandle()) } },
                )
                CommitmentsRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate("$ROUTE_COMMITMENT/${Uri.encode(id)}") },
                    onRegister = register,
                    onOpenCard = { navController.navigate(ROUTE_CARD) },
                    onOpenSubscriptions = { navController.navigate(ROUTE_SUBSCRIPTIONS) },
                )
            }
            if (container.phase.includes(ProductPhase.P2)) {
                composable(
                    "$ROUTE_SIMULATOR?${SimulatorViewModel.ARG_TOTAL}={${SimulatorViewModel.ARG_TOTAL}}&${SimulatorViewModel.ARG_MONTH}={${SimulatorViewModel.ARG_MONTH}}&${SimulatorViewModel.ARG_INTENTION}={${SimulatorViewModel.ARG_INTENTION}}",
                    arguments = listOf(SimulatorViewModel.ARG_TOTAL, SimulatorViewModel.ARG_MONTH, SimulatorViewModel.ARG_INTENTION).map { name ->
                        navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
                    },
                ) {
                    val viewModel: SimulatorViewModel = viewModel(
                        factory = viewModelFactory { initializer { SimulatorViewModel(container, createSavedStateHandle()) } },
                    )
                    SimulatorRoute(
                        viewModel, padding, onBack = { navController.popBackStack() },
                        onAssume = { scenario ->
                            val form = RegisterForm(
                                kind = FormKind.Expense, form = ExpenseForm.Card, amountText = scenario.total.formatPlain(), date = container.clock.today(),
                                categoryId = scenario.categoryId, installmentsText = scenario.installments.toString(),
                                amountMeaning = AmountMeaning.Total.takeIf { scenario.installments > 1 }, originIntentionId = scenario.originIntentionId,
                            )
                            navController.navigate(conversionRoute(form, scenario.firstMonth))
                        },
                        onSaveAsIntention = { scenario ->
                            navController.navigate("$ROUTE_INTENTION/${IntentionViewModel.NEW}?${IntentionViewModel.ARG_VALUE}=${scenario.total.cents}&${IntentionViewModel.ARG_MONTH}=${scenario.firstMonth}")
                        },
                        onOpenMonth = { month, scenario ->
                            navController.navigate("$ROUTE_COMMITMENTS?$ARG_MONTH=$month&${CommitmentsViewModel.ARG_SCENARIO}=${Uri.encode(ScenarioArg.encode(scenario))}")
                        },
                    )
                }
            }
            if (container.phase.includes(ProductPhase.P2)) {
                composable(ROUTE_CLUES) {
                    val vm: CluesViewModel = viewModel(factory = viewModelFactory { initializer { CluesViewModel(container, createSavedStateHandle()) } })
                    CluesRoute(vm, padding, { navController.popBackStack() }) { key, reference ->
                        navController.navigate("$ROUTE_CLUE/${Uri.encode(key)}?reference=${reference.toEpochDay()}")
                    }
                }
                composable("$ROUTE_CLUE/{clueKey}?reference={reference}", arguments = listOf(
                    navArgument("clueKey") { type = NavType.StringType }, navArgument("reference") { type = NavType.StringType },
                )) {
                    val vm: CluesViewModel = viewModel(factory = viewModelFactory { initializer { CluesViewModel(container, createSavedStateHandle()) } })
                    ClueDetailRoute(vm, padding, { navController.popBackStack() },
                        onEntry = { id -> navController.navigate("$ROUTE_ENTRY/${Uri.encode(id)}") },
                        onPending = { navController.navigate(ROUTE_PENDING) })
                }
                composable(ROUTE_INTENTIONS) {
                    val viewModel: IntentionsViewModel = viewModel(factory = viewModelFactory { initializer { IntentionsViewModel(container) } })
                    IntentionsRoute(
                        viewModel, padding, onBack = { navController.popBackStack() },
                        onOpen = { id -> navController.navigate("$ROUTE_INTENTION/${Uri.encode(id)}") },
                        onNew = { navController.navigate("$ROUTE_INTENTION/${IntentionViewModel.NEW}") },
                        onSimulate = { navController.navigate(ROUTE_SIMULATOR) },
                    )
                }
                composable(
                    "$ROUTE_INTENTION/{${IntentionViewModel.ARG_ID}}?${IntentionViewModel.ARG_VALUE}={${IntentionViewModel.ARG_VALUE}}&${IntentionViewModel.ARG_MONTH}={${IntentionViewModel.ARG_MONTH}}",
                    arguments = listOf(
                        navArgument(IntentionViewModel.ARG_ID) { type = NavType.StringType },
                        navArgument(IntentionViewModel.ARG_VALUE) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(IntentionViewModel.ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    val viewModel: IntentionViewModel = viewModel(
                        factory = viewModelFactory { initializer { IntentionViewModel(container, createSavedStateHandle()) } },
                    )
                    IntentionRoute(
                        viewModel, padding, onBack = { navController.popBackStack() },
                        onAssume = { view ->
                            val form = RegisterForm(
                                kind = FormKind.Expense, form = ExpenseForm.Card, description = view.name,
                                amountText = view.estimatedValue?.formatPlain().orEmpty(), date = container.clock.today(), originIntentionId = view.id,
                            )
                            navController.navigate(conversionRoute(form, null))
                        },
                        onOpenConversion = { conversion ->
                            navController.navigate(
                                when (conversion.type) {
                                    EntityType.Movement -> "$ROUTE_ENTRY/${Uri.encode(conversion.id)}"
                                    EntityType.CardPurchase -> "$ROUTE_COMMITMENT/${Uri.encode(conversion.id + "#1")}"
                                    EntityType.Recurrence -> "$ROUTE_COMMITMENT/${Uri.encode(conversion.id + ":1")}"
                                    else -> "$ROUTE_COMMITMENT/${Uri.encode(conversion.id)}"
                                },
                            )
                        },
                        onSimulate = { view ->
                            val total = view.estimatedValue?.cents?.let { "${SimulatorViewModel.ARG_TOTAL}=$it&" } ?: ""
                            val month = view.desiredMonth?.let { "${SimulatorViewModel.ARG_MONTH}=$it&" } ?: ""
                            navController.navigate("$ROUTE_SIMULATOR?$total$month${SimulatorViewModel.ARG_INTENTION}=${Uri.encode(view.id)}")
                        },
                    )
                }
            }
            composable(ROUTE_ACTIVITY) {
                val viewModel: PurchaseActivityViewModel = viewModel(
                    factory = viewModelFactory { initializer { PurchaseActivityViewModel(container, createSavedStateHandle()) } },
                )
                PullToRefresh(viewModel::refresh) {
                    PurchaseActivityRoute(
                        viewModel, padding,
                        ActivityActions(
                            onBack = { navController.popBackStack() },
                            onOpenEntries = { month, categoryId, toClassify ->
                                navController.openFilteredEntries(
                                    month, categoryId, EntrySituation.ToClassify.takeIf { toClassify }, DateCriterion.PurchaseDate,
                                )
                            },
                            onOpenBudget = { navController.openRoot(Root.Budget) },
                            onOpenComparison = { navController.navigate(ROUTE_COMPARISON) },
                        ),
                    )
                }
            }
            composable(ROUTE_WEEKLY_REVIEW) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val viewModel: WeeklyReviewViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { WeeklyReviewViewModel(container, PrefsWeeklyReviewStore(context, container.personId)) }
                    },
                )
                WeeklyReviewRoute(
                    viewModel, padding,
                    WeeklyReviewActions(
                        onBack = { navController.popBackStack() },
                        onOpenCategory = { categoryId, month -> navController.navigate("$ROUTE_FORECAST/${Uri.encode(categoryId)}/$month") },
                        onOpenPendings = { navController.navigate(ROUTE_REVIEW) },
                        onOpenCommitment = { id -> navController.navigate("$ROUTE_COMMITMENT/${Uri.encode(id)}") },
                        onOpenActivity = { navController.navigate(ROUTE_ACTIVITY) },
                        onOpenCommitments = { navController.navigate(ROUTE_COMMITMENTS) },
                        onOpenBudget = { navController.openRoot(Root.Budget) },
                    ),
                )
            }
            composable(ROUTE_COMPARISON) {
                val viewModel: ComparisonViewModel = viewModel(
                    factory = viewModelFactory { initializer { ComparisonViewModel(container, createSavedStateHandle()) } },
                )
                PullToRefresh(viewModel::refresh) {
                    ComparisonRoute(
                        viewModel, padding,
                        ComparisonActions(
                            onBack = { navController.popBackStack() },
                            onOpenEntries = { month, categoryId, criterion, toClassify ->
                                navController.openFilteredEntries(month, categoryId, EntrySituation.ToClassify.takeIf { toClassify }, criterion)
                            },
                            onOpenPendings = { navController.navigate(ROUTE_REVIEW) },
                        ),
                    )
                }
            }
            composable(ROUTE_SUBSCRIPTIONS) {
                val viewModel: SubscriptionsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SubscriptionsViewModel(container, createSavedStateHandle()) } },
                )
                SubscriptionsRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate("$ROUTE_SUBSCRIPTION/${Uri.encode(id)}") },
                    onRegister = register,
                )
            }
            composable(
                "$ROUTE_SUBSCRIPTION/{${SubscriptionDetailViewModel.ARG_ID}}",
                arguments = listOf(navArgument(SubscriptionDetailViewModel.ARG_ID) { type = NavType.StringType }),
            ) {
                val viewModel: SubscriptionDetailViewModel = viewModel(
                    factory = viewModelFactory { initializer { SubscriptionDetailViewModel(container, createSavedStateHandle()) } },
                )
                SubscriptionDetailRoute(
                    viewModel, padding, onBack = { navController.popBackStack() }, onOpenEntry = openEntry,
                    onEdit = { id -> navController.navigate("$ROUTE_EDIT/${Uri.encode(id)}") },
                )
            }
            composable(
                "$ROUTE_COMMITMENT/{${CommitmentDetailViewModel.ARG_ID}}",
                arguments = listOf(navArgument(CommitmentDetailViewModel.ARG_ID) { type = NavType.StringType }),
            ) {
                val viewModel: CommitmentDetailViewModel = viewModel(
                    factory = viewModelFactory { initializer { CommitmentDetailViewModel(container, createSavedStateHandle()) } },
                )
                CommitmentDetailRoute(
                    viewModel, padding, onBack = { navController.popBackStack() }, onOpenEntry = openEntry,
                    onEdit = { id -> navController.navigate("$ROUTE_EDIT/${Uri.encode(id)}") },
                    onOpenIntention = { id -> navController.navigate("$ROUTE_INTENTION/${Uri.encode(id)}") },
                )
            }
            composable(ROUTE_SYNC) {
                val viewModel: SyncViewModel = viewModel(factory = viewModelFactory { initializer { SyncViewModel(container, container.sync) } })
                SyncRoute(
                    viewModel, padding,
                    onBack = { navController.popBackStack() },
                    onOpenEntry = openEntry,
                    onOpenConflict = { id -> navController.navigate("$ROUTE_CONFLICT/${Uri.encode(id)}") },
                    onRenew = { navController.navigate(ROUTE_ACCESS) },
                )
            }
            composable(
                "$ROUTE_CONFLICT/{operationId}",
                arguments = listOf(navArgument("operationId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val operationId = checkNotNull(backStackEntry.arguments?.getString("operationId"))
                val viewModel: ConflictViewModel = viewModel(
                    factory = viewModelFactory { initializer { ConflictViewModel(container, container.sync, operationId) } },
                )
                ConflictRoute(viewModel, padding, onFinished = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_ACCESS) {
                val viewModel: AccessViewModel = viewModel(
                    factory = viewModelFactory { initializer { AccessViewModel(container.access, renewal = true) } },
                )
                AccessRoute(viewModel, padding, onEntered = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_SPACE) {
                val viewModel: SpaceViewModel = viewModel(factory = viewModelFactory { initializer { SpaceViewModel(container.access) } })
                SpaceRoute(viewModel, padding, firstRun = false, onContinue = {}, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_ALERT_PREFS) {
                val viewModel: AlertPreferencesViewModel = viewModel(
                    factory = viewModelFactory { initializer { AlertPreferencesViewModel(container, container.notifications) } },
                )
                AlertPreferencesRoute(viewModel, container.notifications, padding, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_NOTIFICATIONS) {
                val viewModel: AlertPreferencesViewModel = viewModel(
                    factory = viewModelFactory { initializer { AlertPreferencesViewModel(container, container.notifications) } },
                )
                app.accord.ui.more.NotificationsRoute(
                    viewModel, container.notifications, padding, onBack = { navController.popBackStack() },
                    onAlertPreferences = { navController.navigate(ROUTE_ALERT_PREFS) },
                    onBankNotifications = { navController.navigate(ROUTE_BANK_NOTIFICATIONS) },
                )
            }
            composable(ROUTE_BANK_NOTIFICATIONS) {
                val appContext = LocalContext.current.applicationContext
                val viewModel: BankNotificationsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { BankNotificationsViewModel(container.bankNotificationSettings) { BankNotificationsViewModel.installedApps(appContext) } }
                    },
                )
                BankNotificationsRoute(viewModel, padding, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_FIRST_STEPS) {
                val viewModel: FirstStepsViewModel = viewModel(
                    factory = viewModelFactory { initializer { FirstStepsViewModel(container, container.access, container.notifications) } },
                )
                val goToHome: () -> Unit = {
                    navController.navigate(Root.Home.graph) { popUpTo(ROUTE_FIRST_STEPS) { inclusive = true } }
                }
                FirstStepsRoute(
                    viewModel, container.notifications, padding,
                    StepActions(
                        onCategories = { navController.navigate(ROUTE_CATEGORIES) },
                        onCard = { navController.navigate("$ROUTE_CARD?initialSetup=true") },
                        onAccounts = { navController.navigate(ROUTE_ACCOUNTS) },
                        onLimits = { navController.navigate("$ROUTE_LIMITS?initialSetup=true") },
                        onSpace = { navController.navigate(ROUTE_SPACE) },
                        onStart = { viewModel.start(goToHome) },
                        onRegisterManually = {
                            viewModel.start {
                                // Início fica embaixo do formulário: ao sair do registro há uma raiz para onde voltar (accord-6hu).
                                navController.navigate(Root.Home.graph) { popUpTo(ROUTE_FIRST_STEPS) { inclusive = true } }
                                navController.navigate(ROUTE_REGISTER)
                            }
                        },
                        onBack = if (navController.previousBackStackEntry != null) ({ navController.popBackStack() }) else null,
                    ),
                )
            }
            composable(ROUTE_CATEGORIES) {
                val appContext = LocalContext.current.applicationContext
                val viewModel: CategoriesViewModel = viewModel(
                    factory = viewModelFactory { initializer { CategoriesViewModel(container, createSavedStateHandle()) { id, args -> appContext.getString(id, *args) } } },
                )
                PullToRefresh(viewModel::refresh) {
                    CategoriesRoute(
                        viewModel, padding, onBack = { navController.popBackStack() },
                        onMoveEntries = { id -> navController.navigate("$ROUTE_CATEGORY_MOVE/$id?${CategoryMoveViewModel.ARG_REMOVE}=false") },
                        onRemove = { id -> navController.navigate("$ROUTE_CATEGORY_MOVE/$id?${CategoryMoveViewModel.ARG_REMOVE}=true") },
                    )
                }
            }
            composable(
                "$ROUTE_CATEGORY_MOVE/{${CategoryMoveViewModel.ARG_CATEGORY}}?${CategoryMoveViewModel.ARG_REMOVE}={${CategoryMoveViewModel.ARG_REMOVE}}",
                arguments = listOf(
                    navArgument(CategoryMoveViewModel.ARG_CATEGORY) { type = NavType.StringType },
                    navArgument(CategoryMoveViewModel.ARG_REMOVE) { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                val appContext = LocalContext.current.applicationContext
                val viewModel: CategoryMoveViewModel = viewModel(
                    factory = viewModelFactory { initializer { CategoryMoveViewModel(container, createSavedStateHandle()) { id -> appContext.getString(id) } } },
                )
                // Depois de mover ou remover, volta à lista de categorias (a edição pode já nem existir).
                CategoryMoveRoute(viewModel, padding, onBack = {
                    if (!navController.popBackStack(ROUTE_CATEGORIES, inclusive = false)) navController.popBackStack()
                })
            }
            composable(ROUTE_PRIVACY_SECURITY) {
                val activity = LocalActivity.current as app.accord.MainActivity
                val viewModel: app.accord.privacy.PrivacyViewModel = viewModel(factory = viewModelFactory {
                    initializer { app.accord.privacy.PrivacyViewModel(container) }
                })
                app.accord.ui.more.PrivacySecurityScreen(padding, container.appLock, activity.appAuthenticator, viewModel) { navController.popBackStack() }
            }
            composable(ROUTE_CHAT_SETTINGS) {
                app.accord.ui.more.ChatSettingsRoute(
                    container, padding, onBack = { navController.popBackStack() },
                    onModel = { navController.navigate(ROUTE_MODEL) },
                    onArchivedChats = { navController.navigate("archived_chats") },
                )
            }
            composable("archived_chats") {
                app.accord.privacy.ArchivedChatsScreen(padding, container) { navController.popBackStack() }
            }
            composable(ROUTE_ACCOUNTS) {
                val viewModel: AccountsViewModel = viewModel(
                    factory = viewModelFactory { initializer { AccountsViewModel(container, createSavedStateHandle()) } },
                )
                AccountsRoute(viewModel, padding, onBack = { navController.popBackStack() }, onSavings = { navController.navigate("savings?account=${Uri.encode(it)}") })
            }
            composable("savings?account={account}", arguments = listOf(navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                val vm: app.accord.ui.savings.SavingsViewModel = viewModel(factory = viewModelFactory {
                    initializer { app.accord.ui.savings.SavingsViewModel(container, createSavedStateHandle()) }
                })
                app.accord.ui.savings.SavingsRoute(vm, padding, onBack = { navController.popBackStack() }, memberNames = container.memberNames())
            }
            composable(
                "$ROUTE_LIMITS?$ARG_MONTH={$ARG_MONTH}&initialSetup={initialSetup}",
                arguments = listOf(
                    navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("initialSetup") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                val viewModel: LimitsViewModel = viewModel(
                    factory = viewModelFactory { initializer { LimitsViewModel(container, createSavedStateHandle()) } },
                )
                LimitsRoute(viewModel, padding, onExit = { navController.popBackStack() })
            }
            composable("$ROUTE_CARD?initialSetup={initialSetup}", arguments = listOf(navArgument("initialSetup") { type = NavType.BoolType; defaultValue = false })) { cardEntry ->
                val viewModel: CardViewModel = viewModel(
                    factory = viewModelFactory { initializer { CardViewModel(container, createSavedStateHandle()) } },
                )
                PullToRefresh(viewModel::refresh) {
                    CardRoute(
                        viewModel, padding, onBack = { navController.popBackStack() },
                        onOpenInvoice = { month, card -> navController.navigate("$ROUTE_INVOICE/$month?cardId=$card") },
                        onToConfirm = { navController.openFilteredEntries(container.clock.currentMonth(), null, EntrySituation.InvoiceLinkToConfirm) },
                        initialSetup = cardEntry.arguments?.getBoolean("initialSetup") == true,
                    )
                }
            }
            composable("appearance") {
                val theme by container.theme.preference.collectAsStateWithLifecycle()
                val corners by container.theme.corners.collectAsStateWithLifecycle()
                val palette by container.theme.palette.collectAsStateWithLifecycle()
                AppearanceScreen(
                    padding, theme, corners, container.theme::set, container.theme::setCorners,
                    onBack = { navController.popBackStack() }, palette = palette, onPalette = container.theme::setPalette,
                )
            }
            composable(
                "$ROUTE_INVOICE/{${InvoiceViewModel.ARG_MONTH}}?cardId={cardId}",
                arguments = listOf(navArgument(InvoiceViewModel.ARG_MONTH) { type = NavType.StringType }, navArgument("cardId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: InvoiceViewModel = viewModel(
                    factory = viewModelFactory { initializer { InvoiceViewModel(container, createSavedStateHandle()) } },
                )
                PullToRefresh(viewModel::refresh) {
                    InvoiceRoute(
                        viewModel, padding, onBack = { navController.popBackStack() },
                        onSeeEntries = { month -> navController.openFilteredEntries(month, null, null, cardId = viewModel.selectedCardId) },
                    )
                }
            }
            composable(ROUTE_IMPORT_HUB) {
                val hub: ImportHubViewModel = viewModel(factory = viewModelFactory { initializer { ImportHubViewModel(container) } })
                val hubState by hub.state.collectAsStateWithLifecycle()
                RefreshOnResume(hub::refresh)
                PullToRefresh(hub::refresh) {
                    RootScaffold(stringResource(R.string.import_hub_title), padding, onBack = { navController.popBackStack() }) {
                        ImportHub(
                            hubState,
                            onImport = { navController.navigate(ROUTE_IMPORT) },
                            onContinue = { id -> navController.navigate("$ROUTE_IMPORT_REVIEW/${Uri.encode(id)}") },
                            onOpenResult = { id -> navController.navigate("$ROUTE_IMPORT_RESULT/${Uri.encode(id)}") },
                            onDiscard = hub::discard,
                            showTitle = false,
                        )
                    }
                }
            }
            composable(ROUTE_IMPORT) {
                val appContext = LocalContext.current.applicationContext
                val resolver = appContext.contentResolver
                val viewModel: ImportFileViewModel = viewModel(
                    factory = viewModelFactory { initializer { ImportFileViewModel(container, ContentResolverOpener(resolver), AndroidPdfTextExtractor(appContext)) } },
                )
                val shared by container.pendingShare.collectAsStateWithLifecycle()
                LaunchedEffect(shared) {
                    (shared as? app.accord.share.SharedContent.File)?.let {
                        container.consumeShare()
                        viewModel.onFilePicked(it.uri)
                    }
                }
                ImportFileRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpenReview = { id -> navController.navigate("$ROUTE_IMPORT_REVIEW/${Uri.encode(id)}") { popUpTo(ROUTE_IMPORT) { inclusive = true } } },
                    onOpenResult = { id -> navController.navigate("$ROUTE_IMPORT_RESULT/${Uri.encode(id)}") { popUpTo(ROUTE_IMPORT) { inclusive = true } } },
                )
            }
            composable("$ROUTE_IMPORT_REVIEW/{batchId}", arguments = listOf(navArgument("batchId") { type = NavType.StringType })) { entry ->
                val batchId = Uri.decode(entry.arguments?.getString("batchId").orEmpty())
                val viewModel: ImportReviewViewModel = viewModel(
                    factory = viewModelFactory { initializer { ImportReviewViewModel(container, createSavedStateHandle(), batchId) } },
                )
                ImportReviewRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    // Depois de confirmar, voltar do resultado leva a Revisar, nunca de volta à confirmação.
                    onOpenResult = { id -> navController.navigate("$ROUTE_IMPORT_RESULT/${Uri.encode(id)}") { popUpTo("$ROUTE_IMPORT_REVIEW/{batchId}") { inclusive = true } } },
                    onClosed = { navController.popBackStack() },
                )
            }
            composable("$ROUTE_IMPORT_RESULT/{batchId}", arguments = listOf(navArgument("batchId") { type = NavType.StringType })) { entry ->
                val batchId = Uri.decode(entry.arguments?.getString("batchId").orEmpty())
                val viewModel: ImportResultViewModel = viewModel(
                    factory = viewModelFactory { initializer { ImportResultViewModel(container, batchId) } },
                )
                ImportResultRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onSeeEntries = { navController.openRoot(Root.Entries) },
                    onOpenEntry = openEntry,
                    onResolvePending = { navController.navigate("$ROUTE_PENDING?batch=${Uri.encode(batchId)}") },
                )
            }
            composable(
                "$ROUTE_PENDING?batch={batch}",
                arguments = listOf(navArgument("batch") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: PendingQueueViewModel = viewModel(
                    factory = viewModelFactory { initializer { PendingQueueViewModel(container, container.sync, container.chat, createSavedStateHandle()) } },
                )
                PendingQueueRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpenImport = { id, ordinal -> navController.navigate("$ROUTE_PENDING_ITEM/${Uri.encode(id)}/$ordinal") },
                    onOpenConflict = { id -> navController.navigate("$ROUTE_CONFLICT/${Uri.encode(id)}") },
                    onOpenEntry = openEntry,
                    onOpenInvoice = { month, card -> navController.navigate("$ROUTE_INVOICE/$month" + (card?.let { "?cardId=$it" } ?: "")) },
                    onOpenChat = { navController.openRoot(Root.Chat) },
                    onOpenManual = { navController.navigate(it) },
                )
            }
            composable(
                "$ROUTE_PENDING_ITEM/{batchId}/{ordinal}",
                arguments = listOf(navArgument("batchId") { type = NavType.StringType }, navArgument("ordinal") { type = NavType.IntType }),
            ) { entry ->
                val batchId = Uri.decode(entry.arguments?.getString("batchId").orEmpty())
                val ordinal = entry.arguments?.getInt("ordinal") ?: 0
                val viewModel: PendingResolveViewModel = viewModel(
                    factory = viewModelFactory { initializer { PendingResolveViewModel(container, batchId, ordinal) } },
                )
                PendingResolveRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onResolved = { navController.popBackStack() },
                    onOpenCard = { navController.navigate("$ROUTE_CARD?initialSetup=false") },
                )
            }
            composable(ROUTE_RECEIVABLES) {
                val viewModel: app.accord.ui.receivables.ReceivablesViewModel = viewModel(
                    factory = viewModelFactory { initializer { app.accord.ui.receivables.ReceivablesViewModel(container, createSavedStateHandle()) } },
                )
                app.accord.ui.receivables.ReceivablesRoute(
                    viewModel, padding, onBack = { navController.popBackStack() }, onNew = { navController.navigate(ROUTE_RECEIVABLE_NEW) },
                )
            }
            composable(
                "$ROUTE_RECEIVABLE_NEW?amount={amount}&account={account}&date={date}&fromExpense={fromExpense}",
                arguments = listOf(
                    navArgument("amount") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("fromExpense") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { backStackEntry ->
                val viewModel: app.accord.ui.receivables.ReceivableFormViewModel = viewModel(
                    factory = viewModelFactory { initializer { app.accord.ui.receivables.ReceivableFormViewModel(container, createSavedStateHandle()) } },
                )
                val fromExpense = backStackEntry.arguments?.getBoolean("fromExpense") == true
                app.accord.ui.receivables.ReceivableFormRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onDone = {
                        // Salvo: sai do formulário (e do registro de despesa que o abriu) e mostra "Quem me deve".
                        navController.popBackStack()
                        if (fromExpense) navController.popBackStack()
                        navController.navigate(ROUTE_RECEIVABLES)
                    },
                    onBackToExpense = if (fromExpense) ({ navController.popBackStack() }) else null,
                )
            }
            composable(
                "$ROUTE_INCOMES?$ARG_MONTH={$ARG_MONTH}",
                arguments = listOf(navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: IncomesViewModel = viewModel(
                    factory = viewModelFactory { initializer { IncomesViewModel(container, createSavedStateHandle()) } },
                )
                PullToRefresh(viewModel::refresh) {
                    IncomesRoute(
                        viewModel, padding, onBack = { navController.popBackStack() }, onOpen = openEntry,
                        onAdd = { navController.navigate("$ROUTE_REGISTER?$ARG_REGISTER_KIND=income") },
                    )
                }
            }
            composable(ROUTE_MODEL) {
                val viewModel: ModelViewModel = viewModel(factory = viewModelFactory { initializer { ModelViewModel(container) } })
                ModelRoute(
                    viewModel, padding,
                    ModelActions(
                        onBack = { navController.popBackStack() },
                        onBackToChat = { navController.openRoot(Root.Chat) },
                        onManual = register,
                    ),
                )
            }
            composable(
                "$ROUTE_REGISTER?$ARG_REGISTER_KIND={$ARG_REGISTER_KIND}&$ARG_REGISTER_FORM={$ARG_REGISTER_FORM}&$ARG_REGISTER_CHAT={$ARG_REGISTER_CHAT}&sourceDraft={sourceDraft}&${RegisterViewModel.ARG_SIM_INVOICE}={${RegisterViewModel.ARG_SIM_INVOICE}}",
                arguments = listOf(
                    navArgument(RegisterViewModel.ARG_SIM_INVOICE) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("sourceDraft") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_REGISTER_KIND) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_REGISTER_FORM) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_REGISTER_CHAT) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                val viewModel: RegisterViewModel = viewModel(
                    factory = viewModelFactory { initializer { RegisterViewModel(container, createSavedStateHandle()) } },
                )
                RegisterRoute(
                    viewModel, padding, onExit = { navController.popBackStack() },
                    onSomeoneOwesMe = { amount, account, date ->
                        navController.navigate("$ROUTE_RECEIVABLE_NEW?amount=${Uri.encode(amount)}&account=${Uri.encode(account.orEmpty())}&date=$date&fromExpense=true")
                    },
                )
            }
        }
        }
    }
    if (registerChooserOpen && onRoot && selectedRoot == Root.Entries) {
        RegisterTypeChooser(
            onIncome = { openManualRegistration("income") },
            onExpense = { openManualRegistration("expense", "pix") },
            onCardExpense = { openManualRegistration("expense", "card") },
            onDismiss = { registerChooserOpen = false },
        )
    }
}

@Composable
private fun RegisterTypeChooser(
    onIncome: () -> Unit,
    onExpense: () -> Unit,
    onCardExpense: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = accordDialogColor(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_register_manually)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                RegisterTypeAction(stringResource(R.string.register_speed_dial_income), Lucide.drawable.lucide_ic_banknote_arrow_up,
                    "register-choice-income", onIncome)
                RegisterTypeAction(stringResource(R.string.register_speed_dial_expense), Lucide.drawable.lucide_ic_arrow_down_left,
                    "register-choice-expense", onExpense)
                RegisterTypeAction(stringResource(R.string.register_speed_dial_card_expense), Lucide.drawable.lucide_ic_credit_card,
                    "register-choice-card", onCardExpense)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.chat_dialog_cancel))
            }
        },
    )
}

@Composable
private fun RegisterTypeAction(label: String, icon: Int, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(AccordSpacing.s3),
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedDialAction(label: String, icon: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// A tela Início é a base da pilha das raízes. Não usar findStartDestination(): numa sessão iniciada nos Primeiros
// passos o início do grafo é essa tela, já removida da pilha, e o popUpTo deixaria de ter alvo (accord-6hu).
private fun NavHostController.openRoot(root: Root) = navigate(root.graph) {
    popUpTo(Root.Home.start) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.openFilteredEntries(month: YearMonth, categoryId: String?, situation: EntrySituation?, criterion: DateCriterion? = null, cardId: String? = null) {
    val query = buildList {
        cardId?.let { add("filterCardId=$it") }
        add("$ARG_FILTER_MONTH=$month")
        criterion?.let { add("$ARG_FILTER_CRITERION=${it.name}") }
        categoryId?.let { add("$ARG_FILTER_CATEGORY=${Uri.encode(it)}") }
        situation?.let { add("$ARG_FILTER_SITUATION=${it.name}") }
    }.joinToString("&")
    navigate("$ROUTE_ENTRIES_FILTERED?$query")
}

private fun NavBackStackEntry.initialFilter(default: YearMonth): EntriesFilterState {
    val month = arguments?.getString(ARG_FILTER_MONTH)?.let(YearMonth::parse) ?: default
    return EntriesFilterState(
        from = month, to = month, cardId = arguments?.getString("filterCardId"),
        criterion = arguments?.getString(ARG_FILTER_CRITERION)?.let { DateCriterion.valueOf(it) } ?: DateCriterion.BudgetMonth,
        categoryId = arguments?.getString(ARG_FILTER_CATEGORY),
        situations = arguments?.getString(ARG_FILTER_SITUATION)?.let { setOf(EntrySituation.valueOf(it)) }.orEmpty(),
    )
}

@Composable
private fun RenewalBanner(onRenew: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            Text(stringResource(R.string.renew_banner), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRenew, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.more_renew_access)) }
        }
    }
}

