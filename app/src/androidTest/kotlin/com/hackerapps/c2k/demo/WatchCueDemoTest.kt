package com.hackerapps.c2k.demo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.service.WorkoutService
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test. Run on a Wear OS emulator, it posts the real WALK cue notification
 * there and holds it on screen, for the store's smartwatch screenshot. Posting on the watch
 * itself stands in for the phone-to-watch bridge, which drops notifications on emulators.
 * `tools/store-screenshots/capture-watch.sh` sets the app locale, runs this and takes the capture.
 */
@RunWith(AndroidJUnit4::class)
class WatchCueDemoTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun showWalkCue() {
        // It changes settings and records a workout, so it stays off the phones CI tests on.
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))
        runBlocking {
            UserPreferences(context).apply {
                setTtsEnabled(false)
                setVibrationEnabled(true)
                setSmartwatchCues(true)
                setSkipWarmup(false)
                setSkipCooldown(false)
            }
        }
        // The cue fires when the interval changes, so a short run leads into the walk. The walk is
        // week 2's 2:00, matching the phone screenshot it appears beside.
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 2, day = 1,
            intervals = listOf(Interval(IntervalType.RUN, 2), Interval(IntervalType.WALK, 120))
        )
        context.startForegroundService(
            Intent(context, WorkoutService::class.java).apply {
                action = WorkoutService.ACTION_START
                putExtra(WorkoutService.EXTRA_PROGRAM_ID, Programs.ID_C25K)
                putExtra(WorkoutService.EXTRA_WEEK, 2)
                putExtra(WorkoutService.EXTRA_DAY, 1)
            }
        )
        Thread.sleep(HOLD_MS)
        context.startService(Intent(context, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))
        Thread.sleep(1_000)
        WorkoutService.testWorkoutDayOverride = null
    }

    private companion object {
        // Long enough for the run to end and the script to capture the alert.
        const val HOLD_MS = 25_000L
    }
}
