package com.hackerapps.c2k

import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDayTest {

    private fun day(vararg intervals: Interval) = WorkoutDay(week = 1, day = 1, intervals = intervals.toList())

    @Test
    fun skipping_both_drops_warmup_and_cooldown_only() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.WALK, 90),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.skipping(warmup = true, cooldown = true)

        assertEquals(listOf(IntervalType.RUN, IntervalType.WALK, IntervalType.RUN), result.intervals.map { it.type })
    }

    @Test
    fun skipping_warmup_only_keeps_cooldown() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.skipping(warmup = true, cooldown = false)

        assertEquals(listOf(IntervalType.RUN, IntervalType.COOLDOWN), result.intervals.map { it.type })
    }

    @Test
    fun skipping_cooldown_only_keeps_warmup() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.skipping(warmup = false, cooldown = true)

        assertEquals(listOf(IntervalType.WARMUP, IntervalType.RUN), result.intervals.map { it.type })
    }

    @Test
    fun skipping_nothing_returns_the_same_intervals() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.skipping(warmup = false, cooldown = false)

        assertEquals(original.intervals, result.intervals)
    }

    @Test
    fun skipping_preserves_order_and_durations_of_remaining_intervals() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.WALK, 90)
        )

        val result = original.skipping(warmup = true, cooldown = true)

        assertEquals(listOf(Interval(IntervalType.RUN, 60), Interval(IntervalType.WALK, 90)), result.intervals)
    }

    @Test
    fun skipping_preserves_week_and_day() {
        val original = WorkoutDay(week = 3, day = 2, intervals = listOf(Interval(IntervalType.RUN, 60)))

        val result = original.skipping(warmup = true, cooldown = true)

        assertEquals(3, result.week)
        assertEquals(2, result.day)
    }

    @Test
    fun skipping_is_a_no_op_when_there_is_no_warmup_or_cooldown() {
        val original = day(Interval(IntervalType.RUN, 60), Interval(IntervalType.WALK, 90))

        val result = original.skipping(warmup = true, cooldown = true)

        assertEquals(original.intervals, result.intervals)
    }

    @Test
    fun skipping_both_reduces_total_duration_by_exactly_the_warmup_and_cooldown_time() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.skipping(warmup = true, cooldown = true)

        assertEquals(original.totalDurationSeconds - 600, result.totalDurationSeconds)
    }

    // Every program day ships a warm-up, a cool-down, and at least one run interval in between
    // (WorkoutService relies on this: WorkoutEngine.start() indexes intervals[0], so an empty
    // result would crash it). If a future program ever violated this, it would silently produce
    // an empty — and crashing — day whenever these preferences are on, so this guards it directly
    // against the real program data rather than just hand-built fixtures above.
    @Test
    fun skipping_both_never_empties_a_real_program_day() {
        Programs.all().forEach { plan ->
            plan.weeks.flatten().forEach { day ->
                val result = day.skipping(warmup = true, cooldown = true)
                assertTrue(
                    "${plan.programId} W${day.week}D${day.day} had no run/walk intervals left",
                    result.intervals.isNotEmpty()
                )
                assertFalse(
                    "${plan.programId} W${day.week}D${day.day} still had a warm-up/cool-down left",
                    result.intervals.any { it.type == IntervalType.WARMUP || it.type == IntervalType.COOLDOWN }
                )
            }
        }
    }
}
