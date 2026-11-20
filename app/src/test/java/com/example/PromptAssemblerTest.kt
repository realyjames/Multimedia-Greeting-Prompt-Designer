package com.example

import com.example.data.PromptAssembler
import com.example.data.PromptAssemblyInput
import com.example.data.PromptCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    @Test
    fun assemble_singleImage_replacesSignatureDateFrameAndVariables() {
        val formula = PromptCatalog.dimensions.first().formulas.first()
        val variableValues = formula.variables.associate { it.name to it.defaultOptions.first() }

        val result = PromptAssembler.assemble(
            PromptAssemblyInput(
                formula = formula,
                imageCount = "1張",
                signatureText = "小美~早安愉快",
                showSignature = true,
                showDate = true,
                formattedDate = "2026年11月19日",
                showFrame = true,
                frameStyle = "水墨暈染漸層雲霧圖框",
                variableValues = variableValues,
                contextText = "願你今天事事順心",
                contextUrl = "https://example.com/ref",
                attachedFileCount = 2
            )
        )

        assertTrue(result.contains("請為我生成 1 張"))
        assertFalse(result.contains("生成張數與輸出格式強力約束"))
        assertTrue(result.contains("小美~早安愉快"))
        assertTrue(result.contains("2026年11月19日"))
        assertTrue(result.contains("水墨暈染漸層雲霧圖框"))
        assertTrue(result.contains("1/8 尺寸"))
        assertTrue(result.contains("核心內容：願你今天事事順心"))
        assertTrue(result.contains("風格參考：https://example.com/ref"))
        assertTrue(result.contains("需分析上傳之檔案做為排版參考"))
        assertFalse(result.contains("{"))
    }

    @Test
    fun assemble_multiImageAndHiddenSignatureDate_appliesAntiGridAndRemovesSignatureDate() {
        val formula = PromptCatalog.dimensions.first().formulas.first()
        val variableValues = formula.variables.associate { it.name to it.defaultOptions.first() }

        val result = PromptAssembler.assemble(
            PromptAssemblyInput(
                formula = formula,
                imageCount = "4張",
                signatureText = "測試署名",
                showSignature = false,
                showDate = false,
                formattedDate = "2026年11月19日",
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
        assertFalse(result.contains("2026年11月19日"))
        assertFalse(result.contains("【圖框特效排版】"))
    }
}
