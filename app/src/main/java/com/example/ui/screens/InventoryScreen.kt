package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.InventoryLedgerEntity
import com.example.data.db.ProductEntity
import com.example.data.model.formatDateTime
import com.example.data.model.formatMoney
import com.example.data.model.formatStockSummary
import com.example.ui.ShopViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiCard
import com.example.ui.components.SectionHeader
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

@Composable
fun InventoryScreen(viewModel: ShopViewModel) {
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val ledger by viewModel.recentLedger.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol

    var selectedTab by remember { mutableIntStateOf(0) }
    var adjustTarget by remember { mutableStateOf<Pair<ProductEntity, Boolean>?>(null) } // product, isAdd
    var showOpeningCountDialog by remember { mutableStateOf(false) }

    val activeProducts = remember(products) { products.filter { it.isActive } }
    val lowStockCount = remember(activeProducts) {
        activeProducts.count { it.stockUnits in 1..it.lowStockThreshold }
    }
    val outOfStockCount = remember(activeProducts) {
        activeProducts.count { it.stockUnits == 0 }
    }
    val totalInventoryValue = remember(activeProducts) {
        activeProducts.sumOf { (it.stockUnits.toDouble() / it.packSize.coerceAtLeast(1)) * it.purchaseCost }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "إدارة المخزون والجرد",
            subtitle = "تتبع الكميات والتنبيهات وسجل الحركات",
            action = {
                Button(
                    onClick = { showOpeningCountDialog = true },
                    modifier = Modifier.testTag("opening_inventory_button")
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("جرد الصباح")
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "قيمة المخزون (تكلفة)",
                value = formatMoney(totalInventoryValue, currency),
                icon = Icons.Default.Inventory2,
                accentColor = CyanSecondary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "تنبيهات المخزون",
                value = "${lowStockCount + outOfStockCount}",
                subtitle = "$lowStockCount منخفض • $outOfStockCount نافد",
                icon = Icons.Default.WarningAmber,
                accentColor = if (outOfStockCount > 0) CrimsonDanger else AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NavySurface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("مستويات المخزون (${activeProducts.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("سجل الحركات (${ledger.size})") }
            )
        }

        if (selectedTab == 0) {
            if (activeProducts.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.Inventory2,
                    title = "لا توجد منتجات نشطة",
                    description = "أضف منتجات من قسم المنتجات لبدء تتبع المخزون."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeProducts, key = { it.id }) { prod ->
                        InventoryItemCard(
                            product = prod,
                            onAddStock = { adjustTarget = prod to true },
                            onRemoveStock = { adjustTarget = prod to false }
                        )
                    }
                }
            }
        } else {
            if (ledger.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.History,
                    title = "سجل حركات المخزون فارغ",
                    description = "كل عملية بيع أو شراء أو تعديل مخزون تُسجّل هنا تلقائياً."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ledger, key = { it.id }) { entry ->
                        LedgerRowCard(entry = entry)
                    }
                }
            }
        }
    }

    adjustTarget?.let { (product, isAdd) ->
        StockAdjustDialog(
            product = product,
            isAdd = isAdd,
            onDismiss = { adjustTarget = null },
            onConfirm = { deltaUnits, notes ->
                viewModel.adjustStock(
                    productId = product.id,
                    deltaUnits = deltaUnits,
                    changeType = if (deltaUnits > 0) "ADD_STOCK" else "REMOVE_STOCK",
                    notes = notes
                )
                adjustTarget = null
            }
        )
    }

    if (showOpeningCountDialog) {
        OpeningInventoryDialog(
            products = activeProducts,
            onDismiss = { showOpeningCountDialog = false },
            onSave = { counts, notes ->
                viewModel.recordOpeningInventory(counts, notes)
                showOpeningCountDialog = false
            }
        )
    }
}

@Composable
private fun InventoryItemCard(
    product: ProductEntity,
    onAddStock: () -> Unit,
    onRemoveStock: () -> Unit
) {
    val badgeColor = when {
        product.stockUnits == 0 -> CrimsonDanger
        product.stockUnits <= product.lowStockThreshold -> AmberWarning
        else -> EmeraldProfit
    }
    val badgeText = when {
        product.stockUnits == 0 -> "نافد"
        product.stockUnits <= product.lowStockThreshold -> "منخفض"
        else -> "جيد"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatStockSummary(product.stockUnits, product.packSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                StatusPill(text = badgeText, color = badgeColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onAddStock,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_stock_${product.id}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة مخزون")
                }
                OutlinedButton(
                    onClick = onRemoveStock,
                    enabled = product.stockUnits > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("remove_stock_${product.id}")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("سحب / تعديل")
                }
            }
        }
    }
}

@Composable
private fun LedgerRowCard(entry: InventoryLedgerEntity) {
    val isPositive = entry.deltaUnits > 0
    val deltaColor = if (isPositive) EmeraldProfit else CrimsonDanger
    val typeLabel = when (entry.changeType) {
        "OPENING" -> "جرد افتتاحي"
        "PURCHASE" -> "شراء بضاعة"
        "SALE" -> "بيع"
        "ADD_STOCK" -> "إضافة مخزون"
        "REMOVE_STOCK" -> "سحب مخزون"
        "QUICK_ENTRY" -> "إدخال سريع"
        else -> "تعديل"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.productNameSnapshot, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    StatusPill(text = typeLabel, color = CobaltPrimary)
                }
                if (entry.notes.isNotBlank()) {
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${formatDateTime(entry.createdAt)} • ${entry.stockBefore} ← ${entry.stockAfter} وحدة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isPositive) "+${entry.deltaUnits}" else "${entry.deltaUnits}",
                style = MaterialTheme.typography.titleMedium,
                color = deltaColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StockAdjustDialog(
    product: ProductEntity,
    isAdd: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var byPack by remember { mutableStateOf(true) }
    var quantityText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = {
            Text(if (isAdd) "إضافة مخزون: ${product.name}" else "سحب من المخزون: ${product.name}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "المتوفر حالياً: ${formatStockSummary(product.stockUnits, product.packSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = byPack,
                        onClick = { byPack = true },
                        label = { Text("بالعلبة (${product.packSize} وحدة)") }
                    )
                    FilterChip(
                        selected = !byPack,
                        onClick = { byPack = false },
                        label = { Text("بالوحدة المفردة") }
                    )
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(if (byPack) "عدد العلب" else "عدد الوحدات") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("السبب / ملاحظة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let {
                    Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull()
                    if (qty == null || qty <= 0) {
                        errorText = "يرجى إدخال كمية صحيحة أكبر من صفر"
                    } else {
                        val rawUnits = if (byPack) qty * product.packSize else qty
                        val delta = if (isAdd) rawUnits else -rawUnits
                        if (product.stockUnits + delta < 0) {
                            errorText = "لا يمكن سحب كمية أكبر من المخزون المتاح (${product.stockUnits} وحدة)"
                        } else {
                            onConfirm(delta, notes.ifBlank { if (isAdd) "إضافة مخزون يدوي" else "سحب / تسوية مخزون" })
                        }
                    }
                }
            ) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun OpeningInventoryDialog(
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (Map<Long, Int>, String) -> Unit
) {
    val unitsInputs = remember(products) {
        mutableStateMapOf<Long, String>().apply {
            products.forEach { put(it.id, it.stockUnits.toString()) }
        }
    }
    var notes by remember { mutableStateOf("جرد الصباح") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text("جرد الصباح / تعديل جماعي للمخزون") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "أدخل الكمية الفعلية بالوحدات لكل منتج. سيتم تسجيل الفروقات تلقائياً في سجل المخزون.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظة الجرد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                products.forEach { prod ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "حالي: ${prod.stockUnits} وحدة (${prod.packSize}/علبة)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = unitsInputs[prod.id] ?: "0",
                            onValueChange = { unitsInputs[prod.id] = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(105.dp)
                        )
                    }
                }
                errorText?.let {
                    Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = mutableMapOf<Long, Int>()
                    for (prod in products) {
                        val v = unitsInputs[prod.id]?.trim()?.toIntOrNull()
                        if (v == null || v < 0) {
                            errorText = "قيمة غير صالحة للمنتج ${prod.name}"
                            return@Button
                        }
                        parsed[prod.id] = v
                    }
                    onSave(parsed, notes)
                }
            ) {
                Text("حفظ الجرد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
