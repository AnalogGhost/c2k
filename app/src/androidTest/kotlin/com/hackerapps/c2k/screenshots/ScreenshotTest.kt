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
import androidx.compose.ui.test.performScrollTo
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
private const val MS_PER_HOUR = 60 * 60 * 1000L
private const val MS_PER_DAY = 24 * MS_PER_HOUR

// How far into the first run interval the workout shot is taken, so the ring is visibly moving.
private const val WORKOUT_SHOT_DELAY_MS = 40_000L

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
 * (01_home .. 08_progression) via fastlane screengrab, across every locale in Screengrabfile.
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
            prefs.setSmartwatchCues(false)
            // The workout shot should open on a run interval rather than the five-minute warm-up.
            prefs.setSkipWarmup(true)
            prefs.setSkipCooldown(false)
            prefs.clearProgression()
            seedHistory()
        }
    }

    // Week 1 done, leaving Week 2 Day 1 as the natural next "Start" (not "Redo") for the
    // preview and workout screenshots, which the continue button also points at.
    // Sessions sit exactly 14 and 7 days back plus a few hours ago: same weekday, so three
    // consecutive streak weeks regardless of which day of the week the capture runs on (a
    // day-based spread would flip the count at week boundaries), and the latest one keeps
    // 05_history's "This week" card from reading zero.
    // distanceMeters is non-zero (unlike the treadmill mode used live in this test) so
    // 05_history's km/pace and calorie figures have something real to display.
    private suspend fun seedHistory() {
        val now = System.currentTimeMillis()
        fun completedSession(week: Int, day: Int, startedAt: Long) = WorkoutSessionEntity(
            programId = PROGRAM_ID,
            week = week,
            day = day,
            startedAt = startedAt,
            completedAt = startedAt + 25 * 60 * 1000,
            durationSeconds = 25 * 60,
            distanceMeters = 3000f,
            completed = true
        )
        sessionDao().insert(completedSession(week = 1, day = 1, startedAt = now - 14 * MS_PER_DAY))
        sessionDao().insert(completedSession(week = 1, day = 2, startedAt = now - 7 * MS_PER_DAY))
        sessionDao().insert(completedSession(week = 1, day = 3, startedAt = now - 5 * MS_PER_HOUR))
    }

    // Two attempts at the next workout that ended early, which is what makes Home offer
    // "Repeat week 1" for 08_progression. Both run past ProgressionAdvisor.MIN_ATTEMPT_SECONDS
    // and start after the last finished workout.
    private suspend fun seedEarlyStops() {
        val now = System.currentTimeMillis()
        listOf(2L, 1L).forEach { hoursAgo ->
            sessionDao().insert(
                WorkoutSessionEntity(
                    programId = PROGRAM_ID,
                    week = 2,
                    day = 1,
                    startedAt = now - hoursAgo * MS_PER_HOUR,
                    completedAt = now - hoursAgo * MS_PER_HOUR + 6 * 60 * 1000,
                    durationSeconds = 6 * 60,
                    distanceMeters = 0f,
                    completed = false
                )
            )
        }
    }

    @Test
    fun captureScreenshots() {
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }
        capture("01_home")

        composeRule.onNodeWithText(string(R.string.program_c25k)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("day_2_1").assertExists()
        }
        capture("02_program")

        composeRule.onNodeWithTag("day_2_1").performScrollTo().performClick()
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
        Thread.sleep(WORKOUT_SHOT_DELAY_MS)
        capture("04_workout")

        composeRule.onNodeWithTag("workout_stop_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("workout_stop_confirm_button").assertExists()
        }
        composeRule.onNodeWithTag("workout_stop_confirm_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }

        // Stopping the 04_workout run leaves an unfinished Week 2 Day 1 entry that would sit at
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
        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        runBlocking { seedEarlyStops() }
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("progression_suggestion").assertExists()
        }
        capture("08_progression")
    }
}
