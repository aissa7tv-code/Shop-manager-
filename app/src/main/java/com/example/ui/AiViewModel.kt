package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.RestockPlan
import com.example.data.ai.ShopAiService
import com.example.data.db.ProductEntity
import com.example.data.model.FinancialReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "AiViewModel"

data class AiChatMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String
)

sealed interface AiAdviceUiState {
    data object Idle : AiAdviceUiState
    data object Loading : AiAdviceUiState
    data class Streaming(val text: String) : AiAdviceUiState
    data class Success(val text: String) : AiAdviceUiState
    data class Error(val message: String) : AiAdviceUiState
}

sealed interface RestockPlanUiState {
    data object Idle : RestockPlanUiState
    data object Loading : RestockPlanUiState
    data class Success(val plan: RestockPlan) : RestockPlanUiState
    data class Error(val message: String) : RestockPlanUiState
}

class AiViewModel(
    private val aiService: ShopAiService = ShopAiService()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _adviceState = MutableStateFlow<AiAdviceUiState>(AiAdviceUiState.Idle)
    val adviceState: StateFlow<AiAdviceUiState> = _adviceState.asStateFlow()

    private val _restockState = MutableStateFlow<RestockPlanUiState>(RestockPlanUiState.Idle)
    val restockState: StateFlow<RestockPlanUiState> = _restockState.asStateFlow()

    fun askShopAdvisor(
        question: String,
        shopName: String,
        currency: String,
        report: FinancialReport?,
        products: List<ProductEntity>,
        includeProfitDetails: Boolean
    ) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) return
        if (_adviceState.value is AiAdviceUiState.Loading || _adviceState.value is AiAdviceUiState.Streaming) {
            return
        }

        _messages.value = _messages.value + AiChatMessage(isUser = true, text = cleanQuestion)

        viewModelScope.launch {
            _adviceState.value = AiAdviceUiState.Loading
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    aiService.streamShopAdvice(
                        userQuestion = cleanQuestion,
                        shopName = shopName,
                        currency = currency,
                        report = report,
                        products = products,
                        includeProfitDetails = includeProfitDetails
                    ).collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            _adviceState.value = AiAdviceUiState.Streaming(accumulated.toString())
                        }
                    }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    _messages.value = _messages.value + AiChatMessage(isUser = false, text = finalText)
                    _adviceState.value = AiAdviceUiState.Success(finalText)
                } else {
                    _adviceState.value = AiAdviceUiState.Error("أعاد النموذج استجابة فارغة. يرجى المحاولة مرة أخرى.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini streaming timed out after 60s", e)
                _adviceState.value = AiAdviceUiState.Error(
                    "انتهت مهلة الاتصال بالنموذج (60 ثانية). تحقق من الإنترنت وحاول مجدداً."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI advice generation failed", e)
                _adviceState.value = AiAdviceUiState.Error(
                    e.localizedMessage ?: "تعذر الحصول على استجابة من المساعد الذكي."
                )
            }
        }
    }

    fun generateRestockPlan(
        shopName: String,
        currency: String,
        report: FinancialReport?,
        products: List<ProductEntity>
    ) {
        if (_restockState.value is RestockPlanUiState.Loading) return

        viewModelScope.launch {
            _restockState.value = RestockPlanUiState.Loading
            try {
                val plan = withTimeout(60_000L) {
                    aiService.generateRestockPlan(
                        shopName = shopName,
                        currency = currency,
                        report = report,
                        products = products
                    )
                }
                _restockState.value = RestockPlanUiState.Success(plan)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini restock plan timed out after 60s", e)
                _restockState.value = RestockPlanUiState.Error(
                    "انتهت مهلة تحليل خطة التموين (60 ثانية). يرجى المحاولة مجدداً."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI restock plan failed", e)
                _restockState.value = RestockPlanUiState.Error(
                    e.localizedMessage ?: "فشل إنشاء خطة إعادة التموين الذكية."
                )
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _adviceState.value = AiAdviceUiState.Idle
    }
}
