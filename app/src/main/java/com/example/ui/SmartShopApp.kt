package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.data.security.UserRole
import com.example.ui.components.InvoiceReceiptDialog
import com.example.ui.components.StatusPill
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.CreditManagementScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SalesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltContainer
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.NavyBase
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavyVoid

private fun sectionIcon(section: AppSection): ImageVector = when (section) {
    AppSection.SALES -> Icons.Default.PointOfSale
    AppSection.PRODUCTS -> Icons.Default.Inventory
    AppSection.INVENTORY -> Icons.Default.Inventory2
    AppSection.CUSTOMERS -> Icons.Default.People
    AppSection.CREDIT -> Icons.Default.AccountBalanceWallet
    AppSection.EXPENSES -> Icons.Default.MoneyOff
    AppSection.PURCHASES -> Icons.Default.LocalShipping
    AppSection.REPORTS -> Icons.Default.Assessment
    AppSection.AI_ASSISTANT -> Icons.Default.AutoAwesome
    AppSection.SETTINGS -> Icons.Default.Settings
}

private val PRIMARY_MOBILE_TABS = listOf(
    AppSection.SALES,
    AppSection.PRODUCTS,
    AppSection.INVENTORY,
    AppSection.CREDIT,
    AppSection.REPORTS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartShopApp(
    viewModel: ShopViewModel,
    aiViewModel: AiViewModel
) {
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val lastCheckout by viewModel.lastCheckoutResult.collectAsStateWithLifecycle()
    val inspectedInvoice by viewModel.inspectedInvoice.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var lastErrorFlag by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        feedback?.let { msg ->
            lastErrorFlag = msg.isError
            snackbarHostState.showSnackbar(
                message = msg.text,
                duration = if (msg.isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            viewModel.clearFeedback()
        }
    }

    if (isLocked) {
        PinLockScreen(
            shopName = settings.shopName,
            onSubmitPin = { pin, onResult -> viewModel.unlockWithPin(pin, onResult) }
        )
        return
    }

    // Back press on secondary sections returns cleanly to Sales POS
    BackHandler(enabled = currentSection != AppSection.SALES) {
        viewModel.selectSection(AppSection.SALES)
    }

    val activeRole = UserRole.fromCode(settings.activeRole)
    val canViewProfit = RolePermissions.hasPermission(activeRole, ShopPermission.VIEW_PROFIT_AND_COSTS)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyVoid, NavyBase, NavyVoid)
                )
            )
    ) {
        val isExpanded = maxWidth >= 760.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isExpanded) {
                NavigationRail(
                    containerColor = NavySurface,
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    AppSection.entries.forEach { sec ->
                        NavigationRailItem(
                            selected = currentSection == sec,
                            onClick = { viewModel.selectSection(sec) },
                            icon = { Icon(sectionIcon(sec), contentDescription = sec.labelAr) },
                            label = { Text(sec.labelAr, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("nav_rail_${sec.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = NavyVoid,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = NavySurface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = CobaltPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = settings.shopName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentSection.labelAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanSecondary
                                    )
                                }
                            }
                        },
                        actions = {
                            StatusPill(
                                text = if (activeRole == UserRole.OWNER) "المالك" else "البائع",
                                color = if (activeRole == UserRole.OWNER) EmeraldProfit else AmberWarning
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.selectSection(AppSection.AI_ASSISTANT) },
                                modifier = Modifier.testTag("open_ai_assistant_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.nav_ai_assistant),
                                    tint = if (currentSection == AppSection.AI_ASSISTANT) CobaltPrimary else CyanSecondary
                                )
                            }
                            if (!isExpanded) {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.testTag("open_all_sections_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = stringResource(R.string.nav_more),
                                        tint = CyanSecondary
                                    )
                                }
                            }
                            if (settings.pinEnabled) {
                                IconButton(
                                    onClick = { viewModel.lockSession() },
                                    modifier = Modifier.testTag("lock_session_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = stringResource(R.string.cd_lock),
                                        tint = AmberWarning
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isExpanded) {
                        NavigationBar(
                            containerColor = NavySurface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            PRIMARY_MOBILE_TABS.forEach { sec ->
                                NavigationBarItem(
                                    selected = currentSection == sec,
                                    onClick = { viewModel.selectSection(sec) },
                                    icon = { Icon(sectionIcon(sec), contentDescription = sec.labelAr) },
                                    label = {
                                        Text(
                                            text = sec.labelAr,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CobaltPrimary,
                                        selectedTextColor = CobaltPrimary,
                                        indicatorColor = CobaltContainer
                                    ),
                                    modifier = Modifier.testTag("nav_bar_${sec.route}")
                                )
                            }
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = if (lastErrorFlag) CrimsonDanger else EmeraldProfit,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentSection) {
                        AppSection.SALES -> SalesScreen(viewModel)
                        AppSection.PRODUCTS -> ProductsScreen(viewModel)
                        AppSection.INVENTORY -> InventoryScreen(viewModel)
                        AppSection.CUSTOMERS -> CustomersScreen(viewModel)
                        AppSection.CREDIT -> CreditManagementScreen(viewModel)
                        AppSection.EXPENSES -> ExpensesScreen(viewModel)
                        AppSection.PURCHASES -> PurchasesScreen(viewModel)
                        AppSection.REPORTS -> ReportsScreen(viewModel)
                        AppSection.AI_ASSISTANT -> AiAssistantScreen(viewModel, aiViewModel)
                        AppSection.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }

    // All-9-sections quick launcher sheet on compact devices
    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = NavySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "أقسام النظام (9 وحدات)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(AppSection.entries) { section ->
                        val selected = currentSection == section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(94.dp)
                                .testTag("drawer_section_${section.route}")
                                .clickable {
                                    viewModel.selectSection(section)
                                    showMoreMenu = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) CobaltContainer else NavyCard
                            ),
                            border = BorderStroke(1.dp, if (selected) CobaltPrimary else NavyBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = sectionIcon(section),
                                    contentDescription = section.labelAr,
                                    tint = if (selected) CobaltPrimary else CyanSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = section.labelAr,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Completed checkout invoice receipt dialog
    lastCheckout?.let { result ->
        InvoiceReceiptDialog(
            invoice = result.invoice,
            items = result.items,
            currency = settings.currencySymbol,
            showProfitDetails = canViewProfit,
            title = if (result.wasDuplicateRequest) "فاتورة مسجلة مسبقاً #${result.invoice.id}" else "تم تأكيد الفاتورة #${result.invoice.id}",
            onDismiss = { viewModel.dismissCheckoutReceipt() }
        )
    }

    // Historical invoice inspection dialog
    inspectedInvoice?.let { detail ->
        InvoiceReceiptDialog(
            invoice = detail.invoice,
            items = detail.items,
            currency = settings.currencySymbol,
            showProfitDetails = canViewProfit,
            onDismiss = { viewModel.dismissInspectedInvoice() }
        )
    }
}
