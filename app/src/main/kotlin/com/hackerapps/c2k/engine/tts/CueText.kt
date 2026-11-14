package com.hackerapps.c2k.engine.tts

import android.content.Context
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.IntervalType

/**
 * The words spoken for each announcement, in [context]'s language (the app's chosen language, as
 * TtsManager passes it). Separate from TtsManager so the phrases can be checked in every locale
 * without starting a speech engine.
 */
class CueText(private val context: Context) {

    fun of(announcement: TtsAnnouncement): String = when (announcement) {
        is TtsAnnouncement.IntervalStart -> when (announcement.interval.type) {
            IntervalType.WARMUP   -> context.getString(R.string.tts_interval_warmup)
            IntervalType.COOLDOWN -> context.getString(R.string.tts_interval_cooldown)
            IntervalType.RUN      -> context.getString(R.string.tts_interval_run, ttsDuration(announcement.interval.durationSeconds))
            IntervalType.WALK     -> context.getString(R.string.tts_interval_walk, ttsDuration(announcement.interval.durationSeconds))
        }
        is TtsAnnouncement.CountdownWarning -> context.resources.getQuantityString(R.plurals.tts_seconds_remaining, announcement.secondsRemaining, announcement.secondsRemaining)
        is TtsAnnouncement.NextInterval -> when (announcement.interval.type) {
            IntervalType.RUN      -> context.getString(R.string.tts_next_run)
            IntervalType.WALK     -> context.getString(R.string.tts_next_walk)
            IntervalType.WARMUP   -> context.getString(R.string.tts_next_warmup)
            IntervalType.COOLDOWN -> context.getString(R.string.tts_next_cooldown)
        }
        is TtsAnnouncement.IntervalMidpoint -> {
            val phrases = context.resources.getStringArray(R.array.tts_encouragement_phrases)
            phrases[announcement.phraseIndex % phrases.size]
        }
        is TtsAnnouncement.PeriodicTimeRemaining -> context.getString(R.string.tts_time_remaining, ttsDuration(announcement.secondsRemaining))
        is TtsAnnouncement.DistanceSplit -> {
            val distance = ttsDistance(announcement.meters)
            announcement.paceSecondsPerKm
                ?.let { context.getString(R.string.tts_distance_split_pace, distance, ttsDuration(it)) }
                ?: distance
        }
        TtsAnnouncement.WorkoutComplete -> context.getString(R.string.tts_workout_complete)
        TtsAnnouncement.Halfway         -> context.getString(R.string.tts_halfway)
        TtsAnnouncement.LastRunInterval -> context.getString(R.string.tts_last_run)
    }

    // Spoken with whole-number plurals only ("1 kilometre 500 metres", not "1.5 kilometres"):
    // Android plural resources can't select a form for a fraction, and languages disagree on it
    // (French says "1,5 kilomètre" but "2,5 kilomètres").
    private fun ttsDistance(meters: Int): String {
        val km = meters / 1000
        val m = meters % 1000
        val kmStr = if (km > 0) context.resources.getQuantityString(R.plurals.tts_distance_km, km, km) else null
        val mStr = if (m > 0) context.resources.getQuantityString(R.plurals.tts_distance_m, m, m) else null
        return when {
            kmStr != null && mStr != null -> context.getString(R.string.tts_distance_km_m, kmStr, mStr)
            kmStr != null -> kmStr
            else -> mStr.orEmpty()
        }
    }

    private fun ttsDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        val minStr = if (mins > 0) context.resources.getQuantityString(R.plurals.tts_duration_minutes, mins, mins) else null
        val secStr = if (secs > 0) context.resources.getQuantityString(R.plurals.tts_duration_seconds, secs, secs) else null
        return when {
            minStr != null && secStr != null -> context.getString(R.string.tts_duration_min_sec, minStr, secStr)
            minStr != null -> minStr
            secStr != null -> secStr
            else -> ""
        }
    }
}
