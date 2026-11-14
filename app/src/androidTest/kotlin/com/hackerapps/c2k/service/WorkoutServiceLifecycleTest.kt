package com.hackerapps.c2k.service

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
import com.hackerapps.c2k.engine.WorkoutState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Session lifecycle on one WorkoutService instance: a second workout after the first, a start
 * racing a stop, and a start or stop arriving while a completed workout is still being saved.
 * Every session goes to an in-memory database, never the app's real history.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutServiceLifecycleTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    // Mirror WorkoutService's private notification IDs.
    private val ongoingNotificationId = 1
    private val cueNotificationId = 3

    private lateinit var db: AppDatabase
    private var connection: ServiceConnection? = null
    private var serviceWasStarted = false

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        WorkoutService.testSessionRepositoryOverride = SessionRepository(db)
        WorkoutService.testWorkoutDayOverride = null
        setSmartwatchCues(false)
        cancelCues()
    }

    @After
    fun tearDown() {
        if (serviceWasStarted) {
            sendAction(WorkoutService.ACTION_STOP)
            waitUntil { !WorkoutService.isRunning.value }
        }
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        // Let the OS finish destroying the service before the next test starts one (see
        // WorkoutServiceNotificationTest.tearDown).
        waitUntil(timeoutMs = 5_000) { !workoutServiceIsAlive() }
        Thread.sleep(500)
        WorkoutService.testWorkoutDayOverride = null
        WorkoutService.testSessionRepositoryOverride = null
        setSmartwatchCues(false)
        db.close()
    }

    @Test
    fun a_second_workout_on_a_bound_instance_runs_and_tears_down_like_the_first() {
        val binder = bind()
        setSmartwatchCues(true)
        WorkoutService.testWorkoutDayOverride = twoIntervalDay

        startWorkout()
        assertTrue("First workout should complete", waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Completed })
        assertTrue("First workout should tear down", waitUntil(10_000) { !WorkoutService.isRunning.value })
        assertTrue("The bound service should still be alive", workoutServiceIsAlive())
        cancelCues()

        // Same instance, second workout. Before the fix the first session's cleanup left the
        // instance marked cleaned up: watch cues were suppressed and nothing was released.
        startWorkout()
        assertTrue(
            "The new session must not show the previous workout's result",
            // Setup reads prefs and starts the cue player first, which can take seconds on a
            // cold TTS engine; the old code showed the previous Completed throughout.
            waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Active }
        )
        assertTrue(
            "Watch cue expected on the second workout's interval change",
            waitUntil(10_000) { cueNotificationId in activeNotificationIds() }
        )
        assertTrue("Second workout should complete", waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Completed })
        assertTrue("Second workout should tear down", waitUntil(10_000) { !WorkoutService.isRunning.value })
        assertEquals(listOf(true, true), sessions().map { it.completed })
    }

    @Test
    fun a_start_right_after_a_stop_is_not_killed_by_that_stop() {
        WorkoutService.testSessionRepositoryOverride = SlowFinishRepository(db, delayMs = 2_000)
        WorkoutService.testWorkoutDayOverride = longDay

        startWorkout()
        assertTrue(waitUntil { ongoingNotificationId in activeNotificationIds() })
        assertTrue(waitUntil(5_000) { sessions().isNotEmpty() })

        // Stop saves in the background and then stops the service; start again meanwhile.
        sendAction(WorkoutService.ACTION_STOP)
        startWorkout()

        // Past the stop's 2 s save: before the fix its stopSelf() destroyed the service, taking
        // the new workout with it.
        Thread.sleep(3_500)
        assertTrue("Service should still be running the new workout", workoutServiceIsAlive())
        assertTrue(WorkoutService.isRunning.value)
        assertTrue(ongoingNotificationId in activeNotificationIds())
    }

    @Test
    fun a_start_while_the_previous_workout_is_saving_runs_after_it() {
        val binder = bind()
        WorkoutService.testSessionRepositoryOverride = SlowFinishRepository(db, delayMs = 2_000)
        WorkoutService.testWorkoutDayOverride = fastDay

        startWorkout()
        assertTrue(waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Completed })
        // The completed workout is now being saved (2 s). Before the fix, this start was dropped.
        startWorkout()

        assertTrue(
            "Both workouts should be recorded as completed",
            waitUntil(20_000) { sessions().count { it.completed } == 2 }
        )
        assertTrue(waitUntil(10_000) { !WorkoutService.isRunning.value })
    }

    @Test
    fun stop_while_a_completed_workout_is_saving_keeps_it_completed() {
        val binder = bind()
        WorkoutService.testSessionRepositoryOverride = SlowFinishRepository(db, delayMs = 2_000)
        WorkoutService.testWorkoutDayOverride = fastDay

        startWorkout()
        assertTrue(waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Completed })
        // Stop on the still-visible ongoing notification. Before the fix this cancelled the
        // save, leaving the finished workout recorded as abandoned.
        sendAction(WorkoutService.ACTION_STOP)

        assertTrue(waitUntil(10_000) { !WorkoutService.isRunning.value })
        assertTrue(
            "Completed workout should be saved as completed",
            waitUntil(5_000) { sessions().singleOrNull()?.completed == true }
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private val fastDay = WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 1)))
    private val twoIntervalDay = WorkoutDay(
        week = 1, day = 1,
        intervals = listOf(Interval(IntervalType.WALK, 2), Interval(IntervalType.RUN, 2))
    )
    private val longDay = WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 600)))

    private class SlowFinishRepository(db: AppDatabase, private val delayMs: Long) : SessionRepository(db) {
        override suspend fun finishSession(sessionId: Long, durationSeconds: Int, distanceMeters: Float, completed: Boolean) {
            delay(delayMs)
            super.finishSession(sessionId, durationSeconds, distanceMeters, completed)
        }
    }

    private fun sessions() = runBlocking { db.sessionDao().getAll() }

    private fun startWorkout() {
        serviceWasStarted = true
        context.startForegroundService(
            Intent(context, WorkoutService::class.java).apply {
                action = WorkoutService.ACTION_START
                putExtra(WorkoutService.EXTRA_PROGRAM_ID, Programs.ID_C25K)
                putExtra(WorkoutService.EXTRA_WEEK, 1)
                putExtra(WorkoutService.EXTRA_DAY, 1)
            }
        )
    }

    private fun sendAction(action: String) {
        context.startService(Intent(context, WorkoutService::class.java).setAction(action))
    }

    private fun bind(): WorkoutService.LocalBinder {
        val latch = CountDownLatch(1)
        var binder: WorkoutService.LocalBinder? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service as WorkoutService.LocalBinder
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        context.bindService(Intent(context, WorkoutService::class.java), conn, Context.BIND_AUTO_CREATE)
        connection = conn
        assertTrue("Could not bind to WorkoutService", latch.await(5, TimeUnit.SECONDS))
        return binder!!
    }

    private fun setSmartwatchCues(on: Boolean) = runBlocking {
        UserPreferences(context).apply {
            setVibrationEnabled(on)
            setSmartwatchCues(on)
        }
    }

    private fun cancelCues() {
        notificationManager.activeNotifications
            .filter { it.id == cueNotificationId }
            .forEach { notificationManager.cancel(it.tag, it.id) }
    }

    private fun activeNotificationIds(): Set<Int> =
        notificationManager.activeNotifications.map { it.id }.toSet()

    @Suppress("DEPRECATION") // Still returns the caller's own services, which is all this needs.
    private fun workoutServiceIsAlive(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == WorkoutService::class.java.name }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
