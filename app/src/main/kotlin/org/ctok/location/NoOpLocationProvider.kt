package org.ctok.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoOpLocationProvider : LocationProvider {
    override val updates: Flow<LocationUpdate> = emptyFlow()
    override val isAvailable: Boolean = false
    override val totalDistanceMeters: Float = 0f
    override fun start() {}
    override fun stop() {}
}
