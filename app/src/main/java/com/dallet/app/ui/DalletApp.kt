package com.dallet.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dallet.app.R
import com.dallet.app.ledger.Ledger
import com.dallet.app.ui.add.AddExpenseScreen
import com.dallet.app.ui.edit.EditOperationScreen
import com.dallet.app.ui.history.HistoryScreen
import com.dallet.app.ui.home.HomeScreen
import com.dallet.app.ui.navigation.DalletDestination
import com.dallet.app.ui.onboarding.OnboardingScreen

private const val EDIT_ROUTE = "edit/{operationId}"

private val placeholderByRoute = mapOf(
    DalletDestination.Analytics.route to R.string.placeholder_analytics,
    DalletDestination.More.route to R.string.placeholder_more
)

@Composable
fun DalletApp(
    ledger: Ledger,
    viewModel: DalletViewModel = viewModel(factory = DalletViewModel.factory(ledger))
) {
    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val onboardingState by viewModel.onboarding.collectAsStateWithLifecycle()
    val addExpenseState by viewModel.addExpense.collectAsStateWithLifecycle()
    val historyState by viewModel.history.collectAsStateWithLifecycle()
    val editState by viewModel.edit.collectAsStateWithLifecycle()

    if (homeState.needsOnboarding) {
        OnboardingScreen(
            state = onboardingState,
            onCashChange = viewModel::onCashInputChange,
            onCardChange = viewModel::onCardInputChange,
            onSubmit = viewModel::submitOnboarding
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DalletDestination.Home.route

    fun navigateTo(destination: DalletDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openEdit(operationId: Long) {
        navController.navigate("edit/$operationId")
    }

    LaunchedEffect(addExpenseState.saved) {
        if (addExpenseState.saved) {
            viewModel.consumeExpenseSaved()
            navigateTo(DalletDestination.Home)
        }
    }

    LaunchedEffect(editState.done) {
        if (editState.done) {
            viewModel.consumeEditDone()
            navController.popBackStack()
        }
    }

    val hideBottomBar = currentRoute?.startsWith("edit/") == true

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                DalletBottomBar(
                    currentRoute = currentRoute ?: DalletDestination.Home.route,
                    onNavigate = ::navigateTo
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DalletDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(DalletDestination.Home.route) {
                HomeScreen(
                    state = homeState,
                    onExpenseClick = ::openEdit
                )
            }
            composable(DalletDestination.History.route) {
                LaunchedEffect(Unit) { viewModel.refreshHistory() }
                HistoryScreen(
                    state = historyState,
                    onOperationClick = ::openEdit
                )
            }
            composable(DalletDestination.Add.route) {
                LaunchedEffect(Unit) { viewModel.prepareAddExpense() }
                AddExpenseScreen(
                    state = addExpenseState,
                    onKindSelected = viewModel::onAddKindSelected,
                    onAccountSelected = viewModel::onExpenseAccountSelected,
                    onFromAccountSelected = viewModel::onFromAccountSelected,
                    onToAccountSelected = viewModel::onToAccountSelected,
                    onCategorySelected = viewModel::onExpenseCategorySelected,
                    onAmountChange = viewModel::onExpenseAmountChange,
                    onDateChange = viewModel::onExpenseDateChange,
                    onCommentChange = viewModel::onExpenseCommentChange,
                    onSubmit = viewModel::submitExpense
                )
            }
            composable(
                route = EDIT_ROUTE,
                arguments = listOf(navArgument("operationId") { type = NavType.LongType })
            ) { entry ->
                val operationId = entry.arguments?.getLong("operationId") ?: return@composable
                LaunchedEffect(operationId) { viewModel.loadEditOperation(operationId) }
                EditOperationScreen(
                    state = editState,
                    onAccountSelected = viewModel::onEditAccountSelected,
                    onFromAccountSelected = viewModel::onEditFromAccountSelected,
                    onToAccountSelected = viewModel::onEditToAccountSelected,
                    onCategorySelected = viewModel::onEditCategorySelected,
                    onAmountChange = viewModel::onEditAmountChange,
                    onDateChange = viewModel::onEditDateChange,
                    onCommentChange = viewModel::onEditCommentChange,
                    onSave = viewModel::saveEdit,
                    onDelete = viewModel::deleteEdit
                )
            }
            placeholderByRoute.forEach { (route, stringRes) ->
                composable(route) {
                    PlaceholderScreen(text = stringResource(stringRes))
                }
            }
        }
    }
}

@Composable
private fun DalletBottomBar(
    currentRoute: String,
    onNavigate: (DalletDestination) -> Unit
) {
    NavigationBar {
        DalletDestination.bottomBarItems.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route ||
                    (destination == DalletDestination.History && currentRoute.startsWith("edit/")),
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes)
                    )
                },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
    }
}

private val DalletDestination.icon: ImageVector
    get() = when (this) {
        DalletDestination.Home -> Icons.Filled.Home
        DalletDestination.History -> Icons.Filled.History
        DalletDestination.Add -> Icons.Filled.Add
        DalletDestination.Analytics -> Icons.Filled.BarChart
        DalletDestination.More -> Icons.Filled.MoreHoriz
    }

@Composable
private fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}
