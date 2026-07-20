package com.hackerapps.c2k

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.engine.WorkoutEngine
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import com.hackerapps.c2k.engine.tts.TtsInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val announcements = mutableListOf<TtsAnnouncement>()

    private val fakeTts = object : TtsInterface {
        override val isAvailable = true
        override fun announce(a: TtsAnnouncement, queueAdd: Boolean) { announcements.add(a) }
        override fun shutdown() {}
    }

    private fun makeEngine(
        vararg intervals: Interval,
        midIntervalCues: Boolean = false,
        countdownWarnings: Boolean = false,
        countdownWarningSeconds1: Int = 10,
        countdownWarningSeconds2: Int = 5
    ): WorkoutEngine =
        WorkoutEngine(
            day = WorkoutDay(week = 1, day = 1, intervals = intervals.toList()),
            tts = fakeTts,
            ttsEnabled = true,
            countdownWarnings = countdownWarnings,
            countdownWarningSeconds1 = countdownWarningSeconds1,
            countdownWarningSeconds2 = countdownWarningSeconds2,
            midIntervalCues = midIntervalCues,
            scope = testScope,
            // Virtual clock: testScope.currentTime advances with advanceTimeBy()
            clock = { testScope.testScheduler.currentTime }
        )

    @Test
    fun transitions_to_active_after_start() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 60))
        engine.start(1L)
        advanceTimeBy(300)
        assertTrue("Expected Active", engine.state.value is WorkoutState.Active)
        engine.stop()
    }

    @Test
    fun first_interval_is_correct_type() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.WARMUP, 5),
            Interval(IntervalType.RUN, 10)
        )
        engine.start(1L)
        advanceTimeBy(300)
        val s = engine.state.value as WorkoutState.Active
        assertEquals(IntervalType.WARMUP, s.currentInterval.type)
        engine.stop()
    }

    @Test
    fun advances_to_next_interval_after_first_expires() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.WARMUP, 2),
            Interval(IntervalType.RUN, 10)
        )
        engine.start(1L)
        advanceTimeBy(2_500)  // 2.5s → 2s warmup finishes
        val s = engine.state.value as WorkoutState.Active
        assertEquals(IntervalType.RUN, s.currentInterval.type)
    }

    @Test
    fun completes_after_all_intervals() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 1))
        engine.start(1L)
        advanceTimeBy(1_500)  // 1.5s → 1s interval finishes
        assertTrue("Expected Completed", engine.state.value is WorkoutState.Completed)
    }

    @Test
    fun pause_emits_paused_state() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 60))
        engine.start(1L)
        advanceTimeBy(500)
        engine.pause()
        assertTrue("Expected Paused", engine.state.value is WorkoutState.Paused)
        engine.stop()  // cancel tick loop so runTest doesn't see uncompleted coroutines
    }

    @Test
    fun resume_after_pause_returns_to_active() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 60))
        engine.start(1L)
        advanceTimeBy(500)
        engine.pause()
        advanceTimeBy(2_000)  // time passes while paused — shouldn't count
        engine.resume()
        advanceTimeBy(300)
        assertTrue("Expected Active after resume", engine.state.value is WorkoutState.Active)
        engine.stop()
    }

    @Test
    fun paused_time_does_not_count_toward_interval() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 3))
        engine.start(1L)
        advanceTimeBy(500)
        engine.pause()
        advanceTimeBy(10_000)  // 10s paused — should not expire the 3s interval
        engine.resume()
        advanceTimeBy(300)
        // Should still be Active (only 0.5s of real interval time has passed)
        assertTrue("Interval should not have expired during pause",
            engine.state.value is WorkoutState.Active)
        engine.stop()
    }

    @Test
    fun tts_announces_on_interval_start() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 5))
        engine.start(1L)
        advanceTimeBy(300)
        assertTrue("Expected at least one announcement", announcements.isNotEmpty())
        assertTrue(announcements.first() is TtsAnnouncement.IntervalStart)
    }

    @Test
    fun tts_announces_workout_complete() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 1))
        engine.start(1L)
        advanceTimeBy(1_500)
        assertTrue("Expected WorkoutComplete announcement",
            announcements.any { it is TtsAnnouncement.WorkoutComplete })
    }

    @Test
    fun mid_interval_cue_fires_at_halfway_for_long_run() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 60), midIntervalCues = true)
        engine.start(1L)
        advanceTimeBy(29_500)  // 29.5s — just before midpoint
        val beforeMid = announcements.count { it is TtsAnnouncement.IntervalMidpoint }
        assertEquals("No midpoint cue yet at 29s", 0, beforeMid)
        advanceTimeBy(1_000)  // 30.5s — past midpoint
        val afterMid = announcements.count { it is TtsAnnouncement.IntervalMidpoint }
        assertEquals("Midpoint cue should have fired once", 1, afterMid)
        engine.stop()
    }

    @Test
    fun mid_interval_cue_does_not_fire_for_short_run() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 30), midIntervalCues = true)
        engine.start(1L)
        advanceTimeBy(31_000)  // past the end
        assertTrue("Short interval should have completed", engine.state.value is WorkoutState.Completed)
        val midpoints = announcements.count { it is TtsAnnouncement.IntervalMidpoint }
        assertEquals("No midpoint cue for <60s interval", 0, midpoints)
    }

    @Test
    fun mid_interval_cue_fires_once_per_interval() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.WALK, 30),
            Interval(IntervalType.RUN, 60),
            midIntervalCues = true
        )
        engine.start(1L)
        advanceTimeBy(150_500)  // past both run intervals
        val midpoints = announcements.count { it is TtsAnnouncement.IntervalMidpoint }
        assertEquals("One midpoint cue per qualifying run interval", 2, midpoints)
    }

    @Test
    fun countdown_warnings_fire_at_default_thresholds() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.RUN, 15),
            Interval(IntervalType.WALK, 30),
            countdownWarnings = true
        )
        engine.start(1L)
        advanceTimeBy(5_300)  // 5s elapsed of a 15s interval -> 10s remaining
        assertEquals(1, announcements.count {
            it is TtsAnnouncement.CountdownWarning && it.secondsRemaining == 10
        })
        advanceTimeBy(5_000)  // 10.3s elapsed -> 5s remaining
        assertEquals(1, announcements.count {
            it is TtsAnnouncement.CountdownWarning && it.secondsRemaining == 5
        })
        assertTrue("Final (smallest) threshold should announce the next interval",
            announcements.any { it is TtsAnnouncement.NextInterval })
        engine.stop()
    }

    @Test
    fun countdown_warnings_fire_at_custom_thresholds() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.RUN, 20),
            countdownWarnings = true,
            countdownWarningSeconds1 = 15,
            countdownWarningSeconds2 = 8
        )
        engine.start(1L)
        advanceTimeBy(5_300)  // 5s elapsed -> 15s remaining
        assertEquals(1, announcements.count {
            it is TtsAnnouncement.CountdownWarning && it.secondsRemaining == 15
        })
        advanceTimeBy(5_000)  // 10.3s elapsed -> 10s remaining, not a configured threshold
        assertEquals(0, announcements.count {
            it is TtsAnnouncement.CountdownWarning && it.secondsRemaining == 10
        })
        advanceTimeBy(2_000)  // 12.3s elapsed -> 8s remaining, the final configured threshold
        assertEquals(1, announcements.count {
            it is TtsAnnouncement.CountdownWarning && it.secondsRemaining == 8
        })
        assertTrue("No next interval exists, so no look-ahead should be announced",
            announcements.none { it is TtsAnnouncement.NextInterval })
        engine.stop()
    }

    @Test
    fun countdown_warnings_do_not_fire_when_disabled() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 15), countdownWarnings = false)
        engine.start(1L)
        advanceTimeBy(10_300)  // crosses both default 10s and 5s thresholds
        assertEquals(0, announcements.count { it is TtsAnnouncement.CountdownWarning })
        engine.stop()
    }

    @Test
    fun countdown_warning_fires_only_once_per_interval() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 15), countdownWarnings = true)
        engine.start(1L)
        advanceTimeBy(5_900)  // several 200ms ticks land on the same "10s remaining" second
        assertEquals(1, announcements.count {
            it is TtsAnnouncement.CountdownWarning && it.secondsRemaining == 10
        })
        engine.stop()
    }

    @Test
    fun equal_thresholds_collapse_to_single_warning_with_lookahead() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.RUN, 15),
            Interval(IntervalType.WALK, 30),
            countdownWarnings = true,
            countdownWarningSeconds1 = 10,
            countdownWarningSeconds2 = 10
        )
        engine.start(1L)
        advanceTimeBy(5_300)  // 10s remaining
        assertEquals(1, announcements.count { it is TtsAnnouncement.CountdownWarning })
        assertTrue(announcements.any { it is TtsAnnouncement.NextInterval })
        engine.stop()
    }

    @Test
    fun threshold_larger_than_interval_never_fires() = testScope.runTest {
        val engine = makeEngine(
            Interval(IntervalType.RUN, 15),
            countdownWarnings = true,
            countdownWarningSeconds1 = 30,
            countdownWarningSeconds2 = 25
        )
        engine.start(1L)
        advanceTimeBy(15_500)  // whole interval elapses
        assertTrue("Should complete normally without crashing",
            engine.state.value is WorkoutState.Completed)
        assertEquals(0, announcements.count { it is TtsAnnouncement.CountdownWarning })
    }

    @Test
    fun remaining_time_counts_down() = testScope.runTest {
        val engine = makeEngine(Interval(IntervalType.RUN, 10))
        engine.start(1L)
        advanceTimeBy(3_300)  // 3.3s elapsed
        val s = engine.state.value as WorkoutState.Active
        // 10s interval, 3s elapsed → 7s remaining (integer seconds)
        assertEquals(7, s.secondsRemainingInInterval)
        engine.stop()
    }
}
