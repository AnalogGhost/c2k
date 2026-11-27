package com.hackerapps.c2k.ui

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.ui.share.OutlinePoint
import com.hackerapps.c2k.ui.share.RouteOutline
import com.hackerapps.c2k.ui.share.ShareBackground
import com.hackerapps.c2k.ui.share.ShareCardStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class ShareCardTest {

    private fun stats(duration: Int, distance: Float) = ShareCardStats(
        programId = "C25K", week = 3, day = 2, finishedProgram = false,
        durationSeconds = duration, distanceMeters = distance, startedAt = 0L
    )

    private fun session(week: Int, day: Int, completed: Boolean) = WorkoutSessionEntity(
        id = 1, programId = "C25K", week = week, day = day, startedAt = 1234L,
        durationSeconds = 1800, distanceMeters = 4200f, completed = completed
    )

    @Test
    fun `pace is the whole workout's average per kilometre`() {
        assertEquals(450, stats(30 * 60, 4000f).paceSecondsPerKm) // 7:30 / km
    }

    @Test
    fun `no pace without a usable distance`() {
        assertNull(stats(30 * 60, 0f).paceSecondsPerKm)
        assertNull(stats(30 * 60, 50f).paceSecondsPerKm)
        assertNull(stats(0, 3000f).paceSecondsPerKm)
    }

    @Test
    fun `the program's last day finished is a finished program`() {
        val stats = ShareCardStats.of(session(9, 3, completed = true))
        assertTrue(stats.finishedProgram)
        assertEquals(1800, stats.durationSeconds)
        assertEquals(4200f, stats.distanceMeters)
        assertEquals(1234L, stats.startedAt)
    }

    @Test
    fun `a stopped last day or an ordinary day is not`() {
        assertFalse(ShareCardStats.of(session(9, 3, completed = false)).finishedProgram)
        assertFalse(ShareCardStats.of(session(4, 1, completed = true)).finishedProgram)
    }

    @Test
    fun `too few points or standing still give no outline`() {
        assertEquals(emptyList<OutlinePoint>(), RouteOutline.normalized(emptyList(), emptyList()))
        assertEquals(emptyList<OutlinePoint>(), RouteOutline.normalized(listOf(51.5), listOf(-0.1)))
        assertEquals(emptyList<OutlinePoint>(), RouteOutline.normalized(listOf(51.5, 51.5), listOf(-0.1, -0.1)))
    }

    @Test
    fun `the outline fills the unit square with north up`() {
        // Due north, then due east by the same distance on the ground.
        val lat = 51.5
        val step = 0.01
        val meanLat = (lat + 2 * (lat + step)) / 3
        val east = step / cos(Math.toRadians(meanLat))
        val points = RouteOutline.normalized(listOf(lat, lat + step, lat + step), listOf(0.0, 0.0, east))
        val expected = listOf(OutlinePoint(0.0, 1.0), OutlinePoint(0.0, 0.0), OutlinePoint(1.0, 0.0))
        expected.zip(points).forEach { (e, p) ->
            assertEquals(e.x, p.x, 1e-6)
            assertEquals(e.y, p.y, 1e-6)
        }
    }

    @Test
    fun `a straight run is centred, not stretched`() {
        val points = RouteOutline.normalized(listOf(50.0, 50.01), listOf(1.0, 1.0))
        assertEquals(listOf(0.5, 0.5), points.map { it.x })
        assertEquals(1.0, points[0].y, 1e-6)
        assertEquals(0.0, points[1].y, 1e-6)
    }

    @Test
    fun `long routes are thinned but keep their ends`() {
        val lats = (0 until 5000).map { 50 + it * 1e-5 }
        val lons = (0 until 5000).map { 1 + kotlin.math.sin(it / 300.0) * 1e-3 }
        val points = RouteOutline.normalized(lats, lons, maxPoints = 400)
        val full = RouteOutline.normalized(lats, lons, maxPoints = 10_000)
        assertEquals(400, points.size)
        assertEquals(full.first().x, points.first().x, 1e-3)
        assertEquals(full.first().y, points.first().y, 1e-3)
        assertEquals(full.last().x, points.last().x, 1e-3)
        assertEquals(full.last().y, points.last().y, 1e-3)
        assertTrue(points.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
    }

    @Test
    fun `every bundled photo and gradient is offered`() {
        assertEquals(ShareBackground.PHOTO_COUNT + ShareBackground.GRADIENT_COUNT, ShareBackground.builtIn.size)
        assertEquals(ShareBackground.Photo(13), ShareBackground.builtIn.last())
    }
}
