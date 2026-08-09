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
import java.time.Instant
import java.util.TimeZone

class HomeViewModelTest {

    private val msPerDay = 24 * 60 * 60 * 1000L
    private val msPerWeek = 7 * msPerDay

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
    fun streak_is_one_for_single_completion_this_week() {
        val sessions = listOf(session(now()))
        assertEquals(1, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_is_one_for_single_completion_last_week() {
        // Current week hasn't had its workout yet — the streak survives on last week's.
        val sessions = listOf(session(now() - msPerWeek))
        assertEquals(1, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_is_zero_when_last_completion_was_two_weeks_ago() {
        val sessions = listOf(session(now() - 2 * msPerWeek))
        assertEquals(0, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_counts_consecutive_weeks() {
        val sessions = (0..4).map { session(now() - it * msPerWeek) }
        assertEquals(5, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_breaks_at_gap() {
        // this week, last week, then a gap, then weeks -3 and -4 — streak stops at the gap
        val sessions = listOf(
            session(now()),
            session(now() - msPerWeek),
            session(now() - 3 * msPerWeek),
            session(now() - 4 * msPerWeek)
        )
        assertEquals(2, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_ignores_incomplete_sessions_within_range() {
        val sessions = listOf(
            session(now()),
            session(now() - msPerWeek, completed = false),
            session(now() - 2 * msPerWeek)
        )
        // last week's session doesn't count, so the streak breaks after this week
        assertEquals(1, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_dedupes_multiple_sessions_in_same_week() {
        // Three runs this week still count as one streak-week — exactly the pattern the
        // switch from day streaks was made for (issue #29).
        val sessions = listOf(
            session(now()),
            session(now() - msPerDay),
            session(now() - 2 * msPerDay),
            session(now() - msPerWeek)
        )
        assertEquals(2, HomeViewModel.computeStreak(sessions, now()))
    }

    @Test
    fun streak_uses_local_calendar_week_not_utc_week() {
        // Denver is UTC-7 in January (no DST). Sunday 2026-01-04 19:00 local is already
        // Monday 2026-01-05 02:00 UTC — the *next* ISO week in UTC, but still the old week
        // locally. With a session the following local Monday, local bucketing sees two
        // consecutive weeks (streak 2); UTC bucketing would collapse both into one week
        // and understate the streak as 1.
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
        try {
            val sessionA = session(Instant.parse("2026-01-05T02:00:00Z").toEpochMilli()) // Sun Jan 4, 19:00 local
            val sessionB = session(Instant.parse("2026-01-05T20:00:00Z").toEpochMilli()) // Mon Jan 5, 13:00 local
            val now = Instant.parse("2026-01-05T21:00:00Z").toEpochMilli() // Mon Jan 5, 14:00 local
            assertEquals(2, HomeViewModel.computeStreak(listOf(sessionA, sessionB), now))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
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

    private fun now() = System.currentTimeMillis()
}
