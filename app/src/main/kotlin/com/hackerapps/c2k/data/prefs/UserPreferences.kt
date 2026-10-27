package com.hackerapps.c2k.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "c2k_prefs")

private const val READ_RETRIES = 3L
private const val READ_RETRY_DELAY_MS = 50L

/**
 * DataStore can fail a read with FileNotFoundException while a concurrent first write is creating
 * the file. On a fresh install C2KApp's startup write raced the workout service's first read this
 * way, killing the workout's setup. A retry sees the finished file; an I/O error that persists
 * falls back to the defaults instead of crashing whoever collects the settings.
 */
internal fun Flow<Preferences>.recoverReadErrors(): Flow<Preferences> =
    retry(READ_RETRIES) { e -> (e is IOException).also { if (it) delay(READ_RETRY_DELAY_MS) } }
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

class UserPreferences(private val context: Context) {

    companion object {
        val TTS_ENABLED        = booleanPreferencesKey("tts_enabled")
        val GPS_ENABLED        = booleanPreferencesKey("gps_enabled")
        val COUNTDOWN_WARNINGS = booleanPreferencesKey("countdown_warnings")
        val KEEP_SCREEN_ON     = booleanPreferencesKey("keep_screen_on")
        val LAST_PROGRAM_ID          = stringPreferencesKey("last_program_id")
        val BATTERY_PROMPT_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
        val VIBRATION_ENABLED        = booleanPreferencesKey("vibration_enabled")
        val VIBRATION_STRENGTH       = stringPreferencesKey("vibration_strength")
        val TTS_SPEECH_RATE          = floatPreferencesKey("tts_speech_rate")
        val TTS_VOLUME               = floatPreferencesKey("tts_volume")
        val CUE_STYLE                = stringPreferencesKey("cue_style")
        val MID_INTERVAL_CUES        = booleanPreferencesKey("mid_interval_cues")
        val TREADMILL_MODE           = booleanPreferencesKey("treadmill_mode")
        val COUNTDOWN_WARNING_1      = intPreferencesKey("countdown_warning_1")
        val COUNTDOWN_WARNING_2      = intPreferencesKey("countdown_warning_2")
        val COUNTDOWN_WARNING_2_ENABLED = booleanPreferencesKey("countdown_warning_2_enabled")
        val PERIODIC_TIME_CUES         = booleanPreferencesKey("periodic_time_cues")
        val PERIODIC_TIME_CUE_INTERVAL = intPreferencesKey("periodic_time_cue_interval")
        val WEIGHT_KG                = floatPreferencesKey("weight_kg")
        val WEIGHT_UNIT              = stringPreferencesKey("weight_unit")
        val GPS_DISTANCES_RECOMPUTED = booleanPreferencesKey("gps_distances_recomputed")
        // Written by 1.2.20 when there was a single switch; seeds both new flags until the user
        // changes one of them.
        val SKIP_WARMUP_COOLDOWN     = booleanPreferencesKey("skip_warmup_cooldown")
        val SKIP_WARMUP              = booleanPreferencesKey("skip_warmup")
        val SKIP_COOLDOWN            = booleanPreferencesKey("skip_cooldown")
        // "programId|week|sinceMillis", set when the runner accepts a suggestion to repeat a week.
        val REPEATED_WEEK            = stringPreferencesKey("repeated_week")
        val PROGRESSION_DISMISSED_AT = longPreferencesKey("progression_dismissed_at")
    }

    private val data: Flow<Preferences> = context.dataStore.data.recoverReadErrors()

    val ttsEnabled: Flow<Boolean> = data
        .map { it[TTS_ENABLED] ?: true }

    val gpsEnabled: Flow<Boolean> = data
        .map { it[GPS_ENABLED] ?: true }

    val countdownWarnings: Flow<Boolean> = data
        .map { it[COUNTDOWN_WARNINGS] ?: true }

    val countdownWarning1: Flow<Int> = data
        .map { it[COUNTDOWN_WARNING_1] ?: 10 }

    val countdownWarning2: Flow<Int> = data
        .map { it[COUNTDOWN_WARNING_2] ?: 5 }

    val countdownWarning2Enabled: Flow<Boolean> = data
        .map { it[COUNTDOWN_WARNING_2_ENABLED] ?: true }

    val keepScreenOn: Flow<Boolean> = data
        .map { it[KEEP_SCREEN_ON] ?: true }

    val lastProgramId: Flow<String?> = data
        .map { it[LAST_PROGRAM_ID] }

    val batteryPromptDismissed: Flow<Boolean> = data
        .map { it[BATTERY_PROMPT_DISMISSED] ?: false }

    val vibrationEnabled: Flow<Boolean> = data
        .map { it[VIBRATION_ENABLED] ?: false }

    val vibrationStrength: Flow<VibrationStrength> = data
        .map { preferences -> VibrationStrength.fromStored(preferences[VIBRATION_STRENGTH]) }

    suspend fun setTtsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[TTS_ENABLED] = enabled }

    suspend fun setGpsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[GPS_ENABLED] = enabled }

    suspend fun setCountdownWarnings(enabled: Boolean) =
        context.dataStore.edit { it[COUNTDOWN_WARNINGS] = enabled }

    suspend fun setCountdownWarning1(seconds: Int) =
        context.dataStore.edit { it[COUNTDOWN_WARNING_1] = seconds }

    suspend fun setCountdownWarning2(seconds: Int) =
        context.dataStore.edit { it[COUNTDOWN_WARNING_2] = seconds }

    suspend fun setCountdownWarning2Enabled(enabled: Boolean) =
        context.dataStore.edit { it[COUNTDOWN_WARNING_2_ENABLED] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) =
        context.dataStore.edit { it[KEEP_SCREEN_ON] = enabled }

    suspend fun setLastProgramId(id: String) =
        context.dataStore.edit { it[LAST_PROGRAM_ID] = id }

    suspend fun setBatteryPromptDismissed() =
        context.dataStore.edit { it[BATTERY_PROMPT_DISMISSED] = true }

    val ttsSpeechRate: Flow<Float> = data
        .map { it[TTS_SPEECH_RATE] ?: 1.0f }

    suspend fun setVibrationEnabled(enabled: Boolean) =
        context.dataStore.edit { it[VIBRATION_ENABLED] = enabled }

    suspend fun setVibrationStrength(strength: VibrationStrength) =
        context.dataStore.edit { it[VIBRATION_STRENGTH] = strength.name }

    suspend fun setTtsSpeechRate(rate: Float) =
        context.dataStore.edit { it[TTS_SPEECH_RATE] = rate }

    val ttsVolume: Flow<Float> = data
        .map { it[TTS_VOLUME] ?: 1.0f }

    suspend fun setTtsVolume(volume: Float) =
        context.dataStore.edit { it[TTS_VOLUME] = volume }

    val cueStyle: Flow<CueStyle> = data
        .map { preferences -> CueStyle.fromStored(preferences[CUE_STYLE]) }

    suspend fun setCueStyle(style: CueStyle) =
        context.dataStore.edit { it[CUE_STYLE] = style.name }

    val midIntervalCues: Flow<Boolean> = data
        .map { it[MID_INTERVAL_CUES] ?: true }

    suspend fun setMidIntervalCues(enabled: Boolean) =
        context.dataStore.edit { it[MID_INTERVAL_CUES] = enabled }

    val periodicTimeCues: Flow<Boolean> = data
        .map { it[PERIODIC_TIME_CUES] ?: false }

    suspend fun setPeriodicTimeCues(enabled: Boolean) =
        context.dataStore.edit { it[PERIODIC_TIME_CUES] = enabled }

    val periodicTimeCueInterval: Flow<Int> = data
        .map { it[PERIODIC_TIME_CUE_INTERVAL] ?: 30 }

    suspend fun setPeriodicTimeCueInterval(seconds: Int) =
        context.dataStore.edit { it[PERIODIC_TIME_CUE_INTERVAL] = seconds }

    val treadmillMode: Flow<Boolean> = data
        .map { it[TREADMILL_MODE] ?: false }

    suspend fun setTreadmillMode(enabled: Boolean) =
        context.dataStore.edit { it[TREADMILL_MODE] = enabled }

    val weightKg: Flow<Float?> = data
        .map { it[WEIGHT_KG] }

    val weightUnit: Flow<WeightUnit> = data
        .map { WeightUnit.valueOf(it[WEIGHT_UNIT] ?: WeightUnit.KG.name) }

    suspend fun setWeightKg(kg: Float) =
        context.dataStore.edit { it[WEIGHT_KG] = kg }

    suspend fun clearWeightKg() =
        context.dataStore.edit { it.remove(WEIGHT_KG) }

    suspend fun setWeightUnit(unit: WeightUnit) =
        context.dataStore.edit { it[WEIGHT_UNIT] = unit.name }

    val gpsDistancesRecomputed: Flow<Boolean> = data
        .map { it[GPS_DISTANCES_RECOMPUTED] ?: false }

    suspend fun setGpsDistancesRecomputed() =
        context.dataStore.edit { it[GPS_DISTANCES_RECOMPUTED] = true }

    val skipWarmup: Flow<Boolean> = data
        .map { it[SKIP_WARMUP] ?: it[SKIP_WARMUP_COOLDOWN] ?: false }

    val skipCooldown: Flow<Boolean> = data
        .map { it[SKIP_COOLDOWN] ?: it[SKIP_WARMUP_COOLDOWN] ?: false }

    suspend fun setSkipWarmup(enabled: Boolean) =
        context.dataStore.edit { it[SKIP_WARMUP] = enabled }

    suspend fun setSkipCooldown(enabled: Boolean) =
        context.dataStore.edit { it[SKIP_COOLDOWN] = enabled }

    val repeatedWeek: Flow<RepeatedWeek?> = data
        .map { prefs ->
            prefs[REPEATED_WEEK]?.split('|')?.takeIf { it.size == 3 }?.let { (program, week, since) ->
                val w = week.toIntOrNull()
                val s = since.toLongOrNull()
                if (w != null && s != null) RepeatedWeek(program, w, s) else null
            }
        }

    suspend fun setRepeatedWeek(repeated: RepeatedWeek) =
        context.dataStore.edit {
            it[REPEATED_WEEK] = "${repeated.programId}|${repeated.week}|${repeated.sinceMillis}"
        }

    val progressionDismissedAt: Flow<Long?> = data
        .map { it[PROGRESSION_DISMISSED_AT] }

    suspend fun setProgressionDismissedAt(millis: Long) =
        context.dataStore.edit { it[PROGRESSION_DISMISSED_AT] = millis }

    // Run when a program's progress is reset, so a repeated week doesn't outlive the history it
    // was based on.
    suspend fun clearProgression() =
        context.dataStore.edit {
            it.remove(REPEATED_WEEK)
            it.remove(PROGRESSION_DISMISSED_AT)
        }
}

/** A week the runner chose to repeat, and when, so only workouts run since then count toward it. */
data class RepeatedWeek(val programId: String, val week: Int, val sinceMillis: Long)

/** How workout cues sound: spoken announcements, or chimes in their place (issue #42). */
enum class CueStyle {
    VOICE,
    TONES;

    companion object {
        fun fromStored(value: String?): CueStyle =
            entries.firstOrNull { it.name == value } ?: VOICE
    }
}

enum class VibrationStrength {
    LIGHT,
    MEDIUM,
    STRONG;

    companion object {
        fun fromStored(value: String?): VibrationStrength =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}
