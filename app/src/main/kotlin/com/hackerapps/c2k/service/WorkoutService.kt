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
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
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
        // Separate ID from the ongoing workout notification so posting one never clobbers the
        // other; distinct from it specifically so completion can show a dismissible notification
        // side by side with (briefly) or in place of the non-swipable in-progress one.
        private const val COMPLETION_NOTIFICATION_ID = 2
        private const val CHANNEL_ID       = "workout_channel"
        private const val WAKELOCK_TAG     = "C2K::WorkoutLock"
        private const val WAKELOCK_TIMEOUT = 90 * 60 * 1000L
        // Upper bound on how long completion waits for the final spoken cue before tearing down.
        private const val COMPLETION_SPEECH_TIMEOUT_MS = 8_000L
        private const val TAG = "WorkoutService"

        val isRunning = MutableStateFlow(false)
        val currentWorkout = MutableStateFlow<WorkoutInfo?>(null)

        // Test-only seams. Left null in production, where handleStart() falls back to resolving
        // a real WorkoutDay from Programs and the app's real SessionRepository. Instrumented
        // tests use these to (a) drive a real Completed transition in seconds instead of waiting
        // out a multi-minute program, and (b) inject a repository that fails finishSession() to
        // verify the completion teardown (stopForeground + notification swap + stopSelf) still
        // runs when the DB write throws — the suspected root cause of the notification getting
        // stuck until the app is force-stopped.
        @VisibleForTesting
        var testWorkoutDayOverride: WorkoutDay? = null

        @VisibleForTesting
        var testSessionRepositoryOverride: SessionRepository? = null

        // Pure so it can be unit-tested on the JVM without a device — the OS kills any process
        // whose currently-granted runtime permission gets revoked mid-session, which made an
        // earlier instrumented version of this check (revoking ACTIVITY_RECOGNITION/location on
        // the live test process) crash CI's shared-process instrumentation run instead of testing
        // anything about WorkoutService itself. "health" only counts toward the type if
        // ACTIVITY_RECOGNITION is actually granted — the OS validates that permission against the
        // type at startForeground() time and throws a SecurityException if it's missing, same as
        // it does for "location". RequestActivityRecognitionPermission (PermissionGate.kt) lets
        // the user deny that prompt and proceeds anyway, so callers can't assume it was granted.
        // "location" is OR'd in on top of "health" only when this session is actually going to
        // track GPS, since a foreground service that accesses location must include the
        // "location" type or the OS blocks the location access outright.
        @VisibleForTesting
        internal fun foregroundServiceType(
            hasActivityRecognition: Boolean,
            hasLocation: Boolean,
            treadmill: Boolean
        ): Int {
            var type = 0
            if (hasActivityRecognition) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            if (hasLocation && !treadmill) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            return type
        }

        // Pure given a Context, so it can be built and asserted on directly in tests without a
        // running service instance. Unlike the in-progress notification, this one is dismissible:
        // the workout is over, there's nothing ongoing left to represent.
        fun buildCompletionNotification(context: Context): Notification {
            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.workout_complete_title))
                .setContentText(context.getString(R.string.workout_complete_message))
                .setContentIntent(openIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        }
    }

    // A CoroutineExceptionHandler here matters specifically because of the try/finally in the
    // Completed branch below: the finally block does the real teardown work (drop the ongoing
    // notification, post the completion one, stop the service) before an exception from the try
    // (e.g. a DB write failure) is allowed to propagate. Without a handler, that propagation would
    // crash the whole process — this logs it instead so a single failed write can't take down an
    // otherwise-completed workout.
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Unhandled exception in WorkoutService coroutine", e)
        }
    )

    private lateinit var ttsManager: TtsManager
    private lateinit var engine: WorkoutEngine
    // Initialised to NoOp so binder calls before the setup coroutine finishes are safe.
    private var locationProvider: LocationProvider = NoOpLocationProvider()
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var prefs: UserPreferences

    // The Job for handleStart()'s whole per-session setup, including the engine.state collector.
    // engine.stop() only cancels the tick loop, not this — without also cancelling this on manual
    // stop, the collector keeps running and, if a new workout starts before it winds down, it
    // observes the reassigned engine field (a shared var) and double-processes its completion
    // using this session's stale captured sessionRepository/ttsManager.
    private var sessionJob: Job? = null

    // Exposed via binder — safe to collect immediately; populated once setup coroutine runs.
    val workoutStateFlow = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    private val _locationUpdates = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)
    private val _gpsActive = MutableStateFlow(false)

    private var workoutRunning = false
    // Guards cleanup() against running twice. @Volatile + @Synchronized on cleanup() because the
    // completion-speech wait means cleanup() can now be reached concurrently from the completion
    // collector (Dispatchers.Default) and handleStop()/onDestroy() (main thread).
    @Volatile private var cleanedUp = false

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
            // locationProvider is paused/resumed alongside the engine: otherwise GPS fixes keep
            // arriving and accumulating into totalDistanceMeters for the entire duration of the
            // pause, even though the paused time is correctly excluded from elapsedSessionSeconds
            // — silently inflating the recorded distance for any run with a real-world pause.
            ACTION_PAUSE  -> if (::engine.isInitialized) { engine.pause(); locationProvider.pause() }
            ACTION_RESUME -> if (::engine.isInitialized) { engine.resume(); locationProvider.resume() }
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
            val hasActivityRecognition = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            val hasLocation = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            // Read treadmill mode synchronously from the intent extra set by the service starter;
            // prefs aren't available yet (setup coroutine hasn't run), so WorkoutViewModel embeds it.
            val treadmill = intent.getBooleanExtra(EXTRA_TREADMILL_MODE, false)
            val type = foregroundServiceType(hasActivityRecognition, hasLocation, treadmill)
            val notification = buildNotification(getString(R.string.workout_starting))
            try {
                if (type != 0) startForeground(NOTIFICATION_ID, notification, type)
                else startForeground(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Last-resort net: neither permission granted (or the platform validates some
                // other type requirement we don't know about) shouldn't be able to crash the
                // whole process — abort this start cleanly instead. This is the exact crash
                // reported from the Play Store (SecurityException out of handleStart -> startForeground).
                Log.e(TAG, "startForeground rejected for type=$type, aborting workout start", e)
                cleanup()
                stopSelf()
                return
            }
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.workout_starting)))
        }

        val workoutDay = testWorkoutDayOverride ?: Programs.byId(programId).weeks[week - 1][day - 1]
        val app = application as C2KApp
        val sessionRepository = testSessionRepositoryOverride ?: app.sessionRepository

        sessionJob = serviceScope.launch {
            val ttsEnabled        = prefs.ttsEnabled.first()
            val gpsEnabled        = prefs.gpsEnabled.first()
            val treadmillMode     = prefs.treadmillMode.first()
            val countdownWarnings = prefs.countdownWarnings.first()
            val countdownWarning1 = prefs.countdownWarning1.first()
            val countdownWarning2 = prefs.countdownWarning2.first()
            val midIntervalCues   = prefs.midIntervalCues.first()
            val vibrationEnabled  = prefs.vibrationEnabled.first()
            val vibrationStrength = prefs.vibrationStrength.first()
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

            val sessionId = sessionRepository.startSession(programId, week, day)

            engine = WorkoutEngine(
                day = workoutDay,
                tts = ttsManager,
                ttsEnabled = ttsEnabled,
                countdownWarnings = countdownWarnings,
                countdownWarningSeconds1 = countdownWarning1,
                countdownWarningSeconds2 = countdownWarning2,
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
                        sessionRepository.addRoutePoint(update.toEntity(s.sessionId))
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
                                if (vibrationEnabled) {
                                    VibrationPlayer.play(
                                        this@WorkoutService,
                                        VibrationPatterns.forInterval(
                                            state.currentInterval.type,
                                            vibrationStrength
                                        )
                                    )
                                }
                            }
                            lastIntervalIndex = state.intervalIndex
                            updateNotification(state)
                        }
                        is WorkoutState.Paused -> updateNotificationPaused()
                        is WorkoutState.Completed -> {
                            try {
                                if (vibrationEnabled) {
                                    VibrationPlayer.play(
                                        this@WorkoutService,
                                        VibrationPatterns.forCompletion(vibrationStrength)
                                    )
                                }
                                sessionRepository.finishSession(
                                    sessionId = state.sessionId,
                                    durationSeconds = state.elapsedSessionSeconds,
                                    distanceMeters = locationProvider.totalDistanceMeters,
                                    completed = true
                                )
                            } finally {
                                // Drop the ongoing (non-swipable) notification and swap in a
                                // dismissible completion one right away, in a finally so it happens
                                // even if the DB write above throws — otherwise the service never
                                // reaches cleanup()/stopSelf() below and the ongoing notification
                                // is stuck showing a stale countdown until the app is force-stopped.
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                    .notify(COMPLETION_NOTIFICATION_ID, buildCompletionNotification(this@WorkoutService))
                                // Clear the user-facing "running" state immediately (before the
                                // speech wait) so the app doesn't show a workout in progress while
                                // the final cue plays. Full teardown (TTS/location/wakelock)
                                // happens in cleanup().
                                isRunning.value = false
                                currentWorkout.value = null
                                // Let the final "Workout complete" announcement finish speaking
                                // before tearing down: cleanup() calls ttsManager.shutdown() ->
                                // tts.stop(), which otherwise cuts the cue off. Bounded so a
                                // stuck/again TTS engine can't keep the service alive indefinitely.
                                if (::ttsManager.isInitialized) {
                                    withTimeoutOrNull(COMPLETION_SPEECH_TIMEOUT_MS) {
                                        ttsManager.speaking.first { !it }
                                    }
                                }
                                cleanup()
                                stopSelf()
                            }
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
            // Same reasoning as the sessionJob?.cancel() below: without this, a Stop tapped during
            // handleStart()'s async setup (before engine is assigned) leaves that coroutine
            // running — it goes on to create the DB session row and assign engine/ttsManager
            // after the service was supposed to have stopped.
            sessionJob?.cancel()
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

        // stop() cancels the tick loop without emitting Idle, so the state collector won't race
        // with the finishSession coroutine below. Cancelling sessionJob (handleStart()'s whole
        // per-session setup, including that collector) matters for what comes *after* this
        // method returns: without it, the collector keeps running and, if a new workout starts
        // before it winds down, it observes the reassigned engine field (a shared var) and
        // double-processes that new workout's completion using this session's stale
        // sessionRepository/ttsManager.
        engine.stop()
        sessionJob?.cancel()
        cleanup()

        if (sessionId < 0) {
            stopSelf()
            return
        }

        val sessionRepository = testSessionRepositoryOverride ?: (application as C2KApp).sessionRepository
        serviceScope.launch {
            sessionRepository.finishSession(
                sessionId = sessionId,
                durationSeconds = elapsed,
                distanceMeters = distance,
                completed = false
            )
            stopSelf()
        }
    }

    @Synchronized
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
