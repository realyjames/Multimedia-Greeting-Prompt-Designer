package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiImageRepository
import com.example.ai.GeminiTranslationRepository
import com.example.data.AttachedFileItem
import com.example.data.CUSTOM_OPTION_SENTINEL
import com.example.data.ContextTab
import com.example.data.DEFAULT_SIGNATURE
import com.example.data.FormulaVariable
import com.example.data.GreetingPrompt
import com.example.data.PromptAssembler
import com.example.data.PromptAssemblyInput
import com.example.data.PromptCatalog
import com.example.data.PromptDimension
import com.example.data.PromptOptionsLibrary
import com.example.data.PromptPreferencesRepository
import com.example.data.SIGNATURE_SLOT_COUNT
import com.example.data.TranslationService
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "PromptDesignerVM"

enum class ImageStudioMode {
    CREATE,
    EDIT
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val promptUsed: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

data class ConfirmDialogState(
    val title: String,
    val message: String,
    val isDestructive: Boolean = false,
    val onConfirm: () -> Unit
)

data class SnackbarEvent(
    val id: Long = System.nanoTime(),
    val message: String
)

data class PromptDesignerUiState(
    val dimensions: List<PromptDimension> = PromptCatalog.dimensions,
    val expandedDimensionIds: Set<String> = setOf(PromptCatalog.dimensions.first().id),
    val selectedPrompt: GreetingPrompt? = null,

    // Signature settings
    val signatureSlots: List<String> = List(SIGNATURE_SLOT_COUNT) { idx ->
        if (idx == 0) DEFAULT_SIGNATURE else ""
    },
    val selectedSignatureIndex: Int = 0,
    val showSignature: Boolean = true,
    val isSignatureExpanded: Boolean = false,

    // Date settings
    val showDate: Boolean = true,
    val formattedDate: String = PromptAssembler.formatTodayDate(),

    // Image count settings
    val imageCountOptions: List<String> = PromptOptionsLibrary.defaultImageCounts,
    val selectedImageCount: String = PromptOptionsLibrary.defaultImageCounts.first(),
    val isEditingImageCount: Boolean = false,
    val customImageCountInput: String = "",

    // Frame settings
    val showFrame: Boolean = true,
    val frameOptions: List<String> = PromptOptionsLibrary.defaultFrames,
    val selectedFrame: String = PromptOptionsLibrary.defaultFrames.first(),
    val isEditingFrame: Boolean = false,
    val customFrameInput: String = "",

    // Dynamic formula variables
    val customVariableOptionsMap: Map<String, List<String>> = emptyMap(),
    val selectedVariables: Map<String, String> = emptyMap(),
    val editingVariables: Set<String> = emptySet(),
    val customVariableInputs: Map<String, String> = emptyMap(),
    val translatingVariables: Set<String> = emptySet(),

    // Context supplement
    val activeContextTab: ContextTab = ContextTab.TEXT,
    val contextText: String = "",
    val contextUrl: String = "",
    val attachedFiles: List<AttachedFileItem> = emptyList(),

    // Output (Chinese + English Translation from GreetingPrompt & Gemini AI)
    val generatedPrompt: String = "",
    val generatedEnglishPrompt: String = "",
    val promptTranslationState: GreetingTranslationState = GreetingTranslationState.Idle,
    val promptGenerationCounter: Int = 0,

    // AI Image Studio (gemini-nano-banana-2.1 via Firebase AI Logic)
    val isImageStudioOpen: Boolean = false,
    val imageStudioMode: ImageStudioMode = ImageStudioMode.CREATE,
    val studioPromptInput: String = "",
    val studioEditInstruction: String = "",
    val studioSourceBitmap: Bitmap? = null,
    val studioSourceLabel: String = "",
    val imageGenUiState: ImageGenUiState = ImageGenUiState.Idle,

    // Dialog & feedback
    val pendingDialog: ConfirmDialogState? = null,
    val snackbarEvent: SnackbarEvent? = null
) {
    val activeSignatureDisplay: String
        get() = signatureSlots.getOrNull(selectedSignatureIndex)?.trim().orEmpty()
            .ifEmpty { DEFAULT_SIGNATURE }

    fun effectiveOptionsFor(variable: FormulaVariable): List<String> {
        return customVariableOptionsMap[variable.name]?.takeIf { it.isNotEmpty() }
            ?: variable.defaultOptions
    }
}

class PromptDesignerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PromptPreferencesRepository(application.applicationContext)
    private val translationService = TranslationService()
    private val geminiImageRepository = GeminiImageRepository()
    private val geminiTranslationRepository = GeminiTranslationRepository()

    private val _uiState = MutableStateFlow(PromptDesignerUiState())
    val uiState: StateFlow<PromptDesignerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collect { saved ->
                _uiState.update { state ->
                    val validImageCount = if (saved.imageCountOptions.contains(state.selectedImageCount)) {
                        state.selectedImageCount
                    } else {
                        saved.imageCountOptions.firstOrNull()
                            ?: PromptOptionsLibrary.defaultImageCounts.first()
                    }
                    val validFrame = if (saved.frameOptions.contains(state.selectedFrame)) {
                        state.selectedFrame
                    } else {
                        saved.frameOptions.firstOrNull()
                            ?: PromptOptionsLibrary.defaultFrames.first()
                    }
                    state.copy(
                        signatureSlots = saved.signatureSlots,
                        selectedSignatureIndex = saved.selectedSignatureIndex,
                        showSignature = saved.showSignature,
                        showDate = saved.showDate,
                        showFrame = saved.showFrame,
                        imageCountOptions = saved.imageCountOptions,
                        selectedImageCount = validImageCount,
                        frameOptions = saved.frameOptions,
                        selectedFrame = validFrame,
                        customVariableOptionsMap = saved.customVariableOptions
                    )
                }
            }
        }
    }

    // --- Sidebar & GreetingPrompt Selection ---

    fun toggleDimensionExpanded(dimensionId: String) {
        _uiState.update { state ->
            val updated = state.expandedDimensionIds.toMutableSet()
            if (!updated.add(dimensionId)) {
                updated.remove(dimensionId)
            }
            state.copy(expandedDimensionIds = updated)
        }
    }

    fun selectPrompt(prompt: GreetingPrompt) {
        _uiState.update { state ->
            val initialVarSelections = prompt.variables.associate { variable ->
                val opts = state.effectiveOptionsFor(variable)
                variable.name to (opts.firstOrNull() ?: variable.defaultOptions.firstOrNull().orEmpty())
            }
            val expandedDims = state.expandedDimensionIds + prompt.dimensionId
            state.copy(
                selectedPrompt = prompt,
                expandedDimensionIds = expandedDims,
                selectedVariables = initialVarSelections,
                editingVariables = emptySet(),
                customVariableInputs = emptyMap(),
                generatedPrompt = "",
                generatedEnglishPrompt = ""
            )
        }
    }

    fun clearSelectedPrompt() {
        _uiState.update { it.copy(selectedPrompt = null) }
    }

    // --- Signature & General Toggles ---

    fun toggleSignatureExpanded() {
        _uiState.update { it.copy(isSignatureExpanded = !it.isSignatureExpanded) }
    }

    fun setShowSignature(show: Boolean) {
        _uiState.update { it.copy(showSignature = show) }
        persistToggles()
    }

    fun setShowDate(show: Boolean) {
        _uiState.update { it.copy(showDate = show) }
        persistToggles()
    }

    fun setShowFrame(show: Boolean) {
        _uiState.update { it.copy(showFrame = show) }
        persistToggles()
    }

    private fun persistToggles() {
        val s = _uiState.value
        viewModelScope.launch {
            repository.saveToggleSettings(s.showSignature, s.showDate, s.showFrame)
        }
    }

    fun selectSignatureSlot(index: Int) {
        if (index !in 0 until SIGNATURE_SLOT_COUNT) return
        _uiState.update { it.copy(selectedSignatureIndex = index) }
        val s = _uiState.value
        viewModelScope.launch {
            repository.saveSignatures(s.signatureSlots, s.selectedSignatureIndex)
        }
    }

    fun updateSignatureSlotText(index: Int, text: String) {
        if (index !in 0 until SIGNATURE_SLOT_COUNT) return
        _uiState.update { state ->
            val updatedSlots = state.signatureSlots.toMutableList()
            updatedSlots[index] = text
            state.copy(signatureSlots = updatedSlots)
        }
        val s = _uiState.value
        viewModelScope.launch {
            repository.saveSignatures(s.signatureSlots, s.selectedSignatureIndex)
        }
    }

    // --- Image Count Management ---

    fun selectImageCount(value: String) {
        _uiState.update { it.copy(selectedImageCount = value) }
    }

    fun updateCustomImageCountInput(value: String) {
        _uiState.update { it.copy(customImageCountInput = value) }
    }

    fun toggleEditImageCountMode() {
        _uiState.update { it.copy(isEditingImageCount = !it.isEditingImageCount) }
    }

    fun addNewImageCount() {
        val newCount = _uiState.value.customImageCountInput.trim()
        if (newCount.isEmpty()) {
            emitSnackbar("請輸入張數名稱！")
            return
        }
        val current = _uiState.value.imageCountOptions
        if (current.contains(newCount)) {
            _uiState.update { it.copy(selectedImageCount = newCount) }
            emitSnackbar("此張數選項已存在於清單中！")
            return
        }
        val updated = current + newCount
        _uiState.update {
            it.copy(
                imageCountOptions = updated,
                selectedImageCount = newCount,
                customImageCountInput = ""
            )
        }
        viewModelScope.launch { repository.saveImageCountOptions(updated) }
        emitSnackbar("已成功新增至張數選項清單！")
    }

    fun requestDeleteSelectedImageCount() {
        val selected = _uiState.value.selectedImageCount
        if (selected == CUSTOM_OPTION_SENTINEL) {
            emitSnackbar("請先選擇要刪除的張數項目！")
            return
        }
        if (_uiState.value.imageCountOptions.size <= 1) {
            emitSnackbar("至少需保留一個張數選項！")
            return
        }
        _uiState.update {
            it.copy(
                pendingDialog = ConfirmDialogState(
                    title = "確認刪除",
                    message = "確定要將「$selected」從張數清單中永久刪除嗎？",
                    isDestructive = true,
                    onConfirm = {
                        val updated = _uiState.value.imageCountOptions.filterNot { opt -> opt == selected }
                        _uiState.update { s ->
                            s.copy(
                                imageCountOptions = updated,
                                selectedImageCount = updated.firstOrNull() ?: CUSTOM_OPTION_SENTINEL,
                                pendingDialog = null
                            )
                        }
                        viewModelScope.launch { repository.saveImageCountOptions(updated) }
                        emitSnackbar("已刪除「$selected」")
                    }
                )
            )
        }
    }

    fun requestRestoreDefaultImageCounts(keepCustom: Boolean) {
        val message = if (keepCustom) {
            "確定要恢復系統預設張數選項嗎？（您自行新增的選項將會被保留）"
        } else {
            "警告：確定要完全恢復為系統最初預設值嗎？\n(您自行新增的選項將會被永久清除！)"
        }
        _uiState.update {
            it.copy(
                pendingDialog = ConfirmDialogState(
                    title = if (keepCustom) "恢復預設選項" else "警告",
                    message = message,
                    isDestructive = !keepCustom,
                    onConfirm = {
                        val defaults = PromptOptionsLibrary.defaultImageCounts
                        val updated = if (keepCustom) {
                            val custom = _uiState.value.imageCountOptions.filterNot { defaults.contains(it) }
                            (defaults + custom).distinct()
                        } else {
                            defaults
                        }
                        _uiState.update { s ->
                            s.copy(
                                imageCountOptions = updated,
                                selectedImageCount = updated.first(),
                                isEditingImageCount = false,
                                pendingDialog = null
                            )
                        }
                        viewModelScope.launch { repository.saveImageCountOptions(updated) }
                        emitSnackbar(
                            if (keepCustom) "已恢復系統預設清單，並保留您的自訂選項！"
                            else "已完全恢復為系統預設清單！(自訂項目已清除)"
                        )
                    }
                )
            )
        }
    }

    // --- Frame Management ---

    fun selectFrame(value: String) {
        _uiState.update { it.copy(selectedFrame = value) }
    }

    fun updateCustomFrameInput(value: String) {
        _uiState.update { it.copy(customFrameInput = value) }
    }

    fun toggleEditFrameMode() {
        _uiState.update { it.copy(isEditingFrame = !it.isEditingFrame) }
    }

    fun addNewFrame() {
        val newFrame = _uiState.value.customFrameInput.trim()
        if (newFrame.isEmpty()) {
            emitSnackbar("請輸入圖框樣式名稱！")
            return
        }
        val current = _uiState.value.frameOptions
        if (current.contains(newFrame)) {
            _uiState.update { it.copy(selectedFrame = newFrame) }
            emitSnackbar("此圖框已存在於清單中！")
            return
        }
        val updated = current + newFrame
        _uiState.update {
            it.copy(
                frameOptions = updated,
                selectedFrame = newFrame,
                customFrameInput = ""
            )
        }
        viewModelScope.launch { repository.saveFrameOptions(updated) }
        emitSnackbar("已成功新增至圖框選項清單！")
    }

    fun requestDeleteSelectedFrame() {
        val selected = _uiState.value.selectedFrame
        if (selected == CUSTOM_OPTION_SENTINEL) {
            emitSnackbar("請先選擇要刪除的圖框項目！")
            return
        }
        if (_uiState.value.frameOptions.size <= 1) {
            emitSnackbar("至少需保留一個圖框選項！")
            return
        }
        _uiState.update {
            it.copy(
                pendingDialog = ConfirmDialogState(
                    title = "確認刪除",
                    message = "確定要將「$selected」從清單中永久刪除嗎？",
                    isDestructive = true,
                    onConfirm = {
                        val updated = _uiState.value.frameOptions.filterNot { opt -> opt == selected }
                        _uiState.update { s ->
                            s.copy(
                                frameOptions = updated,
                                selectedFrame = updated.firstOrNull() ?: CUSTOM_OPTION_SENTINEL,
                                pendingDialog = null
                            )
                        }
                        viewModelScope.launch { repository.saveFrameOptions(updated) }
                        emitSnackbar("已刪除「$selected」")
                    }
                )
            )
        }
    }

    fun requestRestoreDefaultFrames(keepCustom: Boolean) {
        val message = if (keepCustom) {
            "確定要恢復所有系統預設圖框嗎？（您自行新增的圖框將會被保留）"
        } else {
            "警告：確定要將所有圖框選項完全恢復為系統最初預設值嗎？\n(您自行新增的圖框將會被永久清除！)"
        }
        _uiState.update {
            it.copy(
                pendingDialog = ConfirmDialogState(
                    title = if (keepCustom) "恢復預設圖框" else "警告",
                    message = message,
                    isDestructive = !keepCustom,
                    onConfirm = {
                        val defaults = PromptOptionsLibrary.defaultFrames
                        val updated = if (keepCustom) {
                            val custom = _uiState.value.frameOptions.filterNot { defaults.contains(it) }
                            (defaults + custom).distinct()
                        } else {
                            defaults
                        }
                        _uiState.update { s ->
                            s.copy(
                                frameOptions = updated,
                                selectedFrame = updated.first(),
                                isEditingFrame = false,
                                pendingDialog = null
                            )
                        }
                        viewModelScope.launch { repository.saveFrameOptions(updated) }
                        emitSnackbar(
                            if (keepCustom) "已恢復系統預設清單，並保留您的自訂選項！"
                            else "已完全恢復為系統預設清單！(自訂項目已清除)"
                        )
                    }
                )
            )
        }
    }

    // --- Dynamic Formula Variable Management ---

    fun selectVariableOption(varName: String, value: String) {
        _uiState.update { state ->
            state.copy(selectedVariables = state.selectedVariables + (varName to value))
        }
    }

    fun updateCustomVariableInput(varName: String, text: String) {
        _uiState.update { state ->
            state.copy(customVariableInputs = state.customVariableInputs + (varName to text))
        }
    }

    fun toggleEditVariableMode(varName: String) {
        _uiState.update { state ->
            val updated = state.editingVariables.toMutableSet()
            if (!updated.add(varName)) {
                updated.remove(varName)
            }
            state.copy(editingVariables = updated)
        }
    }

    fun randomizeVariable(variable: FormulaVariable) {
        val opts = _uiState.value.effectiveOptionsFor(variable)
        if (opts.isEmpty()) return
        val current = _uiState.value.selectedVariables[variable.name]
        val candidates = if (opts.size > 1) opts.filterNot { it == current } else opts
        val picked = candidates.random()
        selectVariableOption(variable.name, picked)
    }

    fun randomizeAllVariables() {
        val prompt = _uiState.value.selectedPrompt ?: return
        _uiState.update { state ->
            val randomized = prompt.variables.associate { variable ->
                val opts = state.effectiveOptionsFor(variable)
                variable.name to (opts.randomOrNull() ?: variable.defaultOptions.firstOrNull().orEmpty())
            }
            state.copy(selectedVariables = randomized)
        }
        emitSnackbar("已為所有參數隨機挑選靈感組合！")
    }

    fun addNewVariableOption(variable: FormulaVariable) {
        val rawInput = _uiState.value.customVariableInputs[variable.name]?.trim().orEmpty()
        if (rawInput.isEmpty()) {
            emitSnackbar("請輸入【${variable.name}】的中文名稱！")
            return
        }
        if (_uiState.value.translatingVariables.contains(variable.name)) return

        viewModelScope.launch {
            _uiState.update { it.copy(translatingVariables = it.translatingVariables + variable.name) }
            val formattedVal = try {
                translationService.formatWithAutoTranslation(rawInput)
            } finally {
                _uiState.update { it.copy(translatingVariables = it.translatingVariables - variable.name) }
            }

            val currentOptions = _uiState.value.effectiveOptionsFor(variable)
            if (currentOptions.contains(formattedVal)) {
                _uiState.update { state ->
                    state.copy(
                        selectedVariables = state.selectedVariables + (variable.name to formattedVal)
                    )
                }
                emitSnackbar("此選項已存在於清單中！")
                return@launch
            }

            val updatedOptions = currentOptions + formattedVal
            _uiState.update { state ->
                state.copy(
                    customVariableOptionsMap = state.customVariableOptionsMap + (variable.name to updatedOptions),
                    selectedVariables = state.selectedVariables + (variable.name to formattedVal),
                    customVariableInputs = state.customVariableInputs + (variable.name to "")
                )
            }
            repository.saveVariableOptions(variable.name, updatedOptions)
            emitSnackbar("已成功新增選項：$formattedVal")
        }
    }

    fun requestDeleteSelectedVariableOption(variable: FormulaVariable) {
        val selected = _uiState.value.selectedVariables[variable.name] ?: return
        if (selected == CUSTOM_OPTION_SENTINEL) {
            emitSnackbar("請先選擇要刪除的項目！")
            return
        }
        val currentOptions = _uiState.value.effectiveOptionsFor(variable)
        if (currentOptions.size <= 1) {
            emitSnackbar("至少需保留一個參數選項！")
            return
        }
        _uiState.update {
            it.copy(
                pendingDialog = ConfirmDialogState(
                    title = "確認刪除",
                    message = "確定要將「$selected」從【${variable.name}】清單中永久刪除嗎？",
                    isDestructive = true,
                    onConfirm = {
                        val updated = _uiState.value.effectiveOptionsFor(variable)
                            .filterNot { opt -> opt == selected }
                        _uiState.update { s ->
                            s.copy(
                                customVariableOptionsMap = s.customVariableOptionsMap + (variable.name to updated),
                                selectedVariables = s.selectedVariables + (variable.name to (updated.firstOrNull() ?: CUSTOM_OPTION_SENTINEL)),
                                pendingDialog = null
                            )
                        }
                        viewModelScope.launch { repository.saveVariableOptions(variable.name, updated) }
                        emitSnackbar("已刪除「$selected」")
                    }
                )
            )
        }
    }

    fun requestRestoreDefaultVariable(variable: FormulaVariable, keepCustom: Boolean) {
        val message = if (keepCustom) {
            "確定要恢復【${variable.name}】的系統預設選項嗎？（您自行新增的選項將會被保留）"
        } else {
            "警告：確定要將【${variable.name}】完全恢復為系統最初預設值嗎？\n(您自行新增的選項將會被永久清除！)"
        }
        _uiState.update {
            it.copy(
                pendingDialog = ConfirmDialogState(
                    title = if (keepCustom) "恢復預設選項" else "警告",
                    message = message,
                    isDestructive = !keepCustom,
                    onConfirm = {
                        val defaults = variable.defaultOptions
                        val current = _uiState.value.effectiveOptionsFor(variable)
                        val updated = if (keepCustom) {
                            val custom = current.filterNot { defaults.contains(it) }
                            (defaults + custom).distinct()
                        } else {
                            defaults
                        }
                        _uiState.update { s ->
                            s.copy(
                                customVariableOptionsMap = s.customVariableOptionsMap + (variable.name to updated),
                                selectedVariables = s.selectedVariables + (variable.name to updated.first()),
                                editingVariables = s.editingVariables - variable.name,
                                pendingDialog = null
                            )
                        }
                        viewModelScope.launch { repository.saveVariableOptions(variable.name, updated) }
                        emitSnackbar(
                            if (keepCustom) "已恢復系統預設清單，並保留您的自訂選項！"
                            else "已完全恢復為系統預設清單！(自訂項目已清除)"
                        )
                    }
                )
            )
        }
    }

    // --- Context Supplement ---

    fun selectContextTab(tab: ContextTab) {
        _uiState.update { it.copy(activeContextTab = tab) }
    }

    fun updateContextText(text: String) {
        _uiState.update { it.copy(contextText = text) }
    }

    fun updateContextUrl(url: String) {
        _uiState.update { it.copy(contextUrl = url) }
    }

    fun setAttachedFiles(files: List<AttachedFileItem>) {
        _uiState.update { it.copy(attachedFiles = files) }
    }

    fun clearAttachedFiles() {
        _uiState.update { it.copy(attachedFiles = emptyList()) }
    }

    // --- Prompt Generation & Output ---

    fun generatePrompt() {
        val state = _uiState.value
        val prompt = state.selectedPrompt ?: return

        val effectiveImageCount = if (state.selectedImageCount == CUSTOM_OPTION_SENTINEL) {
            state.customImageCountInput.trim().ifEmpty { "1張" }
        } else {
            state.selectedImageCount
        }

        val effectiveFrame = if (state.selectedFrame == CUSTOM_OPTION_SENTINEL) {
            state.customFrameInput.trim().ifEmpty { "隨機創意圖框" }
        } else {
            state.selectedFrame
        }

        val resolvedVariables = prompt.variables.associate { variable ->
            val rawSelection = state.selectedVariables[variable.name]
            val resolved = if (rawSelection == CUSTOM_OPTION_SENTINEL) {
                state.customVariableInputs[variable.name]?.trim().orEmpty().ifEmpty {
                    state.effectiveOptionsFor(variable).firstOrNull()
                        ?: variable.defaultOptions.firstOrNull().orEmpty()
                }
            } else {
                rawSelection?.takeIf { it.isNotEmpty() }
                    ?: state.effectiveOptionsFor(variable).firstOrNull()
                    ?: variable.defaultOptions.firstOrNull().orEmpty()
            }
            variable.name to resolved
        }

        val result = PromptAssembler.assembleDetailed(
            PromptAssemblyInput(
                prompt = prompt,
                imageCount = effectiveImageCount,
                signatureText = state.activeSignatureDisplay,
                showSignature = state.showSignature,
                showDate = state.showDate,
                formattedDate = state.formattedDate,
                showFrame = state.showFrame,
                frameStyle = effectiveFrame,
                variableValues = resolvedVariables,
                contextText = state.contextText,
                contextUrl = state.contextUrl,
                attachedFileCount = state.attachedFiles.size
            )
        )

        viewModelScope.launch {
            repository.saveSignatures(state.signatureSlots, state.selectedSignatureIndex)
        }

        _uiState.update {
            it.copy(
                generatedPrompt = result.chinesePrompt,
                generatedEnglishPrompt = result.englishPrompt,
                promptTranslationState = GreetingTranslationState.Idle,
                isSignatureExpanded = false,
                promptGenerationCounter = it.promptGenerationCounter + 1
            )
        }
    }

    /**
     * 透過 `firebase-ai` (Gemini 3.8 Flash) 將目前生成的中文問候語提示詞翻譯為自然流暢的英文版本。
     */
    fun translatePromptWithGemini() {
        val state = _uiState.value
        val sourceText = state.generatedPrompt.trim()
        if (sourceText.isEmpty()) {
            emitSnackbar("請先生成或輸入中文問候提示詞！")
            return
        }
        if (state.promptTranslationState is GreetingTranslationState.Loading ||
            state.promptTranslationState is GreetingTranslationState.Streaming
        ) {
            return
        }

        val categoryLabel = state.selectedPrompt?.type?.displayName
        viewModelScope.launch {
            _uiState.update { it.copy(promptTranslationState = GreetingTranslationState.Loading) }
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    geminiTranslationRepository.translateGreetingStream(
                        chineseText = sourceText,
                        greetingTypeLabel = categoryLabel
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        val partial = accumulated.toString()
                        _uiState.update {
                            it.copy(
                                generatedEnglishPrompt = partial,
                                promptTranslationState = GreetingTranslationState.Streaming(partial)
                            )
                        }
                    }
                }
                val finalEnglish = accumulated.toString().trim()
                if (finalEnglish.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            generatedEnglishPrompt = finalEnglish,
                            promptTranslationState = GreetingTranslationState.Success(finalEnglish)
                        )
                    }
                    emitSnackbar("已完成 Gemini 自然流暢英譯！")
                } else {
                    _uiState.update {
                        it.copy(
                            promptTranslationState = GreetingTranslationState.Error(
                                "Gemini 未回傳翻譯內容，請再試一次。"
                            )
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini prompt translation timed out", e)
                _uiState.update {
                    it.copy(
                        promptTranslationState = GreetingTranslationState.Error(
                            "翻譯逾時（超過 60 秒），請稍後重試。"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI prompt translation failed", e)
                _uiState.update {
                    it.copy(
                        promptTranslationState = GreetingTranslationState.Error(
                            e.localizedMessage ?: "Gemini 翻譯失敗，請檢查網路或 Firebase 服務狀態。"
                        )
                    )
                }
            }
        }
    }

    fun updateGeneratedPromptText(text: String) {
        _uiState.update { it.copy(generatedPrompt = text) }
    }

    fun getExportFileName(): String {
        val state = _uiState.value
        val title = state.selectedPrompt?.title ?: "問候"
        return PromptAssembler.buildExportFileName(title, state.formattedDate)
    }

    // --- AI Image Creation & Editing Studio (gemini-nano-banana-2.1) ---

    fun openImageStudio(
        initialPrompt: String? = null,
        mode: ImageStudioMode = ImageStudioMode.CREATE
    ) {
        _uiState.update { state ->
            val prefill = initialPrompt?.takeIf { it.isNotBlank() }
                ?: state.studioPromptInput.ifBlank {
                    state.generatedPrompt.ifBlank { state.generatedEnglishPrompt }
                }
            state.copy(
                isImageStudioOpen = true,
                imageStudioMode = mode,
                studioPromptInput = prefill
            )
        }
    }

    fun closeImageStudio() {
        _uiState.update { it.copy(isImageStudioOpen = false) }
    }

    fun setImageStudioMode(mode: ImageStudioMode) {
        _uiState.update { it.copy(imageStudioMode = mode) }
    }

    fun updateStudioPromptInput(text: String) {
        _uiState.update { it.copy(studioPromptInput = text) }
    }

    fun updateStudioEditInstruction(text: String) {
        _uiState.update { it.copy(studioEditInstruction = text) }
    }

    fun setStudioSourceImage(bitmap: Bitmap, label: String) {
        _uiState.update {
            it.copy(
                studioSourceBitmap = bitmap,
                studioSourceLabel = label,
                imageStudioMode = ImageStudioMode.EDIT
            )
        }
    }

    fun clearStudioSourceImage() {
        _uiState.update {
            it.copy(
                studioSourceBitmap = null,
                studioSourceLabel = ""
            )
        }
    }

    fun useGeneratedImageForEdit() {
        val currentResult = _uiState.value.imageGenUiState
        if (currentResult is ImageGenUiState.Success) {
            _uiState.update {
                it.copy(
                    studioSourceBitmap = currentResult.bitmap,
                    studioSourceLabel = "剛生成的 AI 問候圖",
                    imageStudioMode = ImageStudioMode.EDIT
                )
            }
            emitSnackbar("已載入生成圖片，請輸入修改指令繼續編輯！")
        }
    }

    fun generateImageWithAi() {
        val promptText = _uiState.value.studioPromptInput.trim()
        if (promptText.isEmpty()) {
            emitSnackbar("請先輸入圖片生成提示詞！")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(imageGenUiState = ImageGenUiState.Loading) }
            try {
                val bitmap = withTimeout(60_000L) {
                    geminiImageRepository.generateImage(promptText)
                }
                if (bitmap != null) {
                    _uiState.update {
                        it.copy(imageGenUiState = ImageGenUiState.Success(bitmap, promptText))
                    }
                } else {
                    _uiState.update {
                        it.copy(imageGenUiState = ImageGenUiState.Error("模型未回傳圖片，請調整提示詞後再試一次。"))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.update {
                    it.copy(imageGenUiState = ImageGenUiState.Error("圖片生成逾時（超過 60 秒），請稍後重試。"))
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _uiState.update {
                    it.copy(imageGenUiState = ImageGenUiState.Error("已達模型配額或請求速率上限，請稍後再試（需確認 Firebase Blaze 方案）。"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI image generation failed", e)
                _uiState.update {
                    it.copy(
                        imageGenUiState = ImageGenUiState.Error(
                            e.localizedMessage ?: "圖片生成失敗，請檢查網路或 Firebase 服務狀態。"
                        )
                    )
                }
            }
        }
    }

    fun editImageWithAi() {
        val sourceBitmap = _uiState.value.studioSourceBitmap
        if (sourceBitmap == null) {
            emitSnackbar("請先從相簿挑選或載入一張待編輯圖片！")
            return
        }
        val instruction = _uiState.value.studioEditInstruction.trim()
        if (instruction.isEmpty()) {
            emitSnackbar("請輸入您想要套用的圖片編輯指令！")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(imageGenUiState = ImageGenUiState.Loading) }
            try {
                val bitmap = withTimeout(60_000L) {
                    geminiImageRepository.editImage(sourceBitmap, instruction)
                }
                if (bitmap != null) {
                    _uiState.update {
                        it.copy(imageGenUiState = ImageGenUiState.Success(bitmap, instruction))
                    }
                } else {
                    _uiState.update {
                        it.copy(imageGenUiState = ImageGenUiState.Error("模型未回傳編輯後的圖片，請嘗試更具體的編輯指令。"))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image editing timed out", e)
                _uiState.update {
                    it.copy(imageGenUiState = ImageGenUiState.Error("圖片編輯逾時（超過 60 秒），請稍後重試。"))
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image editing quota exceeded", e)
                _uiState.update {
                    it.copy(imageGenUiState = ImageGenUiState.Error("已達模型配額或請求速率上限，請稍後再試。"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI image editing failed", e)
                _uiState.update {
                    it.copy(
                        imageGenUiState = ImageGenUiState.Error(
                            e.localizedMessage ?: "圖片編輯失敗，請檢查網路或 Firebase 服務狀態。"
                        )
                    )
                }
            }
        }
    }

    // --- Dialog & Snackbar ---

    fun dismissDialog() {
        _uiState.update { it.copy(pendingDialog = null) }
    }

    fun emitSnackbar(message: String) {
        _uiState.update { it.copy(snackbarEvent = SnackbarEvent(message = message)) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarEvent = null) }
    }
}
