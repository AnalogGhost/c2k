package com.hackerapps.c2k.location

import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    val updates: Flow<LocationUpdate>
    val isAvailable: Boolean
    val totalDistanceMeters: Float
    val hasValidFix: Boolean
    fun start()
    fun stop()
    // Distinct from stop(): pause()/resume() bracket a workout pause, where fixes should stop
    // being collected (and, on resume, the gap shouldn't be counted as distance travelled) but
    // totalDistanceMeters must be preserved rather than reset.
    fun pause()
    fun resume()
}
