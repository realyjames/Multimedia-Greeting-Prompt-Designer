package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiTranslationRepository
import com.example.data.DEFAULT_SIGNATURE
import com.example.data.GreetingPrompt
import com.example.data.GreetingPromptType
import com.example.data.MediaTag
import com.example.data.PromptAssembler
import com.example.data.PromptCatalog
import com.example.data.PromptPreferencesRepository
import com.example.data.SIGNATURE_SLOT_COUNT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "GreetingEditorVM"

/**
 * Gemini 翻譯任務 UI 狀態（由 Firebase AI Logic `firebase-ai` 驅動）。
 */
sealed interface GreetingTranslationState {
    data object Idle : GreetingTranslationState
    data object Loading : GreetingTranslationState
    data class Streaming(val partialText: String) : GreetingTranslationState
    data class Success(val translatedText: String) : GreetingTranslationState
    data class Error(val message: String) : GreetingTranslationState
}

/**
 * 問候語編輯介面 UI 狀態。
 * 管理問候類型下拉選單、媒體標籤、提示詞輸入、Gemini 流暢英譯與自訂署名欄位。
 */
data class GreetingEditorUiState(
    val selectedType: GreetingPromptType = GreetingPromptType.MORNING,
    val selectedMediaTags: Set<MediaTag> = setOf(MediaTag.IMAGE),
    val promptText: String = "",
    val englishTranslation: String = "",
    val translationState: GreetingTranslationState = GreetingTranslationState.Idle,
    val showSignature: Boolean = true,
    val signatureSlots: List<String> = List(SIGNATURE_SLOT_COUNT) { idx ->
        if (idx == 0) DEFAULT_SIGNATURE else ""
    },
    val selectedSignatureIndex: Int = 0,
    val showDate: Boolean = true,
    val formattedDate: String = PromptAssembler.formatTodayDate()
) {
    val isTranslating: Boolean
        get() = translationState is GreetingTranslationState.Loading ||
            translationState is GreetingTranslationState.Streaming

    /** 目前選用署名欄位的實際文字（供輸入框綁定） */
    val activeSignatureInput: String
        get() = signatureSlots.getOrNull(selectedSignatureIndex).orEmpty()

    /** 若目前欄位為空，組裝時回退至預設署名 */
    val activeSignatureDisplay: String
        get() = activeSignatureInput.trim().ifEmpty { DEFAULT_SIGNATURE }

    /** 依目前所選類型篩選出的內建範例模板 */
    val presetsForSelectedType: List<GreetingPrompt>
        get() = PromptCatalog.promptsByType(selectedType)

    /** 轉換為核心領域資料結構 GreetingPrompt */
    fun toGreetingPrompt(): GreetingPrompt {
        val tags = selectedMediaTags.ifEmpty { setOf(MediaTag.IMAGE) }.toList()
        return GreetingPrompt(
            id = "custom_editor_${selectedType.name.lowercase()}",
            dimensionId = "custom_editor",
            title = "${selectedType.displayName}自訂問候",
            type = selectedType,
            template = promptText.trim(),
            mediaTags = tags,
            englishTranslation = englishTranslation.trim()
        )
    }

    /** 即時組裝後的完整中文問候提示詞 */
    val assembledPrompt: String
        get() {
            val body = promptText.trim()
            if (body.isEmpty()) return ""

            val parts = mutableListOf<String>()
            val tagsText = selectedMediaTags.ifEmpty { setOf(MediaTag.IMAGE) }
                .joinToString(" / ") { "#${it.displayName}" }
            parts += "【${selectedType.displayName}問候 · $tagsText】"
            parts += body

            val metaParts = mutableListOf<String>()
            if (showDate) {
                metaParts += "日期：$formattedDate"
            }
            if (showSignature) {
                metaParts += "署名：「$activeSignatureDisplay」"
            }
            if (metaParts.isNotEmpty()) {
                parts += "排版與落款要求：請於畫面優雅呈現 ${metaParts.joinToString("，")}，確保繁體中文字體清晰無亂碼。"
            }
            return parts.joinToString("\n\n")
        }
}

/**
 * 專責管理「問候語編輯介面」的 ViewModel：
 * - 透過 DataStore 與全應用程式的 10 組自訂署名及日期偏好即時同步。
 * - 透過 Firebase AI Logic (`firebase-ai`, `gemini-3.8-flash`) 將中文問候語串流翻譯為自然流暢的英文版本。
 */
class GreetingEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PromptPreferencesRepository(application.applicationContext)
    private val geminiTranslationRepository = GeminiTranslationRepository()

    private val _uiState = MutableStateFlow(GreetingEditorUiState())
    val uiState: StateFlow<GreetingEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collect { saved ->
                _uiState.update { current ->
                    current.copy(
                        signatureSlots = saved.signatureSlots,
                        selectedSignatureIndex = saved.selectedSignatureIndex,
                        showSignature = saved.showSignature,
                        showDate = saved.showDate
                    )
                }
            }
        }
    }

    fun selectType(type: GreetingPromptType) {
        _uiState.update { state ->
            val recommendedTags = when (type) {
                GreetingPromptType.VIDEO -> setOf(MediaTag.VIDEO)
                GreetingPromptType.MUSIC -> setOf(MediaTag.MUSIC, MediaTag.BGM)
                else -> state.selectedMediaTags.ifEmpty { setOf(MediaTag.IMAGE) }
            }
            state.copy(
                selectedType = type,
                selectedMediaTags = recommendedTags
            )
        }
    }

    fun toggleMediaTag(tag: MediaTag) {
        _uiState.update { state ->
            val updated = state.selectedMediaTags.toMutableSet()
            if (updated.contains(tag)) {
                if (updated.size > 1) {
                    updated.remove(tag)
                }
            } else {
                updated.add(tag)
            }
            state.copy(selectedMediaTags = updated)
        }
    }

    fun updatePromptText(text: String) {
        _uiState.update { it.copy(promptText = text) }
    }

    fun updateEnglishTranslation(text: String) {
        _uiState.update {
            it.copy(
                englishTranslation = text,
                translationState = GreetingTranslationState.Idle
            )
        }
    }

    fun applyPresetTemplate(preset: GreetingPrompt) {
        val cleanedTemplate = preset.template
            .replace("[圖片張數]", "1張")
            .replace("[日期]", _uiState.value.formattedDate)
            .replace("[署名]", _uiState.value.activeSignatureDisplay)
            .replace("[圖框樣式]", "溫潤圓角金邊圖框")

        val cleanedEnglish = preset.englishTranslation
            .replace("[圖片張數]", "1")
            .replace("[日期]", _uiState.value.formattedDate)
            .replace("[署名]", _uiState.value.activeSignatureDisplay)
            .replace("[圖框樣式]", "elegant thin golden border frame")

        _uiState.update {
            it.copy(
                selectedType = preset.type,
                selectedMediaTags = preset.mediaTags.ifEmpty { listOf(MediaTag.IMAGE) }.toSet(),
                promptText = cleanedTemplate,
                englishTranslation = cleanedEnglish,
                translationState = GreetingTranslationState.Idle
            )
        }
    }

    /**
     * 透過 `firebase-ai` (Gemini 3.8 Flash) 串流將中文問候語自動翻譯為自然流暢的英文版本。
     */
    fun translateGreetingWithGemini(onComplete: (Boolean) -> Unit = {}) {
        val currentState = _uiState.value
        val source = currentState.promptText.trim()
        if (source.isEmpty()) {
            _uiState.update {
                it.copy(translationState = GreetingTranslationState.Error("請先輸入中文問候語再進行翻譯。"))
            }
            onComplete(false)
            return
        }
        if (currentState.isTranslating) return

        val categoryLabel = currentState.selectedType.displayName
        viewModelScope.launch {
            _uiState.update { it.copy(translationState = GreetingTranslationState.Loading) }
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    geminiTranslationRepository.translateGreetingStream(
                        chineseText = source,
                        greetingTypeLabel = categoryLabel
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        val partial = accumulated.toString()
                        _uiState.update {
                            it.copy(
                                englishTranslation = partial,
                                translationState = GreetingTranslationState.Streaming(partial)
                            )
                        }
                    }
                }
                val finalEnglish = accumulated.toString().trim()
                if (finalEnglish.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            englishTranslation = finalEnglish,
                            translationState = GreetingTranslationState.Success(finalEnglish)
                        )
                    }
                    onComplete(true)
                } else {
                    _uiState.update {
                        it.copy(
                            translationState = GreetingTranslationState.Error("Gemini 未回傳翻譯內容，請再試一次。")
                        )
                    }
                    onComplete(false)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini translation timed out", e)
                _uiState.update {
                    it.copy(
                        translationState = GreetingTranslationState.Error("翻譯逾時（超過 60 秒），請稍後重試。")
                    )
                }
                onComplete(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI Gemini translation failed", e)
                _uiState.update {
                    it.copy(
                        translationState = GreetingTranslationState.Error(
                            e.localizedMessage ?: "Gemini 翻譯失敗，請檢查網路或 Firebase 服務狀態。"
                        )
                    )
                }
                onComplete(false)
            }
        }
    }

    fun setShowSignature(show: Boolean) {
        _uiState.update { it.copy(showSignature = show) }
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSignatureAndDateToggles(state.showSignature, state.showDate)
        }
    }

    fun setShowDate(show: Boolean) {
        _uiState.update { it.copy(showDate = show) }
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSignatureAndDateToggles(state.showSignature, state.showDate)
        }
    }

    fun selectSignatureSlot(index: Int) {
        if (index !in 0 until SIGNATURE_SLOT_COUNT) return
        _uiState.update { it.copy(selectedSignatureIndex = index) }
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSignatures(state.signatureSlots, state.selectedSignatureIndex)
        }
    }

    fun updateActiveSignatureText(text: String) {
        val index = _uiState.value.selectedSignatureIndex
        if (index !in 0 until SIGNATURE_SLOT_COUNT) return
        _uiState.update { state ->
            val updated = state.signatureSlots.toMutableList()
            updated[index] = text
            state.copy(signatureSlots = updated)
        }
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSignatures(state.signatureSlots, state.selectedSignatureIndex)
        }
    }

    fun clearDraft() {
        _uiState.update {
            it.copy(
                promptText = "",
                englishTranslation = "",
                translationState = GreetingTranslationState.Idle
            )
        }
    }
}
