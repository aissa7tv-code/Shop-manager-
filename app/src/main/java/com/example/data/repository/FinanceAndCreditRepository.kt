package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CashMovementEntity
import com.example.data.db.CreditTransactionEntity
import com.example.data.db.CustomerEntity
import com.example.data.db.ExpenseEntity
import com.example.data.db.InventoryLedgerEntity
import com.example.data.db.PurchaseEntity
import com.example.data.db.ShopDatabase
import com.example.data.db.ShopSettingsEntity
import com.example.data.model.DebtorAgingRow
import com.example.data.model.FinancialReport
import com.example.data.model.PaymentType
import com.example.data.model.ProductAnalyticsRow
import com.example.data.model.roundMoney
import com.example.data.security.PinSecurity
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.data.security.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class FinanceAndCreditRepository(private val db: ShopDatabase) {
    private val customerDao = db.customerDao()
    private val creditDao = db.creditTransactionDao()
    private val expenseDao = db.expenseDao()
    private val purchaseDao = db.purchaseDao()
    private val cashDao = db.cashMovementDao()
    private val productDao = db.productDao()
    private val ledgerDao = db.inventoryLedgerDao()
    private val invoiceDao = db.invoiceDao()
    private val settingsDao = db.settingsDao()

    private val writeMutex = Mutex()

    fun observeAllCustomers(): Flow<List<CustomerEntity>> = customerDao.observeAllCustomers()
    fun observeDebtors(): Flow<List<CustomerEntity>> = customerDao.observeDebtors()
    fun observeAllCreditTransactions(): Flow<List<CreditTransactionEntity>> = creditDao.observeAllTransactions()
    fun observeCustomerTransactions(customerId: Long): Flow<List<CreditTransactionEntity>> =
        creditDao.observeForCustomer(customerId)
    fun observeAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.observeAllExpenses()
    fun observeAllPurchases(): Flow<List<PurchaseEntity>> = purchaseDao.observeAllPurchases()
    fun observeCashMovements(): Flow<List<CashMovementEntity>> = cashDao.observeAllMovements()
    fun observeSettings(): Flow<ShopSettingsEntity?> = settingsDao.observeSettings()

    suspend fun getSettingsOrDefault(): ShopSettingsEntity =
        settingsDao.getSettingsOnce() ?: ShopSettingsEntity()

    suspend fun addCustomer(
        name: String,
        phone: String,
        notes: String,
        actorRole: UserRole
    ): Long = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_CUSTOMERS)
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "يرجى إدخال اسم الزبون" }
        val now = System.currentTimeMillis()
        customerDao.insert(
            CustomerEntity(
                name = cleanName,
                phone = phone.trim(),
                notes = notes.trim(),
                balance = 0.0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateCustomer(
        customerId: Long,
        name: String,
        phone: String,
        notes: String,
        actorRole: UserRole
    ) = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_CUSTOMERS)
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "يرجى إدخال اسم الزبون" }
        db.withTransaction {
            val current = customerDao.getById(customerId)
                ?: throw IllegalArgumentException("الزبون غير موجود")
            customerDao.update(
                current.copy(
                    name = cleanName,
                    phone = phone.trim(),
                    notes = notes.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun registerDebt(
        customerId: Long,
        amount: Double,
        notes: String,
        actorRole: UserRole
    ): CreditTransactionEntity = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.REGISTER_CREDIT_OR_PAYMENT)
        val cleanAmount = roundMoney(amount)
        require(cleanAmount > 0.0) { "مبلغ الدين يجب أن يكون أكبر من صفر" }

        db.withTransaction {
            val customer = customerDao.getById(customerId)
                ?: throw IllegalArgumentException("الزبون غير موجود")
            val now = System.currentTimeMillis()
            val newBalance = roundMoney(customer.balance + cleanAmount)
            customerDao.updateBalance(customer.id, newBalance, now)
            val tx = CreditTransactionEntity(
                customerId = customer.id,
                type = "DEBT",
                amount = cleanAmount,
                deltaBalance = cleanAmount,
                balanceAfter = newBalance,
                notes = notes.trim().ifBlank { "تسجيل دين جديد" },
                createdBy = actorRole.code,
                createdAt = now
            )
            val id = creditDao.insert(tx)
            tx.copy(id = id)
        }
    }

    suspend fun recordPayment(
        customerId: Long,
        amount: Double,
        notes: String,
        actorRole: UserRole
    ): CreditTransactionEntity = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.REGISTER_CREDIT_OR_PAYMENT)
        val cleanAmount = roundMoney(amount)
        require(cleanAmount > 0.0) { "مبلغ التسديد يجب أن يكون أكبر من صفر" }

        db.withTransaction {
            val customer = customerDao.getById(customerId)
                ?: throw IllegalArgumentException("الزبون غير موجود")
            if (customer.balance <= 0.0) {
                throw IllegalStateException("لا يوجد دين مستحق على هذا الزبون")
            }
            if (cleanAmount > roundMoney(customer.balance + 0.01)) {
                throw IllegalArgumentException(
                    "مبلغ التسديد ($cleanAmount) يتجاوز الرصيد المستحق (${customer.balance})"
                )
            }
            val now = System.currentTimeMillis()
            val newBalance = roundMoney((customer.balance - cleanAmount).coerceAtLeast(0.0))
            customerDao.updateBalance(customer.id, newBalance, now)
            val tx = CreditTransactionEntity(
                customerId = customer.id,
                type = "PAYMENT",
                amount = cleanAmount,
                deltaBalance = -cleanAmount,
                balanceAfter = newBalance,
                notes = notes.trim().ifBlank {
                    if (newBalance == 0.0) "تسديد كامل الدين" else "تسديد جزئي"
                },
                createdBy = actorRole.code,
                createdAt = now
            )
            val id = creditDao.insert(tx)
            tx.copy(id = id)
        }
    }

    /**
     * Reversible credit adjustment — preserves immutable audit trail instead of deleting financial history.
     */
    suspend fun recordCreditAdjustment(
        customerId: Long,
        deltaAmount: Double,
        notes: String,
        actorRole: UserRole
    ): CreditTransactionEntity = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        val cleanDelta = roundMoney(deltaAmount)
        require(cleanDelta != 0.0) { "قيمة التسوية يجب أن تختلف عن الصفر" }
        require(notes.trim().isNotEmpty()) { "يرجى ذكر سبب التسوية للتوثيق" }

        db.withTransaction {
            val customer = customerDao.getById(customerId)
                ?: throw IllegalArgumentException("الزبون غير موجود")
            val newBalance = roundMoney(customer.balance + cleanDelta)
            require(newBalance >= 0.0) { "لا يمكن أن يصبح رصيد الدين سالباً" }
            val now = System.currentTimeMillis()
            customerDao.updateBalance(customer.id, newBalance, now)
            val tx = CreditTransactionEntity(
                customerId = customer.id,
                type = "ADJUSTMENT",
                amount = kotlin.math.abs(cleanDelta),
                deltaBalance = cleanDelta,
                balanceAfter = newBalance,
                notes = "تسوية: ${notes.trim()}",
                createdBy = actorRole.code,
                createdAt = now
            )
            val id = creditDao.insert(tx)
            tx.copy(id = id)
        }
    }

    suspend fun addExpense(
        category: String,
        description: String,
        amount: Double,
        timestampMillis: Long = System.currentTimeMillis(),
        actorRole: UserRole
    ): Long = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.ADD_EXPENSE)
        val cleanAmount = roundMoney(amount)
        require(cleanAmount > 0.0) { "مبلغ المصروف يجب أن يكون أكبر من صفر" }
        require(category.trim().isNotEmpty()) { "يرجى اختيار تصنيف المصروف" }
        require(description.trim().isNotEmpty()) { "يرجى إدخال وصف المصروف" }

        expenseDao.insert(
            ExpenseEntity(
                category = category.trim(),
                description = description.trim(),
                amount = cleanAmount,
                createdBy = actorRole.code,
                createdAt = timestampMillis
            )
        )
    }

    suspend fun updateExpense(
        expenseId: Long,
        category: String,
        description: String,
        amount: Double,
        actorRole: UserRole
    ) = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.EDIT_EXPENSE)
        val cleanAmount = roundMoney(amount)
        require(cleanAmount > 0.0) { "مبلغ المصروف يجب أن يكون أكبر من صفر" }
        require(description.trim().isNotEmpty()) { "يرجى إدخال وصف المصروف" }
        db.withTransaction {
            val existing = expenseDao.getById(expenseId)
                ?: throw IllegalArgumentException("سجل المصروف غير موجود")
            expenseDao.update(
                existing.copy(
                    category = category.trim(),
                    description = description.trim(),
                    amount = cleanAmount
                )
            )
        }
    }

    /**
     * Atomically records an inventory purchase, increments product stock, updates purchaseCost,
     * and logs PURCHASE into the inventory ledger. Never double-counted as an operating expense.
     */
    suspend fun recordPurchase(
        productId: Long,
        supplierName: String,
        packsQuantity: Int,
        unitCostPerPack: Double,
        paidFromCash: Boolean,
        notes: String,
        actorRole: UserRole
    ): PurchaseEntity = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.RECORD_PURCHASE)
        require(packsQuantity > 0) { "عدد العلب المشتراة يجب أن يكون أكبر من صفر" }
        val cleanCost = roundMoney(unitCostPerPack)
        require(cleanCost >= 0.0) { "تكلفة الشراء لا يمكن أن تكون سالبة" }

        db.withTransaction {
            val product = productDao.getById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            val now = System.currentTimeMillis()
            val unitsAdded = Math.multiplyExact(packsQuantity, product.packSize)
            val totalCost = roundMoney(packsQuantity * cleanCost)
            val newStock = product.stockUnits + unitsAdded

            productDao.update(
                product.copy(
                    stockUnits = newStock,
                    purchaseCost = cleanCost,
                    updatedAt = now
                )
            )

            val purchase = PurchaseEntity(
                productId = product.id,
                productNameSnapshot = product.name,
                supplierName = supplierName.trim(),
                packsQuantity = packsQuantity,
                unitsAdded = unitsAdded,
                unitCostPerPack = cleanCost,
                totalCost = totalCost,
                paidFromCash = paidFromCash,
                notes = notes.trim(),
                createdBy = actorRole.code,
                createdAt = now
            )
            val purchaseId = purchaseDao.insert(purchase)

            ledgerDao.insert(
                InventoryLedgerEntity(
                    productId = product.id,
                    productNameSnapshot = product.name,
                    changeType = "PURCHASE",
                    deltaUnits = unitsAdded,
                    stockBefore = product.stockUnits,
                    stockAfter = newStock,
                    referenceId = purchaseId,
                    notes = if (supplierName.isNotBlank()) "شراء من المورد: $supplierName" else "شراء بضاعة ($packsQuantity علبة)",
                    createdBy = actorRole.code,
                    createdAt = now
                )
            )
            purchase.copy(id = purchaseId)
        }
    }

    suspend fun recordCashMovement(
        type: String,
        amount: Double,
        notes: String,
        actorRole: UserRole
    ): Long = writeMutex.withLock {
        require(type in setOf("OPENING_BALANCE", "DEPOSIT", "WITHDRAWAL")) {
            "نوع حركة الصندوق غير صالح"
        }
        if (type == "WITHDRAWAL") {
            RolePermissions.requirePermission(actorRole, ShopPermission.RECORD_WITHDRAWAL)
        } else {
            RolePermissions.requirePermission(actorRole, ShopPermission.PERFORM_SALE)
        }
        val cleanAmount = roundMoney(amount)
        require(cleanAmount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }

        cashDao.insert(
            CashMovementEntity(
                type = type,
                amount = cleanAmount,
                notes = notes.trim(),
                createdBy = actorRole.code,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateShopProfile(
        shopName: String,
        currencySymbol: String,
        defaultPackSize: Int,
        lowStockDefault: Int,
        actorRole: UserRole
    ) = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        require(shopName.trim().isNotEmpty()) { "يرجى إدخال اسم المحل" }
        require(currencySymbol.trim().isNotEmpty()) { "يرجى إدخال رمز العملة" }
        require(defaultPackSize >= 1) { "حجم العلبة الافتراضي يجب أن يكون 1 على الأقل" }
        require(lowStockDefault >= 0) { "حد التنبيه يجب أن يكون غير سالب" }

        val current = getSettingsOrDefault()
        settingsDao.upsert(
            current.copy(
                shopName = shopName.trim(),
                currencySymbol = currencySymbol.trim(),
                defaultPackSize = defaultPackSize,
                lowStockDefault = lowStockDefault
            )
        )
    }

    suspend fun configurePins(
        enablePin: Boolean,
        ownerPin: String?,
        cashierPin: String?,
        actorRole: UserRole
    ) = writeMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)
        val current = getSettingsOrDefault()
        var ownerHash = current.ownerPinHash
        var ownerSalt = current.ownerPinSalt
        var cashierHash = current.cashierPinHash
        var cashierSalt = current.cashierPinSalt

        if (!ownerPin.isNullOrBlank()) {
            ownerSalt = PinSecurity.generateSaltHex()
            ownerHash = PinSecurity.hashPin(ownerPin, ownerSalt)
        }
        if (!cashierPin.isNullOrBlank()) {
            cashierSalt = PinSecurity.generateSaltHex()
            cashierHash = PinSecurity.hashPin(cashierPin, cashierSalt)
        }
        if (enablePin && ownerHash.isBlank()) {
            throw IllegalArgumentException("يرجى تعيين رمز PIN للمالك قبل تفعيل القفل")
        }
        settingsDao.upsert(
            current.copy(
                pinEnabled = enablePin,
                ownerPinHash = ownerHash,
                ownerPinSalt = ownerSalt,
                cashierPinHash = cashierHash,
                cashierPinSalt = cashierSalt
            )
        )
    }

    suspend fun authenticateRole(pin: String): UserRole? {
        val settings = getSettingsOrDefault()
        if (!settings.pinEnabled) return UserRole.fromCode(settings.activeRole)
        if (PinSecurity.verifyPin(pin, settings.ownerPinHash, settings.ownerPinSalt)) {
            settingsDao.upsert(settings.copy(activeRole = UserRole.OWNER.code))
            return UserRole.OWNER
        }
        if (PinSecurity.verifyPin(pin, settings.cashierPinHash, settings.cashierPinSalt)) {
            settingsDao.upsert(settings.copy(activeRole = UserRole.CASHIER.code))
            return UserRole.CASHIER
        }
        return null
    }

    suspend fun switchRoleWithoutPinIfDisabled(targetRole: UserRole) = writeMutex.withLock {
        val settings = getSettingsOrDefault()
        if (settings.pinEnabled) {
            throw SecurityException("تفعيل القفل يتطلب إدخال رمز PIN لتبديل الصلاحية")
        }
        settingsDao.upsert(settings.copy(activeRole = targetRole.code))
    }

    suspend fun buildFinancialReport(fromMillis: Long, toMillis: Long): FinancialReport {
        val invoices = invoiceDao.getInvoicesInRangeOnce(fromMillis, toMillis)
        val items = invoiceDao.getItemsInRangeOnce(fromMillis, toMillis)
        val expenses = expenseDao.getInRangeOnce(fromMillis, toMillis)
        val purchases = purchaseDao.getInRangeOnce(fromMillis, toMillis)
        val creditTxs = creditDao.getInRangeOnce(fromMillis, toMillis)
        val cashMovements = cashDao.getInRangeOnce(fromMillis, toMillis)
        val allProducts = productDao.getAllProductsOnce()
        val allCustomers = customerDao.getAllOnce()
        val allCreditTxs = creditDao.getAllOnce()

        val totalRevenue = roundMoney(invoices.sumOf { it.totalRevenue })
        val cashSales = roundMoney(
            invoices.filter { it.paymentType == PaymentType.CASH.code }.sumOf { it.totalRevenue }
        )
        val creditSales = roundMoney(
            invoices.filter { it.paymentType == PaymentType.CREDIT.code }.sumOf { it.totalRevenue }
        )
        val cogs = roundMoney(invoices.sumOf { it.totalCost })
        val grossProfit = roundMoney(totalRevenue - cogs)
        val totalExpenses = roundMoney(expenses.sumOf { it.amount })
        val netProfit = roundMoney(grossProfit - totalExpenses)

        val packsSold = invoices.sumOf { it.packsSold }
        val unitsSold = invoices.sumOf { it.unitsSold }

        val customerPaymentsCollected = roundMoney(
            creditTxs.filter { it.type == "PAYMENT" }.sumOf { it.amount }
        )
        val totalPurchasesCost = roundMoney(purchases.sumOf { it.totalCost })
        val cashPurchasesCost = roundMoney(purchases.filter { it.paidFromCash }.sumOf { it.totalCost })

        val openingCash = roundMoney(
            cashMovements.filter { it.type == "OPENING_BALANCE" }.sumOf { it.amount }
        )
        val cashDeposits = roundMoney(
            cashMovements.filter { it.type == "DEPOSIT" }.sumOf { it.amount }
        )
        val cashWithdrawals = roundMoney(
            cashMovements.filter { it.type == "WITHDRAWAL" }.sumOf { it.amount }
        )

        val closingCashBalance = roundMoney(
            openingCash + cashSales + customerPaymentsCollected + cashDeposits -
                cashPurchasesCost - totalExpenses - cashWithdrawals
        )

        // Product breakdown
        val productMap = allProducts.associateBy { it.id }
        val groupedItems = items.groupBy { it.productId }
        val productBreakdown = groupedItems.map { (productId, lineList) ->
            val prod = productMap[productId]
            ProductAnalyticsRow(
                productId = productId,
                productName = prod?.name ?: lineList.first().productNameSnapshot,
                packsSold = lineList.filter { it.saleUnitType == "PACK" }.sumOf { it.quantity },
                unitsSold = lineList.filter { it.saleUnitType == "UNIT" }.sumOf { it.quantity },
                totalUnitsDeducted = lineList.sumOf { it.unitsDeducted },
                revenue = roundMoney(lineList.sumOf { it.lineTotal }),
                cost = roundMoney(lineList.sumOf { it.lineCost }),
                profit = roundMoney(lineList.sumOf { it.lineProfit }),
                currentStockUnits = prod?.stockUnits ?: 0,
                packSize = prod?.packSize ?: 20
            )
        }.sortedByDescending { it.revenue }

        val activeProducts = allProducts.filter { it.isActive }
        val outOfStock = activeProducts.filter { it.stockUnits == 0 }
        val lowStock = activeProducts.filter { it.stockUnits in 1..it.lowStockThreshold }

        // Customer aging & debts
        val now = System.currentTimeMillis()
        val txByCustomer = allCreditTxs.groupBy { it.customerId }
        val debtors = allCustomers.filter { it.balance > 0.0 }.map { cust ->
            val custTxs = txByCustomer[cust.id].orEmpty()
            val lastActivity = custTxs.maxOfOrNull { it.createdAt } ?: cust.updatedAt
            val oldestDebtTime = custTxs.filter { it.type == "DEBT" }.minOfOrNull { it.createdAt } ?: cust.createdAt
            val agingDays = TimeUnit.MILLISECONDS.toDays((now - oldestDebtTime).coerceAtLeast(0L)).toInt()
            DebtorAgingRow(
                customerId = cust.id,
                customerName = cust.name,
                phone = cust.phone,
                balance = cust.balance,
                oldestUnpaidDays = agingDays,
                lastActivityAt = lastActivity
            )
        }.sortedByDescending { it.balance }

        val totalOutstandingDebts = roundMoney(debtors.sumOf { it.balance })

        return FinancialReport(
            fromMillis = fromMillis,
            toMillis = toMillis,
            invoiceCount = invoices.size,
            packsSold = packsSold,
            unitsSold = unitsSold,
            totalRevenue = totalRevenue,
            cashSales = cashSales,
            creditSales = creditSales,
            costOfGoodsSold = cogs,
            grossProfit = grossProfit,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            customerPaymentsCollected = customerPaymentsCollected,
            totalPurchasesCost = totalPurchasesCost,
            cashPurchasesCost = cashPurchasesCost,
            openingCashBalance = openingCash,
            cashDeposits = cashDeposits,
            cashWithdrawals = cashWithdrawals,
            closingCashBalance = closingCashBalance,
            productBreakdown = productBreakdown,
            lowStockProducts = lowStock,
            outOfStockProducts = outOfStock,
            totalOutstandingDebts = totalOutstandingDebts,
            debtors = debtors
        )
    }
}
