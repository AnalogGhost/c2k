package org.c2k.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val LAST_PROGRAM_ID    = stringPreferencesKey("last_program_id")
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
}
