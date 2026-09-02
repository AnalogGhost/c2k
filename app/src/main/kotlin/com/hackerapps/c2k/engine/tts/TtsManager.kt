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
import com.hackerapps.c2k.utils.localized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TtsManager(
    context: Context,
    private val speechRate: Float = 1.0f,
    private val volume: Float = 1.0f
) : TtsInterface, DiagnosticTts, TextToSpeech.OnInitListener {

    companion object {
        val isAvailableOnDevice = MutableStateFlow<Boolean?>(null)
        private const val TAG = "TtsManager"
    }

    private val context: Context = context.applicationContext.localized()
    private val tts = TextToSpeech(this.context, this)
    private var ready = false
    private var initializationFailure: TtsDiagnosticResult? = null
    private var pendingAnnouncement: TtsAnnouncement? = null
    private var pendingDiagnostic: DiagnosticRequest? = null
    private val diagnosticRequests = HashMap<String, DiagnosticRequest>()

    private data class DiagnosticRequest(
        val text: String,
        val onSpeaking: () -> Unit,
        val onResult: (TtsDiagnosticResult) -> Unit
    )

    private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val ttsAudioAttributes = AudioAttributes.Builder()
        .setUsage(TtsAudioPolicy.USAGE)
        .setContentType(TtsAudioPolicy.CONTENT_TYPE)
        .build()

    // MAY_DUCK so the system ducks the other player rather than pausing it. GAIN_TRANSIENT sends
    // AUDIOFOCUS_LOSS_TRANSIENT to other apps, which causes music players to pause; a paused media
    // service then drops its foreground state and gets killed by the OS.
    private val focusRequest = AudioFocusRequest.Builder(TtsAudioPolicy.FOCUS_GAIN)
        .setAudioAttributes(ttsAudioAttributes)
        .build()

    // Focus is held for an entire announcement group (a QUEUE_FLUSH utterance plus any QUEUE_ADD
    // follow-ups) and released once all utterances in the group have finished or been discarded.
    // On QUEUE_FLUSH the previous group's pending ids are cleared immediately: a flushed utterance
    // may never deliver a terminal callback, and waiting for one would leak focus.
    private val focusLock = Any()
    private val pending = HashSet<String>()
    private var holdsFocus = false

    private val _speaking = MutableStateFlow(false)
    // True while any announcement is queued or being spoken; drops to false once the queue drains.
    // Lets the caller (e.g. the service on workout completion) wait for a final cue like
    // "Workout complete" to finish before shutting TTS down, instead of cutting it off.
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    override var isAvailable: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageAvailable = selectTtsLanguage(
                context.resources.configuration.locales[0],
                tts::setLanguage
            )
            if (!languageAvailable) {
                initializationFailure = TtsDiagnosticResult.VoiceUnavailable
                isAvailableOnDevice.value = false
                pendingDiagnostic?.onResult?.invoke(TtsDiagnosticResult.VoiceUnavailable)
                pendingDiagnostic = null
                Log.w(TAG, "Neither the device locale nor English is available for text-to-speech")
                return
            }
            tts.setSpeechRate(speechRate)
            tts.setAudioAttributes(ttsAudioAttributes)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val callback = synchronized(focusLock) {
                        utteranceId?.let { diagnosticRequests[it]?.onSpeaking }
                    }
                    callback?.invoke()
                }
                override fun onDone(utteranceId: String?) =
                    finishUtterance(utteranceId, TtsDiagnosticResult.Success)
                // onStop fires when an utterance is cancelled by tts.stop() or a later QUEUE_FLUSH
                override fun onStop(utteranceId: String?, interrupted: Boolean) =
                    finishUtterance(utteranceId, TtsDiagnosticResult.SynthesisOrServiceFailure)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) =
                    finishUtterance(utteranceId, TtsDiagnosticResult.SynthesisOrServiceFailure)
                override fun onError(utteranceId: String?, errorCode: Int) =
                    finishUtterance(utteranceId, diagnosticResultForError(errorCode))
            })
            ready = true
            initializationFailure = null
            isAvailable = true
            isAvailableOnDevice.value = true
            pendingAnnouncement?.let { announce(it) }
            pendingAnnouncement = null
            pendingDiagnostic?.let { speakDiagnostic(it) }
            pendingDiagnostic = null
        } else {
            val hasInstalledEngine = runCatching { tts.engines.isNotEmpty() }
                // A query failure is not evidence that the device has no engine.
                .getOrDefault(true)
            val failure = diagnosticResultForInitializationFailure(hasInstalledEngine)
            initializationFailure = failure
            isAvailableOnDevice.value = false
            pendingDiagnostic?.onResult?.invoke(failure)
            pendingDiagnostic = null
            Log.w(TAG, "TextToSpeech initialization failed (status=$status, hasEngine=$hasInstalledEngine)")
        }
    }

    override fun diagnose(
        text: String,
        onSpeaking: () -> Unit,
        onResult: (TtsDiagnosticResult) -> Unit
    ) {
        val request = DiagnosticRequest(text, onSpeaking, onResult)
        when {
            ready -> speakDiagnostic(request)
            initializationFailure != null -> onResult(initializationFailure!!)
            else -> pendingDiagnostic = request
        }
    }

    private fun speakDiagnostic(request: DiagnosticRequest) {
        val utteranceId = "c2k_diagnostic_${System.nanoTime()}"
        synchronized(focusLock) {
            pending.clear()
            pending.add(utteranceId)
            diagnosticRequests[utteranceId] = request
            _speaking.value = true
            requestAudioFocusIfNeeded()
        }
        val speakResult = tts.speak(
            request.text,
            TextToSpeech.QUEUE_FLUSH,
            speechParams(),
            utteranceId
        )
        if (speakResult == TextToSpeech.ERROR) {
            finishUtterance(utteranceId, TtsDiagnosticResult.SynthesisOrServiceFailure)
        }
    }

    override fun announce(announcement: TtsAnnouncement, queueAdd: Boolean) {
        if (!ready) {
            if (!queueAdd) pendingAnnouncement = announcement
            return
        }
        val text = buildText(announcement)
        val mode = if (queueAdd) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        val params = speechParams()
        val utteranceId = "c2k_${System.nanoTime()}"

        synchronized(focusLock) {
            if (!queueAdd) pending.clear()
            pending.add(utteranceId)
            _speaking.value = true
            requestAudioFocusIfNeeded()
        }

        // speak() is called outside the lock; the id is already registered so an early
        // onDone/onStop callback from the TTS binder thread still balances correctly.
        val speakResult = tts.speak(text, mode, params, utteranceId)
        if (speakResult == TextToSpeech.ERROR) {
            finishUtterance(utteranceId)
        }
    }

    private fun speechParams(): Bundle? = if (volume < 1.0f) Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
    } else null

    private fun requestAudioFocusIfNeeded() {
        if (!holdsFocus) {
            val res = audioManager.requestAudioFocus(focusRequest)
            holdsFocus = res != AudioManager.AUDIOFOCUS_REQUEST_FAILED
            if (!holdsFocus) Log.w(TAG, "Audio focus not granted (res=$res); speaking without duck")
        }
    }

    private fun finishUtterance(
        utteranceId: String?,
        diagnosticResult: TtsDiagnosticResult? = null
    ) {
        var diagnosticCallback: ((TtsDiagnosticResult) -> Unit)? = null
        synchronized(focusLock) {
            if (utteranceId != null) {
                pending.remove(utteranceId)
                diagnosticCallback = diagnosticRequests.remove(utteranceId)?.onResult
            }
            if (pending.isEmpty() && holdsFocus) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                holdsFocus = false
            }
            _speaking.value = pending.isNotEmpty()
        }
        if (diagnosticResult != null) diagnosticCallback?.invoke(diagnosticResult)
    }

    private fun forceAbandon() {
        synchronized(focusLock) {
            pending.clear()
            if (holdsFocus) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                holdsFocus = false
            }
            _speaking.value = false
        }
    }

    override fun shutdown() {
        pendingDiagnostic = null
        synchronized(focusLock) { diagnosticRequests.clear() }
        tts.stop()
        tts.shutdown()
        forceAbandon()
        ready = false
        isAvailable = false
    }

    private fun buildText(announcement: TtsAnnouncement): String = when (announcement) {
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
