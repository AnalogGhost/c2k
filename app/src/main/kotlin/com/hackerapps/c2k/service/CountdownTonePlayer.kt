package com.hackerapps.c2k.service

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlin.math.roundToInt

/**
 * Plays a short built-in tone for a countdown warning: a single beep for the first warning, a
 * double beep for the second.
 *
 * Uses [AudioManager.STREAM_MUSIC], the same output stream as the spoken cues
 * (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE), so the tone follows the media volume and mixes over
 * music. No audio focus is requested: the tones are under a second long.
 */
internal class CountdownTonePlayer private constructor(
    private val generator: ToneGenerator
) {
    private var released = false

    @Synchronized
    fun play(warningNumber: Int) {
        if (released) return
        if (warningNumber <= 1) {
            generator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200) // 400 + 1200 Hz, 200 ms
        } else {
            generator.startTone(ToneGenerator.TONE_PROP_ACK, 400)    // 1200 Hz, 2 x 100 ms
        }
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
        fun createOrNull(volume: Float): CountdownTonePlayer? = try {
            val level = (volume.coerceIn(0f, 1f) * ToneGenerator.MAX_VOLUME).roundToInt()
            CountdownTonePlayer(ToneGenerator(AudioManager.STREAM_MUSIC, level))
        } catch (e: RuntimeException) {
            Log.w(TAG, "ToneGenerator unavailable; falling back to spoken countdown", e)
            null
        }
    }
}
