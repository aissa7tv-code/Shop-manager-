package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CashMovementEntity
import com.example.data.db.CreditTransactionEntity
import com.example.data.db.CustomerEntity
import com.example.data.db.ExpenseEntity
import com.example.data.db.InventoryLedgerEntity
import com.example.data.db.InvoiceEntity
import com.example.data.db.InvoiceItemEntity
import com.example.data.db.ProductEntity
import com.example.data.db.PurchaseEntity
import com.example.data.db.ShopDatabase
import com.example.data.db.ShopSettingsEntity
import com.example.data.model.IntegrityCheckResult
import com.example.data.model.roundMoney
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.data.security.UserRole
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class BackupManager(private val db: ShopDatabase) {

    suspend fun exportBackupJson(actorRole: UserRole): String {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        val now = System.currentTimeMillis()
        val settings = (db.settingsDao().getSettingsOnce() ?: ShopSettingsEntity()).copy(lastBackupAt = now)
        db.settingsDao().upsert(settings)

        val root = JSONObject().apply {
            put("app", "Smart Shop Manager Pro")
            put("schemaVersion", 1)
            put("exportedAt", now)
            put("settings", JSONObject().apply {
                put("shopName", settings.shopName)
                put("currencySymbol", settings.currencySymbol)
                put("defaultPackSize", settings.defaultPackSize)
                put("lowStockDefault", settings.lowStockDefault)
                put("pinEnabled", settings.pinEnabled)
                put("ownerPinHash", settings.ownerPinHash)
                put("ownerPinSalt", settings.ownerPinSalt)
                put("cashierPinHash", settings.cashierPinHash)
                put("cashierPinSalt", settings.cashierPinSalt)
                put("activeRole", settings.activeRole)
                put("lastBackupAt", now)
            })
            put("products", JSONArray().apply {
                db.productDao().getAllProductsOnce().forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("packPrice", p.packPrice)
                        put("unitPrice", p.unitPrice)
                        put("purchaseCost", p.purchaseCost)
                        put("stockUnits", p.stockUnits)
                        put("packSize", p.packSize)
                        put("lowStockThreshold", p.lowStockThreshold)
                        put("isActive", p.isActive)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                    })
                }
            })
            put("customers", JSONArray().apply {
                db.customerDao().getAllOnce().forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("notes", c.notes)
                        put("balance", c.balance)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("invoices", JSONArray().apply {
                db.invoiceDao().getAllInvoicesOnce().forEach { inv ->
                    put(JSONObject().apply {
                        put("id", inv.id)
                        put("clientRequestId", inv.clientRequestId)
                        put("paymentType", inv.paymentType)
                        if (inv.customerId != null) put("customerId", inv.customerId)
                        if (inv.customerNameSnapshot != null) put("customerNameSnapshot", inv.customerNameSnapshot)
                        put("totalRevenue", inv.totalRevenue)
                        put("totalCost", inv.totalCost)
                        put("totalProfit", inv.totalProfit)
                        put("packsSold", inv.packsSold)
                        put("unitsSold", inv.unitsSold)
                        put("notes", inv.notes)
                        put("createdBy", inv.createdBy)
                        put("createdAt", inv.createdAt)
                    })
                }
            })
            put("invoice_items", JSONArray().apply {
                db.invoiceDao().getAllItemsOnce().forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("invoiceId", item.invoiceId)
                        put("productId", item.productId)
                        put("productNameSnapshot", item.productNameSnapshot)
                        put("saleUnitType", item.saleUnitType)
                        put("quantity", item.quantity)
                        put("unitsDeducted", item.unitsDeducted)
                        put("unitSalePrice", item.unitSalePrice)
                        put("lineTotal", item.lineTotal)
                        put("lineCost", item.lineCost)
                        put("lineProfit", item.lineProfit)
                    })
                }
            })
            put("inventory_ledger", JSONArray().apply {
                db.inventoryLedgerDao().getAllOnce().forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("productId", e.productId)
                        put("productNameSnapshot", e.productNameSnapshot)
                        put("changeType", e.changeType)
                        put("deltaUnits", e.deltaUnits)
                        put("stockBefore", e.stockBefore)
                        put("stockAfter", e.stockAfter)
                        if (e.referenceId != null) put("referenceId", e.referenceId)
                        put("notes", e.notes)
                        put("createdBy", e.createdBy)
                        put("createdAt", e.createdAt)
                    })
                }
            })
            put("credit_transactions", JSONArray().apply {
                db.creditTransactionDao().getAllOnce().forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("customerId", tx.customerId)
                        put("type", tx.type)
                        put("amount", tx.amount)
                        put("deltaBalance", tx.deltaBalance)
                        put("balanceAfter", tx.balanceAfter)
                        if (tx.invoiceId != null) put("invoiceId", tx.invoiceId)
                        put("notes", tx.notes)
                        put("createdBy", tx.createdBy)
                        put("createdAt", tx.createdAt)
                    })
                }
            })
            put("expenses", JSONArray().apply {
                db.expenseDao().getAllOnce().forEach { ex ->
                    put(JSONObject().apply {
                        put("id", ex.id)
                        put("category", ex.category)
                        put("description", ex.description)
                        put("amount", ex.amount)
                        put("createdBy", ex.createdBy)
                        put("createdAt", ex.createdAt)
                    })
                }
            })
            put("purchases", JSONArray().apply {
                db.purchaseDao().getAllOnce().forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("productId", p.productId)
                        put("productNameSnapshot", p.productNameSnapshot)
                        put("supplierName", p.supplierName)
                        put("packsQuantity", p.packsQuantity)
                        put("unitsAdded", p.unitsAdded)
                        put("unitCostPerPack", p.unitCostPerPack)
                        put("totalCost", p.totalCost)
                        put("paidFromCash", p.paidFromCash)
                        put("notes", p.notes)
                        put("createdBy", p.createdBy)
                        put("createdAt", p.createdAt)
                    })
                }
            })
            put("cash_movements", JSONArray().apply {
                db.cashMovementDao().getAllOnce().forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("type", m.type)
                        put("amount", m.amount)
                        put("notes", m.notes)
                        put("createdBy", m.createdBy)
                        put("createdAt", m.createdAt)
                    })
                }
            })
        }
        return root.toString(2)
    }

    suspend fun restoreFromBackupJson(rawJson: String, actorRole: UserRole) {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        val root = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("ملف النسخة الاحتياطية غير صالح أو تالف")
        }

        val version = root.optInt("schemaVersion", -1)
        require(version == 1) { "إصدار النسخة الاحتياطية غير مدعوم ($version)" }

        val products = parseProducts(root.optJSONArray("products"))
        require(products.all { it.stockUnits >= 0 && it.packSize >= 1 }) {
            "النسخة الاحتياطية تحتوي على قيم مخزون غير صالحة"
        }
        val customers = parseCustomers(root.optJSONArray("customers"))
        val invoices = parseInvoices(root.optJSONArray("invoices"))
        val invoiceItems = parseInvoiceItems(root.optJSONArray("invoice_items"))
        val ledger = parseLedger(root.optJSONArray("inventory_ledger"))
        val creditTxs = parseCreditTxs(root.optJSONArray("credit_transactions"))
        val expenses = parseExpenses(root.optJSONArray("expenses"))
        val purchases = parsePurchases(root.optJSONArray("purchases"))
        val cashMovements = parseCashMovements(root.optJSONArray("cash_movements"))
        val settingsObj = root.optJSONObject("settings")

        db.withTransaction {
            // Delete child tables before parent tables (respecting foreign keys)
            db.invoiceDao().deleteAllItems()
            db.creditTransactionDao().deleteAll()
            db.inventoryLedgerDao().deleteAll()
            db.purchaseDao().deleteAll()
            db.invoiceDao().deleteAllInvoices()
            db.expenseDao().deleteAll()
            db.cashMovementDao().deleteAll()
            db.customerDao().deleteAll()
            db.productDao().deleteAll()

            // Insert parents before children
            if (settingsObj != null) {
                db.settingsDao().upsert(
                    ShopSettingsEntity(
                        id = 1,
                        shopName = settingsObj.optString("shopName", "Smart Shop Manager Pro"),
                        currencySymbol = settingsObj.optString("currencySymbol", "دج"),
                        defaultPackSize = settingsObj.optInt("defaultPackSize", 20).coerceAtLeast(1),
                        lowStockDefault = settingsObj.optInt("lowStockDefault", 20).coerceAtLeast(0),
                        pinEnabled = settingsObj.optBoolean("pinEnabled", false),
                        ownerPinHash = settingsObj.optString("ownerPinHash", ""),
                        ownerPinSalt = settingsObj.optString("ownerPinSalt", ""),
                        cashierPinHash = settingsObj.optString("cashierPinHash", ""),
                        cashierPinSalt = settingsObj.optString("cashierPinSalt", ""),
                        activeRole = UserRole.OWNER.code,
                        lastBackupAt = settingsObj.optLong("lastBackupAt", System.currentTimeMillis())
                    )
                )
            }
            if (products.isNotEmpty()) db.productDao().insertAllReplace(products)
            if (customers.isNotEmpty()) db.customerDao().insertAllReplace(customers)
            if (invoices.isNotEmpty()) db.invoiceDao().insertInvoicesReplace(invoices)
            if (invoiceItems.isNotEmpty()) db.invoiceDao().insertItemsReplace(invoiceItems)
            if (ledger.isNotEmpty()) db.inventoryLedgerDao().insertAllReplace(ledger)
            if (creditTxs.isNotEmpty()) db.creditTransactionDao().insertAllReplace(creditTxs)
            if (expenses.isNotEmpty()) db.expenseDao().insertAllReplace(expenses)
            if (purchases.isNotEmpty()) db.purchaseDao().insertAllReplace(purchases)
            if (cashMovements.isNotEmpty()) db.cashMovementDao().insertAllReplace(cashMovements)
        }
    }

    suspend fun runIntegrityCheck(): IntegrityCheckResult {
        val products = db.productDao().getAllProductsOnce()
        val negativeStock = products.count { it.stockUnits < 0 }
        val customers = db.customerDao().getAllOnce()
        var balanceMismatches = 0
        for (c in customers) {
            val txs = db.creditTransactionDao().getForCustomerOnce(c.id)
            val ledgerSum = roundMoney(txs.sumOf { it.deltaBalance })
            if (abs(ledgerSum - c.balance) > 0.02) {
                balanceMismatches++
            }
        }
        val healthy = negativeStock == 0 && balanceMismatches == 0
        val msg = if (healthy) {
            "قاعدة البيانات سليمة 100%: تم فحص ${products.size} منتج و ${customers.size} زبون دون أي تعارض."
        } else {
            "تم رصد $negativeStock مخزون سالب و $balanceMismatches عدم تطابق في أرصدة الزبائن."
        }
        return IntegrityCheckResult(
            isHealthy = healthy,
            checkedProducts = products.size,
            negativeStockCount = negativeStock,
            checkedCustomers = customers.size,
            balanceMismatchCount = balanceMismatches,
            summaryMessage = msg
        )
    }

    private fun parseProducts(arr: JSONArray?): List<ProductEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ProductEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                packPrice = o.getDouble("packPrice"),
                unitPrice = o.getDouble("unitPrice"),
                purchaseCost = o.getDouble("purchaseCost"),
                stockUnits = o.getInt("stockUnits"),
                packSize = o.optInt("packSize", 20),
                lowStockThreshold = o.optInt("lowStockThreshold", 20),
                isActive = o.optBoolean("isActive", true),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseCustomers(arr: JSONArray?): List<CustomerEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CustomerEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                phone = o.optString("phone", ""),
                notes = o.optString("notes", ""),
                balance = o.optDouble("balance", 0.0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseInvoices(arr: JSONArray?): List<InvoiceEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            InvoiceEntity(
                id = o.getLong("id"),
                clientRequestId = o.getString("clientRequestId"),
                paymentType = o.getString("paymentType"),
                customerId = if (o.has("customerId") && !o.isNull("customerId")) o.getLong("customerId") else null,
                customerNameSnapshot = if (o.has("customerNameSnapshot") && !o.isNull("customerNameSnapshot")) o.getString("customerNameSnapshot") else null,
                totalRevenue = o.getDouble("totalRevenue"),
                totalCost = o.getDouble("totalCost"),
                totalProfit = o.getDouble("totalProfit"),
                packsSold = o.optInt("packsSold", 0),
                unitsSold = o.optInt("unitsSold", 0),
                notes = o.optString("notes", ""),
                createdBy = o.optString("createdBy", "OWNER"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseInvoiceItems(arr: JSONArray?): List<InvoiceItemEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            InvoiceItemEntity(
                id = o.getLong("id"),
                invoiceId = o.getLong("invoiceId"),
                productId = o.getLong("productId"),
                productNameSnapshot = o.getString("productNameSnapshot"),
                saleUnitType = o.getString("saleUnitType"),
                quantity = o.getInt("quantity"),
                unitsDeducted = o.getInt("unitsDeducted"),
                unitSalePrice = o.getDouble("unitSalePrice"),
                lineTotal = o.getDouble("lineTotal"),
                lineCost = o.getDouble("lineCost"),
                lineProfit = o.getDouble("lineProfit")
            )
        }
    }

    private fun parseLedger(arr: JSONArray?): List<InventoryLedgerEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            InventoryLedgerEntity(
                id = o.getLong("id"),
                productId = o.getLong("productId"),
                productNameSnapshot = o.getString("productNameSnapshot"),
                changeType = o.getString("changeType"),
                deltaUnits = o.getInt("deltaUnits"),
                stockBefore = o.getInt("stockBefore"),
                stockAfter = o.getInt("stockAfter"),
                referenceId = if (o.has("referenceId") && !o.isNull("referenceId")) o.getLong("referenceId") else null,
                notes = o.optString("notes", ""),
                createdBy = o.optString("createdBy", "OWNER"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseCreditTxs(arr: JSONArray?): List<CreditTransactionEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CreditTransactionEntity(
                id = o.getLong("id"),
                customerId = o.getLong("customerId"),
                type = o.getString("type"),
                amount = o.getDouble("amount"),
                deltaBalance = o.getDouble("deltaBalance"),
                balanceAfter = o.getDouble("balanceAfter"),
                invoiceId = if (o.has("invoiceId") && !o.isNull("invoiceId")) o.getLong("invoiceId") else null,
                notes = o.optString("notes", ""),
                createdBy = o.optString("createdBy", "OWNER"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseExpenses(arr: JSONArray?): List<ExpenseEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ExpenseEntity(
                id = o.getLong("id"),
                category = o.getString("category"),
                description = o.getString("description"),
                amount = o.getDouble("amount"),
                createdBy = o.optString("createdBy", "OWNER"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun parsePurchases(arr: JSONArray?): List<PurchaseEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PurchaseEntity(
                id = o.getLong("id"),
                productId = o.getLong("productId"),
                productNameSnapshot = o.getString("productNameSnapshot"),
                supplierName = o.optString("supplierName", ""),
                packsQuantity = o.getInt("packsQuantity"),
                unitsAdded = o.getInt("unitsAdded"),
                unitCostPerPack = o.getDouble("unitCostPerPack"),
                totalCost = o.getDouble("totalCost"),
                paidFromCash = o.optBoolean("paidFromCash", true),
                notes = o.optString("notes", ""),
                createdBy = o.optString("createdBy", "OWNER"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseCashMovements(arr: JSONArray?): List<CashMovementEntity> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CashMovementEntity(
                id = o.getLong("id"),
                type = o.getString("type"),
                amount = o.getDouble("amount"),
                notes = o.optString("notes", ""),
                createdBy = o.optString("createdBy", "OWNER"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
