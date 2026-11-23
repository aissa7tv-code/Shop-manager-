package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FinancialReport
import com.example.data.model.PaymentType
import com.example.data.model.PeriodFilter
import com.example.data.model.formatDateOnly
import com.example.data.model.formatDateTime
import com.example.data.model.formatMoney
import com.example.data.model.formatStockSummary
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.ui.ShopViewModel
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
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReportsScreen(viewModel: ShopViewModel) {
    val report by viewModel.financialReport.collectAsStateWithLifecycle()
    val periodFilter by viewModel.periodFilter.collectAsStateWithLifecycle()
    val recentInvoices by viewModel.recentInvoices.collectAsStateWithLifecycle()
    val isLoading by viewModel.isReportLoading.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.currencySymbol
    val canViewProfit = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.VIEW_PROFIT_AND_COSTS)
    val canWithdraw = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.RECORD_WITHDRAWAL)

    var cashDialogType by remember { mutableStateOf<String?>(null) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "التقارير المالية والصندوق",
                subtitle = report?.let { "${formatDateOnly(it.fromMillis)} إلى ${formatDateOnly(it.toMillis)}" }
            )
        }

        // Period Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeriodFilter.entries.forEach { pf ->
                    FilterChip(
                        selected = periodFilter == pf,
                        onClick = {
                            if (pf == PeriodFilter.CUSTOM) showCustomRangeDialog = true
                            else viewModel.setPeriodFilter(pf)
                        },
                        label = { Text(pf.labelAr) },
                        modifier = Modifier.testTag("period_chip_${pf.name}")
                    )
                }
            }
        }

        // Quick Cash Movement Actions (Opening Balance, Deposit, Withdrawal)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { cashDialogType = "OPENING_BALANCE" },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("رصيد افتتاحي", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { cashDialogType = "DEPOSIT" },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("إيداع نقدي", maxLines = 1)
                }
                if (canWithdraw) {
                    OutlinedButton(
                        onClick = { cashDialogType = "WITHDRAWAL" },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("cash_withdrawal_button")
                    ) {
                        Text("سحب نقدي", maxLines = 1, color = CrimsonDanger)
                    }
                }
            }
        }

        if (isLoading && report == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
        }

        report?.let { r ->
            // Top 4 KPI Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            title = "إجمالي المبيعات",
                            value = formatMoney(r.totalRevenue, currency),
                            subtitle = "${r.invoiceCount} فاتورة • ${r.packsSold} علبة / ${r.unitsSold} وحدة",
                            icon = Icons.Default.AttachMoney,
                            accentColor = CobaltPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "الرصيد النقدي الفعلي",
                            value = formatMoney(r.closingCashBalance, currency),
                            subtitle = "النقد المتاح بالصندوق",
                            icon = Icons.Default.AccountBalance,
                            accentColor = CyanSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            title = if (canViewProfit) "صافي الربح" else "المبيعات النقدية",
                            value = formatMoney(if (canViewProfit) r.netProfit else r.cashSales, currency),
                            subtitle = if (canViewProfit) "الإجمالي: ${formatMoney(r.grossProfit, currency)}" else "تحصيل نقدي مباشر",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = EmeraldProfit,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "إجمالي الكريدي المستحق",
                            value = formatMoney(r.totalOutstandingDebts, currency),
                            subtitle = "${r.debtors.size} زبون مدين",
                            icon = Icons.Default.CreditCard,
                            accentColor = if (r.totalOutstandingDebts > 0) AmberWarning else EmeraldProfit,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Cash Register Summary Card (clearly separating Cash Flow vs Revenue vs Profit)
            item {
                CashAndProfitBreakdownCard(
                    report = r,
                    currency = currency,
                    showProfitDetails = canViewProfit
                )
            }

            // Best-Selling Products Analytics
            if (r.productBreakdown.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("أداء المنتجات الأكثر مبيعاً", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            r.productBreakdown.take(10).forEachIndexed { idx, row ->
                                if (idx > 0) HorizontalDivider(color = NavyBorder.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${idx + 1}. ${row.productName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${row.packsSold} علبة • ${row.unitsSold} وحدة (خصم ${row.totalUnitsDeducted} وحدة)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatMoney(row.revenue, currency),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CobaltPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (canViewProfit) {
                                            Text(
                                                text = "ربح: ${formatMoney(row.profit, currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldProfit
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Low & Zero Stock Report
            if (r.outOfStockProducts.isNotEmpty() || r.lowStockProducts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("تنبيهات المخزون المنخفض والنافد", style = MaterialTheme.typography.titleMedium, color = AmberWarning)
                            r.outOfStockProducts.forEach { p ->
                                ReportLineRow(p.name, "نافد (0)", CrimsonDanger)
                            }
                            r.lowStockProducts.forEach { p ->
                                ReportLineRow(p.name, formatStockSummary(p.stockUnits, p.packSize), AmberWarning)
                            }
                        }
                    }
                }
            }

            // Customer Debt Aging Report
            if (r.debtors.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("تقرير ديون الزبائن وأعمار الديون", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            r.debtors.forEach { d ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(d.customerName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "أقدم دين منذ ${d.oldestUnpaidDays} يوم",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatMoney(d.balance, currency),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CrimsonDanger,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Invoices List (Tap to view full receipt)
        if (recentInvoices.isNotEmpty()) {
            item {
                Text("أحدث الفواتير المسجلة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(recentInvoices.take(20), key = { it.id }) { inv ->
                val pType = PaymentType.fromCode(inv.paymentType)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.inspectInvoice(inv) },
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
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("فاتورة #${inv.id}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                StatusPill(
                                    text = pType.labelAr,
                                    color = if (pType == PaymentType.CASH) EmeraldProfit else AmberWarning
                                )
                            }
                            Text(
                                text = "${formatDateTime(inv.createdAt)} • ${inv.packsSold} علبة، ${inv.unitsSold} وحدة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatMoney(inv.totalRevenue, currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = CobaltPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    cashDialogType?.let { type ->
        CashMovementDialog(
            type = type,
            currency = currency,
            onDismiss = { cashDialogType = null },
            onConfirm = { amount, notes ->
                viewModel.recordCashMovement(type, amount, notes)
                cashDialogType = null
            }
        )
    }

    if (showCustomRangeDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomRangeDialog = false },
            onApply = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showCustomRangeDialog = false
            }
        )
    }
}

@Composable
private fun CashAndProfitBreakdownCard(
    report: FinancialReport,
    currency: String,
    showProfitDetails: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ملخص الصندوق والتدفقات النقدية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ReportLineRow("الرصيد الافتتاحي للصندوق", formatMoney(report.openingCashBalance, currency), CyanSecondary)
            ReportLineRow("(+) المبيعات النقدية المحصّلة", formatMoney(report.cashSales, currency), EmeraldProfit)
            ReportLineRow("(+) تسديدات الزبائن (كريدي)", formatMoney(report.customerPaymentsCollected, currency), EmeraldProfit)
            ReportLineRow("(+) إيداعات نقدية", formatMoney(report.cashDeposits, currency), EmeraldProfit)
            ReportLineRow("(-) مشتريات مدفوعة من الصندوق", formatMoney(report.cashPurchasesCost, currency), AmberWarning)
            ReportLineRow("(-) المصاريف التشغيلية", formatMoney(report.totalExpenses, currency), CrimsonDanger)
            ReportLineRow("(-) المسحوبات النقدية", formatMoney(report.cashWithdrawals, currency), CrimsonDanger)
            HorizontalDivider(color = NavyBorder)
            ReportLineRow("الرصيد النقدي الإغلاقي الفعلي", formatMoney(report.closingCashBalance, currency), CyanSecondary, bold = true)

            Spacer(Modifier.height(6.dp))
            Text("قائمة الدخل والأرباح (منفصلة عن الصندوق)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ReportLineRow("إجمالي الإيرادات (نقدي + آجل)", formatMoney(report.totalRevenue, currency), CobaltPrimary)
            ReportLineRow("منها مبيعات آجل (كريدي)", formatMoney(report.creditSales, currency), AmberWarning)
            if (showProfitDetails) {
                ReportLineRow("(-) تكلفة البضاعة المباعة (COGS)", formatMoney(report.costOfGoodsSold, currency), MaterialTheme.colorScheme.onSurfaceVariant)
                ReportLineRow("إجمالي الربح (Gross Profit)", formatMoney(report.grossProfit, currency), EmeraldProfit, bold = true)
                ReportLineRow("(-) المصاريف التشغيلية", formatMoney(report.totalExpenses, currency), CrimsonDanger)
                HorizontalDivider(color = NavyBorder)
                ReportLineRow(
                    "صافي الربح (Net Profit)",
                    formatMoney(report.netProfit, currency),
                    if (report.netProfit >= 0) EmeraldProfit else CrimsonDanger,
                    bold = true
                )
            }
        }
    }
}

@Composable
private fun ReportLineRow(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun CashMovementDialog(
    type: String,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val title = when (type) {
        "OPENING_BALANCE" -> "تسجيل الرصيد الافتتاحي للصندوق"
        "DEPOSIT" -> "تسجيل إيداع نقدي بالصندوق"
        else -> "تسجيل سحب نقدي من الصندوق"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let { Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountText.toDoubleOrNull()
                if (amt == null || amt <= 0.0) errorText = "يرجى إدخال مبلغ صحيح أكبر من صفر"
                else onConfirm(amt, notes)
            }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember { sdf.format(System.currentTimeMillis()) }
    var fromText by remember { mutableStateOf(today) }
    var toText by remember { mutableStateOf(today) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyElevated,
        title = { Text("تحديد فترة مخصصة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل التاريخ بصيغة YYYY-MM-DD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = { Text("من تاريخ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("إلى تاريخ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let { Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val startDate = sdf.parse(fromText.trim())
                    val endDate = sdf.parse(toText.trim())
                    if (startDate == null || endDate == null) {
                        errorText = "صيغة التاريخ غير صحيحة"
                    } else {
                        val endOfDay = endDate.time + 86_399_999L
                        onApply(startDate.time, endOfDay)
                    }
                } catch (_: Exception) {
                    errorText = "صيغة التاريخ يجب أن تكون YYYY-MM-DD"
                }
            }) { Text("تطبيق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
