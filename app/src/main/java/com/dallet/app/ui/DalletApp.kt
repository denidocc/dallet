package com.dallet.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.dallet.app.ledger.ThemeMode
import com.dallet.app.ledger.Currency
import com.dallet.app.ui.settings.SettingsScreen
import com.dallet.app.ledger.ShoppingService
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.shopping.ProductDetailScreen
import com.dallet.app.ui.shopping.ShoppingHubScreen
import com.dallet.app.ui.shopping.ShoppingListActions
import com.dallet.app.ui.shopping.ShoppingListScreen
import com.dallet.app.ui.shopping.ShoppingViewModel
import com.dallet.app.security.PinLock
import com.dallet.app.ui.accounts.AccountEditorScreen
import com.dallet.app.ui.accounts.AccountsScreen
import com.dallet.app.ui.add.AddExpenseScreen
import com.dallet.app.ui.analytics.AnalyticsScreen
import com.dallet.app.ui.categories.CategoriesScreen
import com.dallet.app.ui.categories.CategoryEditorScreen
import com.dallet.app.ui.common.BackupFiles
import com.dallet.app.ui.edit.EditOperationScreen
import com.dallet.app.ui.history.HistoryScreen
import com.dallet.app.ui.home.HomeScreen
import com.dallet.app.ui.lock.LockScreen
import com.dallet.app.ui.more.MoreScreen
import com.dallet.app.ui.navigation.DalletDestination
import com.dallet.app.ui.onboarding.OnboardingScreen
import com.dallet.app.ui.security.SecurityScreen
import java.time.LocalDate

private const val NAV_FADE_IN_MS = 110
private const val NAV_FADE_OUT_MS = 90
private const val EDIT_ROUTE = "edit/{operationId}"
private const val ACCOUNTS_ROUTE = "accounts"
private const val ACCOUNT_NEW_ROUTE = "account/new"
private const val ACCOUNT_EDIT_ROUTE = "account/{accountId}"
private const val SECURITY_ROUTE = "security"
private const val SETTINGS_ROUTE = "settings"
private const val SHOPPING_ROUTE = "shopping"
private const val SHOPPING_LIST_ROUTE = "shopping/{listId}"
private const val PRODUCT_ROUTE = "shopping/product/{productId}"
private const val CATEGORIES_ROUTE = "categories"
private const val CATEGORY_NEW_ROUTE = "category/new"
private const val CATEGORY_EDIT_ROUTE = "category/{categoryId}"


@Composable
fun DalletApp(
    ledger: Ledger,
    pinLock: PinLock,
    shopping: ShoppingService,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    viewModel: DalletViewModel = viewModel(factory = DalletViewModel.factory(ledger, pinLock)),
    shoppingViewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.factory(ledger, shopping))
) {
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalCurrency provides currency) {
        DalletAppContent(viewModel, shoppingViewModel, themeMode, onThemeModeChange)
    }
}

@Composable
private fun DalletAppContent(
    viewModel: DalletViewModel,
    shoppingViewModel: ShoppingViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val onboardingState by viewModel.onboarding.collectAsStateWithLifecycle()
    val addExpenseState by viewModel.addExpense.collectAsStateWithLifecycle()
    val historyState by viewModel.history.collectAsStateWithLifecycle()
    val editState by viewModel.edit.collectAsStateWithLifecycle()
    val accountsState by viewModel.accounts.collectAsStateWithLifecycle()
    val accountEditorState by viewModel.accountEditor.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categories.collectAsStateWithLifecycle()
    val categoryEditorState by viewModel.categoryEditor.collectAsStateWithLifecycle()
    val analyticsState by viewModel.analytics.collectAsStateWithLifecycle()
    val dataState by viewModel.data.collectAsStateWithLifecycle()
    val lockState by viewModel.lock.collectAsStateWithLifecycle()
    val securityState by viewModel.security.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup { text -> BackupFiles.write(context, uri, text) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup { BackupFiles.read(context, uri) }
        }
    }
    val csvReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportReport(ReportFormat.CSV) { bytes ->
                BackupFiles.writeBytes(context, uri, bytes)
            }
        }
    }
    val pdfReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportReport(ReportFormat.PDF) { bytes ->
                BackupFiles.writeBytes(context, uri, bytes)
            }
        }
    }
    val launchReport = { format: ReportFormat ->
        viewModel.consumeDataMessage()
        when (format) {
            ReportFormat.CSV -> csvReportLauncher.launch("Dallet-report-${LocalDate.now()}.csv")
            ReportFormat.PDF -> pdfReportLauncher.launch("Dallet-report-${LocalDate.now()}.pdf")
        }
    }
    val launchExport = {
        viewModel.consumeDataMessage()
        exportLauncher.launch("Dallet-backup-${LocalDate.now()}.json")
    }
    val launchImport = {
        viewModel.consumeDataMessage()
        importLauncher.launch(arrayOf("*/*"))
    }

    if (lockState.locked) {
        LockScreen(
            state = lockState,
            biometricEnabled = securityState.biometricEnabled,
            onDigit = viewModel::onLockDigit,
            onBackspace = viewModel::onLockBackspace,
            onBiometricSuccess = viewModel::unlockWithBiometric,
            onResetConfirmed = viewModel::resetFromLock
        )
        return
    }

    if (homeState.needsOnboarding) {
        OnboardingScreen(
            state = onboardingState,
            dataState = dataState,
            onCurrencySelected = viewModel::onCurrencySelected,
            onTemplateToggled = viewModel::onTemplateToggled,
            onTemplateOpeningChange = viewModel::onTemplateOpeningChange,
            onCustomNameChange = viewModel::onCustomNameChange,
            onCustomOpeningChange = viewModel::onCustomOpeningChange,
            onSubmit = viewModel::submitOnboarding,
            onRestoreClick = launchImport
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DalletDestination.Home.route
    var showAddSheet by remember { mutableStateOf(false) }
    val hubState by shoppingViewModel.hub.collectAsStateWithLifecycle()
    val editorState by shoppingViewModel.editor.collectAsStateWithLifecycle()
    val closeFormState by shoppingViewModel.closeForm.collectAsStateWithLifecycle()
    val productState by shoppingViewModel.product.collectAsStateWithLifecycle()

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

    LaunchedEffect(accountEditorState.done) {
        if (accountEditorState.done) {
            viewModel.consumeAccountEditorDone()
            navController.popBackStack()
        }
    }

    LaunchedEffect(categoryEditorState.done) {
        if (categoryEditorState.done) {
            viewModel.consumeCategoryEditorDone()
            navController.popBackStack()
        }
    }

    val hideBottomBar = currentRoute?.startsWith("edit/") == true ||
        currentRoute == SECURITY_ROUTE ||
        currentRoute == SETTINGS_ROUTE ||
        currentRoute?.startsWith("shopping") == true ||
        currentRoute?.startsWith("account") == true ||
        currentRoute?.startsWith("categor") == true

    if (showAddSheet) {
        AddChoiceSheet(
            onDismiss = { showAddSheet = false },
            onOperation = {
                showAddSheet = false
                navigateTo(DalletDestination.Add)
            },
            onShopping = {
                showAddSheet = false
                shoppingViewModel.createList { id -> navController.navigate("shopping/$id") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideBottomBar) {
                DalletBottomBar(
                    currentRoute = currentRoute ?: DalletDestination.Home.route,
                    onNavigate = { destination ->
                        if (destination == DalletDestination.Add) {
                            showAddSheet = true
                        } else {
                            navigateTo(destination)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
        NavHost(
            navController = navController,
            startDestination = DalletDestination.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(NAV_FADE_IN_MS)) },
            exitTransition = { fadeOut(tween(NAV_FADE_OUT_MS)) },
            popEnterTransition = { fadeIn(tween(NAV_FADE_IN_MS)) },
            popExitTransition = { fadeOut(tween(NAV_FADE_OUT_MS)) }
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
                    onTimeChange = viewModel::onExpenseTimeChange,
                    onCommentChange = viewModel::onExpenseCommentChange,
                    onSubmit = viewModel::submitExpense
                )
            }
            composable(DalletDestination.Analytics.route) {
                LaunchedEffect(Unit) { viewModel.refreshAnalytics() }
                AnalyticsScreen(
                    state = analyticsState,
                    onPeriodSelected = viewModel::onAnalyticsPeriodSelected,
                    onModeSelected = viewModel::onAnalyticsModeSelected,
                    onStep = viewModel::onAnalyticsStep,
                    dataState = dataState,
                    onExportReport = launchReport
                )
            }
            composable(DalletDestination.More.route) {
                MoreScreen(
                    dataState = dataState,
                    onAccountsClick = { navController.navigate(ACCOUNTS_ROUTE) },
                    onCategoriesClick = { navController.navigate(CATEGORIES_ROUTE) },
                    onSecurityClick = { navController.navigate(SECURITY_ROUTE) },
                    onSettingsClick = { navController.navigate(SETTINGS_ROUTE) },
                    onShoppingClick = {
                        shoppingViewModel.refreshHub()
                        navController.navigate(SHOPPING_ROUTE)
                    },
                    onExportClick = launchExport,
                    onImportClick = launchImport,
                    onResetConfirmed = viewModel::resetAll,
                    onDataMessageShown = viewModel::consumeDataMessage
                )
            }
            composable(SETTINGS_ROUTE) {
                val currentCurrency by viewModel.currency.collectAsStateWithLifecycle()
                SettingsScreen(
                    currency = currentCurrency,
                    themeMode = themeMode,
                    onCurrencyChange = viewModel::setMainCurrency,
                    onThemeModeChange = onThemeModeChange
                )
            }
            composable(SECURITY_ROUTE) {
                SecurityScreen(
                    state = securityState,
                    onExportClick = launchExport,
                    onEnablePin = viewModel::enablePin,
                    onDisablePin = viewModel::disablePin,
                    onBiometricChange = viewModel::setBiometricEnabled
                )
            }
            composable(SHOPPING_ROUTE) {
                LaunchedEffect(Unit) { shoppingViewModel.refreshHub() }
                ShoppingHubScreen(
                    state = hubState,
                    onTabSelected = shoppingViewModel::selectTab,
                    onNewList = {
                        shoppingViewModel.createList { id ->
                            navController.navigate("shopping/$id")
                        }
                    },
                    onListClick = { id -> navController.navigate("shopping/$id") },
                    onProductClick = { id ->
                        shoppingViewModel.openProduct(id)
                        navController.navigate("shopping/product/$id")
                    },
                    onDeleteProduct = shoppingViewModel::deleteProduct
                )
            }
            composable(
                route = PRODUCT_ROUTE,
                arguments = listOf(navArgument("productId") { type = NavType.LongType })
            ) { entry ->
                val productId = entry.arguments?.getLong("productId") ?: return@composable
                LaunchedEffect(productId) { shoppingViewModel.openProduct(productId) }
                ProductDetailScreen(state = productState)
            }
            composable(
                route = SHOPPING_LIST_ROUTE,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { entry ->
                val listId = entry.arguments?.getLong("listId") ?: return@composable
                LaunchedEffect(listId) { shoppingViewModel.openList(listId) }
                ShoppingListScreen(
                    state = editorState,
                    closeForm = closeFormState,
                    actions = ShoppingListActions(
                        onSaveInfo = shoppingViewModel::saveListInfo,
                        onNameChange = shoppingViewModel::onNameChange,
                        onQuantityChange = shoppingViewModel::onQuantityChange,
                        onUnitSelected = shoppingViewModel::onUnitSelected,
                        onPriceChange = shoppingViewModel::onPriceChange,
                        onSuggestionSelected = shoppingViewModel::onSuggestionSelected,
                        onAddItem = shoppingViewModel::addItem,
                        onToggle = shoppingViewModel::toggleItem,
                        onUpdateItem = shoppingViewModel::updateItem,
                        onDeleteItem = shoppingViewModel::deleteItem,
                        onSavePrices = shoppingViewModel::savePrices,
                        onPrepareClose = shoppingViewModel::prepareClose,
                        onCloseAccount = shoppingViewModel::onCloseAccountSelected,
                        onCloseCategory = shoppingViewModel::onCloseCategorySelected,
                        onCloseDate = shoppingViewModel::onCloseDateChange,
                        onCloseTime = shoppingViewModel::onCloseTimeChange,
                        onCloseCarryOver = shoppingViewModel::onCloseCarryOverChange,
                        onConfirmClose = {
                            shoppingViewModel.closePurchase { viewModel.refreshHome() }
                        },
                        onDeleteList = {
                            shoppingViewModel.deleteList(listId) { navController.popBackStack() }
                        },
                        onGoHome = { navigateTo(DalletDestination.Home) },
                        onGoShopping = {
                            navController.navigate(SHOPPING_ROUTE) {
                                popUpTo(SHOPPING_ROUTE) { inclusive = true }
                            }
                        },
                        onOpenExpense = { id -> openEdit(id) }
                    )
                )
            }
            composable(ACCOUNTS_ROUTE) {
                LaunchedEffect(Unit) { viewModel.refreshAccounts() }
                AccountsScreen(
                    state = accountsState,
                    onAddClick = {
                        viewModel.prepareNewAccount()
                        navController.navigate(ACCOUNT_NEW_ROUTE)
                    },
                    onAccountClick = { accountId ->
                        viewModel.prepareEditAccount(accountId)
                        navController.navigate("account/$accountId")
                    }
                )
            }
            composable(ACCOUNT_NEW_ROUTE) {
                AccountEditorScreen(
                    state = accountEditorState,
                    onNameChange = viewModel::onAccountNameChange,
                    onOpeningChange = viewModel::onAccountOpeningChange,
                    onSave = viewModel::saveAccount,
                    onDelete = viewModel::deleteAccount
                )
            }
            composable(
                route = ACCOUNT_EDIT_ROUTE,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType })
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: return@composable
                LaunchedEffect(accountId) { viewModel.prepareEditAccount(accountId) }
                AccountEditorScreen(
                    state = accountEditorState,
                    onNameChange = viewModel::onAccountNameChange,
                    onOpeningChange = viewModel::onAccountOpeningChange,
                    onSave = viewModel::saveAccount,
                    onDelete = viewModel::deleteAccount
                )
            }
            composable(CATEGORIES_ROUTE) {
                LaunchedEffect(Unit) { viewModel.refreshCategories() }
                CategoriesScreen(
                    state = categoriesState,
                    onKindSelected = viewModel::onCategoriesKindSelected,
                    onAddClick = {
                        viewModel.prepareNewCategory()
                        navController.navigate(CATEGORY_NEW_ROUTE)
                    },
                    onCategoryClick = { categoryId ->
                        viewModel.prepareEditCategory(categoryId)
                        navController.navigate("category/$categoryId")
                    }
                )
            }
            composable(CATEGORY_NEW_ROUTE) {
                CategoryEditorScreen(
                    state = categoryEditorState,
                    onNameChange = viewModel::onCategoryNameChange,
                    onColorSelected = viewModel::onCategoryColorSelected,
                    onIconSelected = viewModel::onCategoryIconSelected,
                    onSave = viewModel::saveCategory,
                    onDelete = viewModel::deleteCategory
                )
            }
            composable(
                route = CATEGORY_EDIT_ROUTE,
                arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
            ) { entry ->
                val categoryId = entry.arguments?.getLong("categoryId") ?: return@composable
                LaunchedEffect(categoryId) { viewModel.prepareEditCategory(categoryId) }
                CategoryEditorScreen(
                    state = categoryEditorState,
                    onNameChange = viewModel::onCategoryNameChange,
                    onColorSelected = viewModel::onCategoryColorSelected,
                    onIconSelected = viewModel::onCategoryIconSelected,
                    onSave = viewModel::saveCategory,
                    onDelete = viewModel::deleteCategory
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
                    onTimeChange = viewModel::onEditTimeChange,
                    onCommentChange = viewModel::onEditCommentChange,
                    onSave = viewModel::saveEdit,
                    onDelete = viewModel::deleteEdit
                )
            }
        }
        if (hideBottomBar) {
            BackArrow(
                onBack = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChoiceSheet(
    onDismiss: () -> Unit,
    onOperation: () -> Unit,
    onShopping: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            SurfaceCard(onClick = onOperation) {
                Text(
                    text = stringResource(R.string.add_sheet_operation),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.add_sheet_operation_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SurfaceCard(onClick = onShopping) {
                Text(
                    text = stringResource(R.string.add_sheet_shopping),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.add_sheet_shopping_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackArrow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back)
        )
    }
}

@Composable
private fun DalletBottomBar(
    currentRoute: String,
    onNavigate: (DalletDestination) -> Unit
) {
    NavigationBar {
        DalletDestination.bottomBarItems.forEach { destination ->
            val selected = currentRoute == destination.route ||
                (destination == DalletDestination.History && currentRoute.startsWith("edit/"))
            val isAdd = destination == DalletDestination.Add
            NavigationBarItem(
                selected = selected && !isAdd,
                onClick = { onNavigate(destination) },
                icon = {
                    if (isAdd) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(destination.labelRes),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes)
                        )
                    }
                },
                label = if (isAdd) {
                    null
                } else {
                    {
                        Text(
                            text = stringResource(destination.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                alwaysShowLabel = true
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
