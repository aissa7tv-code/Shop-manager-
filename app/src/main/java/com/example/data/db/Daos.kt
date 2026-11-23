package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stockUnits > 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAvailableForSale(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProductsOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findByExactName(name: String): ProductEntity?

    @Query("SELECT * FROM products WHERE isActive = 1 AND LOWER(name) LIKE '%' || LOWER(TRIM(:query)) || '%' ORDER BY LENGTH(name) ASC LIMIT 1")
    suspend fun findBestActiveMatch(query: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    /**
     * Atomic guarded stock deduction: guarantees stockUnits NEVER becomes negative even under concurrency.
     * Returns affected row count (1 on success, 0 on insufficient stock or inactive product).
     */
    @Query(
        """
        UPDATE products
        SET stockUnits = stockUnits - :unitsToDeduct, updatedAt = :updatedAt
        WHERE id = :productId AND isActive = 1 AND stockUnits >= :unitsToDeduct
        """
    )
    suspend fun deductStockIfSufficient(productId: Long, unitsToDeduct: Int, updatedAt: Long): Int

    @Query(
        """
        UPDATE products
        SET stockUnits = stockUnits + :deltaUnits, updatedAt = :updatedAt
        WHERE id = :productId AND (stockUnits + :deltaUnits) >= 0
        """
    )
    suspend fun adjustStockGuarded(productId: Long, deltaUnits: Int, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM invoice_items WHERE productId = :productId")
    suspend fun countInvoiceItemReferences(productId: Long): Int

    @Query("SELECT COUNT(*) FROM purchases WHERE productId = :productId")
    suspend fun countPurchaseReferences(productId: Long): Int

    @Query("SELECT COUNT(*) FROM inventory_ledger WHERE productId = :productId")
    suspend fun countLedgerReferences(productId: Long): Int

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteById(productId: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC LIMIT 200")
    fun observeRecentInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt DESC")
    fun observeInvoicesInRange(fromMillis: Long, toMillis: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt DESC")
    suspend fun getInvoicesInRangeOnce(fromMillis: Long, toMillis: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices ORDER BY id ASC")
    suspend fun getAllInvoicesOnce(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE clientRequestId = :clientRequestId LIMIT 1")
    suspend fun findByClientRequestId(clientRequestId: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE id = :invoiceId LIMIT 1")
    suspend fun getById(invoiceId: Long): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoicesReplace(invoices: List<InvoiceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<InvoiceItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemsReplace(items: List<InvoiceItemEntity>)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId ORDER BY id ASC")
    suspend fun getItemsForInvoice(invoiceId: Long): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_items ORDER BY id ASC")
    suspend fun getAllItemsOnce(): List<InvoiceItemEntity>

    @Query(
        """
        SELECT ii.* FROM invoice_items ii
        INNER JOIN invoices inv ON inv.id = ii.invoiceId
        WHERE inv.createdAt BETWEEN :fromMillis AND :toMillis
        """
    )
    suspend fun getItemsInRangeOnce(fromMillis: Long, toMillis: Long): List<InvoiceItemEntity>

    @Query("DELETE FROM invoice_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()
}

@Dao
interface InventoryLedgerDao {
    @Query("SELECT * FROM inventory_ledger ORDER BY createdAt DESC, id DESC LIMIT 300")
    fun observeRecentLedger(): Flow<List<InventoryLedgerEntity>>

    @Query("SELECT * FROM inventory_ledger WHERE productId = :productId ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun observeLedgerForProduct(productId: Long): Flow<List<InventoryLedgerEntity>>

    @Query("SELECT * FROM inventory_ledger ORDER BY id ASC")
    suspend fun getAllOnce(): List<InventoryLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: InventoryLedgerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(entries: List<InventoryLedgerEntity>)

    @Query("DELETE FROM inventory_ledger")
    suspend fun deleteAll()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY balance DESC, name COLLATE NOCASE ASC")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE balance > 0 ORDER BY balance DESC")
    fun observeDebtors(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY id ASC")
    suspend fun getAllOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("UPDATE customers SET balance = :newBalance, updatedAt = :updatedAt WHERE id = :customerId")
    suspend fun updateBalance(customerId: Long, newBalance: Double, updatedAt: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface CreditTransactionDao {
    @Query("SELECT * FROM credit_transactions ORDER BY createdAt DESC, id DESC LIMIT 300")
    fun observeAllTransactions(): Flow<List<CreditTransactionEntity>>

    @Query("SELECT * FROM credit_transactions WHERE customerId = :customerId ORDER BY createdAt DESC, id DESC")
    fun observeForCustomer(customerId: Long): Flow<List<CreditTransactionEntity>>

    @Query("SELECT * FROM credit_transactions WHERE customerId = :customerId ORDER BY createdAt ASC, id ASC")
    suspend fun getForCustomerOnce(customerId: Long): List<CreditTransactionEntity>

    @Query("SELECT * FROM credit_transactions WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt DESC")
    suspend fun getInRangeOnce(fromMillis: Long, toMillis: Long): List<CreditTransactionEntity>

    @Query("SELECT * FROM credit_transactions ORDER BY id ASC")
    suspend fun getAllOnce(): List<CreditTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: CreditTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(txs: List<CreditTransactionEntity>)

    @Query("DELETE FROM credit_transactions")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC, id DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt DESC")
    suspend fun getInRangeOnce(fromMillis: Long, toMillis: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY id ASC")
    suspend fun getAllOnce(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY createdAt DESC, id DESC")
    fun observeAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt DESC")
    suspend fun getInRangeOnce(fromMillis: Long, toMillis: Long): List<PurchaseEntity>

    @Query("SELECT * FROM purchases ORDER BY id ASC")
    suspend fun getAllOnce(): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(purchases: List<PurchaseEntity>)

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()
}

@Dao
interface CashMovementDao {
    @Query("SELECT * FROM cash_movements ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeAllMovements(): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt DESC")
    suspend fun getInRangeOnce(fromMillis: Long, toMillis: Long): List<CashMovementEntity>

    @Query("SELECT * FROM cash_movements ORDER BY id ASC")
    suspend fun getAllOnce(): List<CashMovementEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: CashMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(movements: List<CashMovementEntity>)

    @Query("DELETE FROM cash_movements")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<ShopSettingsEntity?>

    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): ShopSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: ShopSettingsEntity)
}
