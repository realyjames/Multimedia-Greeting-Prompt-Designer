package com.example.data

/**
 * 問候提示詞類型（早安、午安、晚安、生日、節慶、影音、音樂）。
 */
enum class GreetingPromptType(
    val displayName: String,
    val badgeLabel: String
) {
    MORNING("早安", "🌅 早安"),
    AFTERNOON("午安", "☀️ 午安"),
    NIGHT("晚安", "🌙 晚安"),
    BIRTHDAY("生日", "🎂 生日"),
    FESTIVAL("節慶", "🎊 節慶"),
    VIDEO("影音", "🎬 影音"),
    MUSIC("音樂", "🎵 音樂")
}

/**
 * 相關媒體標籤（圖文、影音、音樂、純音樂、人聲）。
 */
enum class MediaTag(val displayName: String) {
    IMAGE("圖文"),
    VIDEO("影音"),
    MUSIC("音樂"),
    BGM("純音樂"),
    VOCAL("人聲")
}

/**
 * 應用數據核心結構：問候提示詞資料模型。
 *
 * @property id 唯一識別碼
 * @property dimensionId 所屬維度識別碼
 * @property title 模板名稱
 * @property type 問候類型（如：早安、生日、節慶等）
 * @property template 中文提示詞模板（含變數佔位符）
 * @property mediaTags 相關媒體標籤清單（如：圖文、影音、音樂）
 * @property englishTranslation 對應之英文提示詞模板／英譯內容
 * @property variables 可自訂之動態參數變數定義
 */
data class GreetingPrompt(
    val id: String,
    val dimensionId: String,
    val title: String,
    val type: GreetingPromptType,
    val template: String,
    val mediaTags: List<MediaTag>,
    val englishTranslation: String,
    val variables: List<FormulaVariable> = emptyList()
)
