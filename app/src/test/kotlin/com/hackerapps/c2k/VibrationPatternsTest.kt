package com.hackerapps.c2k

import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.prefs.VibrationStrength
import com.hackerapps.c2k.service.VibrationPatterns
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationPatternsTest {

    @Test
    fun missing_or_invalid_stored_strength_defaults_to_medium() {
        assertEquals(VibrationStrength.MEDIUM, VibrationStrength.fromStored(null))
        assertEquals(VibrationStrength.MEDIUM, VibrationStrength.fromStored("unexpected"))
        assertEquals(VibrationStrength.STRONG, VibrationStrength.fromStored("STRONG"))
    }

    @Test
    fun run_patterns_scale_pulse_duration_and_amplitude() {
        assertPattern(
            VibrationPatterns.forInterval(IntervalType.RUN, VibrationStrength.LIGHT),
            longArrayOf(0, 100, 100, 100),
            96
        )
        assertPattern(
            VibrationPatterns.forInterval(IntervalType.RUN, VibrationStrength.MEDIUM),
            longArrayOf(0, 150, 100, 150),
            null
        )
        assertPattern(
            VibrationPatterns.forInterval(IntervalType.RUN, VibrationStrength.STRONG),
            longArrayOf(0, 300, 100, 300),
            255
        )
    }

    @Test
    fun lower_intensity_intervals_share_single_pulse_patterns() {
        listOf(IntervalType.WALK, IntervalType.WARMUP, IntervalType.COOLDOWN).forEach { type ->
            assertPattern(
                VibrationPatterns.forInterval(type, VibrationStrength.LIGHT),
                longArrayOf(0, 125),
                96
            )
            assertPattern(
                VibrationPatterns.forInterval(type, VibrationStrength.MEDIUM),
                longArrayOf(0, 200),
                null
            )
            assertPattern(
                VibrationPatterns.forInterval(type, VibrationStrength.STRONG),
                longArrayOf(0, 400),
                255
            )
        }
    }

    @Test
    fun completion_patterns_scale_pulse_duration_and_amplitude() {
        assertPattern(
            VibrationPatterns.forCompletion(VibrationStrength.LIGHT),
            longArrayOf(0, 200, 150, 200, 150, 350),
            96
        )
        assertPattern(
            VibrationPatterns.forCompletion(VibrationStrength.MEDIUM),
            longArrayOf(0, 300, 150, 300, 150, 500),
            null
        )
        assertPattern(
            VibrationPatterns.forCompletion(VibrationStrength.STRONG),
            longArrayOf(0, 500, 150, 500, 150, 800),
            255
        )
    }

    private fun assertPattern(
        actual: com.hackerapps.c2k.service.VibrationPattern,
        expectedTimings: LongArray,
        expectedAmplitude: Int?
    ) {
        assertArrayEquals(expectedTimings, actual.timings)
        assertEquals(expectedAmplitude, actual.amplitude)
    }
}
