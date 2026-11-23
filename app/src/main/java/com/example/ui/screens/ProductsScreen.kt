package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.ProductEntity
import com.example.data.model.formatMoney
import com.example.data.model.formatStockSummary
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.ui.ShopViewModel
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShopSearchBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyElevated

private enum class ProductFilter(val label: String) {
    ALL("الكل"),
    ACTIVE("نشط"),
    LOW("منخفض"),
    OUT("نافد"),
    INACTIVE("معطل")
}

@Composable
fun ProductsScreen(viewModel: ShopViewModel) {
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol
    val canManage = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.MANAGE_PRODUCTS)
    val canViewCosts = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.VIEW_PROFIT_AND_COSTS)

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ProductFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val filtered = remember(products, searchQuery, filter) {
        products.filter { p ->
            val matchText = searchQuery.isBlank() || p.name.contains(searchQuery.trim(), ignoreCase = true)
            val matchFilter = when (filter) {
                ProductFilter.ALL -> true
                ProductFilter.ACTIVE -> p.isActive
                ProductFilter.LOW -> p.isActive && p.stockUnits in 1..p.lowStockThreshold
                ProductFilter.OUT -> p.isActive && p.stockUnits == 0
                ProductFilter.INACTIVE -> !p.isActive
            }
            matchText && matchFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "إدارة المنتجات",
            subtitle = "${products.size} منتج مسجل في قاعدة البيانات",
            action = {
                if (canManage) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_product_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("منتج جديد")
                    }
                }
            }
        )

        ShopSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "ابحث في قائمة المنتجات...",
            testTag = "products_search_input"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProductFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) }
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Inventory,
                title = "لا توجد منتجات في هذا التصنيف",
                description = "أضف منتجاً جديداً أو غيّر معايير التصفية.",
                actionLabel = if (canManage) "إضافة منتج" else null,
                onAction = if (canManage) ({ showAddDialog = true }) else null
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { product ->
                    ProductManagementCard(
                        product = product,
                        currency = currency,
                        canManage = canManage,
                        canViewCosts = canViewCosts,
                        onEdit = { editingProduct = product },
                        onToggleActive = { viewModel.toggleProductActive(product) },
                        onDelete = { deletingProduct = product }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ProductEditorDialog(
            initial = null,
            defaultPackSize = settings.defaultPackSize,
            defaultLowStock = settings.lowStockDefault,
            onDismiss = { showAddDialog = false },
            onSave = { name, packPrice, unitPrice, cost, stockUnits, packSize, lowThreshold, _ ->
                viewModel.addProduct(name, packPrice, unitPrice, cost, stockUnits, packSize, lowThreshold)
                showAddDialog = false
            }
        )
    }

    editingProduct?.let { prod ->
        ProductEditorDialog(
            initial = prod,
            defaultPackSize = prod.packSize,
            defaultLowStock = prod.lowStockThreshold,
            onDismiss = { editingProduct = null },
            onSave = { name, packPrice, unitPrice, cost, _, packSize, lowThreshold, isActive ->
                viewModel.updateProduct(prod.id, name, packPrice, unitPrice, cost, packSize, lowThreshold, isActive)
                editingProduct = null
            }
        )
    }

    deletingProduct?.let { prod ->
        ConfirmActionDialog(
            title = "حذف المنتج",
            message = "هل تريد حذف '${prod.name}'؟ إذا كان مرتبطاً بفواتير أو حركات مخزون، سيمنع النظام الحذف حفاظاً على السجلات المالية ويمكنك تعطيله بدلاً من ذلك.",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                viewModel.deleteProduct(prod)
                deletingProduct = null
            },
            onDismiss = { deletingProduct = null }
        )
    }
}

@Composable
private fun ProductManagementCard(
    product: ProductEntity,
    currency: String,
    canManage: Boolean,
    canViewCosts: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when {
        !product.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
        product.stockUnits == 0 -> CrimsonDanger
        product.stockUnits <= product.lowStockThreshold -> AmberWarning
        else -> EmeraldProfit
    }
    val statusText = when {
        !product.isActive -> "معطّل"
        product.stockUnits == 0 -> "نافد"
        product.stockUnits <= product.lowStockThreshold -> "مخزون منخفض"
        else -> "متوفر"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "المخزون: ${formatStockSummary(product.stockUnits, product.packSize)} • حجم العلبة: ${product.packSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(text = statusText, color = statusColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "سعر العلبة: ${formatMoney(product.packPrice, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldProfit,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "سعر الوحدة: ${formatMoney(product.unitPrice, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (canViewCosts) {
                val packMargin = product.packPrice - product.purchaseCost
                Text(
                    text = "تكلفة العلبة: ${formatMoney(product.purchaseCost, currency)} • هامش الربح: ${formatMoney(packMargin, currency)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canManage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_product_${product.id}")) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = CyanSecondary)
                    }
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = if (product.isActive) "تعطيل" else "تفعيل",
                            tint = if (product.isActive) AmberWarning else EmeraldProfit
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_delete), tint = CrimsonDanger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductEntity?,
    defaultPackSize: Int,
    defaultLowStock: Int,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Int, Int, Int, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var packPrice by remember { mutableStateOf(initial?.packPrice?.toString() ?: "") }
    var unitPrice by remember { mutableStateOf(initial?.unitPrice?.toString() ?: "") }
    var purchaseCost by remember { mutableStateOf(initial?.purchaseCost?.toString() ?: "") }
    var packSize by remember { mutableStateOf((initial?.packSize ?: defaultPackSize).toString()) }
    var initialStockPacks by remember { mutableStateOf("0") }
    var lowStockThreshold by remember { mutableStateOf((initial?.lowStockThreshold ?: defaultLowStock).toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = {
            Text(if (initial == null) "إضافة منتج جديد" else "تعديل ${initial.name}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المنتج") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packPrice,
                        onValueChange = { packPrice = it },
                        label = { Text("سعر العلبة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_pack_price_input")
                    )
                    OutlinedTextField(
                        value = unitPrice,
                        onValueChange = { unitPrice = it },
                        label = { Text("سعر الوحدة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_unit_price_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchaseCost,
                        onValueChange = { purchaseCost = it },
                        label = { Text("تكلفة العلبة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = packSize,
                        onValueChange = { packSize = it },
                        label = { Text("وحدات/علبة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (initial == null) {
                    OutlinedTextField(
                        value = initialStockPacks,
                        onValueChange = { initialStockPacks = it },
                        label = { Text("المخزون الافتتاحي (عدد العلب)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = lowStockThreshold,
                    onValueChange = { lowStockThreshold = it },
                    label = { Text("حد تنبيه انخفاض المخزون (بالوحدة)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val pPrice = packPrice.toDoubleOrNull()
                    val uPrice = unitPrice.toDoubleOrNull()
                    val cost = purchaseCost.toDoubleOrNull()
                    val pSize = packSize.toIntOrNull()
                    val low = lowStockThreshold.toIntOrNull()
                    val initPacks = initialStockPacks.toIntOrNull() ?: 0
                    if (name.isBlank() || pPrice == null || uPrice == null || cost == null || pSize == null || pSize < 1 || low == null || low < 0) {
                        errorText = "يرجى إدخال جميع الحقول بقيم رقمية صحيحة"
                    } else if (initPacks < 0) {
                        errorText = "المخزون الافتتاحي لا يمكن أن يكون سالباً"
                    } else {
                        onSave(name, pPrice, uPrice, cost, initPacks * pSize, pSize, low, initial?.isActive ?: true)
                    }
                },
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
