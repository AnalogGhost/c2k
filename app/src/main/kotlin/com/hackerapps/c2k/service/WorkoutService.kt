package com.hackerapps.c2k.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.WorkoutEngine
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.engine.tts.TtsManager
import com.hackerapps.c2k.location.GpsLocationProvider
import com.hackerapps.c2k.location.LocationProvider
import com.hackerapps.c2k.location.LocationUpdate
import com.hackerapps.c2k.location.NoOpLocationProvider
import com.hackerapps.c2k.location.toEntity
import com.hackerapps.c2k.ui.MainActivity

class WorkoutService : Service() {

    data class WorkoutInfo(val programId: String, val week: Int, val day: Int)

    companion object {
        const val ACTION_START  = "com.hackerapps.c2k.action.START"
        const val ACTION_PAUSE  = "com.hackerapps.c2k.action.PAUSE"
        const val ACTION_RESUME = "com.hackerapps.c2k.action.RESUME"
        const val ACTION_STOP   = "com.hackerapps.c2k.action.STOP"

        const val EXTRA_PROGRAM_ID    = "program_id"
        const val EXTRA_WEEK          = "week"
        const val EXTRA_DAY           = "day"
        const val EXTRA_TREADMILL_MODE = "treadmill_mode"

        private const val NOTIFICATION_ID  = 1
        private const val CHANNEL_ID       = "workout_channel"
        private const val WAKELOCK_TAG     = "C2K::WorkoutLock"
        private const val WAKELOCK_TIMEOUT = 90 * 60 * 1000L

        val isRunning = MutableStateFlow(false)
        val currentWorkout = MutableStateFlow<WorkoutInfo?>(null)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var ttsManager: TtsManager
    private lateinit var engine: WorkoutEngine
    // Initialised to NoOp so binder calls before the setup coroutine finishes are safe.
    private var locationProvider: LocationProvider = NoOpLocationProvider()
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var prefs: UserPreferences

    // Exposed via binder — safe to collect immediately; populated once setup coroutine runs.
    val workoutStateFlow = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    private val _locationUpdates = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)
    private val _gpsActive = MutableStateFlow(false)

    private var workoutRunning = false
    private var cleanedUp = false

    inner class LocalBinder : Binder() {
        fun getWorkoutState(): StateFlow<WorkoutState> = workoutStateFlow
        fun getLocationUpdates(): SharedFlow<LocationUpdate> = _locationUpdates
        fun getTotalDistanceMeters(): Float = locationProvider.totalDistanceMeters
        fun getGpsActive(): StateFlow<Boolean> = _gpsActive
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        prefs = UserPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> handleStart(intent)
            ACTION_PAUSE  -> if (::engine.isInitialized) engine.pause()
            ACTION_RESUME -> if (::engine.isInitialized) engine.resume()
            ACTION_STOP   -> handleStop()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (workoutRunning) return

        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID) ?: return
        val week      = intent.getIntExtra(EXTRA_WEEK, -1).takeIf { it >= 0 } ?: return
        val day       = intent.getIntExtra(EXTRA_DAY, -1).takeIf { it >= 0 } ?: return

        workoutRunning = true
        isRunning.value = true
        currentWorkout.value = WorkoutInfo(programId, week, day)
        acquireWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasLocation = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            // Read treadmill mode synchronously from the intent extra set by the service starter;
            // prefs aren't available yet (setup coroutine hasn't run), so WorkoutViewModel embeds it.
            val treadmill = intent.getBooleanExtra(EXTRA_TREADMILL_MODE, false)
            startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.workout_starting)),
                if (hasLocation && !treadmill) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                else 0  // FOREGROUND_SERVICE_TYPE_NONE — timer-only, no location claimed
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.workout_starting)))
        }

        val workoutDay = Programs.byId(programId).weeks[week - 1][day - 1]
        val app = application as C2KApp

        serviceScope.launch {
            val ttsEnabled        = prefs.ttsEnabled.first()
            val gpsEnabled        = prefs.gpsEnabled.first()
            val treadmillMode     = prefs.treadmillMode.first()
            val countdownWarnings = prefs.countdownWarnings.first()
            val midIntervalCues   = prefs.midIntervalCues.first()
            val vibrationEnabled  = prefs.vibrationEnabled.first()
            val speechRate        = prefs.ttsSpeechRate.first()
            val ttsVolume         = prefs.ttsVolume.first()

            val hasLocationPermission = ContextCompat.checkSelfPermission(
                this@WorkoutService, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            withContext(Dispatchers.Main) {
                ttsManager = TtsManager(this@WorkoutService, speechRate, ttsVolume)
            }

            locationProvider = if (!treadmillMode && gpsEnabled && hasLocationPermission)
                GpsLocationProvider(this@WorkoutService)
            else
                NoOpLocationProvider()

            _gpsActive.value = locationProvider.isAvailable

            val sessionId = app.sessionRepository.startSession(programId, week, day)

            engine = WorkoutEngine(
                day = workoutDay,
                tts = ttsManager,
                ttsEnabled = ttsEnabled,
                countdownWarnings = countdownWarnings,
                midIntervalCues = midIntervalCues,
                scope = serviceScope
            )

            locationProvider.start()
            engine.start(sessionId)

            launch {
                locationProvider.updates.collect { update ->
                    _locationUpdates.emit(update)
                    val s = engine.state.value
                    if (s is WorkoutState.Active) {
                        app.sessionRepository.addRoutePoint(update.toEntity(s.sessionId))
                    }
                }
            }

            var lastIntervalIndex = -1
            launch {
                engine.state.collect { state ->
                    workoutStateFlow.value = state
                    when (state) {
                        is WorkoutState.Active -> {
                            if (state.intervalIndex != lastIntervalIndex && lastIntervalIndex >= 0) {
                                if (vibrationEnabled) vibrate()
                            }
                            lastIntervalIndex = state.intervalIndex
                            updateNotification(state)
                        }
                        is WorkoutState.Paused -> updateNotificationPaused()
                        is WorkoutState.Completed -> {
                            if (vibrationEnabled) vibrateCompletion()
                            app.sessionRepository.finishSession(
                                sessionId = state.sessionId,
                                durationSeconds = state.elapsedSessionSeconds,
                                distanceMeters = locationProvider.totalDistanceMeters,
                                completed = true
                            )
                            cleanup()
                            stopSelf()
                        }
                        is WorkoutState.Idle -> { /* initial state — no action needed */ }
                    }
                }
            }
        }
    }

    private fun handleStop() {
        val distance = locationProvider.totalDistanceMeters

        if (!::engine.isInitialized) {
            cleanup()
            stopSelf()
            return
        }

        val state = engine.state.value
        val sessionId = when (state) {
            is WorkoutState.Active -> state.sessionId
            is WorkoutState.Paused -> state.snapshot.sessionId
            else -> -1L
        }
        val elapsed = when (state) {
            is WorkoutState.Active -> state.elapsedSessionSeconds
            is WorkoutState.Paused -> state.snapshot.elapsedSessionSeconds
            else -> 0
        }

        // stop() cancels the tick loop without emitting Idle, so the state collector
        // won't race with the finishSession coroutine below.
        engine.stop()
        cleanup()

        if (sessionId < 0) {
            stopSelf()
            return
        }

        val app = application as C2KApp
        serviceScope.launch {
            app.sessionRepository.finishSession(
                sessionId = sessionId,
                durationSeconds = elapsed,
                distanceMeters = distance,
                completed = false
            )
            stopSelf()
        }
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        locationProvider.stop()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        isRunning.value = false
        currentWorkout.value = null
        workoutRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
    }

    // ── Wake lock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        wakeLock.acquire(WAKELOCK_TIMEOUT)
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun vibrate() {
        val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        doVibrate(effect)
    }

    private fun vibrateCompletion() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1)
        doVibrate(effect)
    }

    private fun doVibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val isPaused = ::engine.isInitialized && engine.state.value is WorkoutState.Paused
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                0,
                if (isPaused) getString(R.string.notification_action_resume)
                else getString(R.string.notification_action_pause),
                actionPending(if (isPaused) ACTION_RESUME else ACTION_PAUSE)
            )
            .addAction(0, getString(R.string.notification_action_stop), actionPending(ACTION_STOP))
            .build()
    }

    private fun updateNotification(state: WorkoutState.Active) {
        val mins = state.secondsRemainingInInterval / 60
        val secs = state.secondsRemainingInInterval % 60
        val time = "%d:%02d".format(mins, secs)
        val progress = "${state.intervalIndex + 1}/${state.totalIntervals}"
        val text = "${intervalLabel(state.currentInterval.type)}  $time  •  $progress"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun updateNotificationPaused() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(getString(R.string.workout_paused_notification)))
    }

    private fun intervalLabel(type: IntervalType): String = when (type) {
        IntervalType.RUN      -> getString(R.string.workout_interval_run)
        IntervalType.WALK     -> getString(R.string.workout_interval_walk)
        IntervalType.WARMUP   -> getString(R.string.workout_interval_warmup)
        IntervalType.COOLDOWN -> getString(R.string.workout_interval_cooldown)
    }

    private fun actionPending(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, WorkoutService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
