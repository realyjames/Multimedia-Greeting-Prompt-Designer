package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AttachedFileItem
import com.example.data.ContextTab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextSupplementCard(
    activeTab: ContextTab,
    contextText: String,
    contextUrl: String,
    attachedFiles: List<AttachedFileItem>,
    onSelectTab: (ContextTab) -> Unit,
    onContextTextChange: (String) -> Unit,
    onContextUrlChange: (String) -> Unit,
    onPickFiles: () -> Unit,
    onClearFiles: () -> Unit,
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
                icon = Icons.Filled.EditNote,
                title = stringResource(R.string.section_context)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Context Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContextTabButton(
                    selected = activeTab == ContextTab.TEXT,
                    label = stringResource(R.string.tab_text),
                    icon = Icons.Filled.TextFields,
                    badge = if (contextText.isNotBlank()) "●" else null,
                    onClick = { onSelectTab(ContextTab.TEXT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("context_tab_text")
                )
                ContextTabButton(
                    selected = activeTab == ContextTab.URL,
                    label = stringResource(R.string.tab_url),
                    icon = Icons.Filled.Link,
                    badge = if (contextUrl.isNotBlank()) "●" else null,
                    onClick = { onSelectTab(ContextTab.URL) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("context_tab_url")
                )
                ContextTabButton(
                    selected = activeTab == ContextTab.FILE,
                    label = stringResource(R.string.tab_file),
                    icon = Icons.Filled.AttachFile,
                    badge = if (attachedFiles.isNotEmpty()) "${attachedFiles.size}" else null,
                    onClick = { onSelectTab(ContextTab.FILE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("context_tab_file")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (activeTab) {
                ContextTab.TEXT -> {
                    OutlinedTextField(
                        value = contextText,
                        onValueChange = onContextTextChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.placeholder_context_text),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("context_text_input")
                    )
                }

                ContextTab.URL -> {
                    OutlinedTextField(
                        value = contextUrl,
                        onValueChange = onContextUrlChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.placeholder_context_url),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("context_url_input")
                    )
                }

                ContextTab.FILE -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onPickFiles,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("context_pick_files_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.btn_pick_files))
                            }

                            if (attachedFiles.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onClearFiles,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("context_clear_files_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ClearAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.btn_clear_files))
                                }
                            }
                        }

                        if (attachedFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                attachedFiles.forEach { file ->
                                    AssistChip(
                                        onClick = {},
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Description,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = file.displayName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "* ${stringResource(R.string.hint_context_file)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextTabButton(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}
