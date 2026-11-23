package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CreditTransactionEntity
import com.example.data.db.InventoryLedgerEntity
import com.example.data.db.InvoiceEntity
import com.example.data.db.InvoiceItemEntity
import com.example.data.db.ProductEntity
import com.example.data.db.ShopDatabase
import com.example.data.model.CheckoutRequest
import com.example.data.model.CheckoutResult
import com.example.data.model.PaymentType
import com.example.data.model.QuickEntryCommand
import com.example.data.model.QuickEntryParser
import com.example.data.model.SaleUnitType
import com.example.data.model.roundMoney
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.data.security.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ShopRepository(private val db: ShopDatabase) {
    private val productDao = db.productDao()
    private val invoiceDao = db.invoiceDao()
    private val ledgerDao = db.inventoryLedgerDao()
    private val customerDao = db.customerDao()
    private val creditDao = db.creditTransactionDao()
    private val settingsDao = db.settingsDao()

    // Serializes concurrent write transactions cleanly in-process alongside SQLite transaction locks
    private val transactionMutex = Mutex()

    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAllProducts()

    fun observeAvailableForSale(): Flow<List<ProductEntity>> = productDao.observeAvailableForSale()

    fun observeRecentInvoices(): Flow<List<InvoiceEntity>> = invoiceDao.observeRecentInvoices()

    fun observeRecentLedger(): Flow<List<InventoryLedgerEntity>> = ledgerDao.observeRecentLedger()

    fun observeLedgerForProduct(productId: Long): Flow<List<InventoryLedgerEntity>> =
        ledgerDao.observeLedgerForProduct(productId)

    suspend fun getInvoiceItems(invoiceId: Long): List<InvoiceItemEntity> =
        invoiceDao.getItemsForInvoice(invoiceId)

    suspend fun getCurrentRole(): UserRole {
        val settings = settingsDao.getSettingsOnce()
        return UserRole.fromCode(settings?.activeRole)
    }

    suspend fun addProduct(
        name: String,
        packPrice: Double,
        unitPrice: Double,
        purchaseCost: Double,
        initialStockUnits: Int,
        packSize: Int,
        lowStockThreshold: Int,
        actorRole: UserRole
    ): Long = transactionMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_PRODUCTS)
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "يرجى إدخال اسم المنتج" }
        require(cleanName.length <= 80) { "اسم المنتج طويل جداً" }
        require(packPrice >= 0.0 && unitPrice >= 0.0 && purchaseCost >= 0.0) {
            "الأسعار والتكلفة يجب أن تكون قيماً غير سالبة"
        }
        require(packSize >= 1) { "حجم العلبة يجب أن يكون 1 على الأقل" }
        require(initialStockUnits >= 0) { "المخزون الأولي لا يمكن أن يكون سالباً" }
        require(lowStockThreshold >= 0) { "حد التنبيه لا يمكن أن يكون سالباً" }

        db.withTransaction {
            val existing = productDao.findByExactName(cleanName)
            require(existing == null) { "يوجد منتج بنفس الاسم بالفعل: $cleanName" }

            val now = System.currentTimeMillis()
            val id = productDao.insert(
                ProductEntity(
                    name = cleanName,
                    packPrice = roundMoney(packPrice),
                    unitPrice = roundMoney(unitPrice),
                    purchaseCost = roundMoney(purchaseCost),
                    stockUnits = initialStockUnits,
                    packSize = packSize,
                    lowStockThreshold = lowStockThreshold,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (initialStockUnits > 0) {
                ledgerDao.insert(
                    InventoryLedgerEntity(
                        productId = id,
                        productNameSnapshot = cleanName,
                        changeType = "OPENING",
                        deltaUnits = initialStockUnits,
                        stockBefore = 0,
                        stockAfter = initialStockUnits,
                        notes = "إضافة منتج جديد برصيد افتتاحي",
                        createdBy = actorRole.code,
                        createdAt = now
                    )
                )
            }
            id
        }
    }

    suspend fun updateProduct(
        productId: Long,
        name: String,
        packPrice: Double,
        unitPrice: Double,
        purchaseCost: Double,
        packSize: Int,
        lowStockThreshold: Int,
        isActive: Boolean,
        actorRole: UserRole
    ) = transactionMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_PRODUCTS)
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "يرجى إدخال اسم المنتج" }
        require(packPrice >= 0.0 && unitPrice >= 0.0 && purchaseCost >= 0.0) {
            "الأسعار والتكلفة يجب أن تكون غير سالبة"
        }
        require(packSize >= 1) { "حجم العلبة يجب أن يكون 1 على الأقل" }
        require(lowStockThreshold >= 0) { "حد التنبيه يجب أن يكون غير سالب" }

        db.withTransaction {
            val current = productDao.getById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            val conflict = productDao.findByExactName(cleanName)
            require(conflict == null || conflict.id == productId) {
                "يوجد منتج آخر يحمل الاسم نفسه"
            }
            productDao.update(
                current.copy(
                    name = cleanName,
                    packPrice = roundMoney(packPrice),
                    unitPrice = roundMoney(unitPrice),
                    purchaseCost = roundMoney(purchaseCost),
                    packSize = packSize,
                    lowStockThreshold = lowStockThreshold,
                    isActive = isActive,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun setProductActive(productId: Long, active: Boolean, actorRole: UserRole) =
        transactionMutex.withLock {
            RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_PRODUCTS)
            db.withTransaction {
                val current = productDao.getById(productId)
                    ?: throw IllegalArgumentException("المنتج غير موجود")
                productDao.update(
                    current.copy(isActive = active, updatedAt = System.currentTimeMillis())
                )
            }
        }

    suspend fun deleteProductSafely(productId: Long, actorRole: UserRole) =
        transactionMutex.withLock {
            RolePermissions.requirePermission(actorRole, ShopPermission.MANAGE_PRODUCTS)
            db.withTransaction {
                val current = productDao.getById(productId)
                    ?: throw IllegalArgumentException("المنتج غير موجود")
                val invRefs = productDao.countInvoiceItemReferences(productId)
                val purchRefs = productDao.countPurchaseReferences(productId)
                val ledgerRefs = productDao.countLedgerReferences(productId)
                if (invRefs > 0 || purchRefs > 0 || ledgerRefs > 0) {
                    throw IllegalStateException(
                        "لا يمكن حذف المنتج '${current.name}' لارتباطه بسجلات مالية أو حركات مخزون. يمكنك تعطيله بدلاً من حذفه حفاظاً على سلامة البيانات."
                    )
                }
                productDao.deleteById(productId)
            }
        }

    suspend fun adjustStock(
        productId: Long,
        deltaUnits: Int,
        changeType: String,
        notes: String,
        actorRole: UserRole
    ): ProductEntity = transactionMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.ADJUST_INVENTORY)
        require(deltaUnits != 0) { "كمية التعديل يجب أن تختلف عن الصفر" }

        db.withTransaction {
            val product = productDao.getById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            val stockAfter = product.stockUnits + deltaUnits
            if (stockAfter < 0) {
                throw IllegalStateException(
                    "لا يمكن أن يصبح المخزون سالباً. المتوفر حالياً من '${product.name}': ${product.stockUnits} وحدة"
                )
            }
            val now = System.currentTimeMillis()
            val updatedRows = productDao.adjustStockGuarded(productId, deltaUnits, now)
            if (updatedRows != 1) {
                throw IllegalStateException("تعذر تحديث المخزون — تحقق من الكمية المتوفرة")
            }
            ledgerDao.insert(
                InventoryLedgerEntity(
                    productId = product.id,
                    productNameSnapshot = product.name,
                    changeType = changeType,
                    deltaUnits = deltaUnits,
                    stockBefore = product.stockUnits,
                    stockAfter = stockAfter,
                    notes = notes.trim(),
                    createdBy = actorRole.code,
                    createdAt = now
                )
            )
            product.copy(stockUnits = stockAfter, updatedAt = now)
        }
    }

    suspend fun recordOpeningInventory(
        targetUnitsByProductId: Map<Long, Int>,
        notes: String,
        actorRole: UserRole
    ) = transactionMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.ADJUST_INVENTORY)
        require(targetUnitsByProductId.isNotEmpty()) { "لم يتم تحديد أي منتج للجرد" }

        db.withTransaction {
            val now = System.currentTimeMillis()
            for ((productId, newUnits) in targetUnitsByProductId) {
                require(newUnits >= 0) { "كمية الجرد لا يمكن أن تكون سالبة" }
                val product = productDao.getById(productId)
                    ?: throw IllegalArgumentException("المنتج رقم $productId غير موجود")
                val delta = newUnits - product.stockUnits
                if (delta != 0) {
                    productDao.update(product.copy(stockUnits = newUnits, updatedAt = now))
                    ledgerDao.insert(
                        InventoryLedgerEntity(
                            productId = product.id,
                            productNameSnapshot = product.name,
                            changeType = "OPENING",
                            deltaUnits = delta,
                            stockBefore = product.stockUnits,
                            stockAfter = newUnits,
                            notes = notes.ifBlank { "جرد الصباح / الرصيد الافتتاحي" },
                            createdBy = actorRole.code,
                            createdAt = now
                        )
                    )
                }
            }
        }
    }

    /**
     * Atomic multi-product cart checkout with:
     * - Full pre-validation of every product, status, price, quantity, and combined stock requirement
     * - Idempotency via unique clientRequestId (prevents duplicate invoices on repeated taps)
     * - Guarded SQLite stock deduction (stock never becomes negative)
     * - Complete rollback on ANY validation or database error
     * - Automatic credit debt registration if paymentType == CREDIT
     */
    suspend fun checkoutCart(
        request: CheckoutRequest,
        actorRole: UserRole
    ): CheckoutResult = transactionMutex.withLock {
        RolePermissions.requirePermission(actorRole, ShopPermission.PERFORM_SALE)
        val cleanRequestId = request.clientRequestId.trim()
        require(cleanRequestId.isNotEmpty()) { "معرف الطلب مطلوب لمنع تكرار الفاتورة" }
        require(request.items.isNotEmpty()) { "سلة المشتريات فارغة" }

        db.withTransaction {
            // 1. Idempotency check
            val existingInvoice = invoiceDao.findByClientRequestId(cleanRequestId)
            if (existingInvoice != null) {
                val existingItems = invoiceDao.getItemsForInvoice(existingInvoice.id)
                return@withTransaction CheckoutResult(
                    invoice = existingInvoice,
                    items = existingItems,
                    wasDuplicateRequest = true
                )
            }

            // 2. Validate customer if CREDIT sale
            val customer = if (request.paymentType == PaymentType.CREDIT) {
                val cid = request.customerId
                    ?: throw IllegalArgumentException("يرجى اختيار الزبون لإتمام البيع بالكريدي")
                customerDao.getById(cid)
                    ?: throw IllegalArgumentException("الزبون المحدد غير موجود")
            } else {
                request.customerId?.let { customerDao.getById(it) }
            }

            // 3. Load and validate every line item & aggregate required units per product
            val loadedProducts = mutableMapOf<Long, ProductEntity>()
            val requiredUnitsByProduct = mutableMapOf<Long, Int>()

            for (line in request.items) {
                require(line.productId > 0) { "معرف المنتج غير صالح" }
                require(line.quantity > 0) { "الكمية يجب أن تكون أكبر من صفر" }

                val product = loadedProducts.getOrPut(line.productId) {
                    productDao.getById(line.productId)
                        ?: throw IllegalArgumentException("المنتج رقم ${line.productId} غير موجود")
                }

                if (!product.isActive) {
                    throw IllegalStateException("المنتج '${product.name}' معطّل حالياً ولا يمكن بيعه")
                }
                if (product.packSize < 1) {
                    throw IllegalStateException("إعداد حجم العلبة للمنتج '${product.name}' غير صالح")
                }

                val salePrice = if (line.saleUnitType == SaleUnitType.PACK) product.packPrice else product.unitPrice
                if (salePrice < 0.0 || product.purchaseCost < 0.0) {
                    throw IllegalStateException("سعر أو تكلفة المنتج '${product.name}' غير صالحة")
                }

                val unitsNeeded = if (line.saleUnitType == SaleUnitType.PACK) {
                    Math.multiplyExact(line.quantity, product.packSize)
                } else {
                    line.quantity
                }
                val prevReq = requiredUnitsByProduct[product.id] ?: 0
                requiredUnitsByProduct[product.id] = Math.addExact(prevReq, unitsNeeded)
            }

            // 4. Validate combined stock for every product BEFORE any mutation
            for ((productId, totalUnitsNeeded) in requiredUnitsByProduct) {
                val product = loadedProducts.getValue(productId)
                if (product.stockUnits <= 0) {
                    throw IllegalStateException("المنتج '${product.name}' نافد من المخزون")
                }
                if (totalUnitsNeeded > product.stockUnits) {
                    throw IllegalStateException(
                        "المخزون غير كافٍ للمنتج '${product.name}': المطلوب $totalUnitsNeeded وحدة والمتوفر ${product.stockUnits} وحدة فقط"
                    )
                }
            }

            // 5. Compute exact line financials
            val now = System.currentTimeMillis()
            var totalRevenue = 0.0
            var totalCost = 0.0
            var packsSold = 0
            var unitsSold = 0

            data class PreparedLine(
                val product: ProductEntity,
                val saleUnitType: SaleUnitType,
                val quantity: Int,
                val unitsDeducted: Int,
                val unitSalePrice: Double,
                val lineTotal: Double,
                val lineCost: Double,
                val lineProfit: Double
            )

            val preparedLines = request.items.map { line ->
                val product = loadedProducts.getValue(line.productId)
                val unitsDeducted = if (line.saleUnitType == SaleUnitType.PACK) {
                    line.quantity * product.packSize
                } else {
                    line.quantity
                }
                val unitSalePrice = if (line.saleUnitType == SaleUnitType.PACK) {
                    product.packPrice
                } else {
                    product.unitPrice
                }
                val lineTotal = roundMoney(unitSalePrice * line.quantity)
                val costPerSingleUnit = product.purchaseCost / product.packSize.toDouble()
                val lineCost = roundMoney(
                    if (line.saleUnitType == SaleUnitType.PACK) {
                        product.purchaseCost * line.quantity
                    } else {
                        costPerSingleUnit * line.quantity
                    }
                )
                val lineProfit = roundMoney(lineTotal - lineCost)

                totalRevenue = roundMoney(totalRevenue + lineTotal)
                totalCost = roundMoney(totalCost + lineCost)
                if (line.saleUnitType == SaleUnitType.PACK) {
                    packsSold += line.quantity
                } else {
                    unitsSold += line.quantity
                }

                PreparedLine(
                    product = product,
                    saleUnitType = line.saleUnitType,
                    quantity = line.quantity,
                    unitsDeducted = unitsDeducted,
                    unitSalePrice = unitSalePrice,
                    lineTotal = lineTotal,
                    lineCost = lineCost,
                    lineProfit = lineProfit
                )
            }

            val totalProfit = roundMoney(totalRevenue - totalCost)

            // 6. Deduct stock atomically via SQLite guard (rollback entire tx if any fails)
            for ((productId, unitsToDeduct) in requiredUnitsByProduct) {
                val affected = productDao.deductStockIfSufficient(productId, unitsToDeduct, now)
                if (affected != 1) {
                    val name = loadedProducts.getValue(productId).name
                    throw IllegalStateException("فشل خصم المخزون للمنتج '$name' — تم إلغاء الفاتورة بالكامل")
                }
            }

            // 7. Insert invoice header
            val invoiceToInsert = InvoiceEntity(
                clientRequestId = cleanRequestId,
                paymentType = request.paymentType.code,
                customerId = customer?.id,
                customerNameSnapshot = customer?.name,
                totalRevenue = totalRevenue,
                totalCost = totalCost,
                totalProfit = totalProfit,
                packsSold = packsSold,
                unitsSold = unitsSold,
                notes = request.notes.trim(),
                createdBy = actorRole.code,
                createdAt = now
            )
            val invoiceId = invoiceDao.insertInvoice(invoiceToInsert)
            val savedInvoice = invoiceToInsert.copy(id = invoiceId)

            // 8. Insert invoice line items
            val itemEntities = preparedLines.map { pl ->
                InvoiceItemEntity(
                    invoiceId = invoiceId,
                    productId = pl.product.id,
                    productNameSnapshot = pl.product.name,
                    saleUnitType = pl.saleUnitType.code,
                    quantity = pl.quantity,
                    unitsDeducted = pl.unitsDeducted,
                    unitSalePrice = pl.unitSalePrice,
                    lineTotal = pl.lineTotal,
                    lineCost = pl.lineCost,
                    lineProfit = pl.lineProfit
                )
            }
            invoiceDao.insertItems(itemEntities)

            // 9. Record inventory ledger entries per deducted product
            for ((productId, unitsDeducted) in requiredUnitsByProduct) {
                val product = loadedProducts.getValue(productId)
                ledgerDao.insert(
                    InventoryLedgerEntity(
                        productId = product.id,
                        productNameSnapshot = product.name,
                        changeType = "SALE",
                        deltaUnits = -unitsDeducted,
                        stockBefore = product.stockUnits,
                        stockAfter = product.stockUnits - unitsDeducted,
                        referenceId = invoiceId,
                        notes = "فاتورة بيع #$invoiceId (${request.paymentType.labelAr})",
                        createdBy = actorRole.code,
                        createdAt = now
                    )
                )
            }

            // 10. If CREDIT sale, update customer debt balance & ledger atomically
            if (request.paymentType == PaymentType.CREDIT && customer != null) {
                val newBalance = roundMoney(customer.balance + totalRevenue)
                customerDao.updateBalance(customer.id, newBalance, now)
                creditDao.insert(
                    CreditTransactionEntity(
                        customerId = customer.id,
                        type = "DEBT",
                        amount = totalRevenue,
                        deltaBalance = totalRevenue,
                        balanceAfter = newBalance,
                        invoiceId = invoiceId,
                        notes = request.notes.ifBlank { "فاتورة مبيعات آجل #$invoiceId" },
                        createdBy = actorRole.code,
                        createdAt = now
                    )
                )
            }

            CheckoutResult(
                invoice = savedInvoice,
                items = itemEntities,
                wasDuplicateRequest = false
            )
        }
    }

    /**
     * Executes shop quick-entry commands such as:
     * "+5s rym", "+1* rym", "-علبة rym", "-5 سجارة rym".
     */
    suspend fun executeQuickEntry(rawInput: String, actorRole: UserRole): String {
        val cmd = QuickEntryParser.parse(rawInput)
            ?: throw IllegalArgumentException(
                "صيغة غير معروفة. أمثلة: +5s rym | +1* rym | -علبة rym | -5 سجارة rym"
            )
        return when (cmd) {
            is QuickEntryCommand.AddStock -> {
                val product = productDao.findBestActiveMatch(cmd.productQuery)
                    ?: throw IllegalArgumentException("لم يتم العثور على منتج مطابق لـ '${cmd.productQuery}'")
                val deltaUnits = if (cmd.isPack) cmd.count * product.packSize else cmd.deltaUnits
                val updated = adjustStock(
                    productId = product.id,
                    deltaUnits = deltaUnits,
                    changeType = "QUICK_ENTRY",
                    notes = "إدخال سريع: $rawInput",
                    actorRole = actorRole
                )
                "تمت إضافة $deltaUnits وحدة إلى ${updated.name} (المتوفر: ${updated.stockUnits} وحدة)"
            }
            is QuickEntryCommand.QuickSell -> {
                val product = productDao.findBestActiveMatch(cmd.productQuery)
                    ?: throw IllegalArgumentException("لم يتم العثور على منتج مطابق لـ '${cmd.productQuery}'")
                val result = checkoutCart(
                    request = CheckoutRequest(
                        clientRequestId = "quick_${UUID.randomUUID()}",
                        items = listOf(
                            com.example.data.model.CheckoutLineRequest(
                                productId = product.id,
                                saleUnitType = cmd.saleUnitType,
                                quantity = cmd.quantity
                            )
                        ),
                        paymentType = PaymentType.CASH,
                        notes = "بيع سريع: $rawInput"
                    ),
                    actorRole = actorRole
                )
                "تم بيع ${cmd.quantity} ${cmd.saleUnitType.labelAr} من ${product.name} — الإجمالي ${result.invoice.totalRevenue}"
            }
        }
    }
}
