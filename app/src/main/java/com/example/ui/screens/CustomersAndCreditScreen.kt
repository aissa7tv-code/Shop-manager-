package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.CreditTransactionEntity
import com.example.data.db.CustomerEntity
import com.example.data.model.formatDateTime
import com.example.data.model.formatMoney
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.ui.ShopViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiCard
import com.example.ui.components.SectionHeader
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
fun CustomersScreen(viewModel: ShopViewModel) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allCreditTxs by viewModel.creditTransactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var statementCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var debtTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    var paymentTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    var adjustmentTarget by remember { mutableStateOf<CustomerEntity?>(null) }

    val filtered = remember(customers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) customers
        else customers.filter {
            it.name.contains(q, ignoreCase = true) || it.phone.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "الزبائن وحساباتهم",
            subtitle = "${customers.size} زبون مسجل",
            action = {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_customer_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("زبون جديد")
                }
            }
        )

        ShopSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "ابحث بالاسم أو رقم الهاتف...",
            testTag = "customers_search_input"
        )

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Person,
                title = "لا يوجد زبائن بعد",
                description = "أضف زبائن المحل لتتبع المبيعات الآجلة، الديون، والتسديدات وكشوف الحساب.",
                actionLabel = "إضافة أول زبون",
                onAction = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { customer ->
                    CustomerCard(
                        customer = customer,
                        currency = currency,
                        onOpenStatement = { statementCustomer = customer },
                        onEdit = { editingCustomer = customer },
                        onRegisterDebt = { debtTarget = customer },
                        onRecordPayment = { paymentTarget = customer }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        CustomerEditorDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, notes ->
                viewModel.addCustomer(name, phone, notes)
                showAddDialog = false
            }
        )
    }

    editingCustomer?.let { cust ->
        CustomerEditorDialog(
            initial = cust,
            onDismiss = { editingCustomer = null },
            onSave = { name, phone, notes ->
                viewModel.updateCustomer(cust.id, name, phone, notes)
                editingCustomer = null
            }
        )
    }

    statementCustomer?.let { target ->
        val freshCustomer = customers.firstOrNull { it.id == target.id } ?: target
        val customerTxs = allCreditTxs.filter { it.customerId == freshCustomer.id }
        val isOwner = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        ModalBottomSheet(
            onDismissRequest = { statementCustomer = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = NavySurface
        ) {
            CustomerStatementSheet(
                customer = freshCustomer,
                transactions = customerTxs,
                currency = currency,
                canAdjust = isOwner,
                onRegisterDebt = { debtTarget = freshCustomer },
                onRecordPayment = { paymentTarget = freshCustomer },
                onAdjust = { adjustmentTarget = freshCustomer }
            )
        }
    }

    debtTarget?.let { cust ->
        CreditOperationDialog(
            customer = cust,
            isPayment = false,
            currency = currency,
            onDismiss = { debtTarget = null },
            onConfirm = { amount, notes ->
                viewModel.registerDebt(cust.id, amount, notes)
                debtTarget = null
            }
        )
    }

    paymentTarget?.let { cust ->
        CreditOperationDialog(
            customer = cust,
            isPayment = true,
            currency = currency,
            onDismiss = { paymentTarget = null },
            onConfirm = { amount, notes ->
                viewModel.recordPayment(cust.id, amount, notes)
                paymentTarget = null
            }
        )
    }

    adjustmentTarget?.let { cust ->
        CreditAdjustmentDialog(
            customer = cust,
            currency = currency,
            onDismiss = { adjustmentTarget = null },
            onConfirm = { delta, notes ->
                viewModel.recordCreditAdjustment(cust.id, delta, notes)
                adjustmentTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditManagementScreen(viewModel: ShopViewModel) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val debtors by viewModel.debtors.collectAsStateWithLifecycle()
    val allTxs by viewModel.creditTransactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol

    val totalDebts = remember(debtors) { debtors.sumOf { it.balance } }
    var statementCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var debtTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    var paymentTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    var adjustmentTarget by remember { mutableStateOf<CustomerEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "إدارة الكريدي والديون",
            subtitle = "تتبع الأرصدة المستحقة والتسديدات الجزئية والكاملة"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "إجمالي الديون المستحقة",
                value = formatMoney(totalDebts, currency),
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = if (totalDebts > 0) CrimsonDanger else EmeraldProfit,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "الزبائن المدينون",
                value = "${debtors.size}",
                subtitle = "من أصل ${customers.size} زبون",
                icon = Icons.Default.Payments,
                accentColor = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        if (debtors.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Payments,
                title = "لا توجد ديون مستحقة حالياً",
                description = "جميع حسابات الزبائن مسددة بالكامل. يمكنك تسجيل بيع بالكريدي من شاشة المبيعات أو تسجيل دين جديد من صفحة الزبائن."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(debtors, key = { it.id }) { debtor ->
                    CustomerCard(
                        customer = debtor,
                        currency = currency,
                        onOpenStatement = { statementCustomer = debtor },
                        onEdit = null,
                        onRegisterDebt = { debtTarget = debtor },
                        onRecordPayment = { paymentTarget = debtor }
                    )
                }
            }
        }
    }

    statementCustomer?.let { target ->
        val fresh = customers.firstOrNull { it.id == target.id } ?: target
        val txs = allTxs.filter { it.customerId == fresh.id }
        val isOwner = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        ModalBottomSheet(
            onDismissRequest = { statementCustomer = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = NavySurface
        ) {
            CustomerStatementSheet(
                customer = fresh,
                transactions = txs,
                currency = currency,
                canAdjust = isOwner,
                onRegisterDebt = { debtTarget = fresh },
                onRecordPayment = { paymentTarget = fresh },
                onAdjust = { adjustmentTarget = fresh }
            )
        }
    }

    debtTarget?.let { cust ->
        CreditOperationDialog(
            customer = cust,
            isPayment = false,
            currency = currency,
            onDismiss = { debtTarget = null },
            onConfirm = { amount, notes ->
                viewModel.registerDebt(cust.id, amount, notes)
                debtTarget = null
            }
        )
    }

    paymentTarget?.let { cust ->
        CreditOperationDialog(
            customer = cust,
            isPayment = true,
            currency = currency,
            onDismiss = { paymentTarget = null },
            onConfirm = { amount, notes ->
                viewModel.recordPayment(cust.id, amount, notes)
                paymentTarget = null
            }
        )
    }

    adjustmentTarget?.let { cust ->
        CreditAdjustmentDialog(
            customer = cust,
            currency = currency,
            onDismiss = { adjustmentTarget = null },
            onConfirm = { delta, notes ->
                viewModel.recordCreditAdjustment(cust.id, delta, notes)
                adjustmentTarget = null
            }
        )
    }
}

@Composable
private fun CustomerCard(
    customer: CustomerEntity,
    currency: String,
    onOpenStatement: () -> Unit,
    onEdit: (() -> Unit)?,
    onRegisterDebt: () -> Unit,
    onRecordPayment: () -> Unit
) {
    val hasDebt = customer.balance > 0.0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenStatement() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, if (hasDebt) AmberWarning.copy(alpha = 0.45f) else NavyBorder.copy(alpha = 0.7f))
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
                    Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (customer.phone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanSecondary)
                            Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (customer.notes.isNotBlank()) {
                        Text(customer.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (hasDebt) formatMoney(customer.balance, currency) else "خالص",
                        color = if (hasDebt) CrimsonDanger else EmeraldProfit
                    )
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = CyanSecondary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRegisterDebt,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("register_debt_${customer.id}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تسجيل دين")
                }
                Button(
                    onClick = onRecordPayment,
                    enabled = hasDebt,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("record_payment_${customer.id}")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تسديد")
                }
            }
        }
    }
}

@Composable
private fun CustomerStatementSheet(
    customer: CustomerEntity,
    transactions: List<CreditTransactionEntity>,
    currency: String,
    canAdjust: Boolean,
    onRegisterDebt: () -> Unit,
    onRecordPayment: () -> Unit,
    onAdjust: () -> Unit
) {
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
            Column {
                Text("كشف حساب: ${customer.name}", style = MaterialTheme.typography.titleLarge)
                if (customer.phone.isNotBlank()) {
                    Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusPill(
                text = "الرصيد: ${formatMoney(customer.balance, currency)}",
                color = if (customer.balance > 0) CrimsonDanger else EmeraldProfit
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRegisterDebt, modifier = Modifier.weight(1f).height(48.dp)) {
                Text("+ دين")
            }
            Button(
                onClick = onRecordPayment,
                enabled = customer.balance > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("تسديد")
            }
            if (canAdjust) {
                OutlinedButton(onClick = onAdjust, modifier = Modifier.height(48.dp)) {
                    Text("تسوية")
                }
            }
        }

        HorizontalDivider(color = NavyBorder)

        if (transactions.isEmpty()) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "لا توجد عمليات مسجلة",
                description = "ستظهر جميع الديون والتسديدات والتسويات هنا بالترتيب الزمني."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { tx ->
                    val typeLabel = when (tx.type) {
                        "DEBT" -> "دين جديد"
                        "PAYMENT" -> "تسديد"
                        else -> "تسوية"
                    }
                    val color = when (tx.type) {
                        "DEBT" -> CrimsonDanger
                        "PAYMENT" -> EmeraldProfit
                        else -> AmberWarning
                    }
                    Card(
                        shape = RoundedCornerShape(12.dp),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(text = typeLabel, color = color)
                                    if (tx.invoiceId != null) {
                                        StatusPill(text = "فاتورة #${tx.invoiceId}", color = CobaltPrimary)
                                    }
                                }
                                if (tx.notes.isNotBlank()) {
                                    Text(tx.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                }
                                Text(
                                    text = "${formatDateTime(tx.createdAt)} • الرصيد بعد العملية: ${formatMoney(tx.balanceAfter, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = (if (tx.deltaBalance >= 0) "+" else "") + formatMoney(tx.deltaBalance, currency),
                                style = MaterialTheme.typography.titleSmall,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerEditorDialog(
    initial: CustomerEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text(if (initial == null) "إضافة زبون جديد" else "تعديل بيانات الزبون") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الزبون") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
            Button(
                onClick = {
                    if (name.isBlank()) errorText = "يرجى إدخال اسم الزبون"
                    else onSave(name, phone, notes)
                },
                modifier = Modifier.testTag("save_customer_button")
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CreditOperationDialog(
    customer: CustomerEntity,
    isPayment: Boolean,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf(if (isPayment) customer.balance.toString() else "") }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text(if (isPayment) "تسديد دين — ${customer.name}" else "تسجيل دين — ${customer.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "الرصيد المستحق الحالي: ${formatMoney(customer.balance, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanSecondary
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("credit_amount_input")
                )
                if (isPayment && customer.balance > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { amountText = (customer.balance / 2.0).toString() }) {
                            Text("نصف المبلغ")
                        }
                        OutlinedButton(onClick = { amountText = customer.balance.toString() }) {
                            Text("تسديد كامل")
                        }
                    }
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
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorText = "يرجى إدخال مبلغ صحيح أكبر من صفر"
                    } else if (isPayment && amt > customer.balance + 0.01) {
                        errorText = "المبلغ يتجاوز الرصيد المستحق"
                    } else {
                        onConfirm(amt, notes)
                    }
                },
                modifier = Modifier.testTag("confirm_credit_op_button")
            ) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CreditAdjustmentDialog(
    customer: CustomerEntity,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var deltaText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text("تسوية رصيد قابل للتتبع — ${customer.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "لا يتم حذف القيود المالية أبداً. أدخل قيمة موجبة لزيادة الدين أو سالبة لتخفيضه (الرصيد الحالي: ${formatMoney(customer.balance, currency)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it },
                    label = { Text("قيمة التسوية (+ أو -)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("سبب التسوية (إلزامي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let { Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val delta = deltaText.toDoubleOrNull()
                if (delta == null || delta == 0.0) {
                    errorText = "يرجى إدخال قيمة صحيحة تختلف عن صفر"
                } else if (customer.balance + delta < 0.0) {
                    errorText = "لا يمكن أن يصبح الرصيد سالباً"
                } else if (notes.isBlank()) {
                    errorText = "سبب التسوية إلزامي"
                } else {
                    onConfirm(delta, notes)
                }
            }) { Text("تسجيل التسوية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
