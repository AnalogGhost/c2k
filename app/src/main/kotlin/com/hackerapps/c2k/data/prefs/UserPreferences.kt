package com.hackerapps.c2k.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "c2k_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        val TTS_ENABLED        = booleanPreferencesKey("tts_enabled")
        val GPS_ENABLED        = booleanPreferencesKey("gps_enabled")
        val COUNTDOWN_WARNINGS = booleanPreferencesKey("countdown_warnings")
        val KEEP_SCREEN_ON     = booleanPreferencesKey("keep_screen_on")
        val LAST_PROGRAM_ID          = stringPreferencesKey("last_program_id")
        val BATTERY_PROMPT_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
        val VIBRATION_ENABLED        = booleanPreferencesKey("vibration_enabled")
        val TTS_SPEECH_RATE          = floatPreferencesKey("tts_speech_rate")
        val TTS_VOLUME               = floatPreferencesKey("tts_volume")
        val MID_INTERVAL_CUES        = booleanPreferencesKey("mid_interval_cues")
        val TREADMILL_MODE           = booleanPreferencesKey("treadmill_mode")
    }

    val ttsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[TTS_ENABLED] ?: true }

    val gpsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[GPS_ENABLED] ?: true }

    val countdownWarnings: Flow<Boolean> = context.dataStore.data
        .map { it[COUNTDOWN_WARNINGS] ?: true }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data
        .map { it[KEEP_SCREEN_ON] ?: true }

    val lastProgramId: Flow<String?> = context.dataStore.data
        .map { it[LAST_PROGRAM_ID] }

    val batteryPromptDismissed: Flow<Boolean> = context.dataStore.data
        .map { it[BATTERY_PROMPT_DISMISSED] ?: false }

    val vibrationEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[VIBRATION_ENABLED] ?: false }

    suspend fun setTtsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[TTS_ENABLED] = enabled }

    suspend fun setGpsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[GPS_ENABLED] = enabled }

    suspend fun setCountdownWarnings(enabled: Boolean) =
        context.dataStore.edit { it[COUNTDOWN_WARNINGS] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) =
        context.dataStore.edit { it[KEEP_SCREEN_ON] = enabled }

    suspend fun setLastProgramId(id: String) =
        context.dataStore.edit { it[LAST_PROGRAM_ID] = id }

    suspend fun setBatteryPromptDismissed() =
        context.dataStore.edit { it[BATTERY_PROMPT_DISMISSED] = true }

    val ttsSpeechRate: Flow<Float> = context.dataStore.data
        .map { it[TTS_SPEECH_RATE] ?: 1.0f }

    suspend fun setVibrationEnabled(enabled: Boolean) =
        context.dataStore.edit { it[VIBRATION_ENABLED] = enabled }

    suspend fun setTtsSpeechRate(rate: Float) =
        context.dataStore.edit { it[TTS_SPEECH_RATE] = rate }

    val ttsVolume: Flow<Float> = context.dataStore.data
        .map { it[TTS_VOLUME] ?: 1.0f }

    suspend fun setTtsVolume(volume: Float) =
        context.dataStore.edit { it[TTS_VOLUME] = volume }

    val midIntervalCues: Flow<Boolean> = context.dataStore.data
        .map { it[MID_INTERVAL_CUES] ?: true }

    suspend fun setMidIntervalCues(enabled: Boolean) =
        context.dataStore.edit { it[MID_INTERVAL_CUES] = enabled }

    val treadmillMode: Flow<Boolean> = context.dataStore.data
        .map { it[TREADMILL_MODE] ?: false }

    suspend fun setTreadmillMode(enabled: Boolean) =
        context.dataStore.edit { it[TREADMILL_MODE] = enabled }
}
