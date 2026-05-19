package org.c2k.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GpsLocationProvider(private val context: Context) : LocationProvider {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _updates = MutableSharedFlow<LocationUpdate>(replay = 0, extraBufferCapacity = 64)
    override val updates: Flow<LocationUpdate> = _updates.asSharedFlow()

    override val isAvailable: Boolean
        get() = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    private var lastLocation: Location? = null
    private var _totalDistance = 0f
    override val totalDistanceMeters: Float get() = _totalDistance

    private val listener = LocationListener { location ->
        lastLocation?.let { prev ->
            _totalDistance += prev.distanceTo(location)
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

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!isAvailable) return
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            2000L,   // min time ms
            5f,      // min distance metres
            listener,
            Looper.getMainLooper()
        )
    }

    override fun stop() {
        locationManager.removeUpdates(listener)
    }
}
