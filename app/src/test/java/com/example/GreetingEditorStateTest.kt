package com.example

import com.example.data.DEFAULT_SIGNATURE
import com.example.data.GreetingPromptType
import com.example.data.MediaTag
import com.example.ui.GreetingEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingEditorStateTest {

    @Test
    fun assembledPrompt_includesTypeTagsPromptDateAndSignature() {
        val state = GreetingEditorUiState(
            selectedType = GreetingPromptType.BIRTHDAY,
            selectedMediaTags = setOf(MediaTag.IMAGE, MediaTag.MUSIC),
            promptText = "溫馨柔和的生日蛋糕與金色蠟燭光芒，祝福心想事成。",
            showSignature = true,
            signatureSlots = List(10) { idx -> if (idx == 0) "小美祝福您" else "" },
            selectedSignatureIndex = 0,
            showDate = true,
            formattedDate = "2026年11月20日"
        )

        val assembled = state.assembledPrompt
        assertTrue(assembled.contains("【生日問候"))
        assertTrue(assembled.contains("#圖文"))
        assertTrue(assembled.contains("#音樂"))
        assertTrue(assembled.contains("溫馨柔和的生日蛋糕與金色蠟燭光芒"))
        assertTrue(assembled.contains("2026年11月20日"))
        assertTrue(assembled.contains("小美祝福您"))
    }

    @Test
    fun assembledPrompt_respectsDisabledSignatureAndDate() {
        val state = GreetingEditorUiState(
            selectedType = GreetingPromptType.MORNING,
            promptText = "清晨花海與暖陽問候",
            showSignature = false,
            showDate = false
        )

        val assembled = state.assembledPrompt
        assertTrue(assembled.contains("清晨花海與暖陽問候"))
        assertFalse(assembled.contains("署名"))
        assertFalse(assembled.contains("日期"))
    }

    @Test
    fun activeSignatureDisplay_fallsBackToDefaultWhenBlank() {
        val state = GreetingEditorUiState(
            signatureSlots = List(10) { "" },
            selectedSignatureIndex = 2
        )

        assertEquals(DEFAULT_SIGNATURE, state.activeSignatureDisplay)
    }

    @Test
    fun toGreetingPrompt_preservesTypeTagsTemplateAndEnglish() {
        val state = GreetingEditorUiState(
            selectedType = GreetingPromptType.VIDEO,
            selectedMediaTags = setOf(MediaTag.VIDEO),
            promptText = "櫻花飄落的早安短影片",
            englishTranslation = "Morning greeting short video with falling cherry blossoms"
        )

        val greetingPrompt = state.toGreetingPrompt()
        assertEquals(GreetingPromptType.VIDEO, greetingPrompt.type)
        assertEquals(listOf(MediaTag.VIDEO), greetingPrompt.mediaTags)
        assertEquals("櫻花飄落的早安短影片", greetingPrompt.template)
        assertEquals(
            "Morning greeting short video with falling cherry blossoms",
            greetingPrompt.englishTranslation
        )
    }
}
