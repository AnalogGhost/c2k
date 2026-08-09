package com.hackerapps.c2k.location

import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceCalculator {

    // Fastest implied speed accepted between two consecutive GPS fixes. A faulty fix can
    // "teleport" hundreds of kilometres in one segment (issue #30: 584 km in 8 minutes),
    // so anything above a pace no human sustains on foot (~45 km/h) is treated as bad data
    // rather than movement.
    const val MAX_SPEED_MPS = 12.5f

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Total distance along a recorded route, skipping segments whose implied speed exceeds
     * [MAX_SPEED_MPS] (as well as segments with a non-positive time delta, which make speed
     * meaningless). Both segments around an isolated spike point exceed the limit, so the
     * spike contributes nothing and the total resumes from the next plausible segment.
     */
    fun filteredDistanceMeters(points: List<RoutePointEntity>): Float {
        var total = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val next = points[i]
            val dtSeconds = (next.recordedAt - prev.recordedAt) / 1000.0
            if (dtSeconds <= 0) continue
            val meters = haversineMeters(
                prev.latitude, prev.longitude, next.latitude, next.longitude
            )
            if (meters / dtSeconds > MAX_SPEED_MPS) continue
            total += meters
        }
        return total.toFloat()
    }
}
