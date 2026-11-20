package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.AttachedFileItem
import com.example.ui.components.AiImageStudioSheet
import com.example.ui.components.AiVideoStudioSheet
import com.example.ui.components.ContextSupplementCard
import com.example.ui.components.GeneralSettingsCard
import com.example.ui.components.GreetingEditorContent
import com.example.ui.components.PromptOutputCard
import com.example.ui.components.SidebarMenuContent
import com.example.ui.components.VariablesSectionCard
import com.example.ui.components.WelcomeLandingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WorkspaceTab {
    LIBRARY,
    GREETING_EDITOR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptDesignerScreen(
    viewModel: PromptDesignerViewModel = viewModel(),
    greetingEditorViewModel: GreetingEditorViewModel = viewModel(),
    aiVideoViewModel: AiVideoViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by greetingEditorViewModel.uiState.collectAsStateWithLifecycle()
    val videoState by aiVideoViewModel.uiState.collectAsStateWithLifecycle()
    var activeTab by rememberSaveable { mutableStateOf(WorkspaceTab.LIBRARY) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // SAF Multi-File Picker (Zero storage permissions required)
    val openFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                AttachedFileItem(
                    uriString = uri.toString(),
                    displayName = queryDisplayName(context, uri)
                )
            }
            viewModel.setAttachedFiles(items)
        }
    }

    // Zero-permission Android Photo Picker for AI Image Editing
    val pickPhotoForEditLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmapFromUri(context, uri, maxSize = 1536)
                }
                if (bitmap != null) {
                    val label = queryDisplayName(context, uri)
                    viewModel.setStudioSourceImage(bitmap, label)
                } else {
                    viewModel.emitSnackbar("無法讀取所選圖片，請選擇其他相片。")
                }
            }
        }
    }

    // SAF Create TXT Document Launcher (Zero storage permissions required)
    val createTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val prompt = state.generatedPrompt
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(prompt.toByteArray(Charsets.UTF_8))
                }
                viewModel.emitSnackbar(context.getString(R.string.toast_saved_txt))
            } catch (_: Exception) {
                viewModel.emitSnackbar(context.getString(R.string.toast_save_txt_failed))
            }
        }
    }

    // SAF Create PNG Image Launcher (Zero storage permissions required)
    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val resultState = state.imageGenUiState
        if (uri != null && resultState is ImageGenUiState.Success) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            resultState.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } ?: false
                    } catch (_: Exception) {
                        false
                    }
                }
                viewModel.emitSnackbar(
                    context.getString(
                        if (saved) R.string.toast_image_saved else R.string.toast_image_save_failed
                    )
                )
            }
        }
    }

    // SAF Create MP4 Video Launcher (Zero storage permissions required)
    val createMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri: Uri? ->
        val resultState = videoState.videoGenUiState
        if (uri != null && resultState is VideoGenUiState.Success) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            resultState.videoFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                            true
                        } ?: false
                    } catch (_: Exception) {
                        false
                    }
                }
                viewModel.emitSnackbar(
                    context.getString(
                        if (saved) R.string.toast_video_saved else R.string.toast_video_save_failed
                    )
                )
            }
        }
    }

    // Snackbar feedback observer
    LaunchedEffect(state.snackbarEvent) {
        val event = state.snackbarEvent ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(event.message)
        viewModel.consumeSnackbar()
    }

    // Smooth scroll to generated prompt output whenever Generate is tapped
    LaunchedEffect(state.promptGenerationCounter) {
        if (state.promptGenerationCounter > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // BackHandler for drawer, greeting editor tab, or returning from prompt editor to welcome hub
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && activeTab == WorkspaceTab.GREETING_EDITOR) {
        activeTab = WorkspaceTab.LIBRARY
    }
    BackHandler(
        enabled = !drawerState.isOpen &&
            activeTab == WorkspaceTab.LIBRARY &&
            state.selectedPrompt != null
    ) {
        viewModel.clearSelectedPrompt()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 840.dp

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            if (!isExpandedScreen) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = stringResource(R.string.cd_open_menu)
                                    )
                                }
                            } else if (activeTab == WorkspaceTab.GREETING_EDITOR) {
                                IconButton(
                                    onClick = { activeTab = WorkspaceTab.LIBRARY },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("back_from_editor_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回總覽"
                                    )
                                }
                            } else if (state.selectedPrompt != null) {
                                IconButton(
                                    onClick = { viewModel.clearSelectedPrompt() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("back_to_hub_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回總覽"
                                    )
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.topbar_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeTab == WorkspaceTab.GREETING_EDITOR) {
                                    Text(
                                        text = "${editorState.selectedType.badgeLabel} · ${stringResource(R.string.editor_screen_title)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    state.selectedPrompt?.let { prompt ->
                                        Text(
                                            text = "${prompt.type.displayName} · ${prompt.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = { viewModel.openImageStudio() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_ai_studio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Brush,
                                    contentDescription = stringResource(R.string.cd_open_ai_studio),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { aiVideoViewModel.openStudio() },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_ai_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MovieCreation,
                                    contentDescription = stringResource(R.string.cd_open_ai_video_studio),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            val isLibrary = activeTab == WorkspaceTab.LIBRARY
                            NavigationBarItem(
                                selected = isLibrary,
                                onClick = {
                                    activeTab = WorkspaceTab.LIBRARY
                                    scope.launch { scrollState.scrollTo(0) }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isLibrary) {
                                            Icons.Filled.AutoStories
                                        } else {
                                            Icons.Outlined.AutoStories
                                        },
                                        contentDescription = stringResource(R.string.nav_library)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_library)) },
                                modifier = Modifier.testTag("nav_tab_library")
                            )
                            val isEditor = activeTab == WorkspaceTab.GREETING_EDITOR
                            NavigationBarItem(
                                selected = isEditor,
                                onClick = {
                                    activeTab = WorkspaceTab.GREETING_EDITOR
                                    scope.launch { scrollState.scrollTo(0) }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isEditor) {
                                            Icons.Filled.EditNote
                                        } else {
                                            Icons.Outlined.EditNote
                                        },
                                        contentDescription = stringResource(R.string.nav_greeting_editor)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_greeting_editor)) },
                                modifier = Modifier.testTag("nav_tab_greeting_editor")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        val activePrompt = state.selectedPrompt
                        if (activeTab == WorkspaceTab.GREETING_EDITOR) {
                            GreetingEditorContent(
                                state = editorState,
                                onSelectType = greetingEditorViewModel::selectType,
                                onToggleMediaTag = greetingEditorViewModel::toggleMediaTag,
                                onApplyPreset = { preset ->
                                    greetingEditorViewModel.applyPresetTemplate(preset)
                                    viewModel.emitSnackbar(context.getString(R.string.toast_editor_preset_loaded))
                                },
                                onPromptTextChange = greetingEditorViewModel::updatePromptText,
                                onEnglishTranslationChange = greetingEditorViewModel::updateEnglishTranslation,
                                onAutoTranslateClick = {
                                    greetingEditorViewModel.translateGreetingWithGemini { success ->
                                        if (success) {
                                            viewModel.emitSnackbar(context.getString(R.string.toast_gemini_translated))
                                        }
                                    }
                                },
                                onToggleShowSignature = greetingEditorViewModel::setShowSignature,
                                onToggleShowDate = greetingEditorViewModel::setShowDate,
                                onSelectSignatureSlot = greetingEditorViewModel::selectSignatureSlot,
                                onActiveSignatureTextChange = greetingEditorViewModel::updateActiveSignatureText,
                                onCopyClick = {
                                    val assembled = editorState.assembledPrompt
                                    if (assembled.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_editor_empty))
                                    } else {
                                        copyToClipboard(context, assembled)
                                        viewModel.emitSnackbar(context.getString(R.string.toast_editor_copied))
                                    }
                                },
                                onShareClick = {
                                    val assembled = editorState.assembledPrompt
                                    if (assembled.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_editor_empty))
                                    } else {
                                        sharePromptText(context, assembled)
                                    }
                                },
                                onClearDraftClick = {
                                    greetingEditorViewModel.clearDraft()
                                    viewModel.emitSnackbar(context.getString(R.string.toast_editor_cleared))
                                },
                                onGenerateAiImageClick = {
                                    val assembled = editorState.assembledPrompt
                                    if (assembled.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_editor_empty))
                                    } else {
                                        viewModel.openImageStudio(
                                            initialPrompt = assembled,
                                            mode = ImageStudioMode.CREATE
                                        )
                                    }
                                },
                                onGenerateAiVideoClick = {
                                    val assembled = editorState.assembledPrompt
                                    if (assembled.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_editor_empty))
                                    } else {
                                        aiVideoViewModel.openStudio(
                                            initialPrompt = editorState.englishTranslation.ifBlank { assembled }
                                        )
                                    }
                                }
                            )
                        } else if (activePrompt == null) {
                            WelcomeLandingView(
                                dimensions = state.dimensions,
                                formattedDate = state.formattedDate,
                                showOpenDrawerButton = !isExpandedScreen,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenAiStudio = { viewModel.openImageStudio() },
                                onOpenAiVideoStudio = { aiVideoViewModel.openStudio() },
                                onOpenGreetingEditor = {
                                    activeTab = WorkspaceTab.GREETING_EDITOR
                                    scope.launch { scrollState.scrollTo(0) }
                                },
                                onSelectPrompt = { prompt ->
                                    activeTab = WorkspaceTab.LIBRARY
                                    viewModel.selectPrompt(prompt)
                                    scope.launch { scrollState.scrollTo(0) }
                                }
                            )
                        } else {
                            // Active GreetingPrompt Header Banner (Type + Media Tags)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = activePrompt.type.badgeLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    activePrompt.mediaTags.forEach { tag ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "#${tag.displayName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activePrompt.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("active_formula_title")
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(
                                    thickness = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 1. General Layout Settings Card
                            GeneralSettingsCard(
                                state = state,
                                onToggleShowSignature = viewModel::setShowSignature,
                                onToggleSignatureExpanded = viewModel::toggleSignatureExpanded,
                                onSelectSignatureSlot = viewModel::selectSignatureSlot,
                                onUpdateSignatureSlot = viewModel::updateSignatureSlotText,
                                onToggleShowDate = viewModel::setShowDate,
                                onSelectImageCount = viewModel::selectImageCount,
                                onToggleEditImageCount = viewModel::toggleEditImageCountMode,
                                onDeleteImageCount = viewModel::requestDeleteSelectedImageCount,
                                onRestoreImageCountKeep = { viewModel.requestRestoreDefaultImageCounts(true) },
                                onRestoreImageCountClear = { viewModel.requestRestoreDefaultImageCounts(false) },
                                onCustomImageCountChange = viewModel::updateCustomImageCountInput,
                                onAddCustomImageCount = viewModel::addNewImageCount,
                                onToggleShowFrame = viewModel::setShowFrame,
                                onSelectFrame = viewModel::selectFrame,
                                onToggleEditFrame = viewModel::toggleEditFrameMode,
                                onDeleteFrame = viewModel::requestDeleteSelectedFrame,
                                onRestoreFrameKeep = { viewModel.requestRestoreDefaultFrames(true) },
                                onRestoreFrameClear = { viewModel.requestRestoreDefaultFrames(false) },
                                onCustomFrameChange = viewModel::updateCustomFrameInput,
                                onAddCustomFrame = viewModel::addNewFrame
                            )

                            // 2. Massive Parameter Settings Card
                            if (activePrompt.variables.isNotEmpty()) {
                                VariablesSectionCard(
                                    prompt = activePrompt,
                                    state = state,
                                    onSelectVariable = viewModel::selectVariableOption,
                                    onToggleEditVariable = viewModel::toggleEditVariableMode,
                                    onDeleteVariable = viewModel::requestDeleteSelectedVariableOption,
                                    onRestoreVariableKeep = { viewModel.requestRestoreDefaultVariable(it, true) },
                                    onRestoreVariableClear = { viewModel.requestRestoreDefaultVariable(it, false) },
                                    onCustomVariableInputChange = viewModel::updateCustomVariableInput,
                                    onAddCustomVariable = viewModel::addNewVariableOption,
                                    onRandomizeVariable = viewModel::randomizeVariable,
                                    onRandomizeAll = viewModel::randomizeAllVariables
                                )
                            }

                            // 3. Context & Quote Supplement Card
                            ContextSupplementCard(
                                activeTab = state.activeContextTab,
                                contextText = state.contextText,
                                contextUrl = state.contextUrl,
                                attachedFiles = state.attachedFiles,
                                onSelectTab = viewModel::selectContextTab,
                                onContextTextChange = viewModel::updateContextText,
                                onContextUrlChange = viewModel::updateContextUrl,
                                onPickFiles = { openFilesLauncher.launch(arrayOf("*/*")) },
                                onClearFiles = viewModel::clearAttachedFiles
                            )

                            // 4. Final Prompt Output Card (Chinese + Gemini English Translation + AI Media CTAs)
                            PromptOutputCard(
                                generatedPrompt = state.generatedPrompt,
                                generatedEnglishPrompt = state.generatedEnglishPrompt,
                                translationState = state.promptTranslationState,
                                formattedDate = state.formattedDate,
                                onGenerateClick = viewModel::generatePrompt,
                                onPromptChange = viewModel::updateGeneratedPromptText,
                                onGeminiTranslateClick = viewModel::translatePromptWithGemini,
                                onCopyClick = {
                                    if (state.generatedPrompt.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_generate_first))
                                    } else {
                                        copyToClipboard(context, state.generatedPrompt)
                                        viewModel.emitSnackbar(context.getString(R.string.toast_prompt_copied))
                                    }
                                },
                                onCopyEnglishClick = {
                                    if (state.generatedEnglishPrompt.isNotBlank()) {
                                        copyToClipboard(context, state.generatedEnglishPrompt)
                                        viewModel.emitSnackbar(context.getString(R.string.toast_english_copied))
                                    }
                                },
                                onDownloadClick = {
                                    if (state.generatedPrompt.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_generate_first))
                                    } else {
                                        createTxtLauncher.launch(viewModel.getExportFileName())
                                    }
                                },
                                onShareClick = {
                                    if (state.generatedPrompt.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_generate_first))
                                    } else {
                                        sharePromptText(context, state.generatedPrompt)
                                    }
                                },
                                onGenerateAiImageClick = {
                                    if (state.generatedPrompt.isBlank()) {
                                        viewModel.generatePrompt()
                                    }
                                    viewModel.openImageStudio(
                                        initialPrompt = null,
                                        mode = ImageStudioMode.CREATE
                                    )
                                },
                                onGenerateAiVideoClick = {
                                    if (state.generatedPrompt.isBlank()) {
                                        viewModel.generatePrompt()
                                    }
                                    val fresh = viewModel.uiState.value
                                    val videoPrompt = fresh.generatedEnglishPrompt
                                        .ifBlank { fresh.generatedPrompt }
                                    aiVideoViewModel.openStudio(initialPrompt = videoPrompt)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        if (isExpandedScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(300.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        SidebarMenuContent(
                            dimensions = state.dimensions,
                            expandedDimensionIds = state.expandedDimensionIds,
                            selectedPromptId = if (activeTab == WorkspaceTab.LIBRARY) state.selectedPrompt?.id else null,
                            isGreetingEditorActive = activeTab == WorkspaceTab.GREETING_EDITOR,
                            showCloseButton = false,
                            onCloseDrawer = {},
                            onOpenGreetingEditor = {
                                activeTab = WorkspaceTab.GREETING_EDITOR
                                scope.launch { scrollState.scrollTo(0) }
                            },
                            onToggleDimension = viewModel::toggleDimensionExpanded,
                            onSelectPrompt = { prompt ->
                                activeTab = WorkspaceTab.LIBRARY
                                viewModel.selectPrompt(prompt)
                                scope.launch { scrollState.scrollTo(0) }
                            }
                        )
                    }
                },
                content = mainScaffold
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.widthIn(max = 310.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        SidebarMenuContent(
                            dimensions = state.dimensions,
                            expandedDimensionIds = state.expandedDimensionIds,
                            selectedPromptId = if (activeTab == WorkspaceTab.LIBRARY) state.selectedPrompt?.id else null,
                            isGreetingEditorActive = activeTab == WorkspaceTab.GREETING_EDITOR,
                            showCloseButton = true,
                            onCloseDrawer = { scope.launch { drawerState.close() } },
                            onOpenGreetingEditor = {
                                activeTab = WorkspaceTab.GREETING_EDITOR
                                scope.launch {
                                    drawerState.close()
                                    scrollState.scrollTo(0)
                                }
                            },
                            onSelectPrompt = { prompt ->
                                activeTab = WorkspaceTab.LIBRARY
                                viewModel.selectPrompt(prompt)
                                scope.launch {
                                    drawerState.close()
                                    scrollState.scrollTo(0)
                                }
                            },
                            onToggleDimension = viewModel::toggleDimensionExpanded
                        )
                    }
                },
                content = mainScaffold
            )
        }
    }

    // AI Image Creation & Editing Studio Sheet (gemini-nano-banana-2.1)
    if (state.isImageStudioOpen) {
        AiImageStudioSheet(
            mode = state.imageStudioMode,
            promptInput = state.studioPromptInput,
            editInstruction = state.studioEditInstruction,
            sourceBitmap = state.studioSourceBitmap,
            sourceLabel = state.studioSourceLabel,
            hasAssembledChinesePrompt = state.generatedPrompt.isNotBlank(),
            hasAssembledEnglishPrompt = state.generatedEnglishPrompt.isNotBlank(),
            imageGenUiState = state.imageGenUiState,
            onSelectMode = viewModel::setImageStudioMode,
            onPromptInputChange = viewModel::updateStudioPromptInput,
            onEditInstructionChange = viewModel::updateStudioEditInstruction,
            onLoadChinesePrompt = {
                viewModel.updateStudioPromptInput(state.generatedPrompt)
            },
            onLoadEnglishPrompt = {
                viewModel.updateStudioPromptInput(state.generatedEnglishPrompt)
            },
            onPickSourcePhoto = {
                pickPhotoForEditLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearSourcePhoto = viewModel::clearStudioSourceImage,
            onGenerateClick = viewModel::generateImageWithAi,
            onEditClick = viewModel::editImageWithAi,
            onContinueEditingResult = viewModel::useGeneratedImageForEdit,
            onSavePngClick = {
                val fileName = "AI問候圖_${state.formattedDate}.png"
                createPngLauncher.launch(fileName)
            },
            onDismiss = viewModel::closeImageStudio
        )
    }

    // AI Video Generation Studio Sheet (gemini-omni-1.2-flash via Interactions API)
    if (videoState.isOpen) {
        val currentZhPrompt = if (activeTab == WorkspaceTab.GREETING_EDITOR) {
            editorState.assembledPrompt
        } else {
            state.generatedPrompt
        }
        val currentEnPrompt = if (activeTab == WorkspaceTab.GREETING_EDITOR) {
            editorState.englishTranslation
        } else {
            state.generatedEnglishPrompt
        }
        AiVideoStudioSheet(
            promptInput = videoState.promptInput,
            selectedAspectRatio = videoState.selectedAspectRatio,
            hasChinesePrompt = currentZhPrompt.isNotBlank(),
            hasEnglishPrompt = currentEnPrompt.isNotBlank(),
            videoGenUiState = videoState.videoGenUiState,
            onPromptInputChange = aiVideoViewModel::updatePromptInput,
            onSelectAspectRatio = aiVideoViewModel::selectAspectRatio,
            onLoadChinesePrompt = { aiVideoViewModel.updatePromptInput(currentZhPrompt) },
            onLoadEnglishPrompt = { aiVideoViewModel.updatePromptInput(currentEnPrompt) },
            onGenerateClick = {
                aiVideoViewModel.generateVideo(
                    onMissingPrompt = {
                        viewModel.emitSnackbar("請先輸入影片生成提示詞！")
                    }
                )
            },
            onSaveMp4Click = {
                val ratioSuffix = videoState.selectedAspectRatio.apiValue.replace(":", "x")
                val fileName = "AI問候影片_${ratioSuffix}_${state.formattedDate}.mp4"
                createMp4Launcher.launch(fileName)
            },
            onDismiss = aiVideoViewModel::closeStudio
        )
    }

    // Confirmation Dialog for Delete / Restore actions
    state.pendingDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = {
                Text(
                    text = dialog.title,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = dialog.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = dialog.onConfirm,
                    colors = if (dialog.isDestructive) {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.testTag("dialog_confirm_button")
                ) {
                    Text(
                        text = stringResource(R.string.dialog_confirm_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDialog,
                    modifier = Modifier.testTag("dialog_cancel_button")
                ) {
                    Text(text = stringResource(R.string.dialog_cancel_btn))
                }
            }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI Greeting Prompt", text)
    clipboard.setPrimaryClip(clip)
}

private fun sharePromptText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, "分享高階提示詞")
    context.startActivity(chooser)
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: uri.lastPathSegment ?: "參考檔案"
            }
        }
    return uri.lastPathSegment ?: "參考檔案"
}

private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxSize: Int): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        var inSampleSize = 1
        while (origW / inSampleSize > maxSize * 2 || origH / inSampleSize > maxSize * 2) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (_: Exception) {
        null
    }
}
