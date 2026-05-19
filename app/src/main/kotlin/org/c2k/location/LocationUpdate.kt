package org.c2k.location

import org.c2k.data.db.entity.RoutePointEntity

data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMps: Float?,
    val recordedAt: Long = System.currentTimeMillis()
)

fun LocationUpdate.toEntity(sessionId: Long) = RoutePointEntity(
    sessionId = sessionId,
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitudeMeters,
    speedMps = speedMps,
    recordedAt = recordedAt
)
