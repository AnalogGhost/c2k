package com.hackerapps.c2k.screenshots

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.prefs.CueStyle
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.prefs.WeightUnit
import com.hackerapps.c2k.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

private const val PROGRAM_ID = "C25K"
private const val MS_PER_DAY = 24 * 60 * 60 * 1000L

// How long to let rendered frames reach the display before a capture.
private const val SETTLE_MS = 750L

private fun ComposeTestRule.waitUntilAssertion(timeoutMillis: Long = 15_000, assertion: () -> Unit) {
    waitUntil(timeoutMillis) {
        try {
            assertion()
            true
        } catch (e: Throwable) {
            // Broader than AssertionError on purpose: this is the only test in the suite using
            // createAndroidComposeRule<MainActivity>() with a real auto-launched Activity (every
            // other screen test calls composeRule.setContent {} explicitly, which blocks until
            // composition happens) — so the very first check here can race MainActivity's own
            // startup and see IllegalStateException("No compose hierarchies found") rather than
            // a plain assertion failure. Retry either the same as "not ready yet".
            false
        }
    }
}

/**
 * Drives the real app through MainActivity to capture the store-listing screenshots
 * (01_home .. 07_settings) via fastlane screengrab, across every locale in Screengrabfile.
 *
 * Not a correctness test — screen-by-screen behavior is already covered elsewhere in this
 * androidTest source set. This just needs the app to look right along one realistic path.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    companion object {
        @ClassRule
        @JvmField
        val localeTestRule = LocaleTestRule()
    }

    // Pre-granted so every RequestXPermission composable short-circuits to an immediate
    // no-dialog grant. The demo workout runs in treadmill mode, which skips the location request,
    // but granting it keeps a system dialog out of the shot regardless. POST_NOTIFICATIONS only
    // exists from Android 13; granting it on an older device fails the whole test, and the
    // older-Android capture is how the fallback colour scheme gets checked.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).toTypedArray()
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun repo() = (app() as C2KApp).sessionRepository
    private fun sessionDao() = (app() as C2KApp).database.sessionDao()

    private fun string(resId: Int, vararg args: Any) = composeRule.activity.getString(resId, *args)

    // The capture reads what is on the display, which can lag the Compose tree the waits above
    // check: 05_history was caught still showing its empty state in about half the locales even
    // though the loaded history was already composed. Let the next frames reach the screen first.
    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        Screengrab.screenshot(name)
    }

    @Before
    fun setUp() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        runBlocking {
            repo().observeAllSessions().first().forEach { repo().deleteSession(it.id) }
            val prefs = UserPreferences(app())
            prefs.setTreadmillMode(true)
            // Pin to defaults — otherwise leftover values from manual on-device testing bleed
            // into the 07_settings store screenshot.
            prefs.setCountdownWarning1(10)
            prefs.setCountdownWarning2(5)
            prefs.setCountdownWarning2Enabled(true)
            prefs.setTtsEnabled(true)
            prefs.setCueStyle(CueStyle.VOICE)
            // Otherwise WorkoutViewModel.checkBatteryOptimization() shows a real AlertDialog on
            // top of the workout screen the first time a workout starts, right where 04_workout
            // gets captured.
            prefs.setBatteryPromptDismissed()
            // Pinned for the same reason as the countdown warnings above — without this, leftover
            // weight/unit values from manual on-device testing bleed into 07_settings, and
            // 05_history's calorie figures would be non-deterministic across runs.
            prefs.setWeightKg(70f)
            prefs.setWeightUnit(WeightUnit.KG)
            seedHistory()
        }
    }

    // A 2-week streak plus recent-workout history, leaving Week 1 Day 3 uncompleted so
    // there's a natural "Start" (not "Redo") flow for the workout preview/active screenshots
    // AND so the continue button coherently points at Week 1 Day 3 (it follows the latest
    // completed day, so completing anything later would make it skip past Day 3 on-screen).
    // Sessions sit exactly 14/7 days back: same weekday, two consecutive streak weeks (kept
    // alive by the current week's grace period) regardless of which day of the week the
    // capture runs on — a day-based spread would flip the count at week boundaries.
    // distanceMeters is non-zero (unlike the treadmill mode used live in this test) so
    // 05_history's km/pace and calorie figures have something real to display.
    private suspend fun seedHistory() {
        val now = System.currentTimeMillis()
        fun completedSession(week: Int, day: Int, daysAgo: Long) = WorkoutSessionEntity(
            programId = PROGRAM_ID,
            week = week,
            day = day,
            startedAt = now - daysAgo * MS_PER_DAY,
            completedAt = now - daysAgo * MS_PER_DAY + 25 * 60 * 1000,
            durationSeconds = 25 * 60,
            distanceMeters = 3000f,
            completed = true
        )
        sessionDao().insert(completedSession(week = 1, day = 1, daysAgo = 14))
        sessionDao().insert(completedSession(week = 1, day = 2, daysAgo = 7))
    }

    @Test
    fun captureScreenshots() {
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }
        capture("01_home")

        composeRule.onNodeWithText(string(R.string.program_c25k)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("day_1_3").assertExists()
        }
        capture("02_program")

        composeRule.onNodeWithTag("day_1_3").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_preview_start)).assertExists()
        }
        capture("03_preview")

        composeRule.onNodeWithText(string(R.string.program_preview_start)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.workout_pause)).assertExists()
        }
        // Let a little real time pass so the ring/elapsed time show something other than 0:00 —
        // WorkoutEngine ticks on a real wall clock here, unlike the virtual clock in
        // WorkoutEngineTest.
        composeRule.waitUntilAssertion(timeoutMillis = 6_000) {
            composeRule.onNodeWithText(string(R.string.workout_elapsed, "0:00")).assertDoesNotExist()
        }
        capture("04_workout")

        composeRule.onNodeWithTag("workout_stop_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("workout_stop_confirm_button").assertExists()
        }
        composeRule.onNodeWithTag("workout_stop_confirm_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }

        // Stopping the 04_workout run leaves an unfinished Week 1 Day 3 entry that would sit at
        // the top of 05_history; drop it so History shows only the seeded workouts.
        runBlocking {
            repo().observeAllSessions().first().filter { !it.completed }.forEach { repo().deleteSession(it.id) }
        }

        composeRule.onNodeWithContentDescription(string(R.string.history_title)).performClick()
        // Waiting for the title alone isn't enough: the stats card and session list load
        // asynchronously from Room after the title renders, so capturing right after the title
        // exists can race ahead and grab a still-empty screen. The pace tile only ever renders
        // once real (seeded) session data has arrived, so wait for that specifically.
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.history_stats_pace)).assertExists()
        }
        capture("05_history")
        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        composeRule.onNodeWithContentDescription(string(R.string.guide_title)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.guide_section_before_start)).assertExists()
        }
        capture("06_guide")
        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        composeRule.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.settings_title)).assertExists()
        }
        // SettingsViewModel's StateFlows seed from a hardcoded default before the real DataStore
        // value propagates — without this wait, the screenshot can race ahead and capture the
        // seed default (treadmillMode=false) instead of what setUp() actually persisted (true).
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("toggle_treadmill_mode").assertIsOn()
        }
        capture("07_settings")
    }
}
