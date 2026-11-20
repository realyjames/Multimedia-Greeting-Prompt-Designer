package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CUSTOM_OPTION_SENTINEL

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditableOptionRow(
    label: String,
    tagPrefix: String,
    options: List<String>,
    selectedValue: String,
    isEditing: Boolean,
    customInput: String,
    customPlaceholder: String,
    addButtonLabel: String,
    isTranslating: Boolean = false,
    showRandomizeButton: Boolean = false,
    helperText: String? = null,
    headerTrailingContent: (@Composable () -> Unit)? = null,
    displayFormatter: (Int, String) -> String = { _, value -> value },
    onSelectValue: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRestoreKeepCustom: () -> Unit,
    onRestoreClearCustom: () -> Unit,
    onCustomInputChange: (String) -> Unit,
    onAddCustom: () -> Unit,
    onRandomize: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showPickerSheet by remember { mutableStateOf(false) }

    val selectedDisplayText = remember(selectedValue, options) {
        if (selectedValue == CUSTOM_OPTION_SENTINEL) {
            "✏️ 自行填寫並新增選項..."
        } else {
            val idx = options.indexOf(selectedValue)
            if (idx >= 0) displayFormatter(idx, selectedValue) else selectedValue
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (headerTrailingContent != null) {
                headerTrailingContent()
            } else if (showRandomizeButton && onRandomize != null) {
                IconButton(
                    onClick = onRandomize,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_randomize_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Casino,
                        contentDescription = stringResource(R.string.cd_randomize_var),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Main selector dropdown row + Edit toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPickerSheet = true }
                    .testTag("${tagPrefix}_selector")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedDisplayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onToggleEdit,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}_edit_btn")
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Filled.Close else Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isEditing) stringResource(R.string.btn_cancel) else stringResource(R.string.btn_edit),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Edit mode actions: Delete, Restore (Keep Custom), Restore (Clear Custom)
        AnimatedVisibility(visible = isEditing) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteSelected,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("${tagPrefix}_delete_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_delete_option),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = onRestoreKeepCustom,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("${tagPrefix}_restore_keep_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_restore_keep_custom),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = onRestoreClearCustom,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("${tagPrefix}_restore_clear_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_restore_clear_custom),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Custom Input + Add Button (with optional auto-translation progress)
        AnimatedVisibility(visible = selectedValue == CUSTOM_OPTION_SENTINEL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = onCustomInputChange,
                    placeholder = {
                        Text(
                            text = customPlaceholder,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${tagPrefix}_custom_input")
                )

                FilledTonalButton(
                    onClick = onAddCustom,
                    enabled = !isTranslating,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_add_custom_btn")
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_translating),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = addButtonLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showPickerSheet) {
        OptionPickerSheet(
            title = label.removePrefix("🔹 ").trim(),
            options = options,
            selectedValue = selectedValue,
            displayFormatter = displayFormatter,
            onSelect = onSelectValue,
            onDismiss = { showPickerSheet = false }
        )
    }
}
