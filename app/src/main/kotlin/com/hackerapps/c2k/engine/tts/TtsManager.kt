package com.hackerapps.c2k.engine.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.IntervalType
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class TtsManager(
    context: Context,
    private val speechRate: Float = 1.0f,
    private val volume: Float = 1.0f
) : TtsInterface, TextToSpeech.OnInitListener {

    companion object {
        val isAvailableOnDevice = MutableStateFlow<Boolean?>(null)
    }

    private val context: Context = context.applicationContext
    private val tts = TextToSpeech(this.context, this)
    private var ready = false
    private var pendingAnnouncement: TtsAnnouncement? = null

    private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val ttsAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ttsAudioAttributes)
        .build()

    override var isAvailable: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH)
            }
            tts.setSpeechRate(speechRate)
            tts.setAudioAttributes(ttsAudioAttributes)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (!tts.isSpeaking) audioManager.abandonAudioFocusRequest(focusRequest)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            })
            ready = true
            isAvailable = true
            isAvailableOnDevice.value = true
            pendingAnnouncement?.let { announce(it) }
            pendingAnnouncement = null
        } else {
            isAvailableOnDevice.value = false
            Log.w("TtsManager", "TextToSpeech initialization failed (status=$status)")
        }
    }

    override fun announce(announcement: TtsAnnouncement, queueAdd: Boolean) {
        if (!ready) {
            if (!queueAdd) pendingAnnouncement = announcement
            return
        }
        val text = buildText(announcement)
        val mode = if (queueAdd) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        val params = if (volume < 1.0f) Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        } else null
        audioManager.requestAudioFocus(focusRequest)
        tts.speak(text, mode, params, "c2k_${System.nanoTime()}")
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
        audioManager.abandonAudioFocusRequest(focusRequest)
        ready = false
    }

    private fun buildText(announcement: TtsAnnouncement): String = when (announcement) {
        is TtsAnnouncement.IntervalStart -> when (announcement.interval.type) {
            IntervalType.WARMUP   -> context.getString(R.string.tts_interval_warmup)
            IntervalType.COOLDOWN -> context.getString(R.string.tts_interval_cooldown)
            IntervalType.RUN      -> context.getString(R.string.tts_interval_run, ttsDuration(announcement.interval.durationSeconds))
            IntervalType.WALK     -> context.getString(R.string.tts_interval_walk, ttsDuration(announcement.interval.durationSeconds))
        }
        is TtsAnnouncement.CountdownWarning -> context.getString(R.string.tts_seconds_remaining, announcement.secondsRemaining)
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
        TtsAnnouncement.WorkoutComplete -> context.getString(R.string.tts_workout_complete)
        TtsAnnouncement.Halfway         -> context.getString(R.string.tts_halfway)
        TtsAnnouncement.LastRunInterval -> context.getString(R.string.tts_last_run)
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
