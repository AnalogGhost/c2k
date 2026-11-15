package com.hackerapps.c2k.service

import android.annotation.SuppressLint
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
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.CueStyle
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
import com.hackerapps.c2k.engine.SplitCueTracker
import com.hackerapps.c2k.engine.WorkoutEngine
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.engine.countdownWarningThresholds
import com.hackerapps.c2k.engine.tone.TonePlayer
import com.hackerapps.c2k.engine.tts.CuePlayer
import com.hackerapps.c2k.engine.tts.TtsManager
import com.hackerapps.c2k.location.GpsLocationProvider
import com.hackerapps.c2k.location.LocationProvider
import com.hackerapps.c2k.location.LocationUpdate
import com.hackerapps.c2k.location.NoOpLocationProvider
import com.hackerapps.c2k.location.toEntity
import com.hackerapps.c2k.ui.MainActivity
import com.hackerapps.c2k.utils.localized

class WorkoutService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.localized())
    }

    data class WorkoutInfo(val programId: String, val week: Int, val day: Int)

    companion object {
        const val ACTION_START  = "com.hackerapps.c2k.action.START"
        const val ACTION_PAUSE  = "com.hackerapps.c2k.action.PAUSE"
        const val ACTION_RESUME = "com.hackerapps.c2k.action.RESUME"
        const val ACTION_STOP   = "com.hackerapps.c2k.action.STOP"
        const val ACTION_SKIP_WARMUP_COOLDOWN = "com.hackerapps.c2k.action.SKIP_WARMUP_COOLDOWN"

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
        // Short-lived cues for smartwatch bridges (see postCue). Their own channel, because the
        // workout channel is IMPORTANCE_LOW and bridges such as Gadgetbridge skip those.
        private const val CUE_NOTIFICATION_ID = 3
        private const val CUE_CHANNEL_ID   = "smartwatch_cues"
        // Long enough for a bridge to forward it, short enough that a missed cancel can't leave a
        // stale cue in the shade.
        private const val CUE_TIMEOUT_MS   = 10_000L
        private const val WAKELOCK_TAG     = "C2K::WorkoutLock"
        // Renewed at every checkpoint and on resume, so it only runs out if the service stops
        // making progress (or stays paused that long, when nothing needs the CPU). A fixed 90
        // minutes used to run out during a paused 70-minute 10K workout.
        @VisibleForTesting
        internal var wakeLockTimeoutMs = 10 * 60 * 1000L
        // Upper bound on how long completion waits for the final spoken cue before tearing down.
        private const val COMPLETION_SPEECH_TIMEOUT_MS = 8_000L
        // How often a running workout's time and distance are saved, so a run the app is closed
        // during (by Android or a crash) keeps nearly all of it.
        @VisibleForTesting
        internal const val CHECKPOINT_INTERVAL_SECONDS = 15
        private const val TAG = "WorkoutService"

        val isRunning = MutableStateFlow(false)
        /**
         * True when Android refused to run the last requested workout in the foreground (on
         * Android 14+, usually because Physical activity was denied and GPS isn't in use), so
         * the workout screen can explain instead of waiting forever. Cleared by the next start.
         */
        val startRejected = MutableStateFlow(false)
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

        // Stand in for GPS and the voice, so tests can feed distance and see which cues play.
        // The location override is used only where the real GPS would be (location permission
        // granted, GPS on, not treadmill mode), so the choice between GPS and none stays tested.
        @VisibleForTesting
        var testLocationProviderOverride: LocationProvider? = null

        @VisibleForTesting
        var testCuePlayerOverride: CuePlayer? = null

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
        // The type constants are plain ints copied in at compile time, so reading them is safe on
        // any API level; the caller only uses the result on API 34+.
        @SuppressLint("InlinedApi")
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

        /**
         * Whether to speak distance and pace at each split: only with spoken cues (a chime can't
         * carry a number) and only while GPS actually tracks the run (not treadmill mode, GPS off,
         * or location denied).
         */
        @VisibleForTesting
        internal fun distanceCuesActive(
            ttsEnabled: Boolean,
            cueStyle: CueStyle,
            distanceCues: Boolean,
            gpsTracking: Boolean
        ): Boolean = ttsEnabled && cueStyle == CueStyle.VOICE && distanceCues && gpsTracking

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

    private lateinit var cuePlayer: CuePlayer
    private lateinit var engine: WorkoutEngine
    // Initialised to NoOp so binder calls before the setup coroutine finishes are safe.
    private var locationProvider: LocationProvider = NoOpLocationProvider()
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var prefs: UserPreferences
    // Tag of the single cue notification currently posted, if any.
    private var cueTag: String? = null

    // The Job for handleStart()'s whole per-session setup, including the engine.state collector.
    // engine.stop() only cancels the tick loop, not this — without also cancelling this on manual
    // stop, the collector keeps running and, if a new workout starts before it winds down, it
    // observes the reassigned engine field (a shared var) and double-processes its completion
    // using this session's stale captured sessionRepository/cuePlayer.
    private var sessionJob: Job? = null

    // Exposed via binder — safe to collect immediately; populated once setup coroutine runs.
    val workoutStateFlow = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    private val _locationUpdates = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)
    private val _gpsActive = MutableStateFlow(false)

    private var workoutRunning = false
    // True from the moment a workout completes until its teardown finishes (the final cue can
    // take up to COMPLETION_SPEECH_TIMEOUT_MS). Only touched on the main thread, like
    // workoutRunning, so a START can't slip between the two.
    private var finishing = false
    // A START that arrived while finishing; run once the previous session is torn down, instead
    // of being dropped (or tearing down the new session along with the old one).
    private var pendingStart: Intent? = null
    private var destroyed = false
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
            // GPS follows only an actual pause or resume. A command the engine ignores (a second
            // Resume, a Pause before the workout is running) must not touch it: a stray resume()
            // drops the last fix and loses a stretch of route, and a stray pause() stops GPS for
            // the rest of the run.
            ACTION_PAUSE  -> if (::engine.isInitialized && engine.pause()) locationProvider.pause()
            ACTION_RESUME -> if (::engine.isInitialized && engine.resume()) {
                locationProvider.resume()
                renewWakeLock()
            }
            ACTION_SKIP_WARMUP_COOLDOWN -> if (::engine.isInitialized) engine.skipWarmupOrCooldown()
            ACTION_STOP   -> handleStop(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (finishing) {
            // The previous workout has already left the foreground, and this START came through
            // startForegroundService(), so the service must go foreground again now or the OS
            // kills the app. The workout itself starts once the old one is torn down.
            if (startForegroundFor(intent)) pendingStart = intent
            else { startRejected.value = true; stopSelf() }
            return
        }
        // Already foreground with a live session: the OS doesn't need another startForeground().
        if (workoutRunning) return

        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID) ?: return
        val week      = intent.getIntExtra(EXTRA_WEEK, -1).takeIf { it >= 0 } ?: return
        val day       = intent.getIntExtra(EXTRA_DAY, -1).takeIf { it >= 0 } ?: return

        // This instance may have run a workout already (a bound client keeps the service alive
        // past stopSelf()). Clear that session's leftovers, or cleanup() would stay a no-op for
        // this one, leaking GPS, TTS and the wake lock, and bound clients would see its result.
        cleanedUp = false
        workoutStateFlow.value = WorkoutState.Idle
        locationProvider = NoOpLocationProvider()
        _gpsActive.value = false

        workoutRunning = true
        isRunning.value = true
        currentWorkout.value = WorkoutInfo(programId, week, day)
        acquireWakeLock()
        if (!startForegroundFor(intent)) {
            startRejected.value = true
            cleanup()
            stopSelf()
            return
        }
        startRejected.value = false

        val workoutDay = testWorkoutDayOverride ?: Programs.byId(programId).weeks[week - 1][day - 1]
        val app = application as C2KApp
        val sessionRepository = testSessionRepositoryOverride ?: app.sessionRepository

        val job = serviceScope.launch {
            val ttsEnabled        = prefs.ttsEnabled.first()
            val gpsEnabled        = prefs.gpsEnabled.first()
            val treadmillMode     = prefs.treadmillMode.first()
            val countdownWarnings = prefs.countdownWarnings.first()
            val countdownWarning1 = prefs.countdownWarning1.first()
            val countdownWarning2 = prefs.countdownWarning2.first()
            val countdownWarning2Enabled = prefs.countdownWarning2Enabled.first()
            val midIntervalCues   = prefs.midIntervalCues.first()
            val periodicTimeCues         = prefs.periodicTimeCues.first()
            val periodicTimeCueInterval  = prefs.periodicTimeCueInterval.first()
            val distanceCues             = prefs.distanceCues.first()
            val distanceCueSplitMeters   = prefs.distanceCueSplitMeters.first()
            val vibrationEnabled  = prefs.vibrationEnabled.first()
            val vibrationStrength = prefs.vibrationStrength.first()
            val smartwatchCues    = prefs.smartwatchCues.first()
            // The watch gets a notification at exactly the moments the phone would vibrate. That
            // notification buzzes the phone too (see createNotificationChannel), so it replaces
            // the phone's own pattern rather than doubling it.
            val notifyWatch       = vibrationEnabled && smartwatchCues
            val vibratePhone      = vibrationEnabled && !smartwatchCues
            val speechRate        = prefs.ttsSpeechRate.first()
            val ttsVolume         = prefs.ttsVolume.first()
            val cueStyle          = prefs.cueStyle.first()
            val skipWarmup        = prefs.skipWarmup.first()
            val skipCooldown      = prefs.skipCooldown.first()
            val effectiveWorkoutDay = workoutDay.skipping(warmup = skipWarmup, cooldown = skipCooldown)

            val hasLocationPermission = ContextCompat.checkSelfPermission(
                this@WorkoutService, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            withContext(Dispatchers.Main) {
                cuePlayer = testCuePlayerOverride ?: when (cueStyle) {
                    CueStyle.VOICE -> TtsManager(this@WorkoutService, speechRate, ttsVolume)
                    CueStyle.TONES -> TonePlayer(
                        this@WorkoutService,
                        ttsVolume,
                        countdownWarningThresholds(countdownWarning1, countdownWarning2, countdownWarning2Enabled)
                    )
                }
            }

            locationProvider = if (!treadmillMode && gpsEnabled && hasLocationPermission)
                testLocationProviderOverride ?: GpsLocationProvider(this@WorkoutService)
            else
                NoOpLocationProvider()

            _gpsActive.value = locationProvider.isAvailable
            // Follow Location being switched on or off during the workout.
            launch { locationProvider.availability.collect { _gpsActive.value = it } }
            val splitTracker = if (
                distanceCuesActive(ttsEnabled, cueStyle, distanceCues, gpsTracking = locationProvider !is NoOpLocationProvider)
            ) SplitCueTracker(distanceCueSplitMeters) else null

            val sessionId = sessionRepository.startSession(programId, week, day)

            engine = WorkoutEngine(
                day = effectiveWorkoutDay,
                tts = cuePlayer,
                ttsEnabled = ttsEnabled,
                countdownWarnings = countdownWarnings,
                countdownWarningSeconds1 = countdownWarning1,
                countdownWarningSeconds2 = countdownWarning2,
                countdownWarning2Enabled = countdownWarning2Enabled,
                midIntervalCues = midIntervalCues,
                periodicTimeCues = periodicTimeCues,
                periodicTimeCueIntervalSeconds = periodicTimeCueInterval,
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
                        // Queued behind any interval cue rather than cutting it off.
                        splitTracker?.onProgress(locationProvider.totalDistanceMeters, s.elapsedSessionSeconds)
                            ?.let { cuePlayer.announce(it, queueAdd = true) }
                    }
                }
            }

            var lastIntervalIndex = -1
            var lastCheckpointSeconds = 0
            // Saves progress to the session row in the background; a failed write is logged by
            // the scope's handler and the next checkpoint tries again.
            fun checkpoint(snapshot: WorkoutState.Active) {
                lastCheckpointSeconds = snapshot.elapsedSessionSeconds
                renewWakeLock()
                val distance = locationProvider.totalDistanceMeters
                launch {
                    sessionRepository.checkpointSession(snapshot.sessionId, snapshot.elapsedSessionSeconds, distance)
                }
            }
            launch {
                engine.state.collect { state ->
                    workoutStateFlow.value = state
                    when (state) {
                        is WorkoutState.Active -> {
                            if (state.intervalIndex != lastIntervalIndex && lastIntervalIndex >= 0) {
                                if (vibratePhone) {
                                    VibrationPlayer.play(
                                        this@WorkoutService,
                                        VibrationPatterns.forInterval(
                                            state.currentInterval.type,
                                            vibrationStrength
                                        )
                                    )
                                }
                                if (notifyWatch) postIntervalCue(state.currentInterval)
                            }
                            lastIntervalIndex = state.intervalIndex
                            updateNotification(state)
                            if (state.elapsedSessionSeconds - lastCheckpointSeconds >= CHECKPOINT_INTERVAL_SECONDS) {
                                checkpoint(state)
                            }
                        }
                        is WorkoutState.Paused -> {
                            updateNotificationPaused()
                            // A paused run is the likeliest to be closed by Android: save it now.
                            checkpoint(state.snapshot)
                        }
                        is WorkoutState.Completed -> {
                            // Mark finishing on the main thread, where START and STOP are handled,
                            // so neither can interleave with the end of the session below.
                            withContext(Dispatchers.Main + NonCancellable) { finishing = true }
                            try {
                                if (vibratePhone) {
                                    VibrationPlayer.play(
                                        this@WorkoutService,
                                        VibrationPatterns.forCompletion(vibrationStrength)
                                    )
                                }
                                // NonCancellable: losing this write would record a finished
                                // workout as abandoned and hold back progression.
                                withContext(NonCancellable) {
                                    sessionRepository.finishSession(
                                        sessionId = state.sessionId,
                                        durationSeconds = state.elapsedSessionSeconds,
                                        distanceMeters = locationProvider.totalDistanceMeters,
                                        completed = true
                                    )
                                }
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
                                // Let the final "Workout complete" announcement (or chime) finish
                                // before tearing down: cleanup() calls cuePlayer.shutdown(), which
                                // otherwise cuts the cue off. Bounded so a stuck TTS engine can't
                                // keep the service alive indefinitely.
                                if (::cuePlayer.isInitialized) {
                                    withTimeoutOrNull(COMPLETION_SPEECH_TIMEOUT_MS) {
                                        cuePlayer.speaking.first { !it }
                                    }
                                }
                                cleanup()
                                // After cleanup(), which cancels the interval notification: this one
                                // is meant to still be on the watch once the service is gone.
                                if (notifyWatch) postCompletionCue()
                                withContext(Dispatchers.Main + NonCancellable) { endFinishing() }
                            }
                        }
                        is WorkoutState.Idle -> { /* initial state — no action needed */ }
                    }
                }
            }
        }
        sessionJob = job
        // If setup fails (say a DB write throws before the engine starts), the exception handler
        // only logs it, which would leave the "Starting…" notification up and the service stuck
        // as running, so a later start would return early. Tear down as Stop would. Skipped when
        // a newer session has replaced this one, so a late failure can't stop that session.
        job.invokeOnCompletion { cause ->
            if (cause != null && cause !is CancellationException && sessionJob === job) {
                Log.e(TAG, "Workout session failed (logged above); stopping the service")
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * Puts the service in the foreground for a START [intent], with the types its permissions
     * allow. Returns false if the OS rejected that, in which case the start must be abandoned.
     */
    private fun startForegroundFor(intent: Intent): Boolean {
        val notification = buildNotification(getString(R.string.workout_starting))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification)
            return true
        }
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
        return try {
            if (type != 0) startForeground(NOTIFICATION_ID, notification, type)
            else startForeground(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            // Last-resort net: neither permission granted (or the platform validates some other
            // type requirement we don't know about) shouldn't be able to crash the whole process
            // — abort this start cleanly instead. This is the exact crash reported from the Play
            // Store (SecurityException out of handleStart -> startForeground).
            Log.e(TAG, "startForeground rejected for type=$type, aborting workout start", e)
            false
        }
    }

    /** Main thread. Ends the completion teardown: start the workout queued meanwhile, or stop. */
    private fun endFinishing() {
        finishing = false
        if (destroyed) return
        val next = pendingStart
        pendingStart = null
        if (next != null) handleStart(next) else stopSelf()
    }

    private fun handleStop(startId: Int) {
        if (finishing) {
            // The workout already completed and is being saved and torn down; a Stop tapped on
            // the notification in that window must not cancel the save. Drop any queued start.
            pendingStart = null
            return
        }
        val distance = locationProvider.totalDistanceMeters

        if (!::engine.isInitialized) {
            // Same reasoning as the sessionJob?.cancel() below: without this, a Stop tapped during
            // handleStart()'s async setup (before engine is assigned) leaves that coroutine
            // running — it goes on to create the DB session row and assign engine/cuePlayer
            // after the service was supposed to have stopped.
            sessionJob?.cancel()
            cleanup()
            stopSelf(startId)
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
        // sessionRepository/cuePlayer.
        engine.stop()
        sessionJob?.cancel()
        cleanup()

        if (sessionId < 0) {
            stopSelf(startId)
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
            // Only if no command arrived since this Stop: a workout started while the save was
            // in flight lives on this same instance, and a bare stopSelf() would kill it.
            stopSelf(startId)
        }
    }

    @Synchronized
    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        locationProvider.stop()
        if (::cuePlayer.isInitialized) cuePlayer.shutdown()
        cueTag?.let { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(it, CUE_NOTIFICATION_ID) }
        cueTag = null
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        isRunning.value = false
        currentWorkout.value = null
        workoutRunning = false
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
    }

    // ── Wake lock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            // Not reference counted: each renewal replaces the timeout rather than stacking
            // holds that cleanup()'s single release() would leave behind.
            .apply { setReferenceCounted(false) }
        wakeLock.acquire(wakeLockTimeoutMs)
    }

    /** Pushes the wake lock's timeout out again; never re-takes it once the session is cleaned up. */
    @Synchronized
    private fun renewWakeLock() {
        if (!cleanedUp && ::wakeLock.isInitialized) wakeLock.acquire(wakeLockTimeoutMs)
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        // Default importance so smartwatch bridges forward it. It must vibrate: Wear OS applies the
        // phone channel's settings on the watch, so a channel without vibration arrives there
        // silently. The phone buzzes with it, which is why the service then skips its own pattern.
        nm.createNotificationChannel(
            NotificationChannel(
                CUE_CHANNEL_ID,
                getString(R.string.notification_cue_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_cue_channel_desc)
                setSound(null, null)
                enableVibration(true)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = openAppIntent()
        val isPaused = ::engine.isInitialized && engine.state.value is WorkoutState.Paused
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            // No personal data here — just interval/time/progress — so show it in full on a
            // locked screen instead of NotificationCompat's default VISIBILITY_PRIVATE, which
            // Android redacts to "Content hidden" unless the user has separately opted in to
            // showing all notification content on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
        val time = formatClock(state.secondsRemainingInInterval)
        val progress = "${state.intervalIndex + 1}/${state.totalIntervals}"
        val runsLeft = resources.getQuantityString(
            R.plurals.notification_runs_remaining,
            state.remainingRunIntervals,
            state.remainingRunIntervals
        )
        val text = "${intervalLabel(state.currentInterval.type)}  $time  •  $progress  •  $runsLeft"
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

    private fun formatClock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // Posts the smartwatch notification as a regular (non-ongoing) notification so a bridge
    // forwards it; the ongoing workout notification can't do that, bridges skip ongoing and
    // foreground-service notifications. At most one exists at a time: the previous one is
    // cancelled before the next is posted, so they never stack up in the shade.
    @Synchronized
    private fun postCue(title: String, text: String?) {
        if (cleanedUp) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        cueTag?.let { nm.cancel(it, CUE_NOTIFICATION_ID) }
        // A fresh tag every time. Re-posting one key would count against the system's update rate
        // limit (the ongoing notification already updates 5x/s) and would not look like a new
        // notification to a bridge.
        val tag = "cue_${SystemClock.elapsedRealtimeNanos()}"
        nm.notify(tag, CUE_NOTIFICATION_ID, buildCueNotification(title, text))
        cueTag = tag
    }

    private fun buildCueNotification(title: String, text: String?): Notification =
        NotificationCompat.Builder(this, CUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setTimeoutAfter(CUE_TIMEOUT_MS)
            // Bridges only forward a repeat from the same app when `when` has moved forward.
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun postIntervalCue(interval: Interval) =
        postCue(intervalLabel(interval.type), formatClock(interval.durationSeconds))

    private fun postCompletionCue() =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            "cue_complete",
            CUE_NOTIFICATION_ID,
            buildCueNotification(
                getString(R.string.workout_complete_title),
                getString(R.string.workout_complete_message)
            )
        )

    private fun actionPending(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, WorkoutService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
