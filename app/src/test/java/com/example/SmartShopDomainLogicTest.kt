package com.example

import com.example.data.model.CartItem
import com.example.data.model.PeriodFilter
import com.example.data.model.QuickEntryCommand
import com.example.data.model.QuickEntryParser
import com.example.data.model.SaleUnitType
import com.example.data.model.formatMoney
import com.example.data.model.formatStockSummary
import com.example.data.model.periodBounds
import com.example.data.model.roundMoney
import com.example.data.security.PinSecurity
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.data.security.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SmartShopDomainLogicTest {

    @Test
    fun quickEntryParser_parsesLegacyShopCommandsAccurately() {
        val addUnits = QuickEntryParser.parse("+5s rym")
        assertTrue(addUnits is QuickEntryCommand.AddStock)
        addUnits as QuickEntryCommand.AddStock
        assertEquals("rym", addUnits.productQuery)
        assertEquals(5, addUnits.deltaUnits)
        assertFalse(addUnits.isPack)

        val addPack = QuickEntryParser.parse("+2* Marlboro")
        assertTrue(addPack is QuickEntryCommand.AddStock)
        addPack as QuickEntryCommand.AddStock
        assertEquals("Marlboro", addPack.productQuery)
        assertEquals(2, addPack.count)
        assertTrue(addPack.isPack)

        val sellPack = QuickEntryParser.parse("-علبة rym")
        assertTrue(sellPack is QuickEntryCommand.QuickSell)
        sellPack as QuickEntryCommand.QuickSell
        assertEquals("rym", sellPack.productQuery)
        assertEquals(SaleUnitType.PACK, sellPack.saleUnitType)
        assertEquals(1, sellPack.quantity)

        val sellUnits = QuickEntryParser.parse("-5 سجارة rym")
        assertTrue(sellUnits is QuickEntryCommand.QuickSell)
        sellUnits as QuickEntryCommand.QuickSell
        assertEquals("rym", sellUnits.productQuery)
        assertEquals(SaleUnitType.UNIT, sellUnits.saleUnitType)
        assertEquals(5, sellUnits.quantity)

        assertNull(QuickEntryParser.parse("invalid command"))
        assertNull(QuickEntryParser.parse("-0 سجارة rym"))
    }

    @Test
    fun cartItem_calculatesPackAndUnitDeductionsAndLineTotals() {
        val packItem = CartItem(
            productId = 1L,
            productName = "Marlboro",
            saleUnitType = SaleUnitType.PACK,
            quantity = 3,
            packSize = 20,
            unitPrice = 450.0,
            availableStockUnits = 100
        )
        assertEquals(60, packItem.unitsRequired)
        assertEquals(1350.0, packItem.lineTotal, 0.001)

        val unitItem = CartItem(
            productId = 1L,
            productName = "Marlboro",
            saleUnitType = SaleUnitType.UNIT,
            quantity = 7,
            packSize = 20,
            unitPrice = 25.0,
            availableStockUnits = 100
        )
        assertEquals(7, unitItem.unitsRequired)
        assertEquals(175.0, unitItem.lineTotal, 0.001)
        assertNotEquals(packItem.key, unitItem.key)
    }

    @Test
    fun pinSecurity_usesUniqueSaltsAndConstantTimeVerification() {
        val salt1 = PinSecurity.generateSaltHex()
        val salt2 = PinSecurity.generateSaltHex()
        assertNotEquals(salt1, salt2)

        val hash1 = PinSecurity.hashPin("2468", salt1)
        val hash2 = PinSecurity.hashPin("2468", salt2)
        assertNotEquals(hash1, hash2)

        assertTrue(PinSecurity.verifyPin("2468", hash1, salt1))
        assertFalse(PinSecurity.verifyPin("1357", hash1, salt1))
        assertFalse(PinSecurity.verifyPin("12", hash1, salt1))

        try {
            PinSecurity.hashPin("12a4", salt1)
            fail("Non-digit PIN must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun rolePermissions_enforcesOwnerAndCashierBoundaries() {
        assertTrue(RolePermissions.hasPermission(UserRole.OWNER, ShopPermission.MANAGE_SETTINGS_AND_BACKUP))
        assertTrue(RolePermissions.hasPermission(UserRole.OWNER, ShopPermission.RECORD_WITHDRAWAL))
        assertTrue(RolePermissions.hasPermission(UserRole.OWNER, ShopPermission.VIEW_PROFIT_AND_COSTS))

        assertTrue(RolePermissions.hasPermission(UserRole.CASHIER, ShopPermission.PERFORM_SALE))
        assertTrue(RolePermissions.hasPermission(UserRole.CASHIER, ShopPermission.REGISTER_CREDIT_OR_PAYMENT))
        assertFalse(RolePermissions.hasPermission(UserRole.CASHIER, ShopPermission.MANAGE_PRODUCTS))
        assertFalse(RolePermissions.hasPermission(UserRole.CASHIER, ShopPermission.RECORD_WITHDRAWAL))
        assertFalse(RolePermissions.hasPermission(UserRole.CASHIER, ShopPermission.MANAGE_SETTINGS_AND_BACKUP))

        try {
            RolePermissions.requirePermission(UserRole.CASHIER, ShopPermission.EDIT_EXPENSE)
            fail("Cashier must fail permission check for EDIT_EXPENSE")
        } catch (_: SecurityException) {}
    }

    @Test
    fun formattersAndPeriodBounds_returnConsistentValues() {
        assertEquals(123.46, roundMoney(123.456), 0.0001)
        assertEquals("1,500 دج", formatMoney(1500.0, "دج"))
        assertEquals("نافد (0)", formatStockSummary(0, 20))
        assertEquals("2 علبة + 5 وحدة", formatStockSummary(45, 20))
        assertEquals("3 علبة (60 وحدة)", formatStockSummary(60, 20))

        val (dailyStart, dailyEnd) = periodBounds(PeriodFilter.DAILY)
        assertTrue(dailyEnd > dailyStart)
        val (weekStart, weekEnd) = periodBounds(PeriodFilter.WEEKLY)
        assertTrue(weekStart < dailyStart)
        assertTrue(weekEnd >= dailyEnd)
        assertNotNull(periodBounds(PeriodFilter.MONTHLY))
    }
}
