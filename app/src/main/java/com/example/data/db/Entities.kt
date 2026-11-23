package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val packPrice: Double,
    val unitPrice: Double,
    val purchaseCost: Double,
    val stockUnits: Int,
    val packSize: Int = 20,
    val lowStockThreshold: Int = 20,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["balance"])
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val balance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["clientRequestId"], unique = true),
        Index(value = ["customerId"]),
        Index(value = ["createdAt"]),
        Index(value = ["paymentType"])
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val clientRequestId: String,
    val paymentType: String, // "CASH" or "CREDIT"
    val customerId: Long? = null,
    val customerNameSnapshot: String? = null,
    val totalRevenue: Double,
    val totalCost: Double,
    val totalProfit: Double,
    val packsSold: Int,
    val unitsSold: Int,
    val notes: String = "",
    val createdBy: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["invoiceId"]),
        Index(value = ["productId"])
    ]
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val invoiceId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val saleUnitType: String, // "PACK" or "UNIT"
    val quantity: Int,
    val unitsDeducted: Int,
    val unitSalePrice: Double,
    val lineTotal: Double,
    val lineCost: Double,
    val lineProfit: Double
)

@Entity(
    tableName = "inventory_ledger",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["createdAt"]),
        Index(value = ["changeType"])
    ]
)
data class InventoryLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val productNameSnapshot: String,
    val changeType: String, // OPENING, PURCHASE, SALE, ADD_STOCK, REMOVE_STOCK, ADJUSTMENT, QUICK_ENTRY
    val deltaUnits: Int,
    val stockBefore: Int,
    val stockAfter: Int,
    val referenceId: Long? = null,
    val notes: String = "",
    val createdBy: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "credit_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["invoiceId"]),
        Index(value = ["createdAt"]),
        Index(value = ["type"])
    ]
)
data class CreditTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val customerId: Long,
    val type: String, // "DEBT", "PAYMENT", "ADJUSTMENT"
    val amount: Double,
    val deltaBalance: Double, // + for DEBT, - for PAYMENT, +/- for ADJUSTMENT
    val balanceAfter: Double,
    val invoiceId: Long? = null,
    val notes: String = "",
    val createdBy: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["category"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val description: String,
    val amount: Double,
    val createdBy: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["createdAt"])
    ]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val productNameSnapshot: String,
    val supplierName: String = "",
    val packsQuantity: Int,
    val unitsAdded: Int,
    val unitCostPerPack: Double,
    val totalCost: Double,
    val paidFromCash: Boolean = true,
    val notes: String = "",
    val createdBy: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cash_movements",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["type"])
    ]
)
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String, // "OPENING_BALANCE", "DEPOSIT", "WITHDRAWAL"
    val amount: Double,
    val notes: String = "",
    val createdBy: String = "OWNER",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shop_settings")
data class ShopSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "Smart Shop Manager Pro",
    val currencySymbol: String = "دج",
    val defaultPackSize: Int = 20,
    val lowStockDefault: Int = 20,
    val pinEnabled: Boolean = false,
    val ownerPinHash: String = "",
    val ownerPinSalt: String = "",
    val cashierPinHash: String = "",
    val cashierPinSalt: String = "",
    val activeRole: String = "OWNER",
    val lastBackupAt: Long = 0L
)
