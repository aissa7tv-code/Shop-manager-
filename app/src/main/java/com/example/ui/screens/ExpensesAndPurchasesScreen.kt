package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.db.ExpenseEntity
import com.example.data.db.ProductEntity
import com.example.data.model.formatDateTime
import com.example.data.model.formatMoney
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
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

private val EXPENSE_CATEGORIES = listOf("إيجار", "كهرباء وماء", "نقل وتوصيل", "عمال", "صيانة", "أخرى")

@Composable
fun ExpensesScreen(viewModel: ShopViewModel) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol
    val canEdit = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.EDIT_EXPENSE)

    var selectedCategory by remember { mutableStateOf("الكل") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    val filtered = remember(expenses, selectedCategory) {
        if (selectedCategory == "الكل") expenses
        else expenses.filter { it.category == selectedCategory }
    }
    val totalAmount = remember(filtered) { filtered.sumOf { it.amount } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "إدارة المصاريف التشغيلية",
            subtitle = "تُخصم من إجمالي الربح لحساب صافي الربح بدقة",
            action = {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_expense_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مصروف جديد")
                }
            }
        )

        KpiCard(
            title = "إجمالي المصاريف ($selectedCategory)",
            value = formatMoney(totalAmount, currency),
            subtitle = "${filtered.size} سجل مصروف",
            icon = Icons.Default.MoneyOff,
            accentColor = CrimsonDanger,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (listOf("الكل") + EXPENSE_CATEGORIES).forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.MoneyOff,
                title = "لا توجد مصاريف مسجلة",
                description = "سجّل مصاريف المحل التشغيلية (إيجار، كهرباء، نقل...) منفصلة عن مشتريات البضاعة.",
                actionLabel = "تسجيل مصروف",
                onAction = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { expense ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusPill(text = expense.category, color = AmberWarning)
                                    Text(expense.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = formatDateTime(expense.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatMoney(expense.amount, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CrimsonDanger,
                                    fontWeight = FontWeight.Bold
                                )
                                if (canEdit) {
                                    IconButton(onClick = { editingExpense = expense }) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = CyanSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ExpenseEditorDialog(
            initial = null,
            currency = currency,
            onDismiss = { showAddDialog = false },
            onSave = { cat, desc, amt ->
                viewModel.addExpense(cat, desc, amt)
                showAddDialog = false
            }
        )
    }

    editingExpense?.let { ex ->
        ExpenseEditorDialog(
            initial = ex,
            currency = currency,
            onDismiss = { editingExpense = null },
            onSave = { cat, desc, amt ->
                viewModel.updateExpense(ex.id, cat, desc, amt)
                editingExpense = null
            }
        )
    }
}

@Composable
private fun ExpenseEditorDialog(
    initial: ExpenseEntity?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit
) {
    var category by remember { mutableStateOf(initial?.category ?: EXPENSE_CATEGORIES.first()) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text(if (initial == null) "تسجيل مصروف جديد" else "تعديل المصروف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EXPENSE_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_desc_input")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                )
                errorText?.let { Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (description.isBlank()) errorText = "يرجى إدخال وصف المصروف"
                    else if (amt == null || amt <= 0.0) errorText = "يرجى إدخال مبلغ أكبر من صفر"
                    else onSave(category, description, amt)
                },
                modifier = Modifier.testTag("save_expense_button")
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun PurchasesScreen(viewModel: ShopViewModel) {
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol

    var showAddDialog by remember { mutableStateOf(false) }
    val totalPurchases = remember(purchases) { purchases.sumOf { it.totalCost } }
    val totalPacks = remember(purchases) { purchases.sumOf { it.packsQuantity } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "المشتريات وتوريد البضاعة",
            subtitle = "تُضاف للمخزون وتُحسب في الصندوق دون ازدواج مع المصاريف",
            action = {
                Button(
                    onClick = { showAddDialog = true },
                    enabled = products.isNotEmpty(),
                    modifier = Modifier.testTag("add_purchase_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("شراء بضاعة")
                }
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = "إجمالي المشتريات",
                value = formatMoney(totalPurchases, currency),
                icon = Icons.Default.LocalShipping,
                accentColor = CobaltPrimary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "الكمية المورّدة",
                value = "$totalPacks علبة",
                subtitle = "${purchases.size} فاتورة شراء",
                icon = Icons.Default.Add,
                accentColor = EmeraldProfit,
                modifier = Modifier.weight(1f)
            )
        }

        if (purchases.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.LocalShipping,
                title = "لا توجد مشتريات مسجلة بعد",
                description = "عند تسجيل شراء بضاعة، يتم تحديث المخزون وسعر التكلفة وسجل المخزون تلقائياً.",
                actionLabel = if (products.isNotEmpty()) "تسجيل شراء بضاعة" else null,
                onAction = if (products.isNotEmpty()) ({ showAddDialog = true }) else null
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(purchases, key = { it.id }) { p ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.productNameSnapshot, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    StatusPill(
                                        text = if (p.paidFromCash) "من الصندوق" else "خارج الصندوق",
                                        color = if (p.paidFromCash) AmberWarning else CyanSecondary
                                    )
                                }
                                Text(
                                    text = "+${p.packsQuantity} علبة (${p.unitsAdded} وحدة) × ${formatMoney(p.unitCostPerPack, currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldProfit
                                )
                                if (p.supplierName.isNotBlank()) {
                                    Text("المورد: ${p.supplierName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatDateTime(p.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = formatMoney(p.totalCost, currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = CobaltPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RecordPurchaseDialog(
            products = products,
            currency = currency,
            onDismiss = { showAddDialog = false },
            onSave = { prodId, supplier, packs, costPerPack, paidFromCash, notes ->
                viewModel.recordPurchase(prodId, supplier, packs, costPerPack, paidFromCash, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun RecordPurchaseDialog(
    products: List<ProductEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Long, String, Int, Double, Boolean, String) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var productMenuExpanded by remember { mutableStateOf(false) }
    var supplierName by remember { mutableStateOf("") }
    var packsText by remember { mutableStateOf("5") }
    var costPerPackText by remember { mutableStateOf(selectedProduct?.purchaseCost?.toString() ?: "") }
    var paidFromCash by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val estimatedTotal = (packsText.toIntOrNull() ?: 0) * (costPerPackText.toDoubleOrNull() ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text("تسجيل شراء بضاعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { productMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(selectedProduct?.name ?: "اختر المنتج")
                    }
                    DropdownMenu(
                        expanded = productMenuExpanded,
                        onDismissRequest = { productMenuExpanded = false },
                        modifier = Modifier.background(NavyElevated)
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.name} (حالي: ${prod.stockUnits} وحدة)") },
                                onClick = {
                                    selectedProduct = prod
                                    costPerPackText = prod.purchaseCost.toString()
                                    productMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("اسم المورد (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packsText,
                        onValueChange = { packsText = it },
                        label = { Text("عدد العلب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costPerPackText,
                        onValueChange = { costPerPackText = it },
                        label = { Text("تكلفة العلبة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "إجمالي التكلفة: ${formatMoney(estimatedTotal, currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = paidFromCash, onCheckedChange = { paidFromCash = it })
                    Text("مدفوعة نقداً من الصندوق اليومي", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let { Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val prod = selectedProduct
                val packs = packsText.toIntOrNull()
                val cost = costPerPackText.toDoubleOrNull()
                if (prod == null) errorText = "يرجى اختيار المنتج"
                else if (packs == null || packs <= 0) errorText = "عدد العلب يجب أن يكون أكبر من صفر"
                else if (cost == null || cost < 0.0) errorText = "تكلفة العلبة غير صالحة"
                else onSave(prod.id, supplierName, packs, cost, paidFromCash, notes)
            }) { Text("تأكيد الشراء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
