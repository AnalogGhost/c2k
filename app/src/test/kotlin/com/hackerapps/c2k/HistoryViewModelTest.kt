package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.engine.CalorieCalculator
import com.hackerapps.c2k.ui.screen.history.HistoryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryViewModelTest {

    private fun session(
        durationSeconds: Int = 600,
        distanceMeters: Float = 1000f,
        completed: Boolean = true,
        week: Int = 1,
        day: Int = 1,
        startedAt: Long = 0L
    ) = WorkoutSessionEntity(
        programId = "c25k",
        week = week,
        day = day,
        startedAt = startedAt,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        completed = completed
    )

    // --- computeStats ---

    @Test
    fun stats_are_zero_for_empty_list() {
        val stats = HistoryViewModel.computeStats(emptyList())
        assertEquals(0, stats.totalSessions)
        assertEquals(0, stats.completedSessions)
        assertEquals(0f, stats.totalKm)
        assertEquals(0, stats.totalTimeSeconds)
    }

    @Test
    fun stats_sum_distance_and_time_across_sessions() {
        val sessions = listOf(
            session(durationSeconds = 600, distanceMeters = 1000f),
            session(durationSeconds = 300, distanceMeters = 500f)
        )
        val stats = HistoryViewModel.computeStats(sessions)
        assertEquals(2, stats.totalSessions)
        assertEquals(900, stats.totalTimeSeconds)
        assertEquals(1.5f, stats.totalKm, 0.0001f)
    }

    @Test
    fun stats_completed_count_excludes_incomplete_sessions() {
        val sessions = listOf(
            session(completed = true),
            session(completed = false),
            session(completed = true)
        )
        val stats = HistoryViewModel.computeStats(sessions)
        assertEquals(3, stats.totalSessions)
        assertEquals(2, stats.completedSessions)
    }

    @Test
    fun total_calories_is_null_when_weight_not_provided() {
        val sessions = listOf(session(durationSeconds = 600, distanceMeters = 1000f))
        val stats = HistoryViewModel.computeStats(sessions)
        assertNull(stats.totalCalories)
    }

    @Test
    fun total_calories_sums_per_session_estimates_when_weight_provided() {
        val sessions = listOf(
            session(durationSeconds = 600, distanceMeters = 1000f),
            session(durationSeconds = 300, distanceMeters = 500f)
        )
        val stats = HistoryViewModel.computeStats(sessions, 70f)
        val expected = sessions.sumOf { s ->
            CalorieCalculator.estimateCalories(s.distanceMeters, s.durationSeconds, 70f) ?: 0
        }
        assertEquals(expected, stats.totalCalories)
    }

    @Test
    fun fastest_pace_is_null_when_no_session_has_distance() {
        val sessions = listOf(session(durationSeconds = 600, distanceMeters = 0f))
        val stats = HistoryViewModel.computeStats(sessions)
        assertNull(stats.fastestPaceSecPerKm)
    }

    @Test
    fun fastest_pace_ignores_incomplete_sessions() {
        val sessions = listOf(
            session(durationSeconds = 100, distanceMeters = 1000f, completed = false), // would be fastest if counted
            session(durationSeconds = 300, distanceMeters = 1000f, completed = true)
        )
        val stats = HistoryViewModel.computeStats(sessions)
        assertEquals(300f, stats.fastestPaceSecPerKm!!, 0.0001f)
    }

    @Test
    fun fastest_pace_picks_the_quickest_session() {
        val sessions = listOf(
            session(durationSeconds = 600, distanceMeters = 1000f), // 600 s/km
            session(durationSeconds = 300, distanceMeters = 1000f), // 300 s/km, fastest
            session(durationSeconds = 900, distanceMeters = 1500f)  // 600 s/km
        )
        val stats = HistoryViewModel.computeStats(sessions)
        assertEquals(300f, stats.fastestPaceSecPerKm!!, 0.0001f)
    }

    @Test
    fun longest_run_is_null_when_no_session_has_distance() {
        val sessions = listOf(session(durationSeconds = 600, distanceMeters = 0f))
        val stats = HistoryViewModel.computeStats(sessions)
        assertNull(stats.longestRunMeters)
    }

    @Test
    fun longest_run_picks_the_longest_completed_session() {
        val sessions = listOf(
            session(distanceMeters = 3000f, completed = true),
            session(distanceMeters = 5000f, completed = false), // would be longest if counted
            session(distanceMeters = 4000f, completed = true)
        )
        val stats = HistoryViewModel.computeStats(sessions)
        assertEquals(4000f, stats.longestRunMeters!!, 0.0001f)
    }

    // --- generateGpx ---

    @Test
    fun gpx_includes_program_week_and_day_in_name() {
        val gpx = HistoryViewModel.generateGpx(session(week = 3, day = 2), emptyList())
        assertTrue(gpx.contains("<name>C2K W3D2"))
    }

    @Test
    fun gpx_is_well_formed_xml_with_no_points() {
        val gpx = HistoryViewModel.generateGpx(session(), emptyList())
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("</gpx>"))
        assertFalse(gpx.contains("<trkpt"))
    }

    @Test
    fun gpx_includes_a_trkpt_per_route_point() {
        val points = listOf(
            RoutePointEntity(sessionId = 1, latitude = 40.0, longitude = -3.0, altitudeMeters = null, speedMps = null, recordedAt = 1000L),
            RoutePointEntity(sessionId = 1, latitude = 40.1, longitude = -3.1, altitudeMeters = 50.0, speedMps = 2.5f, recordedAt = 2000L)
        )
        val gpx = HistoryViewModel.generateGpx(session(), points)
        val trkptCount = Regex("<trkpt").findAll(gpx).count()
        assertEquals(2, trkptCount)
        assertTrue(gpx.contains("""lat="40.0" lon="-3.0""""))
        assertTrue(gpx.contains("""lat="40.1" lon="-3.1""""))
    }

    @Test
    fun gpx_omits_elevation_tag_when_altitude_is_null() {
        val points = listOf(
            RoutePointEntity(sessionId = 1, latitude = 40.0, longitude = -3.0, altitudeMeters = null, speedMps = null, recordedAt = 1000L)
        )
        val gpx = HistoryViewModel.generateGpx(session(), points)
        assertFalse(gpx.contains("<ele>"))
    }

    @Test
    fun gpx_includes_elevation_tag_when_altitude_present() {
        val points = listOf(
            RoutePointEntity(sessionId = 1, latitude = 40.0, longitude = -3.0, altitudeMeters = 123.4, speedMps = null, recordedAt = 1000L)
        )
        val gpx = HistoryViewModel.generateGpx(session(), points)
        assertTrue(gpx.contains("<ele>123.4</ele>"))
    }
}
