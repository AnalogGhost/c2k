package com.hackerapps.c2k.service

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.data.model.Programs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression coverage for a Play Store crash: on API 34+, startForeground() validates the
// foreground service type against the runtime permissions actually granted (ACTIVITY_RECOGNITION
// for "health", ACCESS_FINE_LOCATION for "location") and throws a SecurityException if neither is
// held. RequestActivityRecognitionPermission (PermissionGate.kt) lets the user deny that prompt
// and proceeds anyway, so ACTION_START must survive being denied both permissions instead of
// crashing the whole process (WorkoutService.kt handleStart -> startForeground).
@RunWith(AndroidJUnit4::class)
class WorkoutServiceForegroundPermissionTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var serviceWasStarted = false

    // GrantPermissionRule only ever grants; it can't guarantee ACTIVITY_RECOGNITION/location are
    // denied if an earlier manual install on this device already granted them. Revoke explicitly
    // so this test's "permission missing" premise holds regardless of device history.
    private fun revoke(permission: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm revoke ${context.packageName} $permission")
            .close()
    }

    @Before
    fun denyHealthAndLocationPermissions() {
        revoke(Manifest.permission.ACTIVITY_RECOGNITION)
        revoke(Manifest.permission.ACCESS_FINE_LOCATION)
        revoke(Manifest.permission.ACCESS_COARSE_LOCATION)
        serviceWasStarted = false
    }

    @After
    fun tearDown() {
        if (serviceWasStarted) {
            context.startService(Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))
        }
        Thread.sleep(1_000)
    }

    private fun waitUntil(timeoutMs: Long = 5_000, pollMs: Long = 100, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(pollMs)
        }
        return condition()
    }

    @Test
    fun starting_a_workout_without_activity_recognition_or_location_does_not_crash() {
        serviceWasStarted = true
        val startIntent = Intent(context, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_START
            putExtra(WorkoutService.EXTRA_PROGRAM_ID, Programs.ID_C25K)
            putExtra(WorkoutService.EXTRA_WEEK, 1)
            putExtra(WorkoutService.EXTRA_DAY, 1)
        }
        context.startForegroundService(startIntent)

        // Neither "health" nor "location" can be granted as a foreground service type here, so
        // handleStart() must hit the SecurityException catch path and abort cleanly — reaching
        // this assertion at all (instead of the instrumentation process dying) is most of what
        // this test is checking.
        assertTrue(
            "Expected handleStart() to abort cleanly (isRunning back to false) instead of " +
                "crashing or leaving the service stuck marked as running",
            waitUntil { !WorkoutService.isRunning.value }
        )
    }
}
