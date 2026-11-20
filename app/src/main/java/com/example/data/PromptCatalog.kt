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

    val allFormulas: List<PromptFormula> = dimensions.flatMap { it.formulas }
}
