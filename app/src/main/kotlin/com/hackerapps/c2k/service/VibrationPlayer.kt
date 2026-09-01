package com.hackerapps.c2k.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.prefs.VibrationStrength

internal data class VibrationPattern(
    val timings: LongArray,
    val amplitude: Int?
)

internal object VibrationPatterns {
    fun forInterval(type: IntervalType, strength: VibrationStrength): VibrationPattern {
        val timings = when (strength) {
            VibrationStrength.LIGHT -> when (type) {
                IntervalType.RUN -> longArrayOf(0, 100, 100, 100)
                else -> longArrayOf(0, 125)
            }
            VibrationStrength.MEDIUM -> when (type) {
                IntervalType.RUN -> longArrayOf(0, 150, 100, 150)
                else -> longArrayOf(0, 200)
            }
            VibrationStrength.STRONG -> when (type) {
                IntervalType.RUN -> longArrayOf(0, 300, 100, 300)
                else -> longArrayOf(0, 400)
            }
        }
        return VibrationPattern(timings, strength.amplitude)
    }

    fun forCompletion(strength: VibrationStrength): VibrationPattern {
        val timings = when (strength) {
            VibrationStrength.LIGHT -> longArrayOf(0, 200, 150, 200, 150, 350)
            VibrationStrength.MEDIUM -> longArrayOf(0, 300, 150, 300, 150, 500)
            VibrationStrength.STRONG -> longArrayOf(0, 500, 150, 500, 150, 800)
        }
        return VibrationPattern(timings, strength.amplitude)
    }

    private val VibrationStrength.amplitude: Int?
        get() = when (this) {
            VibrationStrength.LIGHT -> 96
            VibrationStrength.MEDIUM -> null
            VibrationStrength.STRONG -> 255
        }
}

internal object VibrationPlayer {
    fun play(context: Context, pattern: VibrationPattern) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        }

        val effect = if (pattern.amplitude != null && vibrator.hasAmplitudeControl()) {
            val amplitudes = IntArray(pattern.timings.size) { index ->
                if (index % 2 == 0) 0 else pattern.amplitude
            }
            VibrationEffect.createWaveform(pattern.timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(pattern.timings, -1)
        }
        vibrator.vibrate(effect)
    }
}
