package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.example.data.model.CartItem
import com.example.data.model.CheckoutLineRequest
import com.example.data.model.CheckoutRequest
import com.example.data.model.CheckoutResult
import com.example.data.model.FinancialReport
import com.example.data.model.IntegrityCheckResult
import com.example.data.model.PaymentType
import com.example.data.model.PeriodFilter
import com.example.data.model.SaleUnitType
import com.example.data.model.periodBounds
import com.example.data.model.roundMoney
import com.example.data.repository.BackupManager
import com.example.data.repository.FinanceAndCreditRepository
import com.example.data.repository.ShopRepository
import com.example.data.security.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppSection(val route: String, val labelAr: String) {
    SALES("sales", "المبيعات"),
    PRODUCTS("products", "المنتجات"),
    INVENTORY("inventory", "المخزون"),
    CUSTOMERS("customers", "الزبائن"),
    CREDIT("credit", "الكريدي"),
    EXPENSES("expenses", "المصاريف"),
    PURCHASES("purchases", "المشتريات"),
    REPORTS("reports", "التقارير"),
    AI_ASSISTANT("ai_assistant", "المساعد الذكي"),
    SETTINGS("settings", "الإعدادات")
}

data class InvoiceDetailData(
    val invoice: InvoiceEntity,
    val items: List<InvoiceItemEntity>
)

data class FeedbackMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false
)

class ShopViewModel(
    val shopRepo: ShopRepository,
    val financeRepo: FinanceAndCreditRepository,
    val backupManager: BackupManager
) : ViewModel() {

    val allProducts: StateFlow<List<ProductEntity>> = shopRepo.observeAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableForSale: StateFlow<List<ProductEntity>> = shopRepo.observeAvailableForSale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentInvoices: StateFlow<List<InvoiceEntity>> = shopRepo.observeRecentInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLedger: StateFlow<List<InventoryLedgerEntity>> = shopRepo.observeRecentLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = financeRepo.observeAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtors: StateFlow<List<CustomerEntity>> = financeRepo.observeDebtors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val creditTransactions: StateFlow<List<CreditTransactionEntity>> =
        financeRepo.observeAllCreditTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = financeRepo.observeAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = financeRepo.observeAllPurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashMovements: StateFlow<List<CashMovementEntity>> = financeRepo.observeCashMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ShopSettingsEntity> = financeRepo.observeSettings()
        .combine(MutableStateFlow(Unit)) { s, _ -> s ?: ShopSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettingsEntity())

    // Navigation state with backstack support
    private val _currentSection = MutableStateFlow(AppSection.SALES)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    // PIN Lock & Role state
    private val _isSessionLocked = MutableStateFlow(false)
    val isSessionLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()
    private var initialPinLockEvaluated = false

    // Shopping cart state
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _cartPaymentType = MutableStateFlow(PaymentType.CASH)
    val cartPaymentType: StateFlow<PaymentType> = _cartPaymentType.asStateFlow()

    private val _cartCustomerId = MutableStateFlow<Long?>(null)
    val cartCustomerId: StateFlow<Long?> = _cartCustomerId.asStateFlow()

    private val _cartNotes = MutableStateFlow("")
    val cartNotes: StateFlow<String> = _cartNotes.asStateFlow()

    private val _isCheckingOut = MutableStateFlow(false)
    val isCheckingOut: StateFlow<Boolean> = _isCheckingOut.asStateFlow()

    private var activeCartRequestId: String = UUID.randomUUID().toString()

    // Completed checkout receipt & invoice inspector
    private val _lastCheckoutResult = MutableStateFlow<CheckoutResult?>(null)
    val lastCheckoutResult: StateFlow<CheckoutResult?> = _lastCheckoutResult.asStateFlow()

    private val _inspectedInvoice = MutableStateFlow<InvoiceDetailData?>(null)
    val inspectedInvoice: StateFlow<InvoiceDetailData?> = _inspectedInvoice.asStateFlow()

    // Reports & period state
    private val _periodFilter = MutableStateFlow(PeriodFilter.DAILY)
    val periodFilter: StateFlow<PeriodFilter> = _periodFilter.asStateFlow()

    private val _customStartMillis = MutableStateFlow(periodBounds(PeriodFilter.DAILY).first)
    val customStartMillis: StateFlow<Long> = _customStartMillis.asStateFlow()

    private val _customEndMillis = MutableStateFlow(periodBounds(PeriodFilter.DAILY).second)
    val customEndMillis: StateFlow<Long> = _customEndMillis.asStateFlow()

    private val _financialReport = MutableStateFlow<FinancialReport?>(null)
    val financialReport: StateFlow<FinancialReport?> = _financialReport.asStateFlow()

    private val _isReportLoading = MutableStateFlow(false)
    val isReportLoading: StateFlow<Boolean> = _isReportLoading.asStateFlow()

    private val _integrityResult = MutableStateFlow<IntegrityCheckResult?>(null)
    val integrityResult: StateFlow<IntegrityCheckResult?> = _integrityResult.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackMessage?>(null)
    val feedback: StateFlow<FeedbackMessage?> = _feedback.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { s ->
                if (!initialPinLockEvaluated && s.ownerPinHash.isNotBlank()) {
                    initialPinLockEvaluated = true
                    if (s.pinEnabled) {
                        _isSessionLocked.value = true
                    }
                }
            }
        }
        // Keep cart synchronized with live stock & refresh report when financial data updates
        viewModelScope.launch {
            allProducts.collect { products ->
                val map = products.associateBy { it.id }
                _cartItems.value = _cartItems.value.mapNotNull { item ->
                    val fresh = map[item.productId]
                    if (fresh == null || !fresh.isActive || fresh.stockUnits <= 0) {
                        null
                    } else {
                        item.copy(
                            productName = fresh.name,
                            packSize = fresh.packSize,
                            unitPrice = if (item.saleUnitType == SaleUnitType.PACK) fresh.packPrice else fresh.unitPrice,
                            availableStockUnits = fresh.stockUnits
                        )
                    }
                }
                refreshReport()
            }
        }
        viewModelScope.launch {
            combine(
                recentInvoices,
                expenses,
                purchases,
                creditTransactions,
                cashMovements
            ) { _, _, _, _, _ -> Unit }.collect {
                refreshReport()
            }
        }
    }

    val activeRole: UserRole
        get() = UserRole.fromCode(settings.value.activeRole)

    fun selectSection(section: AppSection) {
        _currentSection.value = section
        if (section == AppSection.REPORTS) {
            refreshReport()
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private fun postSuccess(message: String) {
        _feedback.value = FeedbackMessage(text = message, isError = false)
    }

    private fun postError(throwable: Throwable) {
        _feedback.value = FeedbackMessage(
            text = throwable.message ?: "حدث خطأ غير متوقع أثناء تنفيذ العملية",
            isError = true
        )
    }

    // ==================== CART OPERATIONS ====================

    fun unitsInCartForProduct(productId: Long): Int =
        _cartItems.value.filter { it.productId == productId }.sumOf { it.unitsRequired }

    fun canAddToCart(product: ProductEntity, saleUnitType: SaleUnitType): Boolean {
        if (!product.isActive || product.stockUnits <= 0) return false
        val needed = if (saleUnitType == SaleUnitType.PACK) product.packSize else 1
        return unitsInCartForProduct(product.id) + needed <= product.stockUnits
    }

    fun addToCart(product: ProductEntity, saleUnitType: SaleUnitType) {
        val unitsToAdd = if (saleUnitType == SaleUnitType.PACK) product.packSize else 1
        val currentUsed = unitsInCartForProduct(product.id)
        if (currentUsed + unitsToAdd > product.stockUnits) {
            _feedback.value = FeedbackMessage(
                text = "لا يوجد مخزون كافٍ من ${product.name} (المتوفر: ${product.stockUnits} وحدة)",
                isError = true
            )
            return
        }

        val price = if (saleUnitType == SaleUnitType.PACK) product.packPrice else product.unitPrice
        val currentList = _cartItems.value.toMutableList()
        val existingIdx = currentList.indexOfFirst {
            it.productId == product.id && it.saleUnitType == saleUnitType
        }
        if (existingIdx >= 0) {
            val existing = currentList[existingIdx]
            currentList[existingIdx] = existing.copy(
                quantity = existing.quantity + 1,
                unitPrice = price,
                availableStockUnits = product.stockUnits
            )
        } else {
            currentList.add(
                CartItem(
                    productId = product.id,
                    productName = product.name,
                    saleUnitType = saleUnitType,
                    quantity = 1,
                    packSize = product.packSize,
                    unitPrice = price,
                    availableStockUnits = product.stockUnits
                )
            )
        }
        _cartItems.value = currentList
        activeCartRequestId = UUID.randomUUID().toString()
    }

    fun incrementCartItem(item: CartItem) {
        val product = allProducts.value.firstOrNull { it.id == item.productId } ?: return
        addToCart(product, item.saleUnitType)
    }

    fun decrementCartItem(item: CartItem) {
        val currentList = _cartItems.value.toMutableList()
        val idx = currentList.indexOfFirst { it.key == item.key }
        if (idx >= 0) {
            val existing = currentList[idx]
            if (existing.quantity <= 1) {
                currentList.removeAt(idx)
            } else {
                currentList[idx] = existing.copy(quantity = existing.quantity - 1)
            }
            _cartItems.value = currentList
            activeCartRequestId = UUID.randomUUID().toString()
        }
    }

    fun removeCartItem(item: CartItem) {
        _cartItems.value = _cartItems.value.filterNot { it.key == item.key }
        activeCartRequestId = UUID.randomUUID().toString()
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _cartPaymentType.value = PaymentType.CASH
        _cartCustomerId.value = null
        _cartNotes.value = ""
        activeCartRequestId = UUID.randomUUID().toString()
    }

    fun setCartPaymentType(type: PaymentType) {
        _cartPaymentType.value = type
        if (type == PaymentType.CASH) {
            _cartCustomerId.value = null
        }
    }

    fun setCartCustomer(customerId: Long?) {
        _cartCustomerId.value = customerId
    }

    fun setCartNotes(notes: String) {
        _cartNotes.value = notes
    }

    fun cartTotalAmount(): Double = roundMoney(_cartItems.value.sumOf { it.lineTotal })

    fun checkoutCart() {
        if (_isCheckingOut.value) return
        val items = _cartItems.value
        if (items.isEmpty()) {
            _feedback.value = FeedbackMessage(text = "سلة المشتريات فارغة", isError = true)
            return
        }
        val requestId = activeCartRequestId
        val paymentType = _cartPaymentType.value
        val customerId = _cartCustomerId.value
        val notes = _cartNotes.value

        viewModelScope.launch {
            _isCheckingOut.value = true
            try {
                val result = shopRepo.checkoutCart(
                    request = CheckoutRequest(
                        clientRequestId = requestId,
                        items = items.map {
                            CheckoutLineRequest(
                                productId = it.productId,
                                saleUnitType = it.saleUnitType,
                                quantity = it.quantity
                            )
                        },
                        paymentType = paymentType,
                        customerId = customerId,
                        notes = notes
                    ),
                    actorRole = activeRole
                )
                clearCart()
                _lastCheckoutResult.value = result
                postSuccess("تم تأكيد الفاتورة #${result.invoice.id} بنجاح")
            } catch (e: Exception) {
                postError(e)
            } finally {
                _isCheckingOut.value = false
            }
        }
    }

    fun dismissCheckoutReceipt() {
        _lastCheckoutResult.value = null
    }

    fun inspectInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            try {
                val items = shopRepo.getInvoiceItems(invoice.id)
                _inspectedInvoice.value = InvoiceDetailData(invoice, items)
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun dismissInspectedInvoice() {
        _inspectedInvoice.value = null
    }

    fun runQuickEntry(commandText: String) {
        viewModelScope.launch {
            try {
                val msg = shopRepo.executeQuickEntry(commandText, activeRole)
                postSuccess(msg)
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    // ==================== PRODUCTS & INVENTORY ====================

    fun addProduct(
        name: String,
        packPrice: Double,
        unitPrice: Double,
        purchaseCost: Double,
        initialStockUnits: Int,
        packSize: Int,
        lowStockThreshold: Int
    ) {
        viewModelScope.launch {
            try {
                shopRepo.addProduct(
                    name = name,
                    packPrice = packPrice,
                    unitPrice = unitPrice,
                    purchaseCost = purchaseCost,
                    initialStockUnits = initialStockUnits,
                    packSize = packSize,
                    lowStockThreshold = lowStockThreshold,
                    actorRole = activeRole
                )
                postSuccess("تمت إضافة المنتج '$name' بنجاح")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun updateProduct(
        productId: Long,
        name: String,
        packPrice: Double,
        unitPrice: Double,
        purchaseCost: Double,
        packSize: Int,
        lowStockThreshold: Int,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            try {
                shopRepo.updateProduct(
                    productId = productId,
                    name = name,
                    packPrice = packPrice,
                    unitPrice = unitPrice,
                    purchaseCost = purchaseCost,
                    packSize = packSize,
                    lowStockThreshold = lowStockThreshold,
                    isActive = isActive,
                    actorRole = activeRole
                )
                postSuccess("تم تحديث بيانات '$name'")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun toggleProductActive(product: ProductEntity) {
        viewModelScope.launch {
            try {
                shopRepo.setProductActive(product.id, !product.isActive, activeRole)
                val statusLabel = if (!product.isActive) "تفعيل" else "تعطيل"
                postSuccess("تم $statusLabel المنتج '${product.name}'")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            try {
                shopRepo.deleteProductSafely(product.id, activeRole)
                postSuccess("تم حذف المنتج '${product.name}'")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun adjustStock(productId: Long, deltaUnits: Int, changeType: String, notes: String) {
        viewModelScope.launch {
            try {
                val updated = shopRepo.adjustStock(productId, deltaUnits, changeType, notes, activeRole)
                postSuccess("تم تحديث مخزون '${updated.name}' إلى ${updated.stockUnits} وحدة")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun recordOpeningInventory(counts: Map<Long, Int>, notes: String) {
        viewModelScope.launch {
            try {
                shopRepo.recordOpeningInventory(counts, notes, activeRole)
                postSuccess("تم حفظ جرد الصباح وتحديث سجل المخزون")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    // ==================== CUSTOMERS & CREDIT ====================

    fun addCustomer(name: String, phone: String, notes: String) {
        viewModelScope.launch {
            try {
                financeRepo.addCustomer(name, phone, notes, activeRole)
                postSuccess("تمت إضافة الزبون '$name'")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun updateCustomer(customerId: Long, name: String, phone: String, notes: String) {
        viewModelScope.launch {
            try {
                financeRepo.updateCustomer(customerId, name, phone, notes, activeRole)
                postSuccess("تم تحديث بيانات الزبون")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun registerDebt(customerId: Long, amount: Double, notes: String) {
        viewModelScope.launch {
            try {
                financeRepo.registerDebt(customerId, amount, notes, activeRole)
                postSuccess("تم تسجيل الدين بنجاح")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun recordPayment(customerId: Long, amount: Double, notes: String) {
        viewModelScope.launch {
            try {
                financeRepo.recordPayment(customerId, amount, notes, activeRole)
                postSuccess("تم تسجيل التسديد وتحديث الرصيد")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun recordCreditAdjustment(customerId: Long, deltaAmount: Double, notes: String) {
        viewModelScope.launch {
            try {
                financeRepo.recordCreditAdjustment(customerId, deltaAmount, notes, activeRole)
                postSuccess("تم تسجيل تسوية الرصيد بنجاح")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    // ==================== EXPENSES, PURCHASES & CASH ====================

    fun addExpense(category: String, description: String, amount: Double) {
        viewModelScope.launch {
            try {
                financeRepo.addExpense(category, description, amount, actorRole = activeRole)
                postSuccess("تم تسجيل المصروف بنجاح")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun updateExpense(expenseId: Long, category: String, description: String, amount: Double) {
        viewModelScope.launch {
            try {
                financeRepo.updateExpense(expenseId, category, description, amount, activeRole)
                postSuccess("تم تعديل سجل المصروف")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun recordPurchase(
        productId: Long,
        supplierName: String,
        packsQuantity: Int,
        unitCostPerPack: Double,
        paidFromCash: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val p = financeRepo.recordPurchase(
                    productId = productId,
                    supplierName = supplierName,
                    packsQuantity = packsQuantity,
                    unitCostPerPack = unitCostPerPack,
                    paidFromCash = paidFromCash,
                    notes = notes,
                    actorRole = activeRole
                )
                postSuccess("تم تسجيل شراء ${p.packsQuantity} علبة من ${p.productNameSnapshot} وإضافتها للمخزون")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun recordCashMovement(type: String, amount: Double, notes: String) {
        viewModelScope.launch {
            try {
                financeRepo.recordCashMovement(type, amount, notes, activeRole)
                val label = when (type) {
                    "OPENING_BALANCE" -> "الرصيد الافتتاحي للصندوق"
                    "DEPOSIT" -> "إيداع نقدي"
                    else -> "سحب نقدي"
                }
                postSuccess("تم تسجيل $label بنجاح")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    // ==================== REPORTS ====================

    fun setPeriodFilter(filter: PeriodFilter) {
        _periodFilter.value = filter
        refreshReport()
    }

    fun setCustomDateRange(startMillis: Long, endMillis: Long) {
        _customStartMillis.value = startMillis
        _customEndMillis.value = endMillis
        _periodFilter.value = PeriodFilter.CUSTOM
        refreshReport()
    }

    fun refreshReport() {
        viewModelScope.launch {
            _isReportLoading.value = true
            try {
                val (from, to) = periodBounds(
                    _periodFilter.value,
                    _customStartMillis.value,
                    _customEndMillis.value
                )
                _financialReport.value = financeRepo.buildFinancialReport(from, to)
            } catch (e: Exception) {
                postError(e)
            } finally {
                _isReportLoading.value = false
            }
        }
    }

    // ==================== SETTINGS, SECURITY & BACKUP ====================

    fun updateShopProfile(shopName: String, currency: String, defaultPackSize: Int, lowStock: Int) {
        viewModelScope.launch {
            try {
                financeRepo.updateShopProfile(shopName, currency, defaultPackSize, lowStock, activeRole)
                postSuccess("تم حفظ إعدادات المحل")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun configurePins(enablePin: Boolean, ownerPin: String?, cashierPin: String?) {
        viewModelScope.launch {
            try {
                financeRepo.configurePins(enablePin, ownerPin, cashierPin, activeRole)
                postSuccess(if (enablePin) "تم تفعيل حماية رمز PIN" else "تم إيقاف قفل رمز PIN")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun lockSession() {
        if (settings.value.pinEnabled) {
            _isSessionLocked.value = true
        } else {
            _feedback.value = FeedbackMessage(
                text = "قم بتفعيل رمز PIN من الإعدادات أولاً لقفل الجلسة",
                isError = false
            )
        }
    }

    fun unlockWithPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val role = financeRepo.authenticateRole(pin)
                if (role != null) {
                    _isSessionLocked.value = false
                    postSuccess("تم الدخول بصلاحية ${role.labelAr}")
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                postError(e)
                onResult(false)
            }
        }
    }

    fun switchRoleDirectly(role: UserRole) {
        viewModelScope.launch {
            try {
                financeRepo.switchRoleWithoutPinIfDisabled(role)
                postSuccess("تم التبديل إلى صلاحية ${role.labelAr}")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    suspend fun generateBackupPayload(): Result<String> {
        return try {
            Result.success(backupManager.exportBackupJson(activeRole))
        } catch (e: Exception) {
            postError(e)
            Result.failure(e)
        }
    }

    fun restoreFromBackupPayload(rawJson: String) {
        viewModelScope.launch {
            try {
                backupManager.restoreFromBackupJson(rawJson, activeRole)
                clearCart()
                refreshReport()
                postSuccess("تمت استعادة النسخة الاحتياطية بنجاح")
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    fun runDatabaseIntegrityCheck() {
        viewModelScope.launch {
            try {
                val res = backupManager.runIntegrityCheck()
                _integrityResult.value = res
                _feedback.value = FeedbackMessage(text = res.summaryMessage, isError = !res.isHealthy)
            } catch (e: Exception) {
                postError(e)
            }
        }
    }

    class Factory(private val db: ShopDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShopViewModel(
                shopRepo = ShopRepository(db),
                financeRepo = FinanceAndCreditRepository(db),
                backupManager = BackupManager(db)
            ) as T
        }
    }
}
