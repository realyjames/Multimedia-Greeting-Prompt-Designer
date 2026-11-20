package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiVideoRepository
import com.example.ai.VideoAspectRatio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

private const val TAG = "AiVideoViewModel"

sealed interface VideoGenUiState {
    data object Idle : VideoGenUiState
    data class Loading(val statusMessage: String) : VideoGenUiState
    data class Success(
        val videoFile: File,
        val aspectRatio: VideoAspectRatio,
        val promptUsed: String
    ) : VideoGenUiState

    data class Error(val message: String) : VideoGenUiState
}

data class AiVideoStudioUiState(
    val isOpen: Boolean = false,
    val promptInput: String = "",
    val selectedAspectRatio: VideoAspectRatio = VideoAspectRatio.LANDSCAPE_16_9,
    val videoGenUiState: VideoGenUiState = VideoGenUiState.Idle
)

/**
 * 專責管理 AI 問候影片生成工作坊（Gemini Omni 1.2 Flash · Interactions API），
 * 支援 16:9（橫式）與 9:16（直式）影片比例選擇、背景輪詢進度與錯誤回報。
 */
class AiVideoViewModel(application: Application) : AndroidViewModel(application) {

    private val videoRepository = GeminiVideoRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(AiVideoStudioUiState())
    val uiState: StateFlow<AiVideoStudioUiState> = _uiState.asStateFlow()

    fun openStudio(initialPrompt: String? = null) {
        _uiState.update { state ->
            val prefill = initialPrompt?.takeIf { it.isNotBlank() } ?: state.promptInput
            state.copy(
                isOpen = true,
                promptInput = prefill
            )
        }
    }

    fun closeStudio() {
        _uiState.update { it.copy(isOpen = false) }
    }

    fun updatePromptInput(text: String) {
        _uiState.update { it.copy(promptInput = text) }
    }

    fun selectAspectRatio(aspectRatio: VideoAspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = aspectRatio) }
    }

    fun generateVideo(onMissingPrompt: () -> Unit = {}) {
        val currentState = _uiState.value
        val prompt = currentState.promptInput.trim()
        if (prompt.isEmpty()) {
            onMissingPrompt()
            return
        }
        val aspectRatio = currentState.selectedAspectRatio

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    videoGenUiState = VideoGenUiState.Loading(
                        "正在呼叫 Gemini Omni 1.2 Flash（${aspectRatio.apiValue}）..."
                    )
                )
            }
            try {
                val videoFile = withTimeout(VIDEO_GENERATION_TIMEOUT_MS) {
                    videoRepository.generateVideo(
                        promptText = prompt,
                        aspectRatio = aspectRatio,
                        onStatusUpdate = { statusMsg ->
                            _uiState.update { s ->
                                s.copy(videoGenUiState = VideoGenUiState.Loading(statusMsg))
                            }
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        videoGenUiState = VideoGenUiState.Success(
                            videoFile = videoFile,
                            aspectRatio = aspectRatio,
                            promptUsed = prompt
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Video generation timed out", e)
                _uiState.update {
                    it.copy(
                        videoGenUiState = VideoGenUiState.Error(
                            "影片生成逾時，請縮短提示詞或稍後重試。"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Interactions API video generation failed", e)
                _uiState.update {
                    it.copy(
                        videoGenUiState = VideoGenUiState.Error(
                            e.localizedMessage ?: "影片生成失敗，請檢查 API 設定與網路連線。"
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val VIDEO_GENERATION_TIMEOUT_MS = 300_000L
    }
}
