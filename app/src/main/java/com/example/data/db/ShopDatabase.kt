package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        CustomerEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        InventoryLedgerEntity::class,
        CreditTransactionEntity::class,
        ExpenseEntity::class,
        PurchaseEntity::class,
        CashMovementEntity::class,
        ShopSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ShopDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun inventoryLedgerDao(): InventoryLedgerDao
    abstract fun customerDao(): CustomerDao
    abstract fun creditTransactionDao(): CreditTransactionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun cashMovementDao(): CashMovementDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DB_NAME = "smart_shop_manager_pro.db"

        @Volatile
        private var INSTANCE: ShopDatabase? = null

        fun getInstance(context: Context): ShopDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): ShopDatabase {
            return Room.databaseBuilder(appContext, ShopDatabase::class.java, DB_NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                        seedInitialShopData(db)
                    }
                })
                .build()
        }

        /**
         * Seeds the default settings row and the editable starter product catalog specified
         * in Smart Shop Manager Pro's project specification. Financial/sales tables start empty.
         */
        fun seedInitialShopData(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL(
                """
                INSERT OR IGNORE INTO shop_settings (
                    id, shopName, currencySymbol, defaultPackSize, lowStockDefault,
                    pinEnabled, ownerPinHash, ownerPinSalt, cashierPinHash, cashierPinSalt,
                    activeRole, lastBackupAt
                ) VALUES (1, 'Smart Shop Manager Pro', 'دج', 20, 20, 0, '', '', '', '', 'OWNER', 0)
                """.trimIndent()
            )

            data class SeedProduct(
                val name: String,
                val packPrice: Double,
                val unitPrice: Double,
                val cost: Double,
                val stockPacks: Int,
                val packSize: Int
            )

            val starterCatalog = listOf(
                SeedProduct("Marlboro", 450.0, 25.0, 390.0, 10, 20),
                SeedProduct("Gauloises", 380.0, 20.0, 330.0, 10, 20),
                SeedProduct("LM", 360.0, 20.0, 310.0, 10, 20),
                SeedProduct("Philip Morris", 370.0, 20.0, 320.0, 8, 20),
                SeedProduct("Rym", 320.0, 18.0, 275.0, 12, 20),
                SeedProduct("Winston", 400.0, 22.0, 350.0, 10, 20),
                SeedProduct("Lucky Strike", 390.0, 22.0, 340.0, 8, 20),
                SeedProduct("BR", 280.0, 15.0, 235.0, 12, 20),
                SeedProduct("تبغ", 250.0, 25.0, 200.0, 10, 10)
            )

            starterCatalog.forEachIndexed { idx, item ->
                val stockUnits = item.stockPacks * item.packSize
                val productId = idx + 1L
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO products (
                        id, name, packPrice, unitPrice, purchaseCost, stockUnits,
                        packSize, lowStockThreshold, isActive, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        productId,
                        item.name,
                        item.packPrice,
                        item.unitPrice,
                        item.cost,
                        stockUnits,
                        item.packSize,
                        item.packSize,
                        now,
                        now
                    )
                )
                db.execSQL(
                    """
                    INSERT INTO inventory_ledger (
                        productId, productNameSnapshot, changeType, deltaUnits,
                        stockBefore, stockAfter, referenceId, notes, createdBy, createdAt
                    ) VALUES (?, ?, 'OPENING', ?, 0, ?, NULL, 'الرصيد الافتتاحي للمنتج', 'OWNER', ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        productId,
                        item.name,
                        stockUnits,
                        stockUnits,
                        now
                    )
                )
            }
        }
    }
}
