package org.ctok.ui.screen.workout

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ctok.engine.WorkoutState
import org.ctok.location.LocationProvider
import org.ctok.location.LocationUpdate
import org.ctok.location.NoOpLocationProvider
import org.ctok.service.WorkoutService

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val _workoutState = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _locationUpdate = MutableStateFlow<LocationUpdate?>(null)
    val locationUpdate: StateFlow<LocationUpdate?> = _locationUpdate.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()

    private var boundService: WorkoutService.LocalBinder? = null
    private var locationProvider: LocationProvider = NoOpLocationProvider()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as WorkoutService.LocalBinder
            boundService = lb
            locationProvider = lb.getLocationProvider()
            viewModelScope.launch {
                lb.getEngine().state.collect { _workoutState.value = it }
            }
            viewModelScope.launch {
                lb.getLocationProvider().updates.collect { update ->
                    _locationUpdate.value = update
                    _distanceMeters.value = lb.getLocationProvider().totalDistanceMeters
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
        }
    }

    fun bind(context: Context) {
        val intent = Intent(context, WorkoutService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun startWorkout(context: Context, programId: String, week: Int, day: Int) {
        val intent = Intent(context, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_START
            putExtra(WorkoutService.EXTRA_PROGRAM_ID, programId)
            putExtra(WorkoutService.EXTRA_WEEK, week)
            putExtra(WorkoutService.EXTRA_DAY, day)
        }
        context.startForegroundService(intent)
        bind(context)
    }

    fun pause(context: Context) {
        context.startService(
            Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_PAUSE)
        )
    }

    fun resume(context: Context) {
        context.startService(
            Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_RESUME)
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP)
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unbindService(connection)
        } catch (_: Exception) {}
    }
}
