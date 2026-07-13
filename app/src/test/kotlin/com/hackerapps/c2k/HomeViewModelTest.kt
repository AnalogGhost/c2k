package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.ui.screen.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelTest {

    private val msPerDay = 24 * 60 * 60 * 1000L

    private fun session(startedAt: Long, completed: Boolean = true) = WorkoutSessionEntity(
        programId = "c25k",
        week = 1,
        day = 1,
        startedAt = startedAt,
        durationSeconds = 600,
        distanceMeters = 1000f,
        completed = completed
    )

    // --- computeStreak ---

    @Test
    fun streak_is_zero_with_no_sessions() {
        assertEquals(0, HomeViewModel.computeStreak(emptyList(), now()))
    }

    @Test
    fun streak_is_zero_when_all_sessions_incomplete() {
        val sessions = listOf(session(now(), completed = false))
        assertEquals(0, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_is_one_for_single_completion_today() {
        val sessions = listOf(session(now()))
        assertEquals(1, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_is_one_for_single_completion_yesterday() {
        val sessions = listOf(session(now() - msPerDay))
        assertEquals(1, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_is_zero_when_last_completion_was_two_days_ago() {
        val sessions = listOf(session(now() - 2 * msPerDay))
        assertEquals(0, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_counts_consecutive_days() {
        val sessions = (0..4).map { session(now() - it * msPerDay) }
        assertEquals(5, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_breaks_at_gap() {
        // today, yesterday, then a gap, then day-4 and day-5 — streak should stop at the gap
        val sessions = listOf(
            session(now()),
            session(now() - msPerDay),
            session(now() - 3 * msPerDay),
            session(now() - 4 * msPerDay)
        )
        assertEquals(2, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_ignores_incomplete_sessions_within_range() {
        val sessions = listOf(
            session(now()),
            session(now() - msPerDay, completed = false),
            session(now() - 2 * msPerDay)
        )
        // yesterday's session doesn't count, so the streak breaks after today
        assertEquals(1, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_dedupes_multiple_sessions_on_same_day() {
        val sessions = listOf(session(now()), session(now() + 1000), session(now() - msPerDay))
        assertEquals(2, HomeViewModel.computeStreak(sessions, now()))
    }

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
    fun next_workout_is_null_when_program_fully_completed() {
        val completed = setOf(1 to 1, 1 to 2, 1 to 3, 2 to 1, 2 to 2, 2 to 3)
        val next = HomeViewModel.computeNextWorkout(plan(2), completed)
        assertNull(next)
    }

    private fun now() = System.currentTimeMillis()
}
