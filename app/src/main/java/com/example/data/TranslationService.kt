package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TranslationService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * Automatically translates Traditional Chinese custom variable input to English and formats
     * as "中文 (English)" unless the user already included parentheses.
     * Falls back gracefully to the original Chinese text if offline or translation fails.
     */
    suspend fun formatWithAutoTranslation(rawInput: String): String = withContext(Dispatchers.IO) {
        val zhVal = rawInput.trim()
        if (zhVal.isEmpty() || zhVal.contains('(') || zhVal.contains('（')) {
            return@withContext zhVal
        }

        try {
            val encoded = URLEncoder.encode(zhVal, "UTF-8")
            val url =
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-TW&tl=en&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext zhVal
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext zhVal

                val root = JSONArray(body)
                val segments = root.optJSONArray(0) ?: return@withContext zhVal
                val translatedBuilder = StringBuilder()
                for (i in 0 until segments.length()) {
                    val segment = segments.optJSONArray(i)
                    val part = segment?.optString(0, "").orEmpty()
                    translatedBuilder.append(part)
                }
                val enVal = translatedBuilder.toString().trim()
                if (enVal.isNotEmpty() && !enVal.equals(zhVal, ignoreCase = true)) {
                    "$zhVal ($enVal)"
                } else {
                    zhVal
                }
            }
        } catch (_: Exception) {
            zhVal
        }
    }
}
