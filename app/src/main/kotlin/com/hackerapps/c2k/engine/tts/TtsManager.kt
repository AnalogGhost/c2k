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
        private const val TAG = "TtsManager"
    }

    private val context: Context = context.applicationContext
    private val tts = TextToSpeech(this.context, this)
    private var ready = false
    private var pendingAnnouncement: TtsAnnouncement? = null

    private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE + CONTENT_TYPE_SPEECH is the idiomatic pairing for a
    // short spoken cue that should DUCK music rather than pause it (the same profile a navigation
    // app uses for turn-by-turn prompts), which the platform's ducking heuristics are tuned around.
    private val ttsAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // ROOT-CAUSE FIX: request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, not GAIN_TRANSIENT. On API 26+
    // the system auto-ducks the other player for the life of our focus session and delivers it
    // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK (-3, "keep playing quietly") instead of the pause-inducing
    // AUDIOFOCUS_LOSS_TRANSIENT (-2). GAIN_TRANSIENT made music players pause; a paused media
    // service then drops its foreground state and gets killed by the OS, which is why background
    // music "stopped and never restarted". willPauseWhenDucked=false (the default, stated for
    // intent) means we want the other app ducked, never paused.
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(ttsAudioAttributes)
        .setWillPauseWhenDucked(false)
        .build()

    // ---- Focus-session bookkeeping (every access guarded by focusLock) ----------------------
    // One focus session spans a whole announcement GROUP (a QUEUE_FLUSH utterance plus any
    // QUEUE_ADD follow-ups). Live utterances are tracked by id in `pending`; `holdsFocus` records
    // whether we currently own a focus request. Invariants that keep this leak-free:
    //   - Acquire only on the !holdsFocus edge, so a group requests focus at most once and a new
    //     utterance queued mid-duck does not re-request (which could re-trigger the duck ramp).
    //   - On QUEUE_FLUSH, clear `pending` FIRST: the previous group's queued utterances are being
    //     cancelled, and a flushed/never-started utterance is not guaranteed to deliver a terminal
    //     callback. Forgetting their ids up front means a late (or missing) callback for them can
    //     neither strand the session (leak) nor be miscounted -- correctness no longer depends on
    //     whether the engine emits onStop/onError for dropped utterances.
    //   - Abandon exactly once, when `pending` drains to empty while we hold focus.
    // AudioManager request/abandon are made inside the lock so the acquire / last-drain edge
    // decision is atomic with the holdsFocus mutation, closing the acquire-after-abandon race
    // between announce() (engine runLoop thread) and the terminal callbacks (TTS binder thread).
    private val focusLock = Any()
    private val pending = HashSet<String>()
    private var holdsFocus = false

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
                override fun onDone(utteranceId: String?) = finishUtterance(utteranceId)
                // Fires for utterances dropped by a later QUEUE_FLUSH or by tts.stop(); handling it
                // together with clear-on-flush is what closes the focus leak.
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finishUtterance(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishUtterance(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance(utteranceId)
            })
            ready = true
            isAvailable = true
            isAvailableOnDevice.value = true
            pendingAnnouncement?.let { announce(it) }
            pendingAnnouncement = null
        } else {
            isAvailableOnDevice.value = false
            Log.w(TAG, "TextToSpeech initialization failed (status=$status)")
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
        val utteranceId = "c2k_${System.nanoTime()}"

        synchronized(focusLock) {
            // QUEUE_FLUSH discards whatever the previous group left queued; forget those ids now so
            // a missing terminal callback for them can never strand the session (see invariants).
            if (!queueAdd) pending.clear()
            pending.add(utteranceId)

            // Acquire only when we don't already hold focus, so a group ducks the other app once
            // and continuously rather than re-ducking per utterance.
            if (!holdsFocus) {
                val res = audioManager.requestAudioFocus(focusRequest)
                holdsFocus = res != AudioManager.AUDIOFOCUS_REQUEST_FAILED
                if (!holdsFocus) Log.w(TAG, "Audio focus not granted (res=$res); speaking without duck")
            }
        }

        // speak() outside the lock; its callbacks may land on a binder thread, but the id is
        // already registered so an early onDone/onStop still balances correctly.
        val speakResult = tts.speak(text, mode, params, utteranceId)
        if (speakResult == TextToSpeech.ERROR) {
            // Synchronous failure: no callback will arrive for this id, so balance it now.
            finishUtterance(utteranceId)
        }
    }

    // Terminal handler for every utterance (done / stopped / error), on the TTS binder thread — or
    // on the caller thread for a synchronous speak() failure. Removes the id and, once the whole
    // group has drained, abandons focus so the other player ramps back to full volume.
    private fun finishUtterance(utteranceId: String?) {
        synchronized(focusLock) {
            if (utteranceId != null) pending.remove(utteranceId)
            if (pending.isEmpty() && holdsFocus) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                holdsFocus = false
            }
        }
    }

    // Authoritative release: unconditionally drop focus and reset accounting. Used at shutdown so
    // the other player is never left ducked even if callbacks stop arriving.
    private fun forceAbandon() {
        synchronized(focusLock) {
            pending.clear()
            if (holdsFocus) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                holdsFocus = false
            }
        }
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
        forceAbandon()
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
