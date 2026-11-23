package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom

enum class UserRole(val code: String, val labelAr: String) {
    OWNER("OWNER", "المالك / المدير"),
    CASHIER("CASHIER", "البائع / الكاشير");

    companion object {
        fun fromCode(code: String?): UserRole =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OWNER
    }
}

enum class ShopPermission {
    PERFORM_SALE,
    MANAGE_PRODUCTS,
    ADJUST_INVENTORY,
    MANAGE_CUSTOMERS,
    REGISTER_CREDIT_OR_PAYMENT,
    ADD_EXPENSE,
    EDIT_EXPENSE,
    RECORD_PURCHASE,
    RECORD_WITHDRAWAL,
    VIEW_PROFIT_AND_COSTS,
    MANAGE_SETTINGS_AND_BACKUP
}

object RolePermissions {
    private val ownerPermissions: Set<ShopPermission> = ShopPermission.entries.toSet()
    private val cashierPermissions: Set<ShopPermission> = setOf(
        ShopPermission.PERFORM_SALE,
        ShopPermission.ADJUST_INVENTORY,
        ShopPermission.MANAGE_CUSTOMERS,
        ShopPermission.REGISTER_CREDIT_OR_PAYMENT,
        ShopPermission.ADD_EXPENSE,
        ShopPermission.RECORD_PURCHASE
    )

    fun hasPermission(role: UserRole, permission: ShopPermission): Boolean {
        return when (role) {
            UserRole.OWNER -> permission in ownerPermissions
            UserRole.CASHIER -> permission in cashierPermissions
        }
    }

    fun requirePermission(role: UserRole, permission: ShopPermission) {
        if (!hasPermission(role, permission)) {
            throw SecurityException("ليس لديك الصلاحية لتنفيذ هذه العملية بصفتك ${role.labelAr}")
        }
    }
}

object PinSecurity {
    private val secureRandom = SecureRandom()

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
        val normalized = pin.trim()
        require(normalized.length in 4..8 && normalized.all { it.isDigit() }) {
            "يجب أن يتكون رمز PIN من 4 إلى 8 أرقام"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$normalized:smart_shop_v1".toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(input)
        return hashed.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, expectedHash: String, saltHex: String): Boolean {
        if (expectedHash.isBlank() || saltHex.isBlank()) return false
        val normalized = pin.trim()
        if (normalized.length !in 4..8 || !normalized.all { it.isDigit() }) return false
        val actual = hashPin(normalized, saltHex)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}
