package com.hackerapps.c2k.service

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.hackerapps.c2k.data.prefs.CountdownSound
import kotlin.math.roundToInt

/**
 * Plays a short built-in tone for a countdown warning.
 *
 * Uses [AudioManager.STREAM_MUSIC], the same output stream as the spoken cues
 * (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE), so the tone follows the media volume and mixes over
 * music. No audio focus is requested: the tones are under a second long.
 */
internal class CountdownTonePlayer private constructor(
    private val generator: ToneGenerator,
    private val sound: CountdownSound
) {
    private var released = false

    @Synchronized
    fun play() {
        if (released) return
        generator.startTone(sound.toneType, sound.durationMs)
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        generator.release()
    }

    companion object {
        private const val TAG = "CountdownTonePlayer"

        /** Returns null when the device cannot create a [ToneGenerator]; callers fall back to speech. */
        fun createOrNull(sound: CountdownSound, volume: Float): CountdownTonePlayer? = try {
            val level = (volume.coerceIn(0f, 1f) * ToneGenerator.MAX_VOLUME).roundToInt()
            CountdownTonePlayer(ToneGenerator(AudioManager.STREAM_MUSIC, level), sound)
        } catch (e: RuntimeException) {
            Log.w(TAG, "ToneGenerator unavailable; falling back to spoken countdown", e)
            null
        }
    }
}

private val CountdownSound.toneType: Int
    get() = when (this) {
        CountdownSound.BEEP -> ToneGenerator.TONE_PROP_PROMPT        // 400 + 1200 Hz, 200 ms
        CountdownSound.DOUBLE_BEEP -> ToneGenerator.TONE_PROP_ACK    // 1200 Hz, 2 x 100 ms
        CountdownSound.PIPS -> ToneGenerator.TONE_SUP_PIP            // 4 bursts of 480 Hz
    }

private val CountdownSound.durationMs: Int
    get() = when (this) {
        CountdownSound.BEEP -> 200
        CountdownSound.DOUBLE_BEEP -> 400
        CountdownSound.PIPS -> 800
    }
