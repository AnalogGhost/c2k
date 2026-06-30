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
        midIntervalCues: Boolean = false
    ): WorkoutEngine =
        WorkoutEngine(
            day = WorkoutDay(week = 1, day = 1, intervals = intervals.toList()),
            tts = fakeTts,
            ttsEnabled = true,
            countdownWarnings = false,
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
