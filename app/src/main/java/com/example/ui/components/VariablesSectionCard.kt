package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FormulaVariable
import com.example.data.GreetingPrompt
import com.example.ui.PromptDesignerUiState

@Composable
fun VariablesSectionCard(
    prompt: GreetingPrompt,
    state: PromptDesignerUiState,
    onSelectVariable: (String, String) -> Unit,
    onToggleEditVariable: (String) -> Unit,
    onDeleteVariable: (FormulaVariable) -> Unit,
    onRestoreVariableKeep: (FormulaVariable) -> Unit,
    onRestoreVariableClear: (FormulaVariable) -> Unit,
    onCustomVariableInputChange: (String, String) -> Unit,
    onAddCustomVariable: (FormulaVariable) -> Unit,
    onRandomizeVariable: (FormulaVariable) -> Unit,
    onRandomizeAll: () -> Unit,
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
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.section_variables),
                trailing = {
                    FilledTonalButton(
                        onClick = onRandomizeAll,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("randomize_all_variables_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Casino,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_randomize_all),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                prompt.variables.forEachIndexed { index, variable ->
                    val options = state.effectiveOptionsFor(variable)
                    val selected = state.selectedVariables[variable.name]
                        ?: options.firstOrNull().orEmpty()

                    EditableOptionRow(
                        label = "🔹 ${variable.name} (自訂清單)",
                        tagPrefix = "var_$index",
                        options = options,
                        selectedValue = selected,
                        isEditing = state.editingVariables.contains(variable.name),
                        customInput = state.customVariableInputs[variable.name].orEmpty(),
                        customPlaceholder = stringResource(R.string.placeholder_custom_var),
                        addButtonLabel = stringResource(R.string.btn_add_short),
                        isTranslating = state.translatingVariables.contains(variable.name),
                        showRandomizeButton = true,
                        onSelectValue = { onSelectVariable(variable.name, it) },
                        onToggleEdit = { onToggleEditVariable(variable.name) },
                        onDeleteSelected = { onDeleteVariable(variable) },
                        onRestoreKeepCustom = { onRestoreVariableKeep(variable) },
                        onRestoreClearCustom = { onRestoreVariableClear(variable) },
                        onCustomInputChange = { onCustomVariableInputChange(variable.name, it) },
                        onAddCustom = { onAddCustomVariable(variable) },
                        onRandomize = { onRandomizeVariable(variable) }
                    )

                    if (index < prompt.variables.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
