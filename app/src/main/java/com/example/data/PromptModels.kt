package com.example.data

enum class ContextTab {
    TEXT,
    URL,
    FILE
}

data class FormulaVariable(
    val name: String,
    val defaultOptions: List<String>
)

data class PromptFormula(
    val id: String,
    val dimensionId: String,
    val name: String,
    val pattern: String,
    val variables: List<FormulaVariable>
)

data class PromptDimension(
    val id: String,
    val name: String,
    val shortTitle: String,
    val tag: String,
    val formulas: List<PromptFormula>
)

data class AttachedFileItem(
    val uriString: String,
    val displayName: String
)

const val CUSTOM_OPTION_SENTINEL = "__CUSTOM_ADD_OPTION__"
const val DEFAULT_SIGNATURE = "琦閔~祝福您"
const val SIGNATURE_SLOT_COUNT = 10
