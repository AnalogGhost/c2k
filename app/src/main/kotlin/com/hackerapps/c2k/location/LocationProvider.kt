package com.hackerapps.c2k.location

import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    val updates: Flow<LocationUpdate>
    val isAvailable: Boolean
    val totalDistanceMeters: Float
    val hasValidFix: Boolean
    fun start()
    fun stop()
}
