package com.hackerapps.c2k.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.repository.SessionRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression coverage for the "final countdown notification stops at 0:01 and won't go away
// unless the app is force-stopped" report: the ongoing workout notification is non-swipable by
// design (Android requires that while a foreground service is active), but on completion it must
// be dropped promptly and replaced with a dismissible one — not left stuck.
@RunWith(AndroidJUnit4::class)
class WorkoutServiceNotificationTest {

    // ACTIVITY_RECOGNITION is required to start WorkoutService's "health" foreground service type
    // on API 34+ (see PermissionGate.kt) — without it, ACTION_START throws a SecurityException.
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val ongoingNotificationId = 1
    // Mirrors WorkoutService's private COMPLETION_NOTIFICATION_ID — kept in sync manually since
    // that constant isn't exposed outside the service.
    private val completionNotificationId = 2

    // A workout that completes in ~1 second of real elapsed time instead of the 20+ minutes a
    // real program takes, so tests can observe a genuine Completed transition without a long wait.
    private val fastWorkoutDay = WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 1)))

    private lateinit var db: AppDatabase

    private fun activeNotificationIds(): Set<Int> =
        notificationManager.activeNotifications.map { it.id }.toSet()

    private fun waitUntil(timeoutMs: Long = 5_000, pollMs: Long = 100, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(pollMs)
        }
        return condition()
    }

    // Tracks whether *this* test called startWorkout(), independently of the shared/global
    // WorkoutService.isRunning flag. isRunning is companion (process-wide) state, so if a
    // previous test's session is still winding down (e.g. slow first-time DataStore reads
    // delaying how long its workout actually takes to complete), its belated completion can flip
    // isRunning back to false *during* this test's window — making "isRunning.value == false"
    // an unreliable signal for "this test's own session has finished."
    private var serviceWasStarted = false

    private fun startWorkout() {
        serviceWasStarted = true
        val startIntent = Intent(context, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_START
            putExtra(WorkoutService.EXTRA_PROGRAM_ID, Programs.ID_C25K)
            putExtra(WorkoutService.EXTRA_WEEK, 1)
            putExtra(WorkoutService.EXTRA_DAY, 1)
        }
        context.startForegroundService(startIntent)
    }

    @Before
    fun resetServiceState() {
        serviceWasStarted = false
        WorkoutService.testWorkoutDayOverride = null
        WorkoutService.testSessionRepositoryOverride = null
        notificationManager.cancel(completionNotificationId)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        // Unconditionally stop (not "if isRunning") whenever this test started a workout, so
        // handleStop() cancels sessionJob and definitively kills this test's own session —
        // regardless of whether it looked complete already — before the next test starts one.
        // Sending ACTION_STOP when nothing was ever started crashes the app (see startWorkout()
        // comment history / PermissionGate), so this is gated on serviceWasStarted, not a global
        // running check.
        if (serviceWasStarted) {
            context.startService(Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))
            waitUntil { !WorkoutService.isRunning.value }
        }
        // stopSelf()/onDestroy() tear down the ServiceRecord asynchronously from our perspective —
        // our own isRunning flag flips before the OS has necessarily finished destroying it. A
        // startForegroundService() from the next test racing that teardown is what was producing
        // ForegroundServiceDidNotStartInTimeException crashes between tests in this class.
        Thread.sleep(1_000)
        WorkoutService.testWorkoutDayOverride = null
        WorkoutService.testSessionRepositoryOverride = null
        db.close()
    }

    @Test
    fun completion_notification_is_dismissible_unlike_the_ongoing_one() {
        val notification = WorkoutService.buildCompletionNotification(context)
        assertFalse(
            "Completion notification must not be ongoing, or the OS won't let it be swiped away",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        )
        assertTrue(
            "Completion notification should auto-cancel once the user taps/dismisses it",
            notification.flags and Notification.FLAG_AUTO_CANCEL != 0
        )
    }

    @Test
    fun stopping_the_service_removes_the_ongoing_notification() {
        startWorkout()

        // startForeground() runs synchronously at the top of handleStart(), before any coroutine
        // work, so the ongoing notification should appear almost immediately.
        assertTrue(
            "Expected the ongoing workout notification to be posted after starting",
            waitUntil { ongoingNotificationId in activeNotificationIds() }
        )

        context.startService(Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))

        assertTrue(
            "Expected the ongoing notification to be removed once the service stops — a stuck, " +
                "non-swipable notification after the workout ends is exactly what was reported",
            waitUntil { ongoingNotificationId !in activeNotificationIds() }
        )
    }

    @Test
    fun completing_a_workout_swaps_the_ongoing_notification_for_a_dismissible_one() {
        WorkoutService.testWorkoutDayOverride = fastWorkoutDay
        startWorkout()

        assertTrue(
            "Expected the ongoing workout notification to be posted after starting",
            waitUntil { ongoingNotificationId in activeNotificationIds() }
        )

        // Bounded by COMPLETION_SPEECH_TIMEOUT_MS (8s) plus the ~1s workout itself.
        assertTrue(
            "Expected the ongoing notification to be dropped once the workout completes naturally",
            waitUntil(timeoutMs = 12_000) { ongoingNotificationId !in activeNotificationIds() }
        )
        assertTrue(
            "Expected a dismissible completion notification to replace it",
            completionNotificationId in activeNotificationIds()
        )
    }

    @Test
    fun completion_teardown_still_happens_when_finishSession_throws() {
        WorkoutService.testWorkoutDayOverride = fastWorkoutDay
        WorkoutService.testSessionRepositoryOverride = ThrowingSessionRepository(db)
        startWorkout()

        assertTrue(
            "Expected the ongoing workout notification to be posted after starting",
            waitUntil { ongoingNotificationId in activeNotificationIds() }
        )

        // If the try/finally around finishSession() in the Completed branch regressed, the
        // exception would skip stopForeground()/the notification swap entirely, and this would
        // time out with the stale ongoing notification still showing — exactly the reported bug.
        assertTrue(
            "Expected teardown (stopForeground + completion notification) to run even though " +
                "finishSession() threw",
            waitUntil(timeoutMs = 12_000) { ongoingNotificationId !in activeNotificationIds() }
        )
        assertTrue(
            "Expected the dismissible completion notification to be posted despite the DB failure",
            completionNotificationId in activeNotificationIds()
        )
        assertTrue(
            "Expected cleanup() to have run, clearing the running flag",
            waitUntil { !WorkoutService.isRunning.value }
        )
    }

    private class ThrowingSessionRepository(db: AppDatabase) : SessionRepository(db) {
        override suspend fun finishSession(
            sessionId: Long,
            durationSeconds: Int,
            distanceMeters: Float,
            completed: Boolean
        ) {
            throw IllegalStateException("Simulated DB failure to verify completion teardown still runs")
        }
    }
}
