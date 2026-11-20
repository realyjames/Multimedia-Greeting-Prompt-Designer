package com.example.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * 使用 Firebase AI Logic (`firebase-ai`) 呼叫 Gemini 3.8 Flash 模型，
 * 將繁體中文問候語翻譯為自然、流暢且富有溫度的英文版本。
 * 驗證透過 Firebase App Check 完成，無需在客戶端設定 API 金鑰。
 */
class GeminiTranslationRepository {

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    """
                    You are a bilingual literary translator specializing in warm, natural, culturally fluent English greetings and creative AI media prompts.
                    When given a Traditional Chinese greeting or greeting prompt:
                    1. Translate it into natural, idiomatic, fluent English that preserves the emotional warmth, seasonal imagery, and poetic rhythm.
                    2. Avoid stiff, literal word-for-word translations; make it sound like a native English speaker wrote it warmly.
                    3. Preserve any concrete visual/layout details (such as dates, signatures, art styles, or frame descriptions) accurately and naturally.
                    4. Output ONLY the clean English translation text without explanations, labels, quotation wrappers, or markdown code fences.
                    """.trimIndent()
                )
            }
        )
    }

    /**
     * 以串流方式回傳自然流暢的英文翻譯片段，降低使用者等待延遲。
     */
    fun translateGreetingStream(
        chineseText: String,
        greetingTypeLabel: String? = null
    ): Flow<String> {
        val prompt = buildTranslationPrompt(chineseText, greetingTypeLabel)
        return translationModel.generateContentStream(prompt)
            .mapNotNull { chunk -> chunk.text }
    }

    /**
     * 單次呼叫取得完整英文翻譯。
     */
    suspend fun translateGreetingToEnglish(
        chineseText: String,
        greetingTypeLabel: String? = null
    ): String {
        val prompt = buildTranslationPrompt(chineseText, greetingTypeLabel)
        val response = translationModel.generateContent(prompt)
        return response.text?.trim().orEmpty()
    }

    private fun buildTranslationPrompt(
        chineseText: String,
        greetingTypeLabel: String?
    ): String {
        val contextPrefix = if (!greetingTypeLabel.isNullOrBlank()) {
            "Greeting category: $greetingTypeLabel\n"
        } else {
            ""
        }
        return "${contextPrefix}Translate the following Traditional Chinese greeting into natural, fluent English:\n\n${chineseText.trim()}"
    }

    companion object {
        const val MODEL_NAME = "gemini-3.8-flash"
    }
}
