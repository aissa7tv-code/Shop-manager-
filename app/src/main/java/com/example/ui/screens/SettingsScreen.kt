package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.formatDateTime
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.data.security.UserRole
import com.example.ui.ShopViewModel
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyVoid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: ShopViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val integrityResult by viewModel.integrityResult.collectAsStateWithLifecycle()
    val isOwner = RolePermissions.hasPermission(viewModel.activeRole, ShopPermission.MANAGE_SETTINGS_AND_BACKUP)

    var shopName by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var currency by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var defaultPackSize by remember(settings.defaultPackSize) { mutableStateOf(settings.defaultPackSize.toString()) }
    var lowStockDefault by remember(settings.lowStockDefault) { mutableStateOf(settings.lowStockDefault.toString()) }

    var pinEnabled by remember(settings.pinEnabled) { mutableStateOf(settings.pinEnabled) }
    var ownerPin by remember { mutableStateOf("") }
    var cashierPin by remember { mutableStateOf("") }

    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    // SAF zero-permission Export JSON Backup launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val payloadResult = viewModel.generateBackupPayload()
                payloadResult.onSuccess { json ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    viewModel.runDatabaseIntegrityCheck()
                }
            }
        }
    }

    // SAF zero-permission Restore JSON Backup launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (!raw.isNullOrBlank()) {
                    pendingRestoreJson = raw
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "الإعدادات والأمان والنسخ الاحتياطي",
                subtitle = "الصلاحية الحالية: ${viewModel.activeRole.labelAr}"
            )
        }

        // Shop Profile Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("إعدادات المحل العامة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("اسم المحل") },
                        enabled = isOwner,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("رمز العملة") },
                            enabled = isOwner,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = defaultPackSize,
                            onValueChange = { defaultPackSize = it },
                            label = { Text("حجم العلبة") },
                            enabled = isOwner,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = lowStockDefault,
                        onValueChange = { lowStockDefault = it },
                        label = { Text("حد تنبيه انخفاض المخزون الافتراضي") },
                        enabled = isOwner,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isOwner) {
                        Button(
                            onClick = {
                                viewModel.updateShopProfile(
                                    shopName = shopName,
                                    currency = currency,
                                    defaultPackSize = defaultPackSize.toIntOrNull() ?: 20,
                                    lowStock = lowStockDefault.toIntOrNull() ?: 20
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("حفظ إعدادات المحل")
                        }
                    }
                }
            }
        }

        // Security, User Roles & PIN Access Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CyanSecondary)
                            Text("الصلاحيات وحماية رمز PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        StatusPill(
                            text = viewModel.activeRole.labelAr,
                            color = if (viewModel.activeRole == UserRole.OWNER) EmeraldProfit else AmberWarning
                        )
                    }

                    Text(
                        text = "المالك يملك كافة الصلاحيات (التقارير والأرباح، تعديل المنتجات، المسحوبات، الإعدادات والنسخ الاحتياطي). البائع يمكنه البيع، إدارة الزبائن، تسجيل التسديدات والمشتريات اليومية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!settings.pinEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UserRole.entries.forEach { role ->
                                FilterChip(
                                    selected = viewModel.activeRole == role,
                                    onClick = { viewModel.switchRoleDirectly(role) },
                                    label = { Text(role.labelAr) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.lockSession() },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("قفل الجلسة أو تبديل المستخدم عبر PIN")
                        }
                    }

                    if (isOwner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تفعيل قفل رمز PIN المشفر", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = pinEnabled, onCheckedChange = { pinEnabled = it })
                        }
                        OutlinedTextField(
                            value = ownerPin,
                            onValueChange = { if (it.length <= 8) ownerPin = it.filter(Char::isDigit) },
                            label = { Text("رمز PIN للمالك (4-8 أرقام)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = cashierPin,
                            onValueChange = { if (it.length <= 8) cashierPin = it.filter(Char::isDigit) },
                            label = { Text("رمز PIN للبائع (اختياري)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilledTonalButton(
                            onClick = {
                                viewModel.configurePins(
                                    enablePin = pinEnabled,
                                    ownerPin = ownerPin.ifBlank { null },
                                    cashierPin = cashierPin.ifBlank { null }
                                )
                                ownerPin = ""
                                cashierPin = ""
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("تحديث إعدادات الأمان")
                        }
                    }
                }
            }
        }

        // Data Backup, Recovery & Integrity Check Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("النسخ الاحتياطي والاستعادة وسلامة البيانات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "آخر نسخة احتياطية: ${formatDateTime(settings.lastBackupAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportLauncher.launch("smart_shop_backup_$stamp.json")
                            },
                            enabled = isOwner,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("export_backup_button")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصدير نسخة")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = isOwner,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("restore_backup_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("استعادة نسخة")
                        }
                    }

                    FilledTonalButton(
                        onClick = { viewModel.runDatabaseIntegrityCheck() },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("integrity_check_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فحص سلامة قاعدة البيانات والأرصدة")
                    }

                    integrityResult?.let { res ->
                        StatusPill(
                            text = res.summaryMessage,
                            color = if (res.isHealthy) EmeraldProfit else CrimsonDanger
                        )
                    }
                }
            }
        }
    }

    pendingRestoreJson?.let { raw ->
        ConfirmActionDialog(
            title = "تأكيد استعادة النسخة الاحتياطية",
            message = "ستؤدي الاستعادة إلى استبدال جميع البيانات الحالية بالنسخة المختارة ضمن معاملة ذرية آمنة. هل تريد المتابعة؟",
            confirmLabel = "استعادة الآن",
            isDestructive = true,
            onConfirm = {
                viewModel.restoreFromBackupPayload(raw)
                pendingRestoreJson = null
            },
            onDismiss = { pendingRestoreJson = null }
        )
    }
}

@Composable
fun PinLockScreen(
    shopName: String,
    onSubmitPin: (String, (Boolean) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NavyVoid
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CobaltPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(shopName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "أدخل رمز PIN للمالك أو البائع للمتابعة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 8) {
                                pin = it.filter(Char::isDigit)
                                errorText = null
                            }
                        },
                        label = { Text("رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pin_lock_input")
                    )
                    errorText?.let {
                        Text(it, color = CrimsonDanger, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (pin.length < 4) {
                                errorText = "الرمز يجب أن يكون 4 أرقام على الأقل"
                            } else {
                                onSubmitPin(pin) { ok ->
                                    if (!ok) {
                                        errorText = "رمز PIN غير صحيح"
                                        pin = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pin_unlock_button")
                    ) {
                        Text("دخول")
                    }
                }
            }
        }
    }
}
