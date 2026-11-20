package com.example.data

import java.util.Calendar

data class PromptAssemblyInput(
    val prompt: GreetingPrompt,
    val imageCount: String,
    val signatureText: String,
    val showSignature: Boolean,
    val showDate: Boolean,
    val formattedDate: String,
    val showFrame: Boolean,
    val frameStyle: String,
    val variableValues: Map<String, String>,
    val contextText: String,
    val contextUrl: String,
    val attachedFileCount: Int
)

data class AssembledPromptResult(
    val chinesePrompt: String,
    val englishPrompt: String
)

object PromptAssembler {

    fun formatTodayDate(calendar: Calendar = Calendar.getInstance()): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "${year}年${month}月${day}日"
    }

    fun buildExportFileName(promptTitle: String, formattedDate: String): String {
        val timePrefix = when {
            promptTitle.contains("早安") || promptTitle.contains("晨間") -> "早安"
            promptTitle.contains("午安") || promptTitle.contains("午後") || promptTitle.contains("下午茶") -> "午安"
            promptTitle.contains("晚安") || promptTitle.contains("夜景") || promptTitle.contains("助眠") -> "晚安"
            else -> "問候"
        }
        return "AI${timePrefix}提示詞_${formattedDate}.txt"
    }

    fun assembleDetailed(input: PromptAssemblyInput): AssembledPromptResult {
        val zh = assemble(input)
        val en = assembleEnglish(input)
        return AssembledPromptResult(chinesePrompt = zh, englishPrompt = en)
    }

    fun assemble(input: PromptAssemblyInput): String {
        var finalPrompt = input.prompt.template

        // 1. Image count & anti-grid multi-image enforcement
        val imageCountStyle = input.imageCount.trim().ifEmpty { "1張" }
        if (imageCountStyle == "1張" || imageCountStyle == "1") {
            finalPrompt = finalPrompt.replace("請為我生成一張", "請為我生成 1 張")
        } else {
            finalPrompt = finalPrompt.replace(
                "請為我生成一張",
                "請為我「分別」且「獨立」生成 $imageCountStyle "
            )
            val antiGridInstruction =
                "\n\n【生成張數與輸出格式強力約束】\n👉 請務必「分別」生成 $imageCountStyle 獨立的圖片。嚴禁將這 $imageCountStyle 圖片拼貼、組合或分割成單一圖片（絕對禁止九宮格、四宮格、左右對切等多宮格形式）。每一張產出的結果都必須是單獨、完整的圖檔。"

            finalPrompt = insertAfterTextLayoutSection(finalPrompt, antiGridInstruction)
        }

        // 2. Signature handling
        val effectiveSignature = input.signatureText.trim().ifEmpty { DEFAULT_SIGNATURE }
        if (input.showSignature) {
            finalPrompt = finalPrompt.replace(
                Regex("琦閔~祝福您|琦閔"),
                Regex.escapeReplacement(effectiveSignature)
            )
        } else {
            finalPrompt = finalPrompt.replace(
                Regex("\n2\\. 圖片右下方必須顯示創意字體.*?的署名：「琦閔~祝福您」。"),
                ""
            )
            finalPrompt = finalPrompt.replace("影片右下角常駐浮水印：「琦閔」以及", "影片右下角常駐顯示")
            finalPrompt = finalPrompt.replace("影片右下角常駐浮水印：「琦閔」", "")
            finalPrompt = finalPrompt.replace(
                "\n結尾的 [Outro] 歌詞必須包含專屬署名：「願你擁有美好的一刻，這裡是琦閔的祝福。」",
                ""
            )
            finalPrompt = finalPrompt.replace("署名正下方清楚顯示今日日期", "圖片右下方清楚顯示今日日期")
        }

        // 3. Date handling
        if (input.showDate) {
            finalPrompt = finalPrompt.replace("{今日日期}", input.formattedDate)
        } else {
            finalPrompt = finalPrompt.replace("\n3. 署名正下方清楚顯示今日日期：{今日日期}。", "")
            finalPrompt = finalPrompt.replace("\n3. 圖片右下方清楚顯示今日日期：{今日日期}。", "")
            finalPrompt = finalPrompt.replace("以及今日日期：{今日日期}", "")
            finalPrompt = finalPrompt.replace("影片右下角常駐顯示今日日期：{今日日期}。", "")
        }

        // 4. Frame instruction handling
        if (input.showFrame && (input.showSignature || input.showDate)) {
            val frameStyle = input.frameStyle.trim().ifEmpty { "隨機創意圖框" }
            val frameInstruction =
                "\n👉 【圖框特效排版】：請將上述署名與日期完美整合設計於一個「${frameStyle}」之中，並強制設定該圖框大小為佔整體圖片空間版面的 1/8 尺寸，確保字體清晰且與整體視覺風格無縫融合。"

            if (finalPrompt.contains("【文字與排版設定】") || finalPrompt.contains("【文字與後製指示 (若工具支援)】")) {
                finalPrompt = insertAfterTextLayoutSection(finalPrompt, frameInstruction)
            } else {
                finalPrompt += "\n\n【圖框特效排版】\n請將署名與日期完美整合設計於一個「${frameStyle}」之中，並設定為整體版面 1/8 的尺寸。"
            }
        }

        // 5. Formula variable replacements
        input.prompt.variables.forEach { variable ->
            val value = input.variableValues[variable.name]?.trim().orEmpty()
                .ifEmpty { variable.defaultOptions.firstOrNull().orEmpty() }
            finalPrompt = finalPrompt.replace("{${variable.name}}", value)
        }

        // 6. Optional context supplement
        val trimmedText = input.contextText.trim()
        val trimmedUrl = input.contextUrl.trim()
        if (trimmedText.isNotEmpty() || trimmedUrl.isNotEmpty() || input.attachedFileCount > 0) {
            val contextBuilder = StringBuilder("\n\n【附加情境與金句指定】\n")
            if (trimmedText.isNotEmpty()) {
                contextBuilder.append("- 核心內容：").append(trimmedText).append('\n')
            }
            if (trimmedUrl.isNotEmpty()) {
                contextBuilder.append("- 風格參考：").append(trimmedUrl).append('\n')
            }
            if (input.attachedFileCount > 0) {
                contextBuilder.append("- 需分析上傳之檔案做為排版參考。\n")
            }
            finalPrompt += contextBuilder.toString()
        }

        return finalPrompt
    }

    private fun assembleEnglish(input: PromptAssemblyInput): String {
        var englishPrompt = input.prompt.englishTranslation
        if (englishPrompt.isBlank()) return ""

        val effectiveSignature = input.signatureText.trim().ifEmpty { DEFAULT_SIGNATURE }
        if (input.showSignature) {
            englishPrompt = englishPrompt.replace(
                Regex("琦閔~祝福您|琦閔"),
                Regex.escapeReplacement(effectiveSignature)
            )
        } else {
            englishPrompt = englishPrompt
                .replace(Regex("\n2\\. .*?\"琦閔~祝福您\"\\."), "")
                .replace(Regex("Keep a subtle watermark.*?with date"), "Display date")
                .replace(Regex("End the \\[Outro\\].*"), "")
        }

        if (input.showDate) {
            englishPrompt = englishPrompt.replace("{今日日期}", input.formattedDate)
        } else {
            englishPrompt = englishPrompt
                .replace(Regex("\n3\\. .*?\\{今日日期\\}\\."), "")
                .replace(" with date {今日日期}", "")
                .replace("Display date {今日日期}.", "")
        }

        val imageCountStyle = input.imageCount.trim().ifEmpty { "1張" }
        if (imageCountStyle != "1張" && imageCountStyle != "1") {
            englishPrompt += "\n\n[Multi-Image Output Rule]\nGenerate $imageCountStyle separate, standalone images. Do NOT merge them into a collage or split grid."
        }

        if (input.showFrame && (input.showSignature || input.showDate)) {
            val frameStyle = input.frameStyle.trim().ifEmpty { "Creative decorative frame" }
            englishPrompt += "\n\n[Decorative Frame]\nIntegrate the signature and date inside a \"$frameStyle\" occupying 1/8 of the composition."
        }

        input.prompt.variables.forEach { variable ->
            val rawValue = input.variableValues[variable.name]?.trim().orEmpty()
                .ifEmpty { variable.defaultOptions.firstOrNull().orEmpty() }
            englishPrompt = englishPrompt.replace("{${variable.name}}", extractEnglishPreferredValue(rawValue))
        }

        val trimmedText = input.contextText.trim()
        val trimmedUrl = input.contextUrl.trim()
        if (trimmedText.isNotEmpty() || trimmedUrl.isNotEmpty() || input.attachedFileCount > 0) {
            val extra = StringBuilder("\n\n[Additional Context]\n")
            if (trimmedText.isNotEmpty()) extra.append("- Custom quote/note: ").append(trimmedText).append('\n')
            if (trimmedUrl.isNotEmpty()) extra.append("- Style reference URL: ").append(trimmedUrl).append('\n')
            if (input.attachedFileCount > 0) extra.append("- Analyze uploaded reference files for composition.\n")
            englishPrompt += extra.toString()
        }

        return englishPrompt.trim()
    }

    /**
     * Prefers the English phrase inside parentheses when an option follows "中文 (English)" format
     * (excluding Chinese-only tags like "(禪意)" or "(長輩祝壽)").
     */
    private fun extractEnglishPreferredValue(option: String): String {
        val parenRegex = Regex("\\(([A-Za-z0-9][^()]*)\\)")
        val match = parenRegex.find(option)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 3 } ?: option
    }

    private fun insertAfterTextLayoutSection(prompt: String, addition: String): String {
        val textLayoutRegex = Regex("(【文字與排版設定】[\\s\\S]*?)(?=\\n\\n【|$)")
        if (prompt.contains("【文字與排版設定】")) {
            val match = textLayoutRegex.find(prompt)
            if (match != null) {
                return prompt.replaceRange(match.range, match.groupValues[1] + addition)
            }
        }
        val videoLayoutRegex = Regex("(【文字與後製指示 \\(若工具支援\\)】[\\s\\S]*?)(?=\\n\\n【|$)")
        if (prompt.contains("【文字與後製指示 (若工具支援)】")) {
            val match = videoLayoutRegex.find(prompt)
            if (match != null) {
                return prompt.replaceRange(match.range, match.groupValues[1] + addition)
            }
        }
        return prompt + addition
    }
}
