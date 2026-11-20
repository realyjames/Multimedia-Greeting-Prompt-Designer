package com.example

import com.example.data.GreetingPrompt
import com.example.data.GreetingPromptType
import com.example.data.MediaTag
import com.example.data.PromptAssembler
import com.example.data.PromptAssemblyInput
import com.example.data.PromptCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    @Test
    fun greetingPromptCatalog_containsValidTypesMediaTagsAndEnglishTranslations() {
        val allPrompts: List<GreetingPrompt> = PromptCatalog.allPrompts
        assertEquals(12, allPrompts.size)
        assertTrue(PromptCatalog.promptsByType(GreetingPromptType.MORNING).isNotEmpty())
        assertTrue(PromptCatalog.promptsByType(GreetingPromptType.BIRTHDAY).isNotEmpty())
        assertTrue(PromptCatalog.promptsByMediaTag(MediaTag.VIDEO).isNotEmpty())
        assertTrue(PromptCatalog.promptsByMediaTag(MediaTag.MUSIC).isNotEmpty())
        assertTrue(allPrompts.all { it.template.isNotBlank() && it.englishTranslation.isNotBlank() })
    }

    @Test
    fun assembleDetailed_singleImage_replacesSignatureDateFrameVariablesAndBuildsEnglish() {
        val prompt = PromptCatalog.dimensions.first().prompts.first()
        val variableValues = prompt.variables.associate { it.name to it.defaultOptions.first() }

        val detailed = PromptAssembler.assembleDetailed(
            PromptAssemblyInput(
                prompt = prompt,
                imageCount = "1張",
                signatureText = "小美~早安愉快",
                showSignature = true,
                showDate = true,
                formattedDate = "2026年11月20日",
                showFrame = true,
                frameStyle = "水墨暈染漸層雲霧圖框",
                variableValues = variableValues,
                contextText = "願你今天事事順心",
                contextUrl = "https://example.com/ref",
                attachedFileCount = 2
            )
        )

        val zh = detailed.chinesePrompt
        val en = detailed.englishPrompt

        assertTrue(zh.contains("請為我生成 1 張"))
        assertFalse(zh.contains("生成張數與輸出格式強力約束"))
        assertTrue(zh.contains("小美~早安愉快"))
        assertTrue(zh.contains("2026年11月20日"))
        assertTrue(zh.contains("水墨暈染漸層雲霧圖框"))
        assertTrue(zh.contains("1/8 尺寸"))
        assertTrue(zh.contains("核心內容：願你今天事事順心"))
        assertTrue(zh.contains("風格參考：https://example.com/ref"))
        assertTrue(zh.contains("需分析上傳之檔案做為排版參考"))
        assertFalse(zh.contains("{"))

        assertTrue(en.contains("Good Morning"))
        assertTrue(en.contains("Hyper-realistic photography"))
        assertTrue(en.contains("小美~早安愉快"))
        assertTrue(en.contains("2026年11月20日"))
        assertFalse(en.contains("{"))
    }

    @Test
    fun assemble_multiImageAndHiddenSignatureDate_appliesAntiGridAndRemovesSignatureDate() {
        val prompt = PromptCatalog.dimensions.first().prompts.first()
        val variableValues = prompt.variables.associate { it.name to it.defaultOptions.first() }

        val result = PromptAssembler.assemble(
            PromptAssemblyInput(
                prompt = prompt,
                imageCount = "4張",
                signatureText = "測試署名",
                showSignature = false,
                showDate = false,
                formattedDate = "2026年11月20日",
                showFrame = true,
                frameStyle = "隨機創意圖框",
                variableValues = variableValues,
                contextText = "",
                contextUrl = "",
                attachedFileCount = 0
            )
        )

        assertTrue(result.contains("請為我「分別」且「獨立」生成 4張 "))
        assertTrue(result.contains("【生成張數與輸出格式強力約束】"))
        assertFalse(result.contains("測試署名"))
        assertFalse(result.contains("琦閔"))
        assertFalse(result.contains("2026年11月20日"))
        assertFalse(result.contains("【圖框特效排版】"))
    }
}
