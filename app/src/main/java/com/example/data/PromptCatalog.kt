package com.example.data

object PromptCatalog {
    val dimensions: List<PromptDimension> = listOf(
        PromptCatalogDaily.morningDimension,
        PromptCatalogDaily.afternoonDimension,
        PromptCatalogDaily.nightDimension,
        PromptCatalogSpecialAndMedia.birthdayDimension,
        PromptCatalogSpecialAndMedia.festivalDimension,
        PromptCatalogSpecialAndMedia.videoDimension,
        PromptCatalogSpecialAndMedia.musicDimension
    )

    val allPrompts: List<GreetingPrompt> = dimensions.flatMap { it.prompts }

    fun promptsByType(type: GreetingPromptType): List<GreetingPrompt> =
        allPrompts.filter { it.type == type }

    fun promptsByMediaTag(tag: MediaTag): List<GreetingPrompt> =
        allPrompts.filter { tag in it.mediaTags }
}
