package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.components.ContextSupplementCard
import com.example.ui.components.GeneralSettingsCard
import com.example.ui.components.PromptOutputCard
import com.example.ui.components.SidebarMenuContent
import com.example.ui.components.VariablesSectionCard
import com.example.ui.components.WelcomeLandingView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptDesignerScreen(
    viewModel: PromptDesignerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    // BackHandler for drawer or returning from formula editor to welcome hub
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && state.selectedFormula != null) {
        viewModel.clearSelectedFormula()
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
                            } else if (state.selectedFormula != null) {
                                IconButton(
                                    onClick = { viewModel.clearSelectedFormula() },
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
                                state.selectedFormula?.let { formula ->
                                    Text(
                                        text = formula.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    )
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
                        val activeFormula = state.selectedFormula
                        if (activeFormula == null) {
                            WelcomeLandingView(
                                dimensions = state.dimensions,
                                formattedDate = state.formattedDate,
                                showOpenDrawerButton = !isExpandedScreen,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onSelectFormula = { formula ->
                                    viewModel.selectFormula(formula)
                                    scope.launch { scrollState.scrollTo(0) }
                                }
                            )
                        } else {
                            // Active Formula Header Banner
                            val parentDimension = remember(activeFormula, state.dimensions) {
                                state.dimensions.firstOrNull { it.id == activeFormula.dimensionId }
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (parentDimension != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = parentDimension.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activeFormula.name,
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
                            if (activeFormula.variables.isNotEmpty()) {
                                VariablesSectionCard(
                                    formula = activeFormula,
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

                            // 4. Final Prompt Output Card
                            PromptOutputCard(
                                generatedPrompt = state.generatedPrompt,
                                formattedDate = state.formattedDate,
                                onGenerateClick = viewModel::generatePrompt,
                                onPromptChange = viewModel::updateGeneratedPromptText,
                                onCopyClick = {
                                    if (state.generatedPrompt.isBlank()) {
                                        viewModel.emitSnackbar(context.getString(R.string.toast_generate_first))
                                    } else {
                                        copyToClipboard(context, state.generatedPrompt)
                                        viewModel.emitSnackbar(context.getString(R.string.toast_prompt_copied))
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
                            selectedFormulaId = state.selectedFormula?.id,
                            showCloseButton = false,
                            onCloseDrawer = {},
                            onToggleDimension = viewModel::toggleDimensionExpanded,
                            onSelectFormula = { formula ->
                                viewModel.selectFormula(formula)
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
                            selectedFormulaId = state.selectedFormula?.id,
                            showCloseButton = true,
                            onCloseDrawer = { scope.launch { drawerState.close() } },
                            onToggleDimension = viewModel::toggleDimensionExpanded,
                            onSelectFormula = { formula ->
                                viewModel.selectFormula(formula)
                                scope.launch {
                                    drawerState.close()
                                    scrollState.scrollTo(0)
                                }
                            }
                        )
                    }
                },
                content = mainScaffold
            )
        }
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
