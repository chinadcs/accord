package app.accord.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import androidx.compose.ui.res.pluralStringResource
import app.accord.core.sync.EntryStage
import app.accord.core.sync.SessionState
import app.accord.ui.access.AccessRoute
import app.accord.ui.access.AccessViewModel
import app.accord.ui.firststeps.FirstStepsRoute
import app.accord.ui.firststeps.FirstStepsViewModel
import app.accord.ui.firststeps.StepActions
import app.accord.ui.nav.ROUTE_ACCESS
import app.accord.ui.nav.ROUTE_CONFLICT
import app.accord.ui.nav.ROUTE_SYNC
import app.accord.ui.sync.ConflictRoute
import app.accord.ui.sync.ConflictViewModel
import app.accord.ui.sync.LocalOpenSync
import app.accord.ui.sync.LocalSyncStatus
import app.accord.ui.sync.ReviewConflicts
import app.accord.ui.sync.ReviewViewModel
import app.accord.ui.sync.SyncIndicator
import app.accord.ui.sync.SyncRoute
import app.accord.ui.sync.SyncViewModel
import app.accord.ui.components.RefreshOnResume
import androidx.compose.runtime.CompositionLocalProvider
import app.accord.core.sync.ConnectionState
import app.accord.ui.nav.ROUTE_FIRST_STEPS
import app.accord.ui.nav.ROUTE_SPACE
import app.accord.ui.space.SpaceRoute
import app.accord.ui.space.SpaceViewModel
import app.accord.ui.theme.AccordSpacing
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
import app.accord.ui.nav.ROUTE_LIMITS
import app.accord.ui.nav.ARG_REGISTER_CHAT
import app.accord.ui.nav.ARG_REGISTER_FORM
import app.accord.ui.nav.ARG_REGISTER_KIND
import app.accord.ui.nav.ROUTE_MODEL
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatRoute
import app.accord.ui.chat.ChatViewModel
import app.accord.ui.nav.ROUTE_CARD
import app.accord.ui.nav.ROUTE_COMMITMENT
import app.accord.ui.nav.ROUTE_COMMITMENTS
import app.accord.ui.nav.ROUTE_EDIT
import app.accord.ui.nav.ROUTE_INCOMES
import app.accord.ui.nav.ROUTE_INVOICE
import app.accord.ui.nav.ROUTE_REGISTER
import app.accord.ui.nav.Root
import app.accord.ui.categories.CategoriesRoute
import app.accord.ui.categories.CategoriesViewModel
import app.accord.ui.incomes.IncomesRoute
import app.accord.ui.incomes.IncomesViewModel
import app.accord.ui.limits.LimitsRoute
import app.accord.ui.limits.LimitsViewModel
import app.accord.ui.more.AccountsRoute
import app.accord.ui.more.AccountsViewModel
import app.accord.ui.more.MoreActions
import app.accord.ui.more.MoreScreen
import app.accord.ui.more.AppearanceScreen
import app.accord.ui.theme.Accord
import app.accord.ui.register.RegisterRoute
import app.accord.ui.register.RegisterViewModel
import java.time.YearMonth

/**
 * Raiz do aplicativo: o estágio de entrada decide a tela. Sem conta, Acesso (01); com conta e sem
 * espaço, Espaço da casa (02); com espaço novo, Primeiros passos (03); depois, o aplicativo.
 */
@Composable
fun AccordApp(container: AppContainer) {
    val access by container.access.status.collectAsStateWithLifecycle()
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
        access.stage == EntryStage.SignedOut -> AccessGate(container)
        access.stage == EntryStage.NoSpace || spaceIntro -> SpaceGate(container, onContinue = { spaceIntro = false })
        else -> {
            // Fixada na primeira composição: trocar a rota inicial depois recriaria o grafo de navegação.
            val startRoute = remember { if (access.stage == EntryStage.FirstSteps) ROUTE_FIRST_STEPS else Root.Chat.graph }
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
private fun AccessGate(container: AppContainer) {
    Scaffold { padding ->
        val viewModel: AccessViewModel = viewModel(factory = viewModelFactory { initializer { AccessViewModel(container.access) } })
        AccessRoute(viewModel, padding)
    }
}

@Composable
private fun SpaceGate(container: AppContainer, onContinue: () -> Unit) {
    Scaffold { padding ->
        val viewModel: SpaceViewModel = viewModel(factory = viewModelFactory { initializer { SpaceViewModel(container.access) } })
        SpaceRoute(viewModel, padding, firstRun = true, onContinue = onContinue)
    }
}

@Composable
private fun MainApp(container: AppContainer, startRoute: String) {
    val navController = rememberNavController()
    val access by container.access.status.collectAsStateWithLifecycle()
    val syncStatus by container.sync.status.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    // A barra inferior só aparece nas raízes; detalhes e formulários escondem-na.
    val onRoot = Root.entries.any { destination?.route == it.start }

    // Avisos depois de salvar, com Desfazer; ficam aqui para sobreviver à troca de tela.
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // O aviso da ação anterior não cobre o botão Salvar do formulário que abre em seguida.
    val formRoute = destination?.route?.substringBefore('?')?.substringBefore('/')
    val inForm = formRoute in setOf(ROUTE_REGISTER, ROUTE_EDIT, ROUTE_CARD, ROUTE_ACCOUNTS, ROUTE_CATEGORIES, ROUTE_LIMITS, ROUTE_FIRST_STEPS)
    LaunchedEffect(destination?.route) { if (inForm) snackbar.currentSnackbarData?.dismiss() }
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
                val message = context.getString(messageRes, *event.args.toTypedArray())
                val undoLabel = event.undoOperationId?.let { context.getString(R.string.action_undo) }
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
                    snackbar.showSnackbar(context.getString(text))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // Sessão vencida: os dados e a fila ficam; só a pessoa pode entrar de novo, com a mesma conta.
            if (access.session == SessionState.NeedsRenewal && destination?.route != ROUTE_ACCESS) {
                RenewalBanner(onRenew = { navController.navigate(ROUTE_ACCESS) })
            }
        },
        bottomBar = {
            if (onRoot) {
                NavigationBar {
                    Root.entries.forEach { root ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == root.graph } == true,
                            onClick = { navController.openRoot(root) },
                            icon = { Icon(painterResource(root.icon), contentDescription = null) },
                            label = { Text(stringResource(root.label), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(
            LocalSyncStatus provides syncStatus,
            LocalOpenSync provides { navController.navigate(ROUTE_SYNC) },
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
                            onOpenBudget = { navController.openRoot(Root.Budget) },
                            onOpenEntries = { navController.openRoot(Root.Entries) },
                            onOpenCard = { navController.navigate(ROUTE_CARD) },
                        ),
                    )
                }
            }
            navigation(startDestination = Root.Budget.start, route = Root.Budget.graph) {
                composable(Root.Budget.start) {
                    val viewModel: BudgetViewModel = viewModel(
                        factory = viewModelFactory { initializer { BudgetViewModel(container, createSavedStateHandle()) } },
                    )
                    BudgetRoute(
                        viewModel, padding,
                        onOpenCategory = { id, month -> navController.navigate("$ROUTE_CATEGORY/$id/$month") },
                        onClassify = { month -> navController.openFilteredEntries(month, null, EntrySituation.ToClassify) },
                        onRegister = register,
                        onOpenCommitments = { month -> navController.navigate("$ROUTE_COMMITMENTS?$ARG_MONTH=$month") },
                        onOpenIncomes = { month -> navController.navigate("$ROUTE_INCOMES?$ARG_MONTH=$month") },
                        onOpenCard = { navController.navigate(ROUTE_CARD) },
                    )
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
                    )
                }
            }
            navigation(startDestination = Root.Entries.start, route = Root.Entries.graph) {
                composable(Root.Entries.start) {
                    val viewModel: EntriesViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                val month = container.clock.currentMonth()
                                EntriesViewModel(container, createSavedStateHandle(), EntriesFilterState(month, month))
                            }
                        },
                    )
                    EntriesRoute(viewModel, padding, onOpenEntry = openEntry, onNew = register)
                }
            }
            navigation(startDestination = Root.Review.start, route = Root.Review.graph) {
                composable(Root.Review.start) {
                    val viewModel: ReviewViewModel = viewModel(factory = viewModelFactory { initializer { ReviewViewModel(container.sync) } })
                    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
                    RefreshOnResume(viewModel::refresh)
                    ProvisionalRoot(
                        stringResource(R.string.review_title), stringResource(R.string.review_placeholder), padding, register,
                        extra = { ReviewConflicts(conflicts) { id -> navController.navigate("$ROUTE_CONFLICT/${Uri.encode(id)}") } },
                    )
                }
            }
            navigation(startDestination = Root.More.start, route = Root.More.graph) {
                composable(Root.More.start) {
                    val scope = rememberCoroutineScope()
                    var signOutPending by remember { mutableStateOf<Int?>(null) }
                    MoreScreen(
                        padding,
                        actions = MoreActions(
                            onCategories = { navController.navigate(ROUTE_CATEGORIES) },
                            onAccounts = { navController.navigate(ROUTE_ACCOUNTS) },
                            onLimits = { navController.navigate(ROUTE_LIMITS) },
                            onCard = { navController.navigate(ROUTE_CARD) },
                            onIncomes = { navController.navigate(ROUTE_INCOMES) },
                            onCommitments = { navController.navigate(ROUTE_COMMITMENTS) },
                            onRegister = register,
                            onSpace = { navController.navigate(ROUTE_SPACE) },
                            onAppearance = { navController.navigate("appearance") },
                            onSignOut = {
                                // Sempre uma decisão explícita, com o número de operações por enviar à vista.
                                scope.launch {
                                    val summary = container.access.runtime.value?.syncStore?.summary()
                                    signOutPending = summary?.let { it.awaitingOperations + it.openConflicts + it.rejectedOperations } ?: 0
                                }
                            },
                            onRenew = { navController.navigate(ROUTE_ACCESS) },
                            onSync = { navController.navigate(ROUTE_SYNC) },
                        ),
                        account = access.account,
                        sessionState = access.session,
                        syncStatus = syncStatus,
                    )
                    signOutPending?.let { pending ->
                        SignOutDialog(
                            pending,
                            onConfirm = { signOutPending = null; scope.launch { container.access.signOut(confirmPending = true) } },
                            onDismiss = { signOutPending = null },
                        )
                    }
                }
            }

            // Lista aberta por outra tela: filtros já aplicados e voltar retorna à origem; a aba mantém a sua lista.
            composable(
                "$ROUTE_ENTRIES_FILTERED?$ARG_FILTER_MONTH={$ARG_FILTER_MONTH}&$ARG_FILTER_CATEGORY={$ARG_FILTER_CATEGORY}&$ARG_FILTER_SITUATION={$ARG_FILTER_SITUATION}",
                arguments = listOf(
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
                "$ROUTE_COMMITMENTS?$ARG_MONTH={$ARG_MONTH}",
                arguments = listOf(navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: CommitmentsViewModel = viewModel(
                    factory = viewModelFactory { initializer { CommitmentsViewModel(container, createSavedStateHandle()) } },
                )
                CommitmentsRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate("$ROUTE_COMMITMENT/${Uri.encode(id)}") },
                    onRegister = register,
                    onOpenCard = { navController.navigate(ROUTE_CARD) },
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
            composable(ROUTE_FIRST_STEPS) {
                val viewModel: FirstStepsViewModel = viewModel(
                    factory = viewModelFactory { initializer { FirstStepsViewModel(container, container.access) } },
                )
                val goToChat: () -> Unit = {
                    navController.navigate(Root.Chat.graph) { popUpTo(ROUTE_FIRST_STEPS) { inclusive = true } }
                }
                FirstStepsRoute(
                    viewModel, padding,
                    StepActions(
                        onCategories = { navController.navigate(ROUTE_CATEGORIES) },
                        onCard = { navController.navigate("$ROUTE_CARD?initialSetup=true") },
                        onAccounts = { navController.navigate(ROUTE_ACCOUNTS) },
                        onLimits = { navController.navigate("$ROUTE_LIMITS?initialSetup=true") },
                        onSpace = { navController.navigate(ROUTE_SPACE) },
                        onStart = { viewModel.start(goToChat) },
                        onRegisterManually = { viewModel.start { navController.navigate(ROUTE_REGISTER) { popUpTo(ROUTE_FIRST_STEPS) { inclusive = true } } } },
                        onBack = if (navController.previousBackStackEntry != null) ({ navController.popBackStack() }) else null,
                    ),
                )
            }
            composable(ROUTE_CATEGORIES) {
                val viewModel: CategoriesViewModel = viewModel(
                    factory = viewModelFactory { initializer { CategoriesViewModel(container, createSavedStateHandle()) } },
                )
                CategoriesRoute(viewModel, padding, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_ACCOUNTS) {
                val viewModel: AccountsViewModel = viewModel(
                    factory = viewModelFactory { initializer { AccountsViewModel(container, createSavedStateHandle()) } },
                )
                AccountsRoute(viewModel, padding, onBack = { navController.popBackStack() })
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
                CardRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpenInvoice = { month -> navController.navigate("$ROUTE_INVOICE/$month") },
                    onToConfirm = { navController.openFilteredEntries(container.clock.currentMonth(), null, EntrySituation.InvoiceLinkToConfirm) },
                    initialSetup = cardEntry.arguments?.getBoolean("initialSetup") == true,
                )
            }
            composable("appearance") {
                val theme by container.theme.preference.collectAsStateWithLifecycle()
                val corners by container.theme.corners.collectAsStateWithLifecycle()
                AppearanceScreen(padding, theme, corners, container.theme::set, container.theme::setCorners, onBack = { navController.popBackStack() })
            }
            composable(
                "$ROUTE_INVOICE/{${InvoiceViewModel.ARG_MONTH}}",
                arguments = listOf(navArgument(InvoiceViewModel.ARG_MONTH) { type = NavType.StringType }),
            ) {
                val viewModel: InvoiceViewModel = viewModel(
                    factory = viewModelFactory { initializer { InvoiceViewModel(container, createSavedStateHandle()) } },
                )
                InvoiceRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onSeeEntries = { month -> navController.openFilteredEntries(month, null, null) },
                )
            }
            composable(
                "$ROUTE_INCOMES?$ARG_MONTH={$ARG_MONTH}",
                arguments = listOf(navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: IncomesViewModel = viewModel(
                    factory = viewModelFactory { initializer { IncomesViewModel(container, createSavedStateHandle()) } },
                )
                IncomesRoute(
                    viewModel, padding, onBack = { navController.popBackStack() }, onOpen = openEntry,
                    onAdd = { navController.navigate("$ROUTE_REGISTER?$ARG_REGISTER_KIND=income") },
                )
            }
            composable(ROUTE_MODEL) {
                ProvisionalRoot(
                    stringResource(R.string.model_title), stringResource(R.string.model_provisional), padding, register,
                    extra = { TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_back)) } },
                )
            }
            composable(
                "$ROUTE_REGISTER?$ARG_REGISTER_KIND={$ARG_REGISTER_KIND}&$ARG_REGISTER_FORM={$ARG_REGISTER_FORM}&$ARG_REGISTER_CHAT={$ARG_REGISTER_CHAT}",
                arguments = listOf(
                    navArgument(ARG_REGISTER_KIND) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_REGISTER_FORM) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(ARG_REGISTER_CHAT) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                val viewModel: RegisterViewModel = viewModel(
                    factory = viewModelFactory { initializer { RegisterViewModel(container, createSavedStateHandle()) } },
                )
                RegisterRoute(viewModel, padding, onExit = { navController.popBackStack() })
            }
        }
        }
    }
}

private fun NavHostController.openRoot(root: Root) = navigate(root.graph) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.openFilteredEntries(month: YearMonth, categoryId: String?, situation: EntrySituation?) {
    val query = buildList {
        add("$ARG_FILTER_MONTH=$month")
        categoryId?.let { add("$ARG_FILTER_CATEGORY=${Uri.encode(it)}") }
        situation?.let { add("$ARG_FILTER_SITUATION=${it.name}") }
    }.joinToString("&")
    navigate("$ROUTE_ENTRIES_FILTERED?$query")
}

private fun NavBackStackEntry.initialFilter(default: YearMonth): EntriesFilterState {
    val month = arguments?.getString(ARG_FILTER_MONTH)?.let(YearMonth::parse) ?: default
    return EntriesFilterState(
        from = month, to = month, criterion = DateCriterion.BudgetMonth,
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
