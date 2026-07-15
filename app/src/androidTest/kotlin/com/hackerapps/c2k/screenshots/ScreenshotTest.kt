package com.hackerapps.c2k.screenshots

import android.Manifest
import android.app.Application
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
        } catch (e: AssertionError) {
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

    // Notifications so RequestNotificationPermission resolves without a system dialog; location
    // isn't needed since the demo workout runs in treadmill mode (skips RequestLocationPermission
    // entirely — see WorkoutScreen's `permissionResolved` initial value).
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
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
            UserPreferences(app()).setTreadmillMode(true)
            seedHistory()
        }
    }

    // A 3-day streak plus recent-workout history, while leaving Week 1 Day 3 uncompleted so
    // there's a natural "Start" (not "Redo") flow for the workout preview/active screenshots.
    private suspend fun seedHistory() {
        val now = System.currentTimeMillis()
        fun completedSession(week: Int, day: Int, daysAgo: Long) = WorkoutSessionEntity(
            programId = PROGRAM_ID,
            week = week,
            day = day,
            startedAt = now - daysAgo * MS_PER_DAY,
            completedAt = now - daysAgo * MS_PER_DAY + 25 * 60 * 1000,
            durationSeconds = 25 * 60,
            distanceMeters = 0f,
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
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.history_title)).assertExists()
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
        Screengrab.screenshot("07_settings")
    }
}
