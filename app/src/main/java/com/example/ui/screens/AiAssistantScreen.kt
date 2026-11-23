package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.RestockRecommendation
import com.example.data.model.formatMoney
import com.example.data.security.RolePermissions
import com.example.data.security.ShopPermission
import com.example.ui.AiAdviceUiState
import com.example.ui.AiViewModel
import com.example.ui.RestockPlanUiState
import com.example.ui.ShopViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltContainer
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavySurface

private val QUICK_PROMPTS = listOf(
    "أعطني ملخصاً تنفيذياً لوضع المحل والمبيعات اليوم",
    "ما المنتجات التي يجب التركيز عليها لزيادة الربح؟",
    "اكتب رسالة تذكير مهذبة لزبون متأخر في تسديد الكريدي",
    "كيف أحسّن السيولة النقدية في الصندوق اليومي؟"
)

@Composable
fun AiAssistantScreen(
    shopViewModel: ShopViewModel,
    aiViewModel: AiViewModel
) {
    val products by shopViewModel.allProducts.collectAsStateWithLifecycle()
    val report by shopViewModel.financialReport.collectAsStateWithLifecycle()
    val settings by shopViewModel.settings.collectAsStateWithLifecycle()

    val messages by aiViewModel.messages.collectAsStateWithLifecycle()
    val adviceState by aiViewModel.adviceState.collectAsStateWithLifecycle()
    val restockState by aiViewModel.restockState.collectAsStateWithLifecycle()

    val currency = settings.currencySymbol
    val includeProfitDetails = RolePermissions.hasPermission(
        shopViewModel.activeRole,
        ShopPermission.VIEW_PROFIT_AND_COSTS
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    var promptInput by remember { mutableStateOf("") }

    val sendQuestion: (String) -> Unit = { q ->
        aiViewModel.askShopAdvisor(
            question = q,
            shopName = settings.shopName,
            currency = currency,
            report = report,
            products = products,
            includeProfitDetails = includeProfitDetails
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "المساعد الذكي للمحل",
            subtitle = "تحليل فوري لبيانات المبيعات والمخزون والكريدي",
            action = {
                if (selectedTab == 0 && messages.isNotEmpty()) {
                    IconButton(
                        onClick = { aiViewModel.clearChat() },
                        modifier = Modifier.testTag("ai_clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "مسح المحادثة",
                            tint = CrimsonDanger
                        )
                    }
                }
            }
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NavySurface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("المستشار الذكي") },
                modifier = Modifier.testTag("ai_tab_chat")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("خطة التموين الذكية") },
                modifier = Modifier.testTag("ai_tab_restock")
            )
        }

        if (selectedTab == 0) {
            // Quick-action prompt chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_PROMPTS.forEach { preset ->
                    AssistChip(
                        onClick = { sendQuestion(preset) },
                        label = { Text(preset, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyanSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Messages & live streaming output
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && adviceState is AiAdviceUiState.Idle) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.AutoAwesome,
                            title = "اسأل المساعد الذكي عن محلك",
                            description = "يقرأ المساعد بيانات المخزون والمبيعات والصندوق والكريدي الحقيقية من قاعدة البيانات لتقديم نصائح عملية وملخصات فورية."
                        )
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatBubbleCard(isUser = msg.isUser, text = msg.text)
                }

                when (val st = adviceState) {
                    is AiAdviceUiState.Loading -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard),
                                border = BorderStroke(1.dp, NavyBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("جاري تحليل بيانات المحل...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    is AiAdviceUiState.Streaming -> {
                        item {
                            ChatBubbleCard(isUser = false, text = st.text, isStreaming = true)
                        }
                    }
                    is AiAdviceUiState.Error -> {
                        item {
                            AiErrorCard(message = st.message)
                        }
                    }
                    else -> Unit
                }
            }

            // Message input bar
            val isBusy = adviceState is AiAdviceUiState.Loading || adviceState is AiAdviceUiState.Streaming
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("اكتب سؤالك عن المبيعات أو المخزون أو الديون...") },
                    singleLine = true,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        focusedBorderColor = CobaltPrimary,
                        unfocusedBorderColor = NavyBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input")
                )
                Button(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            val text = promptInput
                            promptInput = ""
                            sendQuestion(text)
                        }
                    },
                    enabled = !isBusy && promptInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال")
                }
            }
        } else {
            // Structured JSON Smart Restock Plan Tab
            Button(
                onClick = {
                    aiViewModel.generateRestockPlan(
                        shopName = settings.shopName,
                        currency = currency,
                        report = report,
                        products = products
                    )
                },
                enabled = restockState !is RestockPlanUiState.Loading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_restock_plan_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إنشاء خطة إعادة التموين الذكية")
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (val st = restockState) {
                    is RestockPlanUiState.Idle -> {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Inventory2,
                                title = "تخطيط ذكي لطلبات التوريد",
                                description = "اضغط الزر أعلاه لتحليل المخزون الحالي والمنتجات النافدة والمنخفضة والأكثر مبيعاً وإصدار قائمة توريد مرتبة حسب الأولوية والميزانية."
                            )
                        }
                    }
                    is RestockPlanUiState.Loading -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard),
                                border = BorderStroke(1.dp, NavyBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("جاري إعداد خطة التموين حسب المخزون والمبيعات...")
                                }
                            }
                        }
                    }
                    is RestockPlanUiState.Error -> {
                        item { AiErrorCard(message = st.message) }
                    }
                    is RestockPlanUiState.Success -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CobaltContainer),
                                border = BorderStroke(1.dp, CobaltPrimary)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "ملخص خطة التوريد",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(st.plan.summary, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "الميزانية التقديرية: ${formatMoney(st.plan.totalEstimatedBudget, currency)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldProfit,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(st.plan.items) { rec ->
                            RestockRecommendationCard(rec = rec, currency = currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleCard(
    isUser: Boolean,
    text: String,
    isStreaming: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isUser) CobaltContainer else NavyCard,
        border = BorderStroke(1.dp, if (isUser) CobaltPrimary else NavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "أنت" else "المساعد الذكي",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) CyanSecondary else EmeraldProfit,
                    fontWeight = FontWeight.Bold
                )
                if (isStreaming) {
                    StatusPill(text = "يكتب...", color = CyanSecondary)
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RestockRecommendationCard(
    rec: RestockRecommendation,
    currency: String
) {
    val priorityColor = when (rec.priority) {
        "عاجل" -> CrimsonDanger
        "متوسط" -> AmberWarning
        else -> EmeraldProfit
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(rec.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill(text = rec.priority, color = priorityColor)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الكمية المقترحة: ${rec.recommendedPacks} علبة",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatMoney(rec.estimatedCost, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldProfit,
                    fontWeight = FontWeight.Bold
                )
            }
            if (rec.reason.isNotBlank()) {
                Text(
                    text = rec.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AiErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CrimsonContainer),
        border = BorderStroke(1.dp, CrimsonDanger)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CrimsonDanger)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
