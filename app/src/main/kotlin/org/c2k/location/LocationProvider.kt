package org.c2k.location

import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    val updates: Flow<LocationUpdate>
    val isAvailable: Boolean
    val totalDistanceMeters: Float
    fun start()
    fun stop()
}
