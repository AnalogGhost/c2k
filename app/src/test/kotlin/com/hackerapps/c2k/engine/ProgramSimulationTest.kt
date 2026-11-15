package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import com.hackerapps.c2k.engine.tts.TtsInterface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs every day of every program through the engine on a virtual clock and checks what the
 * runner would hear and see: every interval in order, at full length, with each countdown
 * warning once, and the workout ending when the plan does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramSimulationTest {

    private data class Run(
        val day: WorkoutDay,
        val announcements: List<TtsAnnouncement>,
        val elapsedSeconds: Int,
        val elapsedMs: Long,
    )

    private val allDays: List<Pair<String, WorkoutDay>> = Programs.all().flatMap { plan ->
        plan.weeks.flatten().map { "${plan.programId} W${it.week}D${it.day}" to it }
    }

    /**
     * Runs [day] to completion. [tickMs] is how far the clock moves per 200 ms tick of the loop:
     * 200 is an ideal device; a little more stands in for a real one, where each delay(200)
     * returns a few milliseconds late.
     */
    private fun simulate(day: WorkoutDay, tickMs: Double = 200.0): Run {
        val scope = TestScope(StandardTestDispatcher())
        val announcements = mutableListOf<TtsAnnouncement>()
        val tts = object : TtsInterface {
            override val isAvailable = true
            override fun announce(announcement: TtsAnnouncement, queueAdd: Boolean) { announcements += announcement }
            override fun shutdown() {}
        }
        val engine = WorkoutEngine(
            day = day, tts = tts, ttsEnabled = true,
            countdownWarnings = true, countdownWarningSeconds1 = 10, countdownWarningSeconds2 = 5,
            countdownWarning2Enabled = true, midIntervalCues = true,
            scope = scope,
            clock = { (scope.testScheduler.currentTime / 200.0 * tickMs).toLong() },
        )
        var completed: WorkoutState.Completed? = null
        scope.runTest {
            engine.start(1L)
            advanceUntilIdle()
            completed = engine.state.value as? WorkoutState.Completed
        }
        val done = checkNotNull(completed) { "${day.week}/${day.day} never completed" }
        val endMs = (scope.testScheduler.currentTime / 200.0 * tickMs).toLong()
        return Run(day, announcements, done.elapsedSeconds(), endMs)
    }

    private fun WorkoutState.Completed.elapsedSeconds() = elapsedSessionSeconds

    private fun check(problems: List<String>) =
        assertTrue("${problems.size} problem(s):\n" + problems.take(20).joinToString("\n"), problems.isEmpty())

    @Test
    fun every_program_day_has_runnable_intervals() {
        check(allDays.flatMap { (name, day) ->
            listOfNotNull(
                "$name has no intervals".takeIf { day.intervals.isEmpty() },
                "$name has an interval under 1 s".takeIf { day.intervals.any { it.durationSeconds < 1 } },
                "$name has no run".takeIf { day.intervals.none { it.type == IntervalType.RUN } },
            )
        })
    }

    @Test
    fun every_program_day_plays_its_intervals_in_order_and_ends_on_time() {
        check(allDays.flatMap { (name, day) ->
            val run = simulate(day)
            val started = run.announcements.filterIsInstance<TtsAnnouncement.IntervalStart>().map { it.interval }
            listOfNotNull(
                "$name played $started, planned ${day.intervals}".takeIf { started != day.intervals },
                "$name ended at ${run.elapsedSeconds} s, planned ${day.totalDurationSeconds} s"
                    .takeIf { run.elapsedSeconds != day.totalDurationSeconds },
                "$name: 'workout complete' should be the last cue, once"
                    .takeIf { run.announcements.count { it == TtsAnnouncement.WorkoutComplete } != 1 || run.announcements.last() != TtsAnnouncement.WorkoutComplete },
            )
        })
    }

    @Test
    fun every_countdown_warning_is_spoken_once_per_interval() {
        // On a slightly slow clock too: a tick that lands late must not skip a warning.
        for (tickMs in listOf(200.0, 214.0)) {
            check(allDays.flatMap { (name, day) ->
                val run = simulate(day, tickMs)
                // A warning only fires once the interval has run a moment, so a 10 s interval
                // gets the 5 s warning but not the 10 s one.
                val expected = day.intervals.sumOf { i -> listOf(10, 5).count { it < i.durationSeconds } }
                val spoken = run.announcements.count { it is TtsAnnouncement.CountdownWarning }
                listOfNotNull("$name (tick $tickMs ms): $spoken warnings, expected $expected".takeIf { spoken != expected })
            })
        }
    }

    @Test
    fun the_last_run_is_announced_once() {
        check(allDays.flatMap { (name, day) ->
            val lastRunIndex = day.intervals.indexOfLast { it.type == IntervalType.RUN }
            val expected = if (lastRunIndex > 0) 1 else 0
            val spoken = simulate(day).announcements.count { it == TtsAnnouncement.LastRunInterval }
            listOfNotNull("$name: 'last run' spoken $spoken times, expected $expected".takeIf { spoken != expected })
        })
    }

    @Test
    fun a_slow_clock_does_not_stretch_the_workout() {
        // Each interval change lands on a tick, a little after the boundary. The engine carries
        // that overshoot into the next interval, so the workout as a whole overruns by at most
        // the final tick. Before that, a 26-minute, 18-interval day ran about 2 s over.
        val tickMs = 214.0
        check(allDays.flatMap { (name, day) ->
            val run = simulate(day, tickMs)
            val overMs = run.elapsedMs - day.totalDurationSeconds * 1000L
            listOfNotNull(
                "$name ran $overMs ms over its ${day.totalDurationSeconds} s plan"
                    .takeIf { overMs < 0 || overMs > tickMs }
            )
        })
    }
}
