package com.hackerapps.c2k.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LocationProvider {
    val updates: Flow<LocationUpdate>
    /** Whether the location source is switched on right now; follows the user toggling it. */
    val availability: StateFlow<Boolean>
    val isAvailable: Boolean get() = availability.value
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
