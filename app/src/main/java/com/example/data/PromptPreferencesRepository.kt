package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.promptDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "greeting_prompt_designer_prefs"
)

data class StoredPromptPreferences(
    val signatureSlots: List<String> = List(SIGNATURE_SLOT_COUNT) { idx ->
        if (idx == 0) DEFAULT_SIGNATURE else ""
    },
    val selectedSignatureIndex: Int = 0,
    val showSignature: Boolean = true,
    val showDate: Boolean = true,
    val showFrame: Boolean = true,
    val imageCountOptions: List<String> = PromptOptionsLibrary.defaultImageCounts,
    val frameOptions: List<String> = PromptOptionsLibrary.defaultFrames,
    val customVariableOptions: Map<String, List<String>> = emptyMap()
)

class PromptPreferencesRepository(private val context: Context) {

    private object Keys {
        val SAVED_SLOTS = stringPreferencesKey("savedSlots")
        val SELECTED_SIG_INDEX = intPreferencesKey("selectedSigIndex")
        val SHOW_SIGNATURE = booleanPreferencesKey("showSignature")
        val SHOW_DATE = booleanPreferencesKey("showDate")
        val SHOW_FRAME = booleanPreferencesKey("showFrame")
        val SAVED_IMAGE_COUNTS = stringPreferencesKey("savedImageCountOptions")
        val SAVED_FRAMES = stringPreferencesKey("savedFrameOptions")
        const val VAR_PREFIX = "savedVar_"
    }

    val preferencesFlow: Flow<StoredPromptPreferences> = context.promptDataStore.data.map { prefs ->
        val rawSlots = prefs[Keys.SAVED_SLOTS]?.let(::decodeStringList)
        val normalizedSlots = List(SIGNATURE_SLOT_COUNT) { idx ->
            rawSlots?.getOrNull(idx) ?: if (idx == 0) DEFAULT_SIGNATURE else ""
        }
        val selectedIdx = (prefs[Keys.SELECTED_SIG_INDEX] ?: 0).coerceIn(0, SIGNATURE_SLOT_COUNT - 1)
        val imageCounts = prefs[Keys.SAVED_IMAGE_COUNTS]?.let(::decodeStringList)
            ?.takeIf { it.isNotEmpty() }
            ?: PromptOptionsLibrary.defaultImageCounts
        val frames = prefs[Keys.SAVED_FRAMES]?.let(::decodeStringList)
            ?.takeIf { it.isNotEmpty() }
            ?: PromptOptionsLibrary.defaultFrames

        val varOptions = mutableMapOf<String, List<String>>()
        prefs.asMap().forEach { (key, value) ->
            if (key.name.startsWith(Keys.VAR_PREFIX) && value is String) {
                val varName = key.name.removePrefix(Keys.VAR_PREFIX)
                val decoded = decodeStringList(value)
                if (decoded.isNotEmpty()) {
                    varOptions[varName] = decoded
                }
            }
        }

        StoredPromptPreferences(
            signatureSlots = normalizedSlots,
            selectedSignatureIndex = selectedIdx,
            showSignature = prefs[Keys.SHOW_SIGNATURE] ?: true,
            showDate = prefs[Keys.SHOW_DATE] ?: true,
            showFrame = prefs[Keys.SHOW_FRAME] ?: true,
            imageCountOptions = imageCounts,
            frameOptions = frames,
            customVariableOptions = varOptions
        )
    }

    suspend fun saveSignatures(slots: List<String>, selectedIndex: Int) {
        context.promptDataStore.edit { prefs ->
            prefs[Keys.SAVED_SLOTS] = encodeStringList(slots)
            prefs[Keys.SELECTED_SIG_INDEX] = selectedIndex.coerceIn(0, SIGNATURE_SLOT_COUNT - 1)
        }
    }

    suspend fun saveToggleSettings(showSignature: Boolean, showDate: Boolean, showFrame: Boolean) {
        context.promptDataStore.edit { prefs ->
            prefs[Keys.SHOW_SIGNATURE] = showSignature
            prefs[Keys.SHOW_DATE] = showDate
            prefs[Keys.SHOW_FRAME] = showFrame
        }
    }

    suspend fun saveImageCountOptions(options: List<String>) {
        context.promptDataStore.edit { prefs ->
            prefs[Keys.SAVED_IMAGE_COUNTS] = encodeStringList(options)
        }
    }

    suspend fun saveFrameOptions(options: List<String>) {
        context.promptDataStore.edit { prefs ->
            prefs[Keys.SAVED_FRAMES] = encodeStringList(options)
        }
    }

    suspend fun saveVariableOptions(varName: String, options: List<String>) {
        val key = stringPreferencesKey("${Keys.VAR_PREFIX}$varName")
        context.promptDataStore.edit { prefs ->
            prefs[key] = encodeStringList(options)
        }
    }

    private fun encodeStringList(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeStringList(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            List(array.length()) { index -> array.optString(index, "") }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
