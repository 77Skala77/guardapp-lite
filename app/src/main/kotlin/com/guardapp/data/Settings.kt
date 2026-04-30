// data/Settings.kt
package com.guardapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "guardapp_lite_settings")

/**
 * Простые настройки приложения через DataStore.
 * Никаких облачных синхронизаций.
 */
class Settings(private val context: Context) {

    private val ds = context.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val ANALYSIS_FREQ_SEC = intPreferencesKey("analysis_freq_sec")
        val AUDIO_MUTE_ENABLED = booleanPreferencesKey("audio_mute_enabled")
        val ANCHOR_CONFIG_JSON = stringPreferencesKey("anchor_config_json")
    }

    val onboardingCompleted: Flow<Boolean> = ds.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val protectionEnabled: Flow<Boolean> = ds.data.map { it[Keys.PROTECTION_ENABLED] ?: false }

    val sensitivity: Flow<Sensitivity> = ds.data.map {
        runCatching { Sensitivity.valueOf(it[Keys.SENSITIVITY] ?: "BALANCED") }
            .getOrDefault(Sensitivity.BALANCED)
    }

    val analysisFreqSec: Flow<Int> = ds.data.map { it[Keys.ANALYSIS_FREQ_SEC] ?: 10 }
    val audioMuteEnabled: Flow<Boolean> = ds.data.map { it[Keys.AUDIO_MUTE_ENABLED] ?: true }

    val anchorConfig: Flow<AnchorConfig> = ds.data.map {
        val raw = it[Keys.ANCHOR_CONFIG_JSON] ?: return@map AnchorConfig()
        runCatching { json.decodeFromString<AnchorConfig>(raw) }
            .getOrDefault(AnchorConfig())
    }

    suspend fun setOnboardingCompleted(v: Boolean) = ds.edit { it[Keys.ONBOARDING_COMPLETED] = v }
    suspend fun setProtectionEnabled(v: Boolean) = ds.edit { it[Keys.PROTECTION_ENABLED] = v }
    suspend fun setSensitivity(s: Sensitivity) = ds.edit { it[Keys.SENSITIVITY] = s.name }
    suspend fun setAnalysisFreq(seconds: Int) = ds.edit { it[Keys.ANALYSIS_FREQ_SEC] = seconds }
    suspend fun setAudioMuteEnabled(v: Boolean) = ds.edit { it[Keys.AUDIO_MUTE_ENABLED] = v }

    suspend fun setAnchorConfig(config: AnchorConfig) = ds.edit {
        it[Keys.ANCHOR_CONFIG_JSON] = json.encodeToString(config)
    }

    /** Получить текущий конфиг синхронно (для использования в Activity) */
    suspend fun getAnchorConfigSync(): AnchorConfig = anchorConfig.first()

    suspend fun resetAll() = ds.edit { it.clear() }

    companion object {
        @Volatile private var instance: Settings? = null
        fun getInstance(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
