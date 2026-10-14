package com.hackerapps.c2k.engine.tone

import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.engine.countdownWarningThresholds
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToneCuesTest {

    private val bothWarnings = countdownWarningThresholds(10, 5, warning2Enabled = true)

    private fun start(type: IntervalType) = TtsAnnouncement.IntervalStart(Interval(type, 60))

    @Test
    fun each_interval_type_starts_with_its_own_chime() {
        assertEquals(Chime.WARMUP, ToneCues.chimeFor(start(IntervalType.WARMUP), bothWarnings))
        assertEquals(Chime.RUN, ToneCues.chimeFor(start(IntervalType.RUN), bothWarnings))
        assertEquals(Chime.WALK, ToneCues.chimeFor(start(IntervalType.WALK), bothWarnings))
        assertEquals(Chime.COOLDOWN, ToneCues.chimeFor(start(IntervalType.COOLDOWN), bothWarnings))
    }

    @Test
    fun workout_complete_plays_the_completion_chime() {
        assertEquals(Chime.COMPLETE, ToneCues.chimeFor(TtsAnnouncement.WorkoutComplete, bothWarnings))
    }

    @Test
    fun the_first_warning_ticks_once_and_the_second_twice() {
        assertEquals(Chime.COUNTDOWN_FIRST, ToneCues.chimeFor(TtsAnnouncement.CountdownWarning(10), bothWarnings))
        assertEquals(Chime.COUNTDOWN_SECOND, ToneCues.chimeFor(TtsAnnouncement.CountdownWarning(5), bothWarnings))
    }

    @Test
    fun the_order_the_warnings_fire_in_decides_the_ticks_not_which_setting_they_came_from() {
        // A second warning set larger than the first fires first, so it is the single tick.
        val swapped = countdownWarningThresholds(5, 10, warning2Enabled = true)
        assertEquals(Chime.COUNTDOWN_FIRST, ToneCues.chimeFor(TtsAnnouncement.CountdownWarning(10), swapped))
        assertEquals(Chime.COUNTDOWN_SECOND, ToneCues.chimeFor(TtsAnnouncement.CountdownWarning(5), swapped))
    }

    @Test
    fun a_lone_warning_is_a_single_tick() {
        listOf(
            countdownWarningThresholds(10, 5, warning2Enabled = false),
            countdownWarningThresholds(5, 5, warning2Enabled = true),
            countdownWarningThresholds(10, 0, warning2Enabled = true)
        ).forEach { thresholds ->
            assertEquals(1, thresholds.size)
            assertEquals(
                Chime.COUNTDOWN_FIRST,
                ToneCues.chimeFor(TtsAnnouncement.CountdownWarning(thresholds.single()), thresholds)
            )
        }
    }

    @Test
    fun word_only_cues_have_no_chime() {
        listOf(
            TtsAnnouncement.NextInterval(Interval(IntervalType.RUN, 60)),
            TtsAnnouncement.IntervalMidpoint(0),
            TtsAnnouncement.PeriodicTimeRemaining(30),
            TtsAnnouncement.Halfway,
            TtsAnnouncement.LastRunInterval
        ).forEach { assertNull(it.toString(), ToneCues.chimeFor(it, bothWarnings)) }
    }
}
