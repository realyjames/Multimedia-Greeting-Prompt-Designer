package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.PromptDesignerUiState

@Composable
fun GeneralSettingsCard(
    state: PromptDesignerUiState,
    onToggleShowSignature: (Boolean) -> Unit,
    onToggleSignatureExpanded: () -> Unit,
    onSelectSignatureSlot: (Int) -> Unit,
    onUpdateSignatureSlot: (Int, String) -> Unit,
    onToggleShowDate: (Boolean) -> Unit,
    onSelectImageCount: (String) -> Unit,
    onToggleEditImageCount: () -> Unit,
    onDeleteImageCount: () -> Unit,
    onRestoreImageCountKeep: () -> Unit,
    onRestoreImageCountClear: () -> Unit,
    onCustomImageCountChange: (String) -> Unit,
    onAddCustomImageCount: () -> Unit,
    onToggleShowFrame: (Boolean) -> Unit,
    onSelectFrame: (String) -> Unit,
    onToggleEditFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onRestoreFrameKeep: () -> Unit,
    onRestoreFrameClear: () -> Unit,
    onCustomFrameChange: (String) -> Unit,
    onAddCustomFrame: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitleHeader(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.section_general_settings)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Signature Setting Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔹 ${stringResource(R.string.label_custom_signature)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_show_signature),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = state.showSignature,
                        onCheckedChange = onToggleShowSignature,
                        modifier = Modifier.testTag("show_signature_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Collapsible 10-slot signature panel
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSignatureExpanded() }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .testTag("signature_dropdown_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "當前選用：${state.activeSignatureDisplay}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (state.isSignatureExpanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = stringResource(R.string.cd_expand_section),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = state.isSignatureExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.signatureSlots.forEachIndexed { index, value ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.selectedSignatureIndex == index,
                                        onClick = { onSelectSignatureSlot(index) },
                                        modifier = Modifier.testTag("sig_radio_$index")
                                    )
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { onUpdateSignatureSlot(index, it) },
                                        placeholder = {
                                            Text(
                                                text = if (index == 0) {
                                                    "欄位 1 (例如: 琦閔~祝福您)"
                                                } else {
                                                    "欄位 ${index + 1}"
                                                },
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("sig_input_$index")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // 2. Date Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔹 ${stringResource(R.string.label_show_date)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "今日：${state.formattedDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.showDate,
                    onCheckedChange = onToggleShowDate,
                    modifier = Modifier.testTag("show_date_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // 3. Image Count Setting Row
            EditableOptionRow(
                label = "🔹 ${stringResource(R.string.label_image_count)}",
                tagPrefix = "image_count",
                options = state.imageCountOptions,
                selectedValue = state.selectedImageCount,
                isEditing = state.isEditingImageCount,
                customInput = state.customImageCountInput,
                customPlaceholder = stringResource(R.string.placeholder_custom_image_count),
                addButtonLabel = stringResource(R.string.btn_add_option),
                helperText = "* ${stringResource(R.string.hint_image_count_anti_grid)}",
                onSelectValue = onSelectImageCount,
                onToggleEdit = onToggleEditImageCount,
                onDeleteSelected = onDeleteImageCount,
                onRestoreKeepCustom = onRestoreImageCountKeep,
                onRestoreClearCustom = onRestoreImageCountClear,
                onCustomInputChange = onCustomImageCountChange,
                onAddCustom = onAddCustomImageCount
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // 4. Frame Setting Row
            EditableOptionRow(
                label = "🔹 ${stringResource(R.string.label_frame_settings)}",
                tagPrefix = "frame",
                options = state.frameOptions,
                selectedValue = state.selectedFrame,
                isEditing = state.isEditingFrame,
                customInput = state.customFrameInput,
                customPlaceholder = stringResource(R.string.placeholder_custom_frame),
                addButtonLabel = stringResource(R.string.btn_add_option),
                headerTrailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.label_enable_frame),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = state.showFrame,
                            onCheckedChange = onToggleShowFrame,
                            modifier = Modifier.testTag("show_frame_switch")
                        )
                    }
                },
                displayFormatter = { index, frame ->
                    if (frame == "隨機創意圖框") {
                        frame
                    } else {
                        val prefix = if (index < 9) "0$index" else "$index"
                        "$prefix. $frame"
                    }
                },
                onSelectValue = onSelectFrame,
                onToggleEdit = onToggleEditFrame,
                onDeleteSelected = onDeleteFrame,
                onRestoreKeepCustom = onRestoreFrameKeep,
                onRestoreClearCustom = onRestoreFrameClear,
                onCustomInputChange = onCustomFrameChange,
                onAddCustom = onAddCustomFrame
            )
        }
    }
}

@Composable
fun SectionTitleHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}
