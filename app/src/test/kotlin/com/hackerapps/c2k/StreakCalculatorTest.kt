package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.engine.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class StreakCalculatorTest {

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

    @Test
    fun streak_is_zero_with_no_sessions() {
        assertEquals(0, StreakCalculator.compute(emptyList(), now()))
    }

    @Test
    fun streak_is_zero_when_all_sessions_incomplete() {
        val sessions = listOf(session(now(), completed = false))
        assertEquals(0, StreakCalculator.compute(sessions, now()))
    }

    @Test
    fun streak_is_one_for_single_completion_this_week() {
        val sessions = listOf(session(now()))
        assertEquals(1, StreakCalculator.compute(sessions, now()))
    }

    @Test
    fun streak_is_one_for_single_completion_last_week() {
        // Current week hasn't had its workout yet — the streak survives on last week's.
        val sessions = listOf(session(now() - msPerWeek))
        assertEquals(1, StreakCalculator.compute(sessions, now()))
    }

    @Test
    fun streak_is_zero_when_last_completion_was_two_weeks_ago() {
        val sessions = listOf(session(now() - 2 * msPerWeek))
        assertEquals(0, StreakCalculator.compute(sessions, now()))
    }

    @Test
    fun streak_counts_consecutive_weeks() {
        val sessions = (0..4).map { session(now() - it * msPerWeek) }
        assertEquals(5, StreakCalculator.compute(sessions, now()))
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
        assertEquals(2, StreakCalculator.compute(sessions, now()))
    }

    @Test
    fun streak_ignores_incomplete_sessions_within_range() {
        val sessions = listOf(
            session(now()),
            session(now() - msPerWeek, completed = false),
            session(now() - 2 * msPerWeek)
        )
        // last week's session doesn't count, so the streak breaks after this week
        assertEquals(1, StreakCalculator.compute(sessions, now()))
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
        assertEquals(2, StreakCalculator.compute(sessions, now()))
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
            assertEquals(2, StreakCalculator.compute(listOf(sessionA, sessionB), now))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun now() = System.currentTimeMillis()
}
