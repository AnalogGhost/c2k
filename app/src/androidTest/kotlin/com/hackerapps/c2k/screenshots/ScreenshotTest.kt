package com.hackerapps.c2k.screenshots

import android.Manifest
import android.app.Application
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

    // All three pre-granted so every RequestXPermission composable short-circuits to an
    // immediate no-dialog grant. Location is included even though the demo workout runs in
    // treadmill mode (which is supposed to skip RequestLocationPermission entirely) because
    // WorkoutScreen's `permissionResolved` initial value is keyed off a DataStore-backed
    // StateFlow set in @Before — that write can still be propagating when WorkoutScreen first
    // composes, especially since automated clicks happen far faster than a human would, so it
    // can transiently read treadmillMode=false and fire a real system dialog otherwise.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun repo() = (app() as C2KApp).sessionRepository
    private fun sessionDao() = (app() as C2KApp).database.sessionDao()

    private fun string(resId: Int, vararg args: Any) = composeRule.activity.getString(resId, *args)

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

    // A 3-day streak plus recent-workout history, while leaving Week 1 Day 3 uncompleted so
    // there's a natural "Start" (not "Redo") flow for the workout preview/active screenshots.
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
        sessionDao().insert(completedSession(week = 1, day = 1, daysAgo = 2))
        sessionDao().insert(completedSession(week = 1, day = 2, daysAgo = 1))
        sessionDao().insert(completedSession(week = 2, day = 1, daysAgo = 0))
    }

    @Test
    fun captureScreenshots() {
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }
        Screengrab.screenshot("01_home")

        composeRule.onNodeWithText(string(R.string.program_c25k)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("day_1_3").assertExists()
        }
        Screengrab.screenshot("02_program")

        composeRule.onNodeWithTag("day_1_3").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_preview_start)).assertExists()
        }
        Screengrab.screenshot("03_preview")

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
        Screengrab.screenshot("04_workout")

        composeRule.onNodeWithTag("workout_stop_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("workout_stop_confirm_button").assertExists()
        }
        composeRule.onNodeWithTag("workout_stop_confirm_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }

        composeRule.onNodeWithContentDescription(string(R.string.history_title)).performClick()
        // Waiting for the title alone isn't enough: the stats card and session list load
        // asynchronously from Room after the title renders, so capturing right after the title
        // exists can race ahead and grab a still-empty screen. The pace tile only ever renders
        // once real (seeded) session data has arrived, so wait for that specifically.
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.history_stats_pace)).assertExists()
        }
        Screengrab.screenshot("05_history")
        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        composeRule.onNodeWithContentDescription(string(R.string.guide_title)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.guide_section_before_start)).assertExists()
        }
        Screengrab.screenshot("06_guide")
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
        Screengrab.screenshot("07_settings")
    }
}
