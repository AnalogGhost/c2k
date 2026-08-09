package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.location.DistanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceCalculatorTest {

    // ~0.0001° latitude ≈ 11.1 m; at 5 s intervals that's ~2.2 m/s, a normal running pace.
    private fun point(index: Int, lat: Double, lon: Double = 0.0, atSeconds: Long) =
        RoutePointEntity(
            id = index.toLong(),
            sessionId = 1L,
            latitude = lat,
            longitude = lon,
            altitudeMeters = null,
            speedMps = null,
            recordedAt = atSeconds * 1000
        )

    @Test
    fun empty_and_single_point_routes_have_zero_distance() {
        assertEquals(0f, DistanceCalculator.filteredDistanceMeters(emptyList()), 0.001f)
        assertEquals(
            0f,
            DistanceCalculator.filteredDistanceMeters(listOf(point(0, 50.0, atSeconds = 0))),
            0.001f
        )
    }

    @Test
    fun plausible_route_sums_all_segments() {
        val points = (0..4).map { i -> point(i, 50.0 + i * 0.0001, atSeconds = i * 5L) }
        val distance = DistanceCalculator.filteredDistanceMeters(points)
        // 4 segments of ~11.1 m each
        assertEquals(44.5f, distance, 1f)
    }

    @Test
    fun teleporting_spike_contributes_nothing() {
        // Same route, but one point jumps ~111 km away and back — the pattern from issue
        // #30, where a faulty GPS lock added 584 km. Both segments touching the spike imply
        // impossible speed and must be discarded; the rest of the route still counts.
        val points = listOf(
            point(0, 50.0000, atSeconds = 0),
            point(1, 50.0001, atSeconds = 5),
            point(2, 51.0000, atSeconds = 10), // spike: ~111 km in 5 s
            point(3, 50.0002, atSeconds = 15),
            point(4, 50.0003, atSeconds = 20)
        )
        val distance = DistanceCalculator.filteredDistanceMeters(points)
        // Only the two clean segments (0→1 and 3→4), ~11.1 m each
        assertEquals(22.2f, distance, 1f)
    }

    @Test
    fun sustained_wrong_track_counts_only_the_jumps_out_and_back() {
        // GPS settles somewhere wrong, records a few self-consistent points there, then
        // recovers. The huge jump segments are dropped; the small movements at the wrong
        // location are indistinguishable from real running and legitimately kept.
        val points = listOf(
            point(0, 50.0000, atSeconds = 0),
            point(1, 51.0000, atSeconds = 5),  // jump out: dropped
            point(2, 51.0001, atSeconds = 10), // ~11 m while "wrong": kept
            point(3, 50.0001, atSeconds = 15)  // jump back: dropped
        )
        val distance = DistanceCalculator.filteredDistanceMeters(points)
        assertEquals(11.1f, distance, 1f)
    }

    @Test
    fun non_positive_time_delta_segments_are_skipped() {
        val points = listOf(
            point(0, 50.0000, atSeconds = 10),
            point(1, 50.0001, atSeconds = 10), // dt = 0: speed undefined, skip
            point(2, 50.0002, atSeconds = 15)
        )
        val distance = DistanceCalculator.filteredDistanceMeters(points)
        assertEquals(11.1f, distance, 1f)
    }

    @Test
    fun haversine_matches_known_distance() {
        // One degree of latitude along a meridian: Earth's mean circumference / 360 ≈ 111.19 km
        val meters = DistanceCalculator.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, meters, 10.0)
    }
}
