package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.ShopDatabase
import com.example.data.model.CheckoutLineRequest
import com.example.data.model.CheckoutRequest
import com.example.data.model.PaymentType
import com.example.data.model.SaleUnitType
import com.example.data.repository.BackupManager
import com.example.data.repository.FinanceAndCreditRepository
import com.example.data.repository.ShopRepository
import com.example.data.security.UserRole
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartShopComprehensiveTest {

    private lateinit var db: ShopDatabase
    private lateinit var shopRepo: ShopRepository
    private lateinit var financeRepo: FinanceAndCreditRepository
    private lateinit var backupManager: BackupManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShopDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            })
            .build()
        shopRepo = ShopRepository(db)
        financeRepo = FinanceAndCreditRepository(db)
        backupManager = BackupManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ==================== 1. SALES & TRANSACTION ROLLBACK TESTS ====================

    @Test
    fun sellSingleUnitAndPack_deductsExactInventoryAndCalculatesProfit() = runTest {
        val rymId = shopRepo.addProduct("Rym", 320.0, 20.0, 260.0, 40, 20, 10, UserRole.OWNER)

        // Sell 1 single unit
        val unitRes = shopRepo.checkoutCart(
            CheckoutRequest(
                clientRequestId = "req-unit-1",
                items = listOf(CheckoutLineRequest(rymId, SaleUnitType.UNIT, 1)),
                paymentType = PaymentType.CASH
            ),
            UserRole.OWNER
        )
        assertEquals(20.0, unitRes.invoice.totalRevenue, 0.01)
        assertEquals(13.0, unitRes.invoice.totalCost, 0.01) // 260 / 20 = 13
        assertEquals(7.0, unitRes.invoice.totalProfit, 0.01)
        assertEquals(0, unitRes.invoice.packsSold)
        assertEquals(1, unitRes.invoice.unitsSold)
        assertEquals(39, db.productDao().getById(rymId)!!.stockUnits)

        // Sell 1 full pack (20 units)
        val packRes = shopRepo.checkoutCart(
            CheckoutRequest(
                clientRequestId = "req-pack-1",
                items = listOf(CheckoutLineRequest(rymId, SaleUnitType.PACK, 1)),
                paymentType = PaymentType.CASH
            ),
            UserRole.OWNER
        )
        assertEquals(320.0, packRes.invoice.totalRevenue, 0.01)
        assertEquals(260.0, packRes.invoice.totalCost, 0.01)
        assertEquals(60.0, packRes.invoice.totalProfit, 0.01)
        assertEquals(1, packRes.invoice.packsSold)
        assertEquals(19, db.productDao().getById(rymId)!!.stockUnits)
    }

    @Test
    fun multiProductCart_andCombinedPackPlusUnitSameProduct_atomicSuccessAndRollbackOnOverflow() = runTest {
        val p1 = shopRepo.addProduct("Marlboro", 450.0, 25.0, 390.0, 25, 20, 10, UserRole.OWNER)
        val p2 = shopRepo.addProduct("Winston", 400.0, 22.0, 350.0, 20, 20, 10, UserRole.OWNER)

        // Exceed combined stock on p1 (1 pack = 20 + 6 units = 26 > 25) while p2 is valid -> MUST ROLLBACK BOTH
        try {
            shopRepo.checkoutCart(
                CheckoutRequest(
                    clientRequestId = "req-overflow",
                    items = listOf(
                        CheckoutLineRequest(p2, SaleUnitType.PACK, 1),
                        CheckoutLineRequest(p1, SaleUnitType.PACK, 1),
                        CheckoutLineRequest(p1, SaleUnitType.UNIT, 6)
                    )
                ),
                UserRole.OWNER
            )
            fail("Expected IllegalStateException for insufficient combined stock")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("المخزون غير كافٍ"))
        }

        // Verify complete rollback: zero invoices, stock untouched on BOTH products
        assertEquals(25, db.productDao().getById(p1)!!.stockUnits)
        assertEquals(20, db.productDao().getById(p2)!!.stockUnits)
        assertEquals(0, db.invoiceDao().getAllInvoicesOnce().size)

        // Now sell valid combined cart (p1: 1 pack + 5 units = 25, p2: 1 pack = 20)
        val okRes = shopRepo.checkoutCart(
            CheckoutRequest(
                clientRequestId = "req-multi-ok",
                items = listOf(
                    CheckoutLineRequest(p1, SaleUnitType.PACK, 1),
                    CheckoutLineRequest(p1, SaleUnitType.UNIT, 5),
                    CheckoutLineRequest(p2, SaleUnitType.PACK, 1)
                )
            ),
            UserRole.OWNER
        )
        assertEquals(975.0, okRes.invoice.totalRevenue, 0.01) // 450 + 125 + 400
        assertEquals(0, db.productDao().getById(p1)!!.stockUnits)
        assertEquals(0, db.productDao().getById(p2)!!.stockUnits)
        // Zero-stock products must not appear in available-for-sale query
        assertTrue(shopRepo.observeAvailableForSale().first().isEmpty())
    }

    @Test
    fun duplicateCheckoutRequest_isIdempotentAndPreventsDoubleDeduction() = runTest {
        val pId = shopRepo.addProduct("LM", 360.0, 20.0, 300.0, 40, 20, 10, UserRole.OWNER)
        val req = CheckoutRequest(
            clientRequestId = "same-uuid-123",
            items = listOf(CheckoutLineRequest(pId, SaleUnitType.PACK, 1))
        )
        val first = shopRepo.checkoutCart(req, UserRole.OWNER)
        val second = shopRepo.checkoutCart(req, UserRole.OWNER)

        assertFalse(first.wasDuplicateRequest)
        assertTrue(second.wasDuplicateRequest)
        assertEquals(first.invoice.id, second.invoice.id)
        assertEquals(1, db.invoiceDao().getAllInvoicesOnce().size)
        assertEquals(20, db.productDao().getById(pId)!!.stockUnits)
    }

    @Test
    fun invalidProductIdsAndQuantitiesAndConcurrentSales_neverProduceNegativeStock() = runTest {
        val pId = shopRepo.addProduct("Gauloises", 380.0, 20.0, 320.0, 20, 20, 5, UserRole.OWNER)

        // Invalid product ID
        try {
            shopRepo.checkoutCart(
                CheckoutRequest("bad-id", listOf(CheckoutLineRequest(9999L, SaleUnitType.UNIT, 1))),
                UserRole.OWNER
            )
            fail("Expected failure for nonexistent product")
        } catch (_: IllegalArgumentException) {}

        // Invalid zero quantity
        try {
            shopRepo.checkoutCart(
                CheckoutRequest("bad-qty", listOf(CheckoutLineRequest(pId, SaleUnitType.UNIT, 0))),
                UserRole.OWNER
            )
            fail("Expected failure for zero quantity")
        } catch (_: IllegalArgumentException) {}

        // Two concurrent checkouts each trying to buy the last 1 pack (20 units)
        val results = listOf("conc-1", "conc-2").map { reqId ->
            async {
                runCatching {
                    shopRepo.checkoutCart(
                        CheckoutRequest(reqId, listOf(CheckoutLineRequest(pId, SaleUnitType.PACK, 1))),
                        UserRole.OWNER
                    )
                }
            }
        }.awaitAll()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals(0, db.productDao().getById(pId)!!.stockUnits)
    }

    // ==================== 2. INVENTORY, PURCHASES & QUICK ENTRY TESTS ====================

    @Test
    fun inventoryAdjustmentsOpeningCountPurchasesAndQuickEntry_preserveLedgerAndNonNegativeStock() = runTest {
        val rymId = shopRepo.addProduct("Rym", 320.0, 18.0, 270.0, 20, 20, 10, UserRole.OWNER)

        // Cannot remove more than available
        try {
            shopRepo.adjustStock(rymId, -25, "REMOVE_STOCK", "تلف", UserRole.OWNER)
            fail("Stock must never become negative")
        } catch (_: IllegalStateException) {}
        assertEquals(20, db.productDao().getById(rymId)!!.stockUnits)

        // Purchase 2 packs (+40 units) at new cost 280
        financeRepo.recordPurchase(rymId, "مورد الجملة", 2, 280.0, true, "دفعة الصباح", UserRole.OWNER)
        val afterPurchase = db.productDao().getById(rymId)!!
        assertEquals(60, afterPurchase.stockUnits)
        assertEquals(280.0, afterPurchase.purchaseCost, 0.01)

        // Quick entry commands
        shopRepo.executeQuickEntry("+5s rym", UserRole.OWNER)
        assertEquals(65, db.productDao().getById(rymId)!!.stockUnits)

        shopRepo.executeQuickEntry("+1* rym", UserRole.OWNER)
        assertEquals(85, db.productDao().getById(rymId)!!.stockUnits)

        shopRepo.executeQuickEntry("-علبة rym", UserRole.OWNER)
        assertEquals(65, db.productDao().getById(rymId)!!.stockUnits)

        shopRepo.executeQuickEntry("-5 سجارة rym", UserRole.OWNER)
        assertEquals(60, db.productDao().getById(rymId)!!.stockUnits)

        // Opening count adjusts to 50 units
        shopRepo.recordOpeningInventory(mapOf(rymId to 50), "جرد الصباح", UserRole.OWNER)
        assertEquals(50, db.productDao().getById(rymId)!!.stockUnits)
        assertTrue(db.inventoryLedgerDao().getAllOnce().size >= 6)

        // Safe delete blocks product with history
        try {
            shopRepo.deleteProductSafely(rymId, UserRole.OWNER)
            fail("Must not hard-delete product referenced by invoices/ledger")
        } catch (_: IllegalStateException) {}
    }

    // ==================== 3. CREDIT & FINANCIAL REPORT ACCURACY TESTS ====================

    @Test
    fun creditFlowAndFinancialReport_separatesCashFromRevenueAndAvoidsDoubleCountingPurchases() = runTest {
        val prodId = shopRepo.addProduct("Marlboro", 500.0, 25.0, 400.0, 20, 20, 10, UserRole.OWNER)
        val custId = financeRepo.addCustomer("أحمد بن علي", "0550112233", "زبون دائم", UserRole.OWNER)

        // 1. Opening cash = 10,000
        financeRepo.recordCashMovement("OPENING_BALANCE", 10_000.0, "بداية اليوم", UserRole.OWNER)

        // 2. Purchase 2 packs at 400 each = 800 paid from cash (adds 40 units -> stock = 60)
        //    CRITICAL: Must reduce Cash by 800, must NOT reduce Net Profit as an operating expense!
        financeRepo.recordPurchase(prodId, "المورد الأول", 2, 400.0, true, "", UserRole.OWNER)

        // 3. Cash sale of 2 packs: Revenue = 1,000, COGS = 800, Gross Profit = 200
        shopRepo.checkoutCart(
            CheckoutRequest("sale-cash", listOf(CheckoutLineRequest(prodId, SaleUnitType.PACK, 2)), PaymentType.CASH),
            UserRole.OWNER
        )

        // 4. Credit sale of 1 pack to Ahmed: Revenue = 500, COGS = 400, Gross Profit = 100, Ahmed balance = 500
        shopRepo.checkoutCart(
            CheckoutRequest("sale-credit", listOf(CheckoutLineRequest(prodId, SaleUnitType.PACK, 1)), PaymentType.CREDIT, custId),
            UserRole.OWNER
        )
        assertEquals(500.0, db.customerDao().getById(custId)!!.balance, 0.01)

        // 5. Partial payment of 300 by Ahmed -> balance becomes 200, cash increases by 300
        financeRepo.recordPayment(custId, 300.0, "دفعة أولى", UserRole.OWNER)
        assertEquals(200.0, db.customerDao().getById(custId)!!.balance, 0.01)

        // Overpayment rejected
        try {
            financeRepo.recordPayment(custId, 250.0, "زيادة خاطئة", UserRole.OWNER)
            fail("Overpayment must be rejected")
        } catch (_: IllegalArgumentException) {}

        // 6. Operating expense of 150 (electricity) & owner withdrawal of 500
        financeRepo.addExpense("كهرباء وماء", "فاتورة الكهرباء", 150.0, actorRole = UserRole.OWNER)
        financeRepo.recordCashMovement("WITHDRAWAL", 500.0, "سحب شخصي", UserRole.OWNER)

        val now = System.currentTimeMillis()
        val report = financeRepo.buildFinancialReport(now - 60_000L, now + 60_000L)

        assertEquals(2, report.invoiceCount)
        assertEquals(1_500.0, report.totalRevenue, 0.01) // 1000 cash + 500 credit
        assertEquals(1_000.0, report.cashSales, 0.01)
        assertEquals(500.0, report.creditSales, 0.01)
        assertEquals(1_200.0, report.costOfGoodsSold, 0.01) // 3 packs * 400
        assertEquals(300.0, report.grossProfit, 0.01) // 1500 - 1200
        assertEquals(150.0, report.totalExpenses, 0.01)
        assertEquals(150.0, report.netProfit, 0.01) // 300 - 150 (purchase 800 NOT double counted!)
        assertEquals(300.0, report.customerPaymentsCollected, 0.01)
        assertEquals(200.0, report.totalOutstandingDebts, 0.01)

        // Closing Cash = 10,000 (opening) + 1,000 (cash sales) + 300 (payments) - 800 (cash purchase) - 150 (expense) - 500 (withdrawal) = 9,850
        assertEquals(9_850.0, report.closingCashBalance, 0.01)
    }

    // ==================== 4. SECURITY, RBAC, SQL INJECTION & BACKUP TESTS ====================

    @Test
    fun securityPermissionsPinAuthSqlInjectionAndBackupRestore() = runTest {
        // RBAC: Cashier cannot manage products, withdraw cash, or export backup
        try {
            shopRepo.addProduct("Blocked", 100.0, 10.0, 80.0, 20, 20, 5, UserRole.CASHIER)
            fail("Cashier must not add products")
        } catch (_: SecurityException) {}

        try {
            financeRepo.recordCashMovement("WITHDRAWAL", 100.0, "test", UserRole.CASHIER)
            fail("Cashier must not withdraw cash")
        } catch (_: SecurityException) {}

        // PIN authentication with salted SHA-256
        financeRepo.configurePins(enablePin = true, ownerPin = "1234", cashierPin = "5678", actorRole = UserRole.OWNER)
        assertEquals(UserRole.OWNER, financeRepo.authenticateRole("1234"))
        assertEquals(UserRole.CASHIER, financeRepo.authenticateRole("5678"))
        assertNull(financeRepo.authenticateRole("9999"))

        // SQL Injection string safely parameterized as literal product name
        val sqliName = "Rym'); DROP TABLE products; --"
        val sqliId = shopRepo.addProduct(sqliName, 300.0, 15.0, 250.0, 20, 20, 5, UserRole.OWNER)
        assertNotNull(db.productDao().getById(sqliId))
        assertEquals(sqliName, db.productDao().getById(sqliId)!!.name)

        // Full backup export -> mutate DB -> restore -> verify exact restoration + integrity check
        val backupJson = backupManager.exportBackupJson(UserRole.OWNER)
        shopRepo.addProduct("TempProduct", 100.0, 10.0, 50.0, 10, 10, 2, UserRole.OWNER)
        assertEquals(2, db.productDao().getAllProductsOnce().size)

        backupManager.restoreFromBackupJson(backupJson, UserRole.OWNER)
        val restoredProducts = db.productDao().getAllProductsOnce()
        assertEquals(1, restoredProducts.size)
        assertEquals(sqliName, restoredProducts.first().name)

        val integrity = backupManager.runIntegrityCheck()
        assertTrue(integrity.isHealthy)
        assertEquals(0, integrity.negativeStockCount)
        assertEquals(0, integrity.balanceMismatchCount)
    }
}
