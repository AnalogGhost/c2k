package com.hackerapps.c2k.engine.tone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import com.hackerapps.c2k.engine.tts.TtsAudioPolicy
import com.hackerapps.c2k.engine.tts.TtsInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays chimes in place of spoken announcements (Tones mode, issue #42). It shares the voice's
 * audio usage and duck-only focus, so music behaves the same as under a spoken cue.
 */
class TonePlayer(
    context: Context,
    private val volume: Float = 1.0f,
    private val countdownThresholds: List<Int> = emptyList()
) : TtsInterface {

    companion object {
        private const val TAG = "TonePlayer"

        // Kept after a chime's last sample before releasing it, covering output latency so the
        // tail isn't cut off and focus isn't returned while the chime is still audible.
        private const val TAIL_MS = 150L
    }

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(TtsAudioPolicy.USAGE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(TtsAudioPolicy.FOCUS_GAIN)
        .setAudioAttributes(attributes)
        .build()
    private val format = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(ChimeSynth.SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    // announce() is called from the engine's tick thread, the release timer from the main thread.
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val rendered = HashMap<Chime, ShortArray>()
    private var track: AudioTrack? = null
    private var holdsFocus = false
    private var shutDown = false

    private val _speaking = MutableStateFlow(false)
    // True while a chime is playing, like TtsManager.speaking, so the service can let the
    // completion chime finish before shutting down.
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    override val isAvailable: Boolean
        get() = synchronized(lock) { !shutDown }

    // Every announcement that maps to a chime arrives as a flush, never queued, so a new chime
    // simply replaces whatever is still ringing.
    override fun announce(announcement: TtsAnnouncement, queueAdd: Boolean) {
        val chime = ToneCues.chimeFor(announcement, countdownThresholds) ?: return
        play(chime)
    }

    internal fun play(chime: Chime) {
        synchronized(lock) { playLocked(chime) }
    }

    private fun playLocked(chime: Chime) {
        if (shutDown) return
        stopTrackLocked()
        val pcm = rendered.getOrPut(chime) { ChimeSynth.render(chime) }
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not create an audio track for $chime", e)
            releaseLocked()
            return
        }
        if (newTrack.state != AudioTrack.STATE_INITIALIZED || newTrack.write(pcm, 0, pcm.size) != pcm.size) {
            Log.w(TAG, "Audio track for $chime failed to initialize")
            newTrack.release()
            releaseLocked()
            return
        }
        newTrack.setVolume(volume)
        requestFocusLocked()
        newTrack.play()
        track = newTrack
        _speaking.value = true
        val durationMs = pcm.size * 1000L / ChimeSynth.SAMPLE_RATE
        handler.postDelayed({
            synchronized(lock) { if (track === newTrack) releaseLocked() }
        }, durationMs + TAIL_MS)
    }

    private fun requestFocusLocked() {
        if (!holdsFocus) {
            val result = audioManager.requestAudioFocus(focusRequest)
            holdsFocus = result != AudioManager.AUDIOFOCUS_REQUEST_FAILED
            if (!holdsFocus) Log.w(TAG, "Audio focus not granted (res=$result); playing without duck")
        }
    }

    // Stops the current chime but keeps focus, for when another chime replaces it right away.
    private fun stopTrackLocked() {
        handler.removeCallbacksAndMessages(null)
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    private fun releaseLocked() {
        stopTrackLocked()
        if (holdsFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            holdsFocus = false
        }
        _speaking.value = false
    }

    override fun shutdown() {
        synchronized(lock) {
            shutDown = true
            releaseLocked()
        }
    }
}
