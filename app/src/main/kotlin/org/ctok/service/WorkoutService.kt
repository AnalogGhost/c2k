package org.ctok.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ctok.CtoKApp
import org.ctok.R
import org.ctok.data.model.Programs
import org.ctok.data.prefs.UserPreferences
import org.ctok.engine.WorkoutEngine
import org.ctok.engine.WorkoutState
import org.ctok.engine.tts.TtsManager
import org.ctok.location.GpsLocationProvider
import org.ctok.location.LocationProvider
import org.ctok.location.NoOpLocationProvider
import org.ctok.location.toEntity
import org.ctok.ui.MainActivity

class WorkoutService : Service() {

    companion object {
        const val ACTION_START  = "org.ctok.action.START"
        const val ACTION_PAUSE  = "org.ctok.action.PAUSE"
        const val ACTION_RESUME = "org.ctok.action.RESUME"
        const val ACTION_STOP   = "org.ctok.action.STOP"

        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_WEEK       = "week"
        const val EXTRA_DAY        = "day"

        private const val NOTIFICATION_ID  = 1
        private const val CHANNEL_ID       = "workout_channel"
        private const val WAKELOCK_TAG     = "CtoK::WorkoutLock"
        private const val WAKELOCK_TIMEOUT = 90 * 60 * 1000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var ttsManager: TtsManager
    private lateinit var engine: WorkoutEngine
    private lateinit var locationProvider: LocationProvider
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var prefs: UserPreferences

    inner class LocalBinder : Binder() {
        fun getEngine(): WorkoutEngine = engine
        fun getLocationProvider(): LocationProvider = locationProvider
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
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID) ?: return
        val week      = intent.getIntExtra(EXTRA_WEEK, -1).takeIf { it >= 0 } ?: return
        val day       = intent.getIntExtra(EXTRA_DAY, -1).takeIf { it >= 0 } ?: return

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Starting…"))

        val workoutDay = Programs.byId(programId).weeks[week - 1][day - 1]
        val app = application as CtoKApp

        serviceScope.launch {
            val ttsEnabled        = prefs.ttsEnabled.first()
            val gpsEnabled        = prefs.gpsEnabled.first()
            val countdownWarnings = prefs.countdownWarnings.first()

            ttsManager = TtsManager(this@WorkoutService)

            locationProvider = if (gpsEnabled) GpsLocationProvider(this@WorkoutService)
                               else NoOpLocationProvider()

            val sessionId = app.sessionRepository.startSession(programId, week, day)

            engine = WorkoutEngine(
                day = workoutDay,
                tts = ttsManager,
                ttsEnabled = ttsEnabled,
                countdownWarnings = countdownWarnings,
                scope = serviceScope
            )

            locationProvider.start()
            engine.start(sessionId)

            launch {
                locationProvider.updates.collect { update ->
                    val s = engine.state.value
                    if (s is WorkoutState.Active) {
                        app.sessionRepository.addRoutePoint(update.toEntity(s.sessionId))
                    }
                }
            }

            launch {
                engine.state.collect { state ->
                    when (state) {
                        is WorkoutState.Active    -> updateNotification(state)
                        is WorkoutState.Paused    -> updateNotificationPaused()
                        is WorkoutState.Completed -> {
                            app.sessionRepository.finishSession(
                                sessionId = sessionId,
                                durationSeconds = workoutDay.totalDurationSeconds,
                                distanceMeters = locationProvider.totalDistanceMeters,
                                completed = true
                            )
                            stopSelf()
                        }
                        is WorkoutState.Idle -> stopSelf()
                    }
                }
            }
        }
    }

    private fun handleStop() {
        if (::engine.isInitialized) {
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
            engine.stop()
            if (sessionId >= 0) {
                val app = application as CtoKApp
                val distance = if (::locationProvider.isInitialized)
                    locationProvider.totalDistanceMeters else 0f
                serviceScope.launch {
                    app.sessionRepository.finishSession(
                        sessionId = sessionId,
                        durationSeconds = elapsed,
                        distanceMeters = distance,
                        completed = false
                    )
                }
            }
        }
        if (::locationProvider.isInitialized) locationProvider.stop()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationProvider.isInitialized) locationProvider.stop()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
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
        val text = "${state.currentInterval.type.name}  %d:%02d".format(mins, secs)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun updateNotificationPaused() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification("Paused"))
    }

    private fun actionPending(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, WorkoutService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
