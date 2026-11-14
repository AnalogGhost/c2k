package com.hackerapps.c2k.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class NoOpLocationProvider : LocationProvider {
    override val updates: Flow<LocationUpdate> = emptyFlow()
    override val availability: StateFlow<Boolean> = MutableStateFlow(false)
    override val totalDistanceMeters: Float = 0f
    override val hasValidFix: Boolean = false
    override fun start() {}
    override fun stop() {}
    override fun pause() {}
    override fun resume() {}
}
