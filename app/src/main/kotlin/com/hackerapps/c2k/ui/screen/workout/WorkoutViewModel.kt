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
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.location.LocationUpdate
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

    // true once the GPS provider has received a valid, accurate fix
    val hasGpsLock: StateFlow<Boolean> = _locationUpdate
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // true if the service is using a real GPS provider (not NoOp)
    private val _gpsActive = MutableStateFlow(false)
    val gpsActive: StateFlow<Boolean> = _gpsActive.asStateFlow()

    val keepScreenOn: StateFlow<Boolean> = prefs.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val treadmillMode: StateFlow<Boolean> = prefs.treadmillMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _showBatteryPrompt = MutableStateFlow(false)
    val showBatteryPrompt: StateFlow<Boolean> = _showBatteryPrompt.asStateFlow()

    private val _personalBest = MutableStateFlow<WorkoutSessionEntity?>(null)
    val personalBest: StateFlow<WorkoutSessionEntity?> = _personalBest.asStateFlow()

    // Stored when startWorkout is called so CompletedContent can look up prior best
    private var currentProgramId: String = ""
    private var currentWeek: Int = 0
    private var currentDay: Int = 0

    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as WorkoutService.LocalBinder
            viewModelScope.launch {
                lb.getGpsActive().collect { active -> _gpsActive.value = active }
            }
            viewModelScope.launch {
                lb.getWorkoutState().collect { state ->
                    _workoutState.value = state
                    if (state is WorkoutState.Completed && currentProgramId.isNotEmpty()) {
                        loadPersonalBest()
                    }
                }
            }
            viewModelScope.launch {
                lb.getLocationUpdates().collect { update ->
                    _locationUpdate.value = update
                    _distanceMeters.value = lb.getTotalDistanceMeters()
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
        currentProgramId = programId
        currentWeek = week
        currentDay = day
        viewModelScope.launch { prefs.setLastProgramId(programId) }
        checkBatteryOptimization()
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_START
            putExtra(WorkoutService.EXTRA_PROGRAM_ID, programId)
            putExtra(WorkoutService.EXTRA_WEEK, week)
            putExtra(WorkoutService.EXTRA_DAY, day)
            putExtra(WorkoutService.EXTRA_TREADMILL_MODE, treadmillMode.value)
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

    private fun loadPersonalBest() {
        viewModelScope.launch {
            val repo = (getApplication<Application>() as C2KApp).sessionRepository
            // Fetch the best PREVIOUS run for this day (exclude the session just completed)
            val current = _workoutState.value
            val currentSessionId = if (current is WorkoutState.Completed) current.sessionId else -1L
            val best = repo.getBestForDay(currentProgramId, currentWeek, currentDay)
            // Only show if the best is a different session (not the one we just completed)
            _personalBest.value = if (best != null && best.id != currentSessionId) best else null
        }
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
