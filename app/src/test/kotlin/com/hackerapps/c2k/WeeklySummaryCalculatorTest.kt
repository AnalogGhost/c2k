package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.engine.CalorieCalculator
import com.hackerapps.c2k.engine.WeeklySummary
import com.hackerapps.c2k.engine.WeeklySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklySummaryCalculatorTest {

    private val utc = ZoneId.of("UTC")

    // 2026-09-16 is a Wednesday. Its Monday-start week runs 2026-09-14..2026-09-20; its
    // Sunday-start week runs 2026-09-13..2026-09-19.
    private fun millis(
        year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: ZoneId = utc
    ) = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val wednesday = millis(2026, 9, 16)

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
        now: Long = wednesday,
        weightKg: Float? = null,
        zone: ZoneId = utc,
        firstDay: DayOfWeek = DayOfWeek.MONDAY
    ): WeeklySummary = WeeklySummaryCalculator.compute(sessions, now, weightKg, zone, firstDay)

    @Test
    fun empty_history_gives_zero_totals_and_no_activity() {
        val summary = compute(emptyList())
        assertEquals(0, summary.thisWeek.completedSessions)
        assertEquals(0f, summary.thisWeek.totalKm)
        assertEquals(0, summary.thisWeek.totalTimeSeconds)
        assertFalse(summary.thisWeek.hasActivity)
        assertFalse(summary.lastWeek.hasActivity)
    }

    @Test
    fun sessions_this_week_are_summed() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 14), durationSeconds = 600, distanceMeters = 1000f),
                session(millis(2026, 9, 16), durationSeconds = 300, distanceMeters = 500f)
            )
        )
        assertEquals(2, summary.thisWeek.completedSessions)
        assertEquals(1.5f, summary.thisWeek.totalKm, 0.0001f)
        assertEquals(900, summary.thisWeek.totalTimeSeconds)
        assertTrue(summary.thisWeek.hasActivity)
        assertFalse(summary.lastWeek.hasActivity)
    }

    @Test
    fun last_week_is_totalled_separately_and_older_weeks_are_ignored() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 16), durationSeconds = 600, distanceMeters = 1000f),
                session(millis(2026, 9, 10), durationSeconds = 1200, distanceMeters = 2000f),
                session(millis(2026, 9, 2), durationSeconds = 9999, distanceMeters = 9000f)
            )
        )
        assertEquals(1, summary.thisWeek.completedSessions)
        assertEquals(1.0f, summary.thisWeek.totalKm, 0.0001f)
        assertEquals(1, summary.lastWeek.completedSessions)
        assertEquals(2.0f, summary.lastWeek.totalKm, 0.0001f)
        assertEquals(1200, summary.lastWeek.totalTimeSeconds)
    }

    @Test
    fun sessions_after_the_current_week_are_not_counted() {
        val summary = compute(listOf(session(millis(2026, 9, 21))))
        assertFalse(summary.thisWeek.hasActivity)
        assertFalse(summary.lastWeek.hasActivity)
    }

    @Test
    fun week_boundary_falls_on_local_midnight_of_the_first_day() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 14, hour = 0, minute = 0)),  // Monday 00:00 -> this week
                session(millis(2026, 9, 13, hour = 23, minute = 59)) // Sunday 23:59 -> last week
            )
        )
        assertEquals(1, summary.thisWeek.completedSessions)
        assertEquals(1, summary.lastWeek.completedSessions)
    }

    @Test
    fun first_day_of_week_decides_which_week_a_sunday_session_belongs_to() {
        val sunday = session(millis(2026, 9, 13))

        val mondayStart = compute(listOf(sunday), firstDay = DayOfWeek.MONDAY)
        assertFalse(mondayStart.thisWeek.hasActivity)
        assertTrue(mondayStart.lastWeek.hasActivity)

        val sundayStart = compute(listOf(sunday), firstDay = DayOfWeek.SUNDAY)
        assertTrue(sundayStart.thisWeek.hasActivity)
        assertFalse(sundayStart.lastWeek.hasActivity)
    }

    @Test
    fun now_on_the_first_day_of_the_week_starts_a_fresh_week() {
        val monday = millis(2026, 9, 14, hour = 8)
        val summary = compute(listOf(session(millis(2026, 9, 11))), now = monday)
        assertFalse(summary.thisWeek.hasActivity)
        assertEquals(1, summary.lastWeek.completedSessions)
    }

    @Test
    fun weeks_are_bucketed_in_the_given_time_zone_not_utc() {
        val auckland = ZoneId.of("Pacific/Auckland")
        // Monday 00:30 in Auckland is still Sunday 12:30 UTC — a UTC-based split would put
        // this run in last week.
        val session = session(millis(2026, 9, 14, hour = 0, minute = 30, zone = auckland))
        val now = millis(2026, 9, 16, zone = auckland)

        val summary = compute(listOf(session), now = now, zone = auckland)
        assertEquals(1, summary.thisWeek.completedSessions)
        assertFalse(summary.lastWeek.hasActivity)
    }

    @Test
    fun a_dst_change_inside_the_week_does_not_shift_the_boundary() {
        val newYork = ZoneId.of("America/New_York")
        // DST ends Sunday 2026-11-01, so this Monday-start week is 25 hours long. Adding a
        // fixed 7*24h to Monday 00:00 would end the week at 23:00 on Sunday and drop this run.
        val lateSunday = session(millis(2026, 11, 1, hour = 23, minute = 30, zone = newYork))
        val now = millis(2026, 10, 28, zone = newYork)

        val summary = compute(listOf(lateSunday), now = now, zone = newYork)
        assertEquals(1, summary.thisWeek.completedSessions)
    }

    @Test
    fun distance_and_time_include_incomplete_sessions_but_the_count_does_not() {
        val summary = compute(
            listOf(
                session(millis(2026, 9, 14), durationSeconds = 600, distanceMeters = 1000f, completed = true),
                session(millis(2026, 9, 15), durationSeconds = 200, distanceMeters = 400f, completed = false)
            )
        )
        assertEquals(1, summary.thisWeek.completedSessions)
        assertEquals(1.4f, summary.thisWeek.totalKm, 0.0001f)
        assertEquals(800, summary.thisWeek.totalTimeSeconds)
    }

    @Test
    fun calories_are_null_without_a_weight() {
        val summary = compute(listOf(session(millis(2026, 9, 14))))
        assertNull(summary.thisWeek.totalCalories)
        assertNull(summary.lastWeek.totalCalories)
    }

    @Test
    fun calories_sum_per_session_estimates_for_each_week() {
        val thisWeek = session(millis(2026, 9, 14), durationSeconds = 600, distanceMeters = 1000f)
        val lastWeek = session(millis(2026, 9, 8), durationSeconds = 900, distanceMeters = 1500f)

        val summary = compute(listOf(thisWeek, lastWeek), weightKg = 70f)
        assertEquals(CalorieCalculator.estimateCalories(1000f, 600, 70f), summary.thisWeek.totalCalories)
        assertEquals(CalorieCalculator.estimateCalories(1500f, 900, 70f), summary.lastWeek.totalCalories)
    }

    @Test
    fun treadmill_session_without_distance_counts_time_but_no_calories() {
        val summary = compute(
            listOf(session(millis(2026, 9, 14), durationSeconds = 1800, distanceMeters = 0f)),
            weightKg = 70f
        )
        assertEquals(1800, summary.thisWeek.totalTimeSeconds)
        assertEquals(0, summary.thisWeek.totalCalories)
        assertTrue(summary.thisWeek.hasActivity)
    }
}
