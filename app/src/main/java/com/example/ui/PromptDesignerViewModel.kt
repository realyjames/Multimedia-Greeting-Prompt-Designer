package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttachedFileItem
import com.example.data.CUSTOM_OPTION_SENTINEL
import com.example.data.ContextTab
import com.example.data.DEFAULT_SIGNATURE
import com.example.data.FormulaVariable
import com.example.data.PromptAssembler
import com.example.data.PromptAssemblyInput
import com.example.data.PromptCatalog
import com.example.data.PromptDimension
import com.example.data.PromptFormula
import com.example.data.PromptOptionsLibrary
import com.example.data.PromptPreferencesRepository
import com.example.data.SIGNATURE_SLOT_COUNT
import com.example.data.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val selectedFormula: PromptFormula? = null,

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

    // Output
    val generatedPrompt: String = "",
    val promptGenerationCounter: Int = 0,

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

    private val _uiState = MutableStateFlow(PromptDesignerUiState())
    val uiState: StateFlow<PromptDesignerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.preferencesFlow.first()
            _uiState.update { state ->
                val validImageCount = saved.imageCountOptions.firstOrNull()
                    ?: PromptOptionsLibrary.defaultImageCounts.first()
                val validFrame = saved.frameOptions.firstOrNull()
                    ?: PromptOptionsLibrary.defaultFrames.first()
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

    // --- Sidebar & Formula Selection ---

    fun toggleDimensionExpanded(dimensionId: String) {
        _uiState.update { state ->
            val updated = state.expandedDimensionIds.toMutableSet()
            if (!updated.add(dimensionId)) {
                updated.remove(dimensionId)
            }
            state.copy(expandedDimensionIds = updated)
        }
    }

    fun selectFormula(formula: PromptFormula) {
        _uiState.update { state ->
            val initialVarSelections = formula.variables.associate { variable ->
                val opts = state.effectiveOptionsFor(variable)
                variable.name to (opts.firstOrNull() ?: variable.defaultOptions.firstOrNull().orEmpty())
            }
            val expandedDims = state.expandedDimensionIds + formula.dimensionId
            state.copy(
                selectedFormula = formula,
                expandedDimensionIds = expandedDims,
                selectedVariables = initialVarSelections,
                editingVariables = emptySet(),
                customVariableInputs = emptyMap(),
                generatedPrompt = ""
            )
        }
    }

    fun clearSelectedFormula() {
        _uiState.update { it.copy(selectedFormula = null) }
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
        val formula = _uiState.value.selectedFormula ?: return
        _uiState.update { state ->
            val randomized = formula.variables.associate { variable ->
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
        val formula = state.selectedFormula ?: return

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

        val resolvedVariables = formula.variables.associate { variable ->
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

        val assembled = PromptAssembler.assemble(
            PromptAssemblyInput(
                formula = formula,
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
                generatedPrompt = assembled,
                isSignatureExpanded = false,
                promptGenerationCounter = it.promptGenerationCounter + 1
            )
        }
    }

    fun updateGeneratedPromptText(text: String) {
        _uiState.update { it.copy(generatedPrompt = text) }
    }

    fun getExportFileName(): String {
        val state = _uiState.value
        val formulaName = state.selectedFormula?.name ?: "問候"
        return PromptAssembler.buildExportFileName(formulaName, state.formattedDate)
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
