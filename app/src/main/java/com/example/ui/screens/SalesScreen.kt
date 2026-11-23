package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.CustomerEntity
import com.example.data.db.ProductEntity
import com.example.data.model.CartItem
import com.example.data.model.PaymentType
import com.example.data.model.SaleUnitType
import com.example.data.model.formatMoney
import com.example.data.model.formatStockSummary
import com.example.ui.ShopViewModel
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ShopSearchBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.NavySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(viewModel: ShopViewModel) {
    val availableProducts by viewModel.availableForSale.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val paymentType by viewModel.cartPaymentType.collectAsStateWithLifecycle()
    val selectedCustomerId by viewModel.cartCustomerId.collectAsStateWithLifecycle()
    val cartNotes by viewModel.cartNotes.collectAsStateWithLifecycle()
    val isCheckingOut by viewModel.isCheckingOut.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val currency = settings.currencySymbol
    var searchQuery by remember { mutableStateOf("") }
    var quickInput by remember { mutableStateOf("") }
    var showQuickBar by remember { mutableStateOf(false) }
    var showCartSheet by remember { mutableStateOf(false) }
    var showClearCartConfirm by remember { mutableStateOf(false) }

    val filteredProducts = remember(availableProducts, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) availableProducts
        else availableProducts.filter { it.name.contains(q, ignoreCase = true) }
    }

    val totalAmount = remember(cartItems) { cartItems.sumOf { it.lineTotal } }
    val totalLineCount = remember(cartItems) { cartItems.sumOf { it.quantity } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search & Quick-entry toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShopSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث عن منتج للبيع...",
                    modifier = Modifier.weight(1f),
                    testTag = "sales_search_input"
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (showQuickBar) CobaltPrimary else NavyCard,
                    border = BorderStroke(1.dp, if (showQuickBar) CobaltPrimary else NavyBorder),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("quick_entry_toggle_button")
                        .clickable { showQuickBar = !showQuickBar }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = stringResource(R.string.cd_quick_entry),
                            tint = if (showQuickBar) MaterialTheme.colorScheme.onPrimary else CyanSecondary
                        )
                    }
                }
            }

            // Optional Quick Entry Bar supporting +5s, +1*, -علبة rym, -5 سجارة rym
            if (showQuickBar) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    border = BorderStroke(1.dp, CyanSecondary.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "الإدخال السريع (+5s rym • +1* rym • -علبة rym • -5 سجارة rym)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickInput,
                                onValueChange = { quickInput = it },
                                placeholder = { Text("مثال: -علبة rym") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_entry_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanSecondary,
                                    unfocusedBorderColor = NavyBorder
                                )
                            )
                            Button(
                                onClick = {
                                    if (quickInput.isNotBlank()) {
                                        viewModel.runQuickEntry(quickInput)
                                        quickInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("quick_entry_submit")
                            ) {
                                Text("تنفيذ")
                            }
                        }
                    }
                }
            }

            if (filteredProducts.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.Storefront,
                    title = if (availableProducts.isEmpty()) "لا توجد منتجات متوفرة في المخزون" else "لا توجد نتائج مطابقة",
                    description = if (availableProducts.isEmpty())
                        "المنتجات التي رصيدها صفر مخفية تلقائياً من شاشة البيع. أضف مخزوناً أو سجّل مشتريات جديدة."
                    else
                        "جرّب البحث باسم آخر أو امسح نص البحث."
                )
            } else {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val columns = when {
                        maxWidth >= 900.dp -> 4
                        maxWidth >= 600.dp -> 3
                        else -> 2
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(bottom = if (cartItems.isNotEmpty()) 96.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val inCartUnits = cartItems
                                .filter { it.productId == product.id }
                                .sumOf { it.unitsRequired }
                            val remainingAfterCart = (product.stockUnits - inCartUnits).coerceAtLeast(0)
                            val canAddUnit = remainingAfterCart >= 1
                            val canAddPack = remainingAfterCart >= product.packSize

                            SalesProductCard(
                                product = product,
                                remainingAfterCart = remainingAfterCart,
                                inCartUnits = inCartUnits,
                                currency = currency,
                                canAddUnit = canAddUnit,
                                canAddPack = canAddPack,
                                onAddUnit = { viewModel.addToCart(product, SaleUnitType.UNIT) },
                                onAddPack = { viewModel.addToCart(product, SaleUnitType.PACK) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Cart Summary Bar
        if (cartItems.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("floating_cart_bar")
                    .clickable { showCartSheet = true },
                shape = RoundedCornerShape(18.dp),
                color = CobaltPrimary,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = stringResource(R.string.cd_cart),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "$totalLineCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "عرض السلة وإتمام البيع",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "${cartItems.size} بند في الفاتورة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Text(
                        text = formatMoney(totalAmount, currency),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showCartSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = sheetState,
            containerColor = NavySurface
        ) {
            ShoppingCartSheetContent(
                cartItems = cartItems,
                paymentType = paymentType,
                selectedCustomerId = selectedCustomerId,
                customers = customers,
                notes = cartNotes,
                currency = currency,
                isCheckingOut = isCheckingOut,
                onIncrement = { viewModel.incrementCartItem(it) },
                onDecrement = { viewModel.decrementCartItem(it) },
                onRemove = { viewModel.removeCartItem(it) },
                onClearRequest = { showClearCartConfirm = true },
                onPaymentTypeChange = { viewModel.setCartPaymentType(it) },
                onCustomerSelect = { viewModel.setCartCustomer(it) },
                onNotesChange = { viewModel.setCartNotes(it) },
                onConfirmCheckout = {
                    viewModel.checkoutCart()
                    showCartSheet = false
                }
            )
        }
    }

    if (showClearCartConfirm) {
        ConfirmActionDialog(
            title = "إفراغ السلة",
            message = "هل تريد حذف جميع العناصر من سلة المشتريات الحالية؟",
            confirmLabel = "إفراغ السلة",
            onConfirm = {
                viewModel.clearCart()
                showClearCartConfirm = false
                showCartSheet = false
            },
            onDismiss = { showClearCartConfirm = false }
        )
    }
}

@Composable
private fun SalesProductCard(
    product: ProductEntity,
    remainingAfterCart: Int,
    inCartUnits: Int,
    currency: String,
    canAddUnit: Boolean,
    canAddPack: Boolean,
    onAddUnit: () -> Unit,
    onAddPack: () -> Unit
) {
    val isLowStock = product.stockUnits <= product.lowStockThreshold
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sales_product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(
            width = 1.dp,
            color = if (inCartUnits > 0) CobaltPrimary else NavyBorder.copy(alpha = 0.75f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (inCartUnits > 0) {
                    StatusPill(text = "سلة: $inCartUnits", color = CobaltPrimary)
                }
            }

            // Remaining stock indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = if (isLowStock) AmberWarning else CyanSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = formatStockSummary(remainingAfterCart, product.packSize),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLowStock) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Pack & unit pricing box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NavySurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "العلبة (${product.packSize})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(product.packPrice, currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldProfit,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "الوحدة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(product.unitPrice, currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Touch-friendly Add Pack / Add Unit buttons (minimum 48dp height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onAddPack,
                    enabled = canAddPack,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_pack_btn_${product.id}")
                ) {
                    Text("+ علبة", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onAddUnit,
                    enabled = canAddUnit,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_unit_btn_${product.id}")
                ) {
                    Text("+ وحدة", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ShoppingCartSheetContent(
    cartItems: List<CartItem>,
    paymentType: PaymentType,
    selectedCustomerId: Long?,
    customers: List<CustomerEntity>,
    notes: String,
    currency: String,
    isCheckingOut: Boolean,
    onIncrement: (CartItem) -> Unit,
    onDecrement: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
    onClearRequest: () -> Unit,
    onPaymentTypeChange: (PaymentType) -> Unit,
    onCustomerSelect: (Long?) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirmCheckout: () -> Unit
) {
    val totalAmount = cartItems.sumOf { it.lineTotal }
    var customerDropdownExpanded by remember { mutableStateOf(false) }
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "سلة المشتريات (${cartItems.size})",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedButton(
                onClick = onClearRequest,
                border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonDanger),
                modifier = Modifier.testTag("clear_cart_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إفراغ")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height((cartItems.size * 92).coerceIn(92, 270).dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cartItems, key = { it.key }) { item ->
                val usedForProduct = cartItems
                    .filter { it.productId == item.productId }
                    .sumOf { it.unitsRequired }
                val stepUnits = if (item.saleUnitType == SaleUnitType.PACK) item.packSize else 1
                val canIncrement = usedForProduct + stepUnits <= item.availableStockUnits

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.productName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                StatusPill(
                                    text = item.saleUnitType.labelAr,
                                    color = if (item.saleUnitType == SaleUnitType.PACK) CobaltPrimary else CyanSecondary
                                )
                            }
                            Text(
                                text = "${formatMoney(item.unitPrice, currency)} × ${item.quantity} = ${formatMoney(item.lineTotal, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldProfit,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "سيُخصم ${item.unitsRequired} وحدة • المتاح: ${item.availableStockUnits}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onDecrement(item) },
                                modifier = Modifier.testTag("cart_dec_${item.key}")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص")
                            }
                            Text(
                                text = "${item.quantity}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { onIncrement(item) },
                                enabled = canIncrement,
                                modifier = Modifier.testTag("cart_inc_${item.key}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة")
                            }
                            IconButton(
                                onClick = { onRemove(item) },
                                modifier = Modifier.testTag("cart_remove_${item.key}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    tint = CrimsonDanger
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = NavyBorder)

        // Payment mode selector (Cash vs Credit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaymentType.entries.forEach { pt ->
                FilterChip(
                    selected = paymentType == pt,
                    onClick = { onPaymentTypeChange(pt) },
                    label = { Text(pt.labelAr) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_type_${pt.code}")
                )
            }
        }

        // Customer dropdown when CREDIT is selected
        if (paymentType == PaymentType.CREDIT) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { customerDropdownExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("select_credit_customer_btn")
                ) {
                    Text(
                        text = selectedCustomer?.let { "${it.name} (الدين: ${formatMoney(it.balance, currency)})" }
                            ?: "اختر الزبون لتسجيل الفاتورة بالكريدي"
                    )
                }
                DropdownMenu(
                    expanded = customerDropdownExpanded,
                    onDismissRequest = { customerDropdownExpanded = false },
                    modifier = Modifier.background(NavyElevated)
                ) {
                    if (customers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("لا يوجد زبائن — أضف زبوناً من قسم الزبائن أولاً") },
                            onClick = { customerDropdownExpanded = false }
                        )
                    } else {
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${formatMoney(c.balance, currency)})") },
                                onClick = {
                                    onCustomerSelect(c.id)
                                    customerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("ملاحظة على الفاتورة (اختياري)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إجمالي الفاتورة", style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatMoney(totalAmount, currency),
                style = MaterialTheme.typography.headlineSmall,
                color = EmeraldProfit,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onConfirmCheckout,
            enabled = !isCheckingOut && cartItems.isNotEmpty() &&
                (paymentType == PaymentType.CASH || selectedCustomerId != null),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("confirm_checkout_button")
        ) {
            if (isCheckingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (paymentType == PaymentType.CASH) "تأكيد الفاتورة النقدية" else "تأكيد فاتورة الكريدي",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
