package org.ctok

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.ctok.data.model.Interval
import org.ctok.data.model.IntervalType
import org.ctok.data.model.WorkoutDay
import org.ctok.engine.WorkoutEngine
import org.ctok.engine.WorkoutState
import org.ctok.engine.tts.TtsAnnouncement
import org.ctok.engine.tts.TtsInterface
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
        override fun announce(a: TtsAnnouncement) { announcements.add(a) }
        override fun shutdown() {}
    }

    private fun makeEngine(vararg intervals: Interval): WorkoutEngine =
        WorkoutEngine(
            day = WorkoutDay(week = 1, day = 1, intervals = intervals.toList()),
            tts = fakeTts,
            ttsEnabled = true,
            countdownWarnings = false,
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
