package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiVideoRepo"

/**
 * 影片生成畫面比例：支援 16:9 橫式寬螢幕與 9:16 直式短影音。
 */
enum class VideoAspectRatio(
    val apiValue: String,
    val displayLabel: String,
    val subtitle: String,
    val ratio: Float
) {
    LANDSCAPE_16_9("16:9", "16:9 橫式寬螢幕", "適合 LINE 分享、電視與桌面播放", 16f / 9f),
    PORTRAIT_9_16("9:16", "9:16 直式短影音", "適合手機滿版、Reels 與動態賀卡", 9f / 16f)
}

/**
 * 透過 Gemini Interactions API (`v1beta/interactions`) 呼叫 `gemini-omni-1.2-flash` 模型，
 * 支援背景任務輪詢與 16:9 / 9:16 問候影片生成。
 */
class GeminiVideoRepository(private val appContext: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 根據文字提示詞與指定畫面比例 (16:9 或 9:16) 呼叫 Interactions API 生成影片，
     * 並將成品寫入應用程式快取目錄中的 MP4 檔案。
     */
    suspend fun generateVideo(
        promptText: String,
        aspectRatio: VideoAspectRatio,
        onStatusUpdate: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "尚未設定 GEMINI_API_KEY。請在 AI Studio 的 Secrets 面板中填入您的 Gemini API 金鑰以啟用 Interactions API 影片生成功能。"
            )
        }

        onStatusUpdate("正在透過 Interactions API 建立影片生成任務（${aspectRatio.apiValue}）...")

        val requestPayload = JSONObject().apply {
            put("model", MODEL_NAME)
            put("input", promptText.trim())
            put("background", true)
            put(
                "response_format",
                JSONObject().apply {
                    put("type", "video")
                    put("aspect_ratio", aspectRatio.apiValue)
                    put("resolution", "720p")
                }
            )
        }

        val createRequest = Request.Builder()
            .url(INTERACTIONS_ENDPOINT)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(requestPayload.toString().toRequestBody(jsonMediaType))
            .build()

        var interactionJson = executeJsonRequest(createRequest, "建立影片任務")
        val interactionId = interactionJson.optString("id", "").trim()
        var status = interactionJson.optString("status", "").lowercase()

        var pollAttempt = 0
        while (status in IN_PROGRESS_STATUSES) {
            if (interactionId.isEmpty()) {
                throw IOException("Interactions API 未回傳任務 ID，無法輪詢影片進度。")
            }
            pollAttempt++
            onStatusUpdate("Gemini Omni 正在渲染影片中（第 $pollAttempt 次進度查詢 · ${aspectRatio.apiValue}）...")
            delay(POLL_INTERVAL_MS)

            val pollRequest = Request.Builder()
                .url("$INTERACTIONS_ENDPOINT/$interactionId")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            interactionJson = executeJsonRequest(pollRequest, "查詢影片進度")
            status = interactionJson.optString("status", "").lowercase()
        }

        if (status == "failed" || status == "cancelled" || status == "error") {
            val errorObj = interactionJson.optJSONObject("error")
            val errorMsg = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: interactionJson.optString("error").takeIf { it.isNotBlank() }
                ?: "模型回報影片生成失敗（狀態：$status）。"
            throw IOException(errorMsg)
        }

        onStatusUpdate("影片渲染完成，正在準備播放器...")
        val mediaResult = extractVideoPayload(interactionJson)
            ?: throw IOException("Interactions API 已完成但未回傳影片資料，請調整提示詞後再試一次。")

        val outputFile = createCacheVideoFile()
        when {
            !mediaResult.base64Data.isNullOrBlank() -> {
                val bytes = Base64.decode(mediaResult.base64Data, Base64.DEFAULT)
                if (bytes.isEmpty()) {
                    throw IOException("解碼影片資料失敗（內容為空）。")
                }
                outputFile.writeBytes(bytes)
            }

            !mediaResult.uri.isNullOrBlank() -> {
                onStatusUpdate("正在下載生成的 MP4 影片檔案...")
                downloadVideoToFile(mediaResult.uri, apiKey, outputFile)
            }

            else -> {
                throw IOException("未在回傳結果中找到可讀取的影片資料或下載連結。")
            }
        }

        outputFile
    }

    private fun executeJsonRequest(request: Request, actionLabel: String): JSONObject {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedError = try {
                    val errJson = JSONObject(bodyStr)
                    errJson.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: errJson.optString("message").takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
                Log.e(TAG, "$actionLabel HTTP ${response.code}: $bodyStr")
                throw IOException(
                    parsedError ?: "$actionLabel 失敗（HTTP ${response.code}）。"
                )
            }
            if (bodyStr.isBlank()) {
                throw IOException("$actionLabel 回傳空內容。")
            }
            return JSONObject(bodyStr)
        }
    }

    private fun downloadVideoToFile(uri: String, apiKey: String, destination: File) {
        val downloadUrl = if (uri.startsWith("http://") || uri.startsWith("https://")) {
            uri
        } else {
            "https://generativelanguage.googleapis.com/v1beta/$uri?alt=media"
        }

        val reqBuilder = Request.Builder().url(downloadUrl).get()
        if (downloadUrl.contains("googleapis.com")) {
            reqBuilder.header("x-goog-api-key", apiKey)
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下載影片檔案失敗（HTTP ${response.code}）。")
            }
            val body = response.body ?: throw IOException("影片下載回應為空。")
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (destination.length() <= 0L) {
            throw IOException("下載的影片檔案為空。")
        }
    }

    private data class ExtractedVideoPayload(
        val base64Data: String? = null,
        val uri: String? = null
    )

    /**
     * 相容 Interactions API 各種影片輸出節點格式：
     * - `output_video`
     * - `outputs[]` (type == "video" 或 mime_type 含 video/)
     * - `steps[].content[]` (type == "video" 或 mime_type 含 video/)
     */
    private fun extractVideoPayload(root: JSONObject): ExtractedVideoPayload? {
        root.optJSONObject("output_video")?.let { obj ->
            extractFromContentBlock(obj)?.let { return it }
        }

        root.optJSONArray("outputs")?.let { outputs ->
            findVideoInArray(outputs)?.let { return it }
        }

        root.optJSONArray("steps")?.let { steps ->
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                step.optJSONArray("content")?.let { contentArr ->
                    findVideoInArray(contentArr)?.let { return it }
                }
                step.optJSONArray("outputs")?.let { outArr ->
                    findVideoInArray(outArr)?.let { return it }
                }
            }
        }

        return null
    }

    private fun findVideoInArray(array: JSONArray): ExtractedVideoPayload? {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val type = item.optString("type", "").lowercase()
            val mime = item.optString("mime_type", item.optString("mimeType", "")).lowercase()
            if (type == "video" || mime.startsWith("video/")) {
                extractFromContentBlock(item)?.let { return it }
            }
            // Also inspect nested video / file_data blocks
            item.optJSONObject("video")?.let { nested ->
                extractFromContentBlock(nested)?.let { return it }
            }
        }
        return null
    }

    private fun extractFromContentBlock(obj: JSONObject): ExtractedVideoPayload? {
        val data = obj.optString("data", "").takeIf { it.isNotBlank() }
        val uri = obj.optString("uri", obj.optString("url", "")).takeIf { it.isNotBlank() }
        return if (data != null || uri != null) {
            ExtractedVideoPayload(base64Data = data, uri = uri)
        } else {
            null
        }
    }

    private fun createCacheVideoFile(): File {
        val dir = File(appContext.cacheDir, "ai_videos").apply { mkdirs() }
        // Keep cache tidy by pruning older generated clips beyond the latest 4
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        return File(dir, "greeting_video_${System.currentTimeMillis()}.mp4")
    }

    companion object {
        const val MODEL_NAME = "gemini-omni-1.2-flash"
        private const val INTERACTIONS_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val POLL_INTERVAL_MS = 4_000L
        private val IN_PROGRESS_STATUSES = setOf("in_progress", "queued", "running", "pending")
    }
}
