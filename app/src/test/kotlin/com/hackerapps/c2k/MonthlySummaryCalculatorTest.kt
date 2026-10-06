package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.engine.CalorieCalculator
import com.hackerapps.c2k.engine.MonthlySummary
import com.hackerapps.c2k.engine.MonthlySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MonthlySummaryCalculatorTest {

    private val utc = ZoneId.of("UTC")

    private fun millis(
        year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: ZoneId = utc
    ) = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val midSeptember = millis(2026, 9, 16)

    private fun session(
        startedAt: Long,
        durationSeconds: Int = 600,
        distanceMeters: Float = 1000f,
        completed: Boolean = true
    ) = WorkoutSessionEntity(
        programId = "c25k",
        week = 1,
        day = 1,
        startedAt = startedAt,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        completed = completed
    )

    private fun compute(
        sessions: List<WorkoutSessionEntity>,
        now: Long = midSeptember,
        weightKg: Float? = null,
        zone: ZoneId = utc
    ): MonthlySummary = MonthlySummaryCalculator.compute(sessions, now, weightKg, zone)

    @Test
    fun empty_history_gives_zero_totals_and_no_activity() {
        val summary = compute(emptyList())
        assertEquals(0, summary.thisMonth.completedSessions)
        assertEquals(0f, summary.thisMonth.totalKm)
        assertEquals(0, summary.thisMonth.totalTimeSeconds)
        assertFalse(summary.thisMonth.hasActivity)
        assertFalse(summary.lastMonth.hasActivity)
    }

    @Test
    fun sessions_this_month_are_summed() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 1), durationSeconds = 600, distanceMeters = 1000f),
                session(millis(2026, 9, 30), durationSeconds = 300, distanceMeters = 500f)
            )
        )
        assertEquals(2, summary.thisMonth.completedSessions)
        assertEquals(1.5f, summary.thisMonth.totalKm, 0.0001f)
        assertEquals(900, summary.thisMonth.totalTimeSeconds)
        assertTrue(summary.thisMonth.hasActivity)
        assertFalse(summary.lastMonth.hasActivity)
    }

    @Test
    fun last_month_is_totalled_separately_and_older_months_are_ignored() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 16), durationSeconds = 600, distanceMeters = 1000f),
                session(millis(2026, 8, 15), durationSeconds = 1200, distanceMeters = 2000f),
                session(millis(2026, 7, 1), durationSeconds = 9999, distanceMeters = 9000f)
            )
        )
        assertEquals(1, summary.thisMonth.completedSessions)
        assertEquals(1.0f, summary.thisMonth.totalKm, 0.0001f)
        assertEquals(1, summary.lastMonth.completedSessions)
        assertEquals(2.0f, summary.lastMonth.totalKm, 0.0001f)
        assertEquals(1200, summary.lastMonth.totalTimeSeconds)
    }

    @Test
    fun sessions_after_the_current_month_are_not_counted() {
        val summary = compute(listOf(session(millis(2026, 10, 1))))
        assertFalse(summary.thisMonth.hasActivity)
        assertFalse(summary.lastMonth.hasActivity)
    }

    @Test
    fun month_boundary_falls_on_local_midnight_of_the_1st() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 1, hour = 0, minute = 0)),  // 1st 00:00 -> this month
                session(millis(2026, 8, 31, hour = 23, minute = 59)) // last day 23:59 -> last month
            )
        )
        assertEquals(1, summary.thisMonth.completedSessions)
        assertEquals(1, summary.lastMonth.completedSessions)
    }

    @Test
    fun february_length_does_not_shift_the_boundary_in_a_leap_or_common_year() {
        // 2028 is a leap year (Feb has 29 days), 2026 is not (28 days). A fixed day-count
        // boundary would misplace the last day of February in one of these.
        val leapFeb = session(millis(2028, 2, 29, hour = 23, minute = 30))
        val leapSummary = compute(listOf(leapFeb), now = millis(2028, 3, 5))
        assertEquals(1, leapSummary.lastMonth.completedSessions)

        val commonFeb = session(millis(2026, 2, 28, hour = 23, minute = 30))
        val commonSummary = compute(listOf(commonFeb), now = millis(2026, 3, 5))
        assertEquals(1, commonSummary.lastMonth.completedSessions)
    }

    @Test
    fun now_on_the_1st_starts_a_fresh_month() {
        val firstOfMonth = millis(2026, 9, 1, hour = 8)
        val summary = compute(listOf(session(millis(2026, 8, 20))), now = firstOfMonth)
        assertFalse(summary.thisMonth.hasActivity)
        assertEquals(1, summary.lastMonth.completedSessions)
    }

    @Test
    fun year_rolls_over_from_december_to_january() {
        val summary = compute(
            listOf(session(millis(2025, 12, 20))),
            now = millis(2026, 1, 10)
        )
        assertFalse(summary.thisMonth.hasActivity)
        assertEquals(1, summary.lastMonth.completedSessions)
    }

    @Test
    fun months_are_bucketed_in_the_given_time_zone_not_utc() {
        val auckland = ZoneId.of("Pacific/Auckland")
        // 1st 00:30 in Auckland is still the last day of the previous month at 12:30 UTC — a
        // UTC-based split would put this run in last month.
        val run = session(millis(2026, 9, 1, hour = 0, minute = 30, zone = auckland))
        val now = millis(2026, 9, 16, zone = auckland)

        val summary = compute(listOf(run), now = now, zone = auckland)
        assertEquals(1, summary.thisMonth.completedSessions)
        assertFalse(summary.lastMonth.hasActivity)
    }

    @Test
    fun a_dst_change_inside_the_month_does_not_shift_the_boundary() {
        val newYork = ZoneId.of("America/New_York")
        // DST ends 2026-11-01, inside this October->November boundary window in general, but
        // specifically check a session late in a month containing a DST change is still bucketed
        // correctly: November 2026 has a clock change on its first day.
        val lateOctober = session(millis(2026, 10, 31, hour = 23, minute = 30, zone = newYork))
        val now = millis(2026, 11, 5, zone = newYork)

        val summary = compute(listOf(lateOctober), now = now, zone = newYork)
        assertEquals(1, summary.lastMonth.completedSessions)
    }

    @Test
    fun distance_and_time_include_incomplete_sessions_but_the_count_does_not() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 14), durationSeconds = 600, distanceMeters = 1000f, completed = true),
                session(millis(2026, 9, 15), durationSeconds = 200, distanceMeters = 400f, completed = false)
            )
        )
        assertEquals(1, summary.thisMonth.completedSessions)
        assertEquals(1.4f, summary.thisMonth.totalKm, 0.0001f)
        assertEquals(800, summary.thisMonth.totalTimeSeconds)
    }

    @Test
    fun calories_are_null_without_a_weight() {
        val summary = compute(listOf(session(millis(2026, 9, 14))))
        assertNull(summary.thisMonth.totalCalories)
        assertNull(summary.lastMonth.totalCalories)
    }

    @Test
    fun calories_sum_per_session_estimates_for_each_month() {
        val thisMonth = session(millis(2026, 9, 14), durationSeconds = 600, distanceMeters = 1000f)
        val lastMonth = session(millis(2026, 8, 8), durationSeconds = 900, distanceMeters = 1500f)

        val summary = compute(listOf(thisMonth, lastMonth), weightKg = 70f)
        assertEquals(CalorieCalculator.estimateCalories(1000f, 600, 70f), summary.thisMonth.totalCalories)
        assertEquals(CalorieCalculator.estimateCalories(1500f, 900, 70f), summary.lastMonth.totalCalories)
    }

    @Test
    fun treadmill_session_without_distance_counts_time_but_no_calories() {
        val summary = compute(
            listOf(session(millis(2026, 9, 14), durationSeconds = 1800, distanceMeters = 0f)),
            weightKg = 70f
        )
        assertEquals(1800, summary.thisMonth.totalTimeSeconds)
        assertEquals(0, summary.thisMonth.totalCalories)
        assertTrue(summary.thisMonth.hasActivity)
    }
}
