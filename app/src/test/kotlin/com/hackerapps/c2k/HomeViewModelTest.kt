package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.data.prefs.RepeatedWeek
import com.hackerapps.c2k.ui.screen.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelTest {

    // --- computeNextWorkout ---

    private fun plan(weeks: Int, daysPerWeek: Int = 3): WorkoutPlan = WorkoutPlan(
        programId = "test",
        displayName = "Test Plan",
        description = "",
        weeks = (1..weeks).map { w ->
            (1..daysPerWeek).map { d ->
                WorkoutDay(week = w, day = d, intervals = listOf(Interval(IntervalType.RUN, 60)))
            }
        }
    )

    @Test
    fun next_workout_is_week1_day1_when_nothing_completed() {
        val next = HomeViewModel.computeNextWorkout(plan(2), emptySet())
        assertEquals(1, next?.week)
        assertEquals(1, next?.day)
    }

    @Test
    fun next_workout_skips_completed_days() {
        val completed = setOf(1 to 1, 1 to 2)
        val next = HomeViewModel.computeNextWorkout(plan(2), completed)
        assertEquals(1, next?.week)
        assertEquals(3, next?.day)
    }

    @Test
    fun next_workout_crosses_into_next_week() {
        val completed = setOf(1 to 1, 1 to 2, 1 to 3)
        val next = HomeViewModel.computeNextWorkout(plan(2), completed)
        assertEquals(2, next?.week)
        assertEquals(1, next?.day)
    }

    @Test
    fun next_workout_follows_latest_completed_when_user_skipped_ahead() {
        // User skipped weeks 1-2 and started at week 3 (issue #28): the suggestion must
        // move forward from their latest completed day, not point back at the earliest gap.
        val completed = setOf(3 to 1, 3 to 2)
        val next = HomeViewModel.computeNextWorkout(plan(4), completed)
        assertEquals(3, next?.week)
        assertEquals(3, next?.day)
    }

    @Test
    fun next_workout_crosses_week_after_skipped_ahead_week_completes() {
        val completed = setOf(3 to 1, 3 to 2, 3 to 3)
        val next = HomeViewModel.computeNextWorkout(plan(4), completed)
        assertEquals(4, next?.week)
        assertEquals(1, next?.day)
    }

    @Test
    fun next_workout_ignores_gaps_behind_the_latest_completed_day() {
        // Day 1-2 was skipped but 1-3 is done: suggest 2-1, not the 1-2 gap.
        val completed = setOf(1 to 1, 1 to 3)
        val next = HomeViewModel.computeNextWorkout(plan(2), completed)
        assertEquals(2, next?.week)
        assertEquals(1, next?.day)
    }

    @Test
    fun next_workout_is_null_when_final_day_completed_despite_earlier_gaps() {
        val completed = setOf(2 to 3)
        val next = HomeViewModel.computeNextWorkout(plan(2), completed)
        assertNull(next)
    }

    @Test
    fun next_workout_is_null_when_program_fully_completed() {
        val completed = setOf(1 to 1, 1 to 2, 1 to 3, 2 to 1, 2 to 2, 2 to 3)
        val next = HomeViewModel.computeNextWorkout(plan(2), completed)
        assertNull(next)
    }

    // --- repeating a week ---

    private fun finished(week: Int, day: Int, startedAt: Long) = WorkoutSessionEntity(
        programId = "test", week = week, day = day, startedAt = startedAt,
        durationSeconds = 60, distanceMeters = 0f, completed = true
    )

    @Test
    fun repeated_week_comes_before_the_day_the_runner_was_stuck_on() {
        val earlier = (1..2).flatMap { w -> (1..3).map { d -> finished(w, d, startedAt = 10L * w + d) } }
        val completed = earlier.map { it.week to it.day }.toSet()
        val next = HomeViewModel.computeNextWorkout(
            plan(3), completed, RepeatedWeek("test", week = 2, sinceMillis = 100), earlier
        )
        assertEquals(2, next?.week)
        assertEquals(1, next?.day)
    }

    @Test
    fun finishing_the_repeated_week_returns_to_the_usual_next_workout() {
        val earlier = (1..2).flatMap { w -> (1..3).map { d -> finished(w, d, startedAt = 10L * w + d) } }
        val redone = (1..3).map { d -> finished(2, d, startedAt = 100L + d) }
        val sessions = earlier + redone
        val completed = sessions.map { it.week to it.day }.toSet()
        val next = HomeViewModel.computeNextWorkout(
            plan(3), completed, RepeatedWeek("test", week = 2, sinceMillis = 100), sessions
        )
        assertEquals(3, next?.week)
        assertEquals(1, next?.day)
    }
}
