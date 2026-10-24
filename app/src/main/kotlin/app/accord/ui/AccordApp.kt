package app.accord.ui

import android.net.Uri
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
import app.accord.ui.nav.ARG_REGISTER_KIND
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
import app.accord.ui.register.RegisterRoute
import app.accord.ui.register.RegisterViewModel
import java.time.YearMonth

@Composable
fun AccordApp(container: AppContainer) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    // A barra inferior só aparece nas raízes; detalhes e formulários escondem-na.
    val onRoot = Root.entries.any { destination?.route == it.start }

    // Avisos depois de salvar, com Desfazer; ficam aqui para sobreviver à troca de tela.
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // O aviso da ação anterior não cobre o botão Salvar do formulário que abre em seguida.
    val inForm = destination?.route == ROUTE_REGISTER
    LaunchedEffect(inForm) { if (inForm) snackbar.currentSnackbarData?.dismiss() }
    LaunchedEffect(container) {
        container.feedback.events.collect { event ->
            launch {
                val message = context.getString(event.messageRes, *event.args.toTypedArray())
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
        NavHost(navController, startDestination = Root.Chat.graph) {
            val register = { navController.navigate(ROUTE_REGISTER) }
            val openEntry = { id: String -> navController.navigate("$ROUTE_ENTRY/${Uri.encode(id)}") }

            navigation(startDestination = Root.Chat.start, route = Root.Chat.graph) {
                composable(Root.Chat.start) {
                    ProvisionalRoot(stringResource(R.string.chat_title), stringResource(R.string.chat_placeholder), padding, register)
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
                    ProvisionalRoot(stringResource(R.string.review_title), stringResource(R.string.review_placeholder), padding, register)
                }
            }
            navigation(startDestination = Root.More.start, route = Root.More.graph) {
                composable(Root.More.start) {
                    val theme by container.theme.preference.collectAsStateWithLifecycle()
                    MoreScreen(
                        padding, theme, onTheme = container.theme::set,
                        actions = MoreActions(
                            onCategories = { navController.navigate(ROUTE_CATEGORIES) },
                            onAccounts = { navController.navigate(ROUTE_ACCOUNTS) },
                            onLimits = { navController.navigate(ROUTE_LIMITS) },
                            onCard = { navController.navigate(ROUTE_CARD) },
                            onIncomes = { navController.navigate(ROUTE_INCOMES) },
                            onCommitments = { navController.navigate(ROUTE_COMMITMENTS) },
                            onRegister = register,
                        ),
                    )
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
                "$ROUTE_LIMITS?$ARG_MONTH={$ARG_MONTH}",
                arguments = listOf(navArgument(ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: LimitsViewModel = viewModel(
                    factory = viewModelFactory { initializer { LimitsViewModel(container, createSavedStateHandle()) } },
                )
                LimitsRoute(viewModel, padding, onExit = { navController.popBackStack() })
            }
            composable(ROUTE_CARD) {
                val viewModel: CardViewModel = viewModel(
                    factory = viewModelFactory { initializer { CardViewModel(container, createSavedStateHandle()) } },
                )
                CardRoute(
                    viewModel, padding, onBack = { navController.popBackStack() },
                    onOpenInvoice = { month -> navController.navigate("$ROUTE_INVOICE/$month") },
                    onToConfirm = { navController.openFilteredEntries(container.clock.currentMonth(), null, EntrySituation.InvoiceLinkToConfirm) },
                )
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
            composable(
                "$ROUTE_REGISTER?$ARG_REGISTER_KIND={$ARG_REGISTER_KIND}",
                arguments = listOf(navArgument(ARG_REGISTER_KIND) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                val viewModel: RegisterViewModel = viewModel(
                    factory = viewModelFactory { initializer { RegisterViewModel(container, createSavedStateHandle()) } },
                )
                RegisterRoute(viewModel, padding, onExit = { navController.popBackStack() })
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
