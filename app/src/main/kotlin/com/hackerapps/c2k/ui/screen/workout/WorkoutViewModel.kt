package com.hackerapps.c2k.ui.screen.workout

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.Celebration
import com.hackerapps.c2k.engine.CelebrationCheck
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.location.LocationUpdate
import com.hackerapps.c2k.service.WorkoutService

/** What the workout screen's start request should do; see [startDecision]. */
internal enum class StartDecision { START, RECONNECT, LEAVE }

/**
 * Decides what a start request from the workout screen does. The screen asks every time its
 * composition is (re)created, so after a rotation, a theme or font change, or process death it
 * asks again for a workout it already started; starting again there would silently run a second,
 * hidden workout.
 *
 * - [startedHere]: this ViewModel instance already started the workout (configuration change).
 * - [startedBefore]: the saved state says a workout was started from this screen, which is how a
 *   ViewModel recreated after process death knows.
 * - [serviceRunning]: a workout is live in the service.
 */
internal fun startDecision(startedHere: Boolean, startedBefore: Boolean, serviceRunning: Boolean): StartDecision =
    when {
        startedHere -> StartDecision.RECONNECT
        // Process death took the service (same process, START_NOT_STICKY) and its workout with
        // it; there's nothing to show, and re-running the workout isn't what the user asked for.
        startedBefore -> if (serviceRunning) StartDecision.RECONNECT else StartDecision.LEAVE
        else -> StartDecision.START
    }

class WorkoutViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {

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

    // Whether this workout will use GPS, and so needs the location permission: the same rule
    // WorkoutService uses to pick a location provider. Null until the settings have loaded, so
    // the screen neither asks nor skips the permission on a default value.
    val locationNeeded: StateFlow<Boolean?> = combine(prefs.gpsEnabled, prefs.treadmillMode) { gps, treadmill ->
        gps && !treadmill
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightKg: StateFlow<Float?> = prefs.weightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Android refused to start the workout; the screen explains instead of "Starting…". */
    val startFailed: StateFlow<Boolean> = WorkoutService.startRejected.asStateFlow()

    private val _showBatteryPrompt = MutableStateFlow(false)
    val showBatteryPrompt: StateFlow<Boolean> = _showBatteryPrompt.asStateFlow()

    private val _personalBest = MutableStateFlow<WorkoutSessionEntity?>(null)
    val personalBest: StateFlow<WorkoutSessionEntity?> = _personalBest.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    /** Set once a finished workout completes a program or, the first time, week 3. */
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()
    private val celebrationCheck = CelebrationCheck(
        isWeekThreeCelebrated = { prefs.weekThreeCelebrated.first() },
        markWeekThreeCelebrated = { prefs.setWeekThreeCelebrated() }
    )

    // Stored when startWorkout is called so CompletedContent can look up prior best
    private var currentProgramId: String = ""
    private var currentWeek: Int = 0
    private var currentDay: Int = 0

    private var isBound = false
    private var startedHere = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as WorkoutService.LocalBinder
            viewModelScope.launch {
                lb.getGpsActive().collect { active -> _gpsActive.value = active }
            }
            viewModelScope.launch {
                lb.getWorkoutState().collect { state ->
                    // Latch the terminal state: once completed, ignore any late/stale Active or
                    // Idle frame (e.g. a fresh Idle service auto-created by a rebind after the
                    // foreground service self-stops) so the UI can't revert to a live counter.
                    if (_workoutState.value is WorkoutState.Completed && state !is WorkoutState.Completed) {
                        return@collect
                    }
                    _workoutState.value = state
                    if (state is WorkoutState.Completed && currentProgramId.isNotEmpty()) {
                        loadPersonalBest()
                        checkCelebration(state)
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

    /** Starts the workout, or reconnects to it; see [startDecision]. */
    internal fun startWorkout(programId: String, week: Int, day: Int): StartDecision {
        val decision = startDecision(
            startedHere = startedHere,
            startedBefore = savedState.get<Boolean>(KEY_STARTED) == true,
            serviceRunning = WorkoutService.isRunning.value
        )
        when (decision) {
            StartDecision.START -> {
                startedHere = true
                savedState[KEY_STARTED] = true
                launchWorkout(programId, week, day)
            }
            StartDecision.RECONNECT -> {
                startedHere = true
                currentProgramId = programId
                currentWeek = week
                currentDay = day
                bind()
            }
            StartDecision.LEAVE -> Unit
        }
        return decision
    }

    /** Tries again after Android refused the start, say once the runner has granted the permission. */
    fun retryStart(programId: String, week: Int, day: Int) = launchWorkout(programId, week, day)

    private fun launchWorkout(programId: String, week: Int, day: Int) {
        // A refusal from an earlier attempt mustn't show while this one is starting.
        WorkoutService.startRejected.value = false
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

    fun skipToNextInterval() {
        getApplication<Application>().startService(
            Intent(getApplication(), WorkoutService::class.java).setAction(WorkoutService.ACTION_SKIP_NEXT)
        )
    }

    fun skipToPreviousInterval() {
        getApplication<Application>().startService(
            Intent(getApplication(), WorkoutService::class.java).setAction(WorkoutService.ACTION_SKIP_PREVIOUS)
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

    private fun checkCelebration(state: WorkoutState.Completed) {
        val programId = currentProgramId
        val week = currentWeek
        val day = currentDay
        viewModelScope.launch {
            _celebration.value = celebrationCheck.check(state, Programs.byId(programId), week, day)
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

    internal companion object {
        const val KEY_STARTED = "workout_started"
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try { getApplication<Application>().unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
