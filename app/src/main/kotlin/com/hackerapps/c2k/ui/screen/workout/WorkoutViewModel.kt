package com.hackerapps.c2k.ui.screen.workout

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.location.LocationProvider
import com.hackerapps.c2k.location.LocationUpdate
import com.hackerapps.c2k.location.NoOpLocationProvider
import com.hackerapps.c2k.service.WorkoutService

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private val _workoutState = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _locationUpdate = MutableStateFlow<LocationUpdate?>(null)
    val locationUpdate: StateFlow<LocationUpdate?> = _locationUpdate.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()

    private val _currentSpeedMps = MutableStateFlow<Float?>(null)
    val currentPaceMinPerKm: StateFlow<String?> = _currentSpeedMps
        .map { speed ->
            if (speed == null || speed < 0.5f) null
            else {
                val pace = 1000f / (speed * 60f)
                "%d:%02d".format(pace.toInt(), ((pace % 1) * 60).toInt())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val keepScreenOn: StateFlow<Boolean> = prefs.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _showBatteryPrompt = MutableStateFlow(false)
    val showBatteryPrompt: StateFlow<Boolean> = _showBatteryPrompt.asStateFlow()

    private var locationProvider: LocationProvider = NoOpLocationProvider()
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as WorkoutService.LocalBinder
            locationProvider = lb.getLocationProvider()
            viewModelScope.launch {
                lb.getEngine().state.collect { _workoutState.value = it }
            }
            viewModelScope.launch {
                lb.getLocationProvider().updates.collect { update ->
                    _locationUpdate.value = update
                    _distanceMeters.value = lb.getLocationProvider().totalDistanceMeters
                    _currentSpeedMps.value = update.speedMps
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    private fun bind() {
        if (isBound) return
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutService::class.java)
        app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        isBound = true
    }

    fun startWorkout(programId: String, week: Int, day: Int) {
        checkBatteryOptimization()
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_START
            putExtra(WorkoutService.EXTRA_PROGRAM_ID, programId)
            putExtra(WorkoutService.EXTRA_WEEK, week)
            putExtra(WorkoutService.EXTRA_DAY, day)
        }
        app.startForegroundService(intent)
        bind()
    }

    fun pause() {
        getApplication<Application>().startService(
            Intent(getApplication(), WorkoutService::class.java).setAction(WorkoutService.ACTION_PAUSE)
        )
    }

    fun resume() {
        getApplication<Application>().startService(
            Intent(getApplication(), WorkoutService::class.java).setAction(WorkoutService.ACTION_RESUME)
        )
    }

    fun stop() {
        getApplication<Application>().startService(
            Intent(getApplication(), WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP)
        )
    }

    fun dismissBatteryPrompt() {
        _showBatteryPrompt.value = false
        viewModelScope.launch { prefs.setBatteryPromptDismissed() }
    }

    private fun checkBatteryOptimization() {
        viewModelScope.launch {
            val alreadyDismissed = prefs.batteryPromptDismissed.first()
            if (alreadyDismissed) return@launch
            val app = getApplication<Application>()
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(app.packageName)) {
                _showBatteryPrompt.value = true
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try { getApplication<Application>().unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
