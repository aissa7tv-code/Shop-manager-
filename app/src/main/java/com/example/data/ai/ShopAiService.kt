package com.example.data.ai

import com.example.data.db.ProductEntity
import com.example.data.model.FinancialReport
import com.example.data.model.formatMoney
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class RestockRecommendation(
    val productName: String,
    val priority: String,
    val recommendedPacks: Int,
    val estimatedCost: Double,
    val reason: String
)

data class RestockPlan(
    val summary: String,
    val totalEstimatedBudget: Double,
    val items: List<RestockRecommendation>
)

class ShopAiService {

    // Lazily initialized so Firebase.ai is only evaluated upon first AI inference
    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    أنت مستشار مالي وتجاري ذكي مدمج داخل تطبيق Smart Shop Manager Pro لإدارة المحلات التجارية.
                    أجب دائماً باللغة العربية الواضحة والعملية وبأسلوب موجز ومنظم يناسب صاحب محل تجاري يستخدم هاتفه.
                    استند بدقة إلى بيانات المحل الفعلية المرفقة مع السؤال، ولا تخترع أرقاماً غير موجودة في السياق.
                    """.trimIndent()
                )
            }
        )
    }

    private val restockSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "ملخص موجز بالعربية لوضع المخزون وخطة التوريد"),
            "totalEstimatedBudget" to Schema.double(description = "التكلفة التقديرية الإجمالية للطلبية"),
            "items" to Schema.array(
                description = "قائمة المنتجات المقترح إعادة تموينها مرتبة حسب الأولوية",
                items = Schema.obj(
                    properties = mapOf(
                        "productName" to Schema.string(description = "اسم المنتج"),
                        "priority" to Schema.enumeration(
                            values = listOf("عاجل", "متوسط", "منخفض"),
                            description = "أولوية التوريد"
                        ),
                        "recommendedPacks" to Schema.integer(description = "عدد العلب المقترح شراؤها"),
                        "estimatedCost" to Schema.double(description = "التكلفة التقديرية لهذا المنتج"),
                        "reason" to Schema.string(description = "سبب التوصية باختصار")
                    )
                )
            )
        )
    )

    private val structuredRestockModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = restockSchema
            },
            systemInstruction = content {
                text(
                    "أنت مخطط مشتريات ومخزون لمحل تجاري. حلّل بيانات المخزون والمبيعات الفعلية وأعد خطة توريد عملية باللغة العربية وفق المخطط JSON المحدد."
                )
            }
        )
    }

    fun streamShopAdvice(
        userQuestion: String,
        shopName: String,
        currency: String,
        report: FinancialReport?,
        products: List<ProductEntity>,
        includeProfitDetails: Boolean
    ): Flow<String> {
        val contextBlock = buildShopContextPrompt(
            shopName = shopName,
            currency = currency,
            report = report,
            products = products,
            includeProfitDetails = includeProfitDetails
        )
        val fullPrompt = """
            $contextBlock

            سؤال صاحب المحل:
            $userQuestion
        """.trimIndent()
        return chatModel.generateContentStream(fullPrompt).map { chunk -> chunk.text.orEmpty() }
    }

    suspend fun generateRestockPlan(
        shopName: String,
        currency: String,
        report: FinancialReport?,
        products: List<ProductEntity>
    ): RestockPlan {
        val inventoryLines = products.filter { it.isActive }.joinToString("\n") { p ->
            val packsLeft = p.stockUnits / p.packSize.coerceAtLeast(1)
            val unitsRem = p.stockUnits % p.packSize.coerceAtLeast(1)
            "- ${p.name}: متوفر $packsLeft علبة و $unitsRem وحدة (المجموع ${p.stockUnits} وحدة، حد التنبيه ${p.lowStockThreshold} وحدة، حجم العلبة ${p.packSize}، تكلفة العلبة ${p.purchaseCost} $currency)"
        }
        val salesLines = report?.productBreakdown?.take(10)?.joinToString("\n") { row ->
            "- ${row.productName}: مباع ${row.packsSold} علبة و ${row.unitsSold} وحدة (إيراد ${row.revenue} $currency)"
        } ?: "لا توجد مبيعات مسجلة في الفترة الحالية."

        val prompt = """
            المحل: $shopName | العملة: $currency
            الرصيد النقدي المتاح بالصندوق: ${report?.closingCashBalance ?: 0.0} $currency
            حالة المنتجات النشطة:
            $inventoryLines

            أكثر المنتجات مبيعاً في الفترة الحالية:
            $salesLines

            اقترح خطة إعادة تموين ذكية ومناسبة للميزانية مع التركيز على المنتجات النافدة والمنخفضة والأكثر طلباً.
        """.trimIndent()

        val response = structuredRestockModel.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("أعاد النموذج استجابة فارغة.")
        val json = JSONObject(jsonText)
        val itemsArr = json.optJSONArray("items")
        val parsedItems = if (itemsArr != null) {
            List(itemsArr.length()) { idx ->
                val obj = itemsArr.getJSONObject(idx)
                RestockRecommendation(
                    productName = obj.optString("productName", ""),
                    priority = obj.optString("priority", "متوسط"),
                    recommendedPacks = obj.optInt("recommendedPacks", 1).coerceAtLeast(0),
                    estimatedCost = obj.optDouble("estimatedCost", 0.0),
                    reason = obj.optString("reason", "")
                )
            }
        } else {
            emptyList()
        }

        return RestockPlan(
            summary = json.optString("summary", ""),
            totalEstimatedBudget = json.optDouble("totalEstimatedBudget", 0.0),
            items = parsedItems
        )
    }

    private fun buildShopContextPrompt(
        shopName: String,
        currency: String,
        report: FinancialReport?,
        products: List<ProductEntity>,
        includeProfitDetails: Boolean
    ): String {
        val activeProducts = products.filter { it.isActive }
        val outOfStock = activeProducts.filter { it.stockUnits == 0 }.map { it.name }
        val lowStock = activeProducts.filter { it.stockUnits in 1..it.lowStockThreshold }
            .map { "${it.name} (${it.stockUnits} وحدة)" }

        val sb = StringBuilder()
        sb.appendLine("بيانات المحل الفعلية الحالية ($shopName):")
        sb.appendLine("- عدد المنتجات النشطة: ${activeProducts.size}")
        sb.appendLine("- منتجات نافدة: ${if (outOfStock.isEmpty()) "لا يوجد" else outOfStock.joinToString("، ")}")
        sb.appendLine("- منتجات منخفضة المخزون: ${if (lowStock.isEmpty()) "لا يوجد" else lowStock.joinToString("، ")}")

        if (report != null) {
            sb.appendLine("- عدد الفواتير في الفترة المحددة: ${report.invoiceCount}")
            sb.appendLine("- إجمالي المبيعات: ${formatMoney(report.totalRevenue, currency)} (نقدي: ${formatMoney(report.cashSales, currency)}، كريدي: ${formatMoney(report.creditSales, currency)})")
            if (includeProfitDetails) {
                sb.appendLine("- تكلفة البضاعة المباعة (COGS): ${formatMoney(report.costOfGoodsSold, currency)}")
                sb.appendLine("- إجمالي الربح: ${formatMoney(report.grossProfit, currency)}")
                sb.appendLine("- المصاريف التشغيلية: ${formatMoney(report.totalExpenses, currency)}")
                sb.appendLine("- صافي الربح: ${formatMoney(report.netProfit, currency)}")
            }
            sb.appendLine("- الرصيد النقدي الفعلي بالصندوق: ${formatMoney(report.closingCashBalance, currency)}")
            sb.appendLine("- إجمالي ديون الزبائن المستحقة: ${formatMoney(report.totalOutstandingDebts, currency)} (${report.debtors.size} زبون مدين)")
            if (report.debtors.isNotEmpty()) {
                val topDebtors = report.debtors.take(5)
                    .joinToString("، ") { "${it.customerName}: ${formatMoney(it.balance, currency)} (منذ ${it.oldestUnpaidDays} يوم)" }
                sb.appendLine("- أبرز المدينين: $topDebtors")
            }
            if (report.productBreakdown.isNotEmpty()) {
                val topProducts = report.productBreakdown.take(5)
                    .joinToString("، ") { "${it.productName} (${it.packsSold} علبة / ${it.unitsSold} وحدة = ${formatMoney(it.revenue, currency)})" }
                sb.appendLine("- الأكثر مبيعاً: $topProducts")
            }
        }
        return sb.toString()
    }
}
