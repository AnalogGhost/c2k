package com.hackerapps.c2k.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsLocationProvider(private val context: Context) : LocationProvider {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _updates = MutableSharedFlow<LocationUpdate>(replay = 0, extraBufferCapacity = 64)
    override val updates: Flow<LocationUpdate> = _updates.asSharedFlow()

    private val _availability =
        MutableStateFlow(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    override val availability: StateFlow<Boolean> = _availability.asStateFlow()

    private var _hasValidFix = false
    override val hasValidFix: Boolean get() = _hasValidFix

    private var lastLocation: Location? = null
    private var _totalDistance = 0f
    override val totalDistanceMeters: Float get() = _totalDistance

    // Implemented as an explicit object, not a SAM lambda: onProviderDisabled/onProviderEnabled/
    // onStatusChanged only became default methods in API 30, so on API < 30 a lambda that omits
    // them throws AbstractMethodError when the platform invokes one (e.g. user disables GPS).
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Skip inaccurate fixes (cold-start drift can add 50–100 m to distance)
            if (location.hasAccuracy() && location.accuracy > 25f) return
            if (!_hasValidFix) _hasValidFix = true
            lastLocation?.let { prev ->
                val dtSeconds = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
                val meters = prev.distanceTo(location)
                if (dtSeconds <= 0 || meters / dtSeconds > DistanceCalculator.MAX_SPEED_MPS) {
                    // A fix implying impossible speed is bad data, not movement (issue #30: one
                    // teleporting fix added 584 km). Skip the delta and the route point, but
                    // rebase on the new position so tracking resumes from wherever GPS settles.
                    lastLocation = location
                    return
                }
                _totalDistance += meters
            }
            lastLocation = location
            _updates.tryEmit(
                LocationUpdate(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    speedMps = if (location.hasSpeed()) location.speed else null
                )
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
            _availability.value = true
        }

        override fun onProviderDisabled(provider: String) {
            _availability.value = false
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        // Registered even while Location is off: the request stays in place and fixes start
        // arriving as soon as the runner switches Location on. Returning early here, as this used
        // to, left a workout started with Location off recording 0 km for good.
        _availability.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            2000L,
            5f,
            listener,
            Looper.getMainLooper()
        )
    }

    override fun stop() {
        locationManager.removeUpdates(listener)
    }

    override fun pause() {
        locationManager.removeUpdates(listener)
    }

    override fun resume() {
        // Drop the pre-pause fix so the first post-resume update doesn't compute a distance delta
        // spanning however far the user moved (or GPS drifted) while the workout was paused.
        lastLocation = null
        start()
    }
}
