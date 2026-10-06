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
    fun withoutWarmupCooldown_drops_warmup_and_cooldown_only() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.WALK, 90),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.withoutWarmupCooldown()

        assertEquals(listOf(IntervalType.RUN, IntervalType.WALK, IntervalType.RUN), result.intervals.map { it.type })
    }

    @Test
    fun withoutWarmupCooldown_preserves_order_and_durations_of_remaining_intervals() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.WALK, 90)
        )

        val result = original.withoutWarmupCooldown()

        assertEquals(listOf(Interval(IntervalType.RUN, 60), Interval(IntervalType.WALK, 90)), result.intervals)
    }

    @Test
    fun withoutWarmupCooldown_preserves_week_and_day() {
        val original = WorkoutDay(week = 3, day = 2, intervals = listOf(Interval(IntervalType.RUN, 60)))

        val result = original.withoutWarmupCooldown()

        assertEquals(3, result.week)
        assertEquals(2, result.day)
    }

    @Test
    fun withoutWarmupCooldown_is_a_no_op_when_there_is_no_warmup_or_cooldown() {
        val original = day(Interval(IntervalType.RUN, 60), Interval(IntervalType.WALK, 90))

        val result = original.withoutWarmupCooldown()

        assertEquals(original.intervals, result.intervals)
    }

    @Test
    fun withoutWarmupCooldown_reduces_total_duration_by_exactly_the_warmup_and_cooldown_time() {
        val original = day(
            Interval(IntervalType.WARMUP, 300),
            Interval(IntervalType.RUN, 60),
            Interval(IntervalType.COOLDOWN, 300)
        )

        val result = original.withoutWarmupCooldown()

        assertEquals(original.totalDurationSeconds - 600, result.totalDurationSeconds)
    }

    // Every program day ships a warm-up, a cool-down, and at least one run interval in between
    // (WorkoutService relies on this: WorkoutEngine.start() indexes intervals[0], so an empty
    // result would crash it). If a future program ever violated this, it would silently produce
    // an empty — and crashing — day whenever this preference is on, so this guards it directly
    // against the real program data rather than just hand-built fixtures above.
    @Test
    fun withoutWarmupCooldown_never_empties_a_real_program_day() {
        Programs.all().forEach { plan ->
            plan.weeks.flatten().forEach { day ->
                val result = day.withoutWarmupCooldown()
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
