package com.example.data.model

import com.example.data.db.InvoiceEntity
import com.example.data.db.InvoiceItemEntity
import com.example.data.db.ProductEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class SaleUnitType(val code: String, val labelAr: String) {
    PACK("PACK", "علبة"),
    UNIT("UNIT", "وحدة");

    companion object {
        fun fromCode(code: String): SaleUnitType =
            if (code.equals("PACK", ignoreCase = true)) PACK else UNIT
    }
}

enum class PaymentType(val code: String, val labelAr: String) {
    CASH("CASH", "نقدي"),
    CREDIT("CREDIT", "كريدي / آجل");

    companion object {
        fun fromCode(code: String): PaymentType =
            if (code.equals("CREDIT", ignoreCase = true)) CREDIT else CASH
    }
}

enum class PeriodFilter(val labelAr: String) {
    DAILY("اليوم"),
    WEEKLY("الأسبوع"),
    MONTHLY("الشهر"),
    CUSTOM("مخصص")
}

data class CartItem(
    val productId: Long,
    val productName: String,
    val saleUnitType: SaleUnitType,
    val quantity: Int,
    val packSize: Int,
    val unitPrice: Double,
    val availableStockUnits: Int
) {
    val unitsRequired: Int
        get() = if (saleUnitType == SaleUnitType.PACK) quantity * packSize else quantity

    val lineTotal: Double
        get() = roundMoney(unitPrice * quantity)

    val key: String
        get() = "${productId}_${saleUnitType.code}"
}

data class CheckoutLineRequest(
    val productId: Long,
    val saleUnitType: SaleUnitType,
    val quantity: Int
)

data class CheckoutRequest(
    val clientRequestId: String,
    val items: List<CheckoutLineRequest>,
    val paymentType: PaymentType = PaymentType.CASH,
    val customerId: Long? = null,
    val notes: String = ""
)

data class CheckoutResult(
    val invoice: InvoiceEntity,
    val items: List<InvoiceItemEntity>,
    val wasDuplicateRequest: Boolean = false
)

data class ProductAnalyticsRow(
    val productId: Long,
    val productName: String,
    val packsSold: Int,
    val unitsSold: Int,
    val totalUnitsDeducted: Int,
    val revenue: Double,
    val cost: Double,
    val profit: Double,
    val currentStockUnits: Int,
    val packSize: Int
)

data class DebtorAgingRow(
    val customerId: Long,
    val customerName: String,
    val phone: String,
    val balance: Double,
    val oldestUnpaidDays: Int,
    val lastActivityAt: Long
)

data class FinancialReport(
    val fromMillis: Long,
    val toMillis: Long,
    val invoiceCount: Int,
    val packsSold: Int,
    val unitsSold: Int,
    val totalRevenue: Double,
    val cashSales: Double,
    val creditSales: Double,
    val costOfGoodsSold: Double,
    val grossProfit: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val customerPaymentsCollected: Double,
    val totalPurchasesCost: Double,
    val cashPurchasesCost: Double,
    val openingCashBalance: Double,
    val cashDeposits: Double,
    val cashWithdrawals: Double,
    val closingCashBalance: Double,
    val productBreakdown: List<ProductAnalyticsRow>,
    val lowStockProducts: List<ProductEntity>,
    val outOfStockProducts: List<ProductEntity>,
    val totalOutstandingDebts: Double,
    val debtors: List<DebtorAgingRow>
)

data class IntegrityCheckResult(
    val isHealthy: Boolean,
    val checkedProducts: Int,
    val negativeStockCount: Int,
    val checkedCustomers: Int,
    val balanceMismatchCount: Int,
    val summaryMessage: String
)

sealed class QuickEntryCommand {
    data class AddStock(val productQuery: String, val deltaUnits: Int, val isPack: Boolean, val count: Int) : QuickEntryCommand()
    data class QuickSell(val productQuery: String, val saleUnitType: SaleUnitType, val quantity: Int) : QuickEntryCommand()
}

/**
 * Supports the shop's established fast-entry syntax:
 *  - "+5s rym" or "+5s Rym" => add 5 single units
 *  - "+1* rym" or "+2* Marlboro" => add 1 or 2 full packs
 *  - "-علبة rym" or "-2 علبة rym" => sell 1 or 2 packs
 *  - "-5 سجارة rym" or "-5 سيجارة rym" or "-5 وحدة rym" => sell 5 single units
 */
object QuickEntryParser {
    private val addUnitsRegex = Regex("""^\+(\d+)\s*[sSو]\s+(.+)$""")
    private val addPacksRegex = Regex("""^\+(\d+)\s*\*\s+(.+)$""")
    private val sellPackRegex = Regex("""^-(\d*)\s*(?:علبة|علب|باكي|pack)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val sellUnitRegex = Regex("""^-(\d+)\s*(?:سجارة|سيجارة|وحدة|حبة|unit|s)\s+(.+)$""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): QuickEntryCommand? {
        val text = rawInput.trim().replace(Regex("\\s+"), " ")
        if (text.isBlank()) return null

        addUnitsRegex.matchEntire(text)?.let { m ->
            val count = m.groupValues[1].toIntOrNull() ?: return null
            val query = m.groupValues[2].trim()
            if (count > 0 && query.isNotBlank()) {
                return QuickEntryCommand.AddStock(query, deltaUnits = count, isPack = false, count = count)
            }
        }

        addPacksRegex.matchEntire(text)?.let { m ->
            val count = m.groupValues[1].toIntOrNull() ?: return null
            val query = m.groupValues[2].trim()
            if (count > 0 && query.isNotBlank()) {
                return QuickEntryCommand.AddStock(query, deltaUnits = count, isPack = true, count = count)
            }
        }

        sellPackRegex.matchEntire(text)?.let { m ->
            val count = m.groupValues[1].ifBlank { "1" }.toIntOrNull() ?: return null
            val query = m.groupValues[2].trim()
            if (count > 0 && query.isNotBlank()) {
                return QuickEntryCommand.QuickSell(query, SaleUnitType.PACK, count)
            }
        }

        sellUnitRegex.matchEntire(text)?.let { m ->
            val count = m.groupValues[1].toIntOrNull() ?: return null
            val query = m.groupValues[2].trim()
            if (count > 0 && query.isNotBlank()) {
                return QuickEntryCommand.QuickSell(query, SaleUnitType.UNIT, count)
            }
        }

        return null
    }
}

fun roundMoney(value: Double): Double = (value * 100.0).roundToLong() / 100.0

fun formatMoney(amount: Double, currency: String = "دج"): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    val pattern = if (abs(amount - amount.roundToLong().toDouble()) < 0.005) "#,##0" else "#,##0.00"
    val formatted = DecimalFormat(pattern, symbols).format(amount)
    return "$formatted $currency"
}

fun formatStockSummary(stockUnits: Int, packSize: Int): String {
    val safePack = packSize.coerceAtLeast(1)
    val packs = stockUnits / safePack
    val remainder = stockUnits % safePack
    return when {
        stockUnits <= 0 -> "نافد (0)"
        packs > 0 && remainder > 0 -> "$packs علبة + $remainder وحدة"
        packs > 0 -> "$packs علبة ($stockUnits وحدة)"
        else -> "$remainder وحدة"
    }
}

fun formatDateTime(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US).format(Date(millis))
}

fun formatDateOnly(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(millis))
}

fun periodBounds(filter: PeriodFilter, customStart: Long? = null, customEnd: Long? = null): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val endOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    return when (filter) {
        PeriodFilter.DAILY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis to endOfToday
        }
        PeriodFilter.WEEKLY -> {
            cal.add(Calendar.DAY_OF_YEAR, -6)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis to endOfToday
        }
        PeriodFilter.MONTHLY -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis to endOfToday
        }
        PeriodFilter.CUSTOM -> {
            val start = customStart ?: periodBounds(PeriodFilter.DAILY).first
            val end = customEnd ?: endOfToday
            minOf(start, end) to maxOf(start, end)
        }
    }
}
