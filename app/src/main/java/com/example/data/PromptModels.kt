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

data class PromptDimension(
    val id: String,
    val name: String,
    val shortTitle: String,
    val tag: String,
    val type: GreetingPromptType,
    val prompts: List<GreetingPrompt>
)

data class AttachedFileItem(
    val uriString: String,
    val displayName: String
)

const val CUSTOM_OPTION_SENTINEL = "__CUSTOM_ADD_OPTION__"
const val DEFAULT_SIGNATURE = "琦閔~祝福您"
const val SIGNATURE_SLOT_COUNT = 10
