package com.hackerapps.c2k.service

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.CueStyle
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
import com.hackerapps.c2k.data.prefs.VibrationTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    // Mirrors WorkoutService's private CUE_NOTIFICATION_ID, same as above.
    private val cueNotificationId = 3

    // A workout that completes in ~1 second of real elapsed time instead of the 20+ minutes a
    // real program takes, so tests can observe a genuine Completed transition without a long wait.
    private val fastWorkoutDay = WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 1)))

    private lateinit var db: AppDatabase

    private fun activeNotificationIds(): Set<Int> =
        notificationManager.activeNotifications.map { it.id }.toSet()

    private fun ongoingNotificationText(): CharSequence? =
        notificationManager.activeNotifications
            .firstOrNull { it.id == ongoingNotificationId }
            ?.notification
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)

    @Suppress("DEPRECATION") // Still returns the caller's own services, which is all this needs.
    private fun workoutServiceIsAlive(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == WorkoutService::class.java.name }

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

    private fun vibrateOn(target: VibrationTarget) = runBlocking {
        UserPreferences(context).apply {
            setVibrationEnabled(true)
            setVibrationTarget(target)
        }
    }

    private fun resetVibration() = runBlocking {
        UserPreferences(context).apply {
            setVibrationEnabled(false)
            setVibrationTarget(VibrationTarget.PHONE)
        }
    }

    @Before
    fun resetServiceState() {
        serviceWasStarted = false
        WorkoutService.testWorkoutDayOverride = null
        WorkoutService.testSessionRepositoryOverride = null
        resetVibration()
        notificationManager.cancel(completionNotificationId)
        // A watch notification from an earlier test can outlive it (it times out after 10 s).
        notificationManager.activeNotifications
            .filter { it.id == cueNotificationId }
            .forEach { notificationManager.cancel(it.tag, it.id) }
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
        resetVibration()
        runBlocking { UserPreferences(context).setCueStyle(CueStyle.VOICE) }
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

        // Same CI flakiness as the ongoing-notification-removal checks below (slower, 2-core,
        // software-rendered emulator): stopForeground() is an async binder call, and this one was
        // left on the 5s default when those were bumped to 12s for the same race. 5/5 local runs
        // finished in 1.2-2.1s, so this is a timeout-margin fix, not a behavior change.
        assertTrue(
            "Expected the ongoing notification to be removed once the service stops — a stuck, " +
                "non-swipable notification after the workout ends is exactly what was reported",
            waitUntil(timeoutMs = 12_000) { ongoingNotificationId !in activeNotificationIds() }
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
        // stopForeground() and the completion notify() are two separate async binder calls with
        // no atomicity guarantee between them, so a bare one-shot check here can lose a genuine
        // (harmless) race on slower/CI emulators even though the first waitUntil already passed.
        assertTrue(
            "Expected a dismissible completion notification to replace it",
            waitUntil(timeoutMs = 2_000) { completionNotificationId in activeNotificationIds() }
        )
    }

    @Test
    fun a_workout_in_tones_mode_completes_and_tears_down() {
        runBlocking { UserPreferences(context).setCueStyle(CueStyle.TONES) }
        WorkoutService.testWorkoutDayOverride = fastWorkoutDay
        startWorkout()

        assertTrue(
            "Expected the ongoing workout notification to be posted after starting",
            waitUntil { ongoingNotificationId in activeNotificationIds() }
        )
        assertTrue(
            "Expected the workout to complete with chimes in place of the voice",
            waitUntil(timeoutMs = 12_000) { completionNotificationId in activeNotificationIds() }
        )
        // Teardown waits on the completion chime's speaking signal; a signal that never clears
        // would keep the service alive for COMPLETION_SPEECH_TIMEOUT_MS (8 s) rather than the
        // chime's ~1 s. (isRunning can't show this: it is cleared before that wait.)
        assertTrue(
            "Expected the service to stop once the completion chime has played",
            waitUntil(timeoutMs = 6_000) { !workoutServiceIsAlive() }
        )
    }

    @Test
    fun ongoing_notification_shows_remaining_run_intervals() {
        // WARMUP first so the notification reflects both RUN intervals before either has started.
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 1, day = 1,
            intervals = listOf(
                Interval(IntervalType.WARMUP, 10),
                Interval(IntervalType.RUN, 10),
                Interval(IntervalType.WALK, 10),
                Interval(IntervalType.RUN, 10)
            )
        )
        startWorkout()

        val expectedRunsLeft = context.resources.getQuantityString(
            R.plurals.notification_runs_remaining, 2, 2
        )
        assertTrue(
            "Expected the ongoing notification to report both RUN intervals as remaining " +
                "before either has started, got: ${ongoingNotificationText()}",
            waitUntil { ongoingNotificationText()?.contains(expectedRunsLeft) == true }
        )
    }

    @Test
    fun wearable_cue_is_posted_on_interval_change_and_removed_on_stop() {
        vibrateOn(VibrationTarget.SMARTWATCH)
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 1, day = 1,
            intervals = listOf(Interval(IntervalType.WALK, 2), Interval(IntervalType.RUN, 30))
        )
        startWorkout()

        assertTrue(
            "Expected a cue notification when the RUN interval starts",
            waitUntil(timeoutMs = 10_000) { cueNotificationId in activeNotificationIds() }
        )
        val cue = notificationManager.activeNotifications.first { it.id == cueNotificationId }
        assertEquals(
            "A cue must not be ongoing, or notification bridges ignore it",
            0, cue.notification.flags and Notification.FLAG_ONGOING_EVENT
        )
        assertEquals(
            context.getString(R.string.workout_interval_run),
            cue.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertTrue(
            "Notification bridges ignore channels below default importance",
            notificationManager.getNotificationChannel("workout_cues").importance >=
                NotificationManager.IMPORTANCE_DEFAULT
        )

        context.startService(Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))

        // The cue's own timeout is 10s, so being gone after at most 3s means stopping removed it.
        assertTrue(
            "Expected the cue to be removed when the workout stops",
            waitUntil(timeoutMs = 3_000) { cueNotificationId !in activeNotificationIds() }
        )
    }

    @Test
    fun wearable_cues_replace_each_other_and_never_stack() {
        vibrateOn(VibrationTarget.SMARTWATCH)
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 1, day = 1,
            intervals = listOf(
                Interval(IntervalType.WALK, 1),
                Interval(IntervalType.RUN, 2),
                Interval(IntervalType.WALK, 2),
                Interval(IntervalType.RUN, 30)
            )
        )
        startWorkout()

        // Sample the shade while three interval changes (at ~1s, ~3s and ~5s of the workout) post a cue each.
        val seenTags = mutableSetOf<String?>()
        var mostAtOnce = 0
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val cues = notificationManager.activeNotifications.filter { it.id == cueNotificationId }
            mostAtOnce = maxOf(mostAtOnce, cues.size)
            cues.forEach { seenTags.add(it.tag) }
            Thread.sleep(50)
        }

        assertTrue("Expected one cue per interval change, saw ${seenTags.size}", seenTags.size >= 3)
        assertEquals("Each cue must replace the previous one, never pile up", 1, mostAtOnce)
    }

    @Test
    fun no_watch_notification_when_only_the_phone_vibrates() {
        vibrateOn(VibrationTarget.PHONE)
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 1, day = 1,
            intervals = listOf(Interval(IntervalType.WALK, 1), Interval(IntervalType.RUN, 30))
        )
        startWorkout()

        val runLabel = context.getString(R.string.workout_interval_run)
        assertTrue(
            "Expected the workout to reach the RUN interval",
            waitUntil(timeoutMs = 10_000) { ongoingNotificationText()?.contains(runLabel) == true }
        )
        assertFalse(
            "The interval change only vibrates the phone, so no watch notification is expected",
            waitUntil(timeoutMs = 2_000) { cueNotificationId in activeNotificationIds() }
        )
    }

    @Test
    fun watch_notification_is_posted_when_the_workout_completes() {
        vibrateOn(VibrationTarget.SMARTWATCH)
        WorkoutService.testWorkoutDayOverride = fastWorkoutDay
        startWorkout()

        // fastWorkoutDay has a single interval, so the only watch notification is the completion one.
        val completeTitle = context.getString(R.string.workout_complete_title)
        assertTrue(
            "Expected a watch notification when the workout completes, as the phone vibrates then too",
            waitUntil(timeoutMs = 12_000) {
                notificationManager.activeNotifications.any {
                    it.id == cueNotificationId &&
                        it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == completeTitle
                }
            }
        )
    }

    @Test
    fun skip_action_ends_the_final_cooldown_and_completes_the_workout() {
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 1, day = 1,
            intervals = listOf(Interval(IntervalType.RUN, 1), Interval(IntervalType.COOLDOWN, 300))
        )
        startWorkout()

        val cooldownLabel = context.getString(R.string.workout_interval_cooldown)
        assertTrue(
            "Expected the workout to reach its cool-down, got: ${ongoingNotificationText()}",
            waitUntil(timeoutMs = 10_000) { ongoingNotificationText()?.contains(cooldownLabel) == true }
        )

        context.startService(
            Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_SKIP_WARMUP_COOLDOWN)
        )

        // The cool-down is 5 minutes; a completion notification within seconds means it was skipped.
        assertTrue(
            "Expected the workout to complete once the cool-down is skipped",
            waitUntil(timeoutMs = 12_000) { completionNotificationId in activeNotificationIds() }
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
        // stopForeground() and the completion notify() are two separate async binder calls with
        // no atomicity guarantee between them, so a bare one-shot check here can lose a genuine
        // (harmless) race on slower/CI emulators even though the first waitUntil already passed.
        assertTrue(
            "Expected the dismissible completion notification to be posted despite the DB failure",
            waitUntil(timeoutMs = 2_000) { completionNotificationId in activeNotificationIds() }
        )
        assertTrue(
            "Expected cleanup() to have run, clearing the running flag",
            waitUntil { !WorkoutService.isRunning.value }
        )
    }

    @Test
    fun a_failed_setup_stops_the_service_instead_of_hanging() {
        WorkoutService.testSessionRepositoryOverride = FailingStartSessionRepository(db)
        startWorkout()

        // The session row is written during setup, before the engine exists. Without teardown on
        // that failure, the "Starting…" notification stayed up and the service kept running.
        assertTrue(
            "Expected the ongoing notification to be removed after setup failed",
            waitUntil(timeoutMs = 12_000) { ongoingNotificationId !in activeNotificationIds() }
        )
        assertTrue(
            "Expected the service to stop after setup failed",
            waitUntil(timeoutMs = 6_000) { !workoutServiceIsAlive() && !WorkoutService.isRunning.value }
        )
    }

    private class FailingStartSessionRepository(db: AppDatabase) : SessionRepository(db) {
        override suspend fun startSession(programId: String, week: Int, day: Int): Long {
            throw IllegalStateException("Simulated DB failure while setting up the workout")
        }
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
