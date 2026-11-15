package com.hackerapps.c2k.service

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.FileInputStream

/**
 * The wake lock that keeps the CPU running through a workout with the screen off. It has a
 * timeout so a stuck service can't drain the battery, so it must be renewed while the workout
 * runs: a fixed 90 minutes used to run out during a paused 70-minute 10K workout, and with GPS
 * off the phone could then sleep through the rest of it. Timeouts here are shortened to seconds.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutServiceWakeLockTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var serviceWasStarted = false
    private val defaultTimeout = WorkoutService.wakeLockTimeoutMs

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        WorkoutService.testSessionRepositoryOverride = SessionRepository(db)
        WorkoutService.testWorkoutDayOverride =
            WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 600)))
    }

    @After
    fun tearDown() {
        if (serviceWasStarted) {
            sendAction(WorkoutService.ACTION_STOP)
            waitUntil { !WorkoutService.isRunning.value }
        }
        waitUntil { !workoutServiceIsAlive() }
        Thread.sleep(500)
        WorkoutService.wakeLockTimeoutMs = defaultTimeout
        WorkoutService.testWorkoutDayOverride = null
        WorkoutService.testSessionRepositoryOverride = null
        db.close()
    }

    @Test
    fun a_running_workout_keeps_renewing_its_wake_lock() {
        // Shorter than the time the test runs for, longer than the 15 s between checkpoints.
        WorkoutService.wakeLockTimeoutMs = 20_000
        startWorkout()
        assertTrue("Wake lock should be taken at the start", waitUntil { wakeLockHeld() })

        Thread.sleep(25_000)
        assertTrue("Wake lock should still be held after its first timeout", wakeLockHeld())
    }

    @Test
    fun resuming_after_a_long_pause_takes_the_wake_lock_again() {
        WorkoutService.wakeLockTimeoutMs = 3_000
        startWorkout()
        assertTrue(waitUntil { WorkoutService.isRunning.value && wakeLockHeld() })
        Thread.sleep(1_000)
        sendAction(WorkoutService.ACTION_PAUSE)

        // Nothing needs the CPU while paused, so letting the lock run out then is fine...
        assertTrue("Expected the lock to time out during the pause", waitUntil(10_000) { !wakeLockHeld() })
        // ...as long as resuming takes it again.
        sendAction(WorkoutService.ACTION_RESUME)
        assertTrue("Resume should take the wake lock again", waitUntil { wakeLockHeld() })
    }

    @Test
    fun stopping_releases_the_wake_lock() {
        startWorkout()
        assertTrue(waitUntil { WorkoutService.isRunning.value && wakeLockHeld() })
        sendAction(WorkoutService.ACTION_STOP)
        assertTrue(waitUntil { !WorkoutService.isRunning.value })
        assertTrue("Wake lock should be released", waitUntil { !wakeLockHeld() })
        Thread.sleep(1_000)
        assertFalse("Wake lock must not be taken again after the stop", wakeLockHeld())
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Whether the workout's wake lock is held right now, from the platform's own list. */
    private fun wakeLockHeld(): Boolean {
        val dump = shell("dumpsys power")
        // Only the current list: newer Android also keeps a log of past acquires and releases.
        val current = dump.lineSequence()
            .dropWhile { !it.trimStart().startsWith("Wake Locks: size=") }
            .drop(1)
            .takeWhile { it.isNotBlank() }
        return current.any { "'C2K::WorkoutLock'" in it }
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(fd.fileDescriptor).use { it.readBytes().decodeToString() }.also { fd.close() }
    }

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
