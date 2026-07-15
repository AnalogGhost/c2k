package com.hackerapps.c2k.demo

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PROGRAM_ID = "C25K"

private fun ComposeTestRule.waitUntilAssertion(timeoutMillis: Long = 15_000, assertion: () -> Unit) {
    waitUntil(timeoutMillis) {
        try {
            assertion()
            true
        } catch (e: Throwable) {
            // See ScreenshotTest for why this is broader than AssertionError.
            false
        }
    }
}

/**
 * Not a correctness test. Drives a GPS-tracked workout (FOREGROUND_SERVICE_LOCATION) and a
 * treadmill workout (FOREGROUND_SERVICE_HEALTH), backgrounding the app during each so the
 * persistent notification stays visible, for Play Console's foreground-service-permission
 * declaration demo video. Meant to be run under `record-demo-video.sh`, which captures the
 * screen and feeds simulated GPS movement via the emulator console — running this test alone
 * produces a workout that never shows GPS lock or moving distance, since there's no real GPS
 * signal in the emulator otherwise.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundServiceDemoTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun repo() = (app() as C2KApp).sessionRepository
    private fun string(resId: Int, vararg args: Any) = composeRule.activity.getString(resId, *args)

    @Before
    fun setUp() {
        runBlocking {
            repo().observeAllSessions().first().forEach { repo().deleteSession(it.id) }
            UserPreferences(app()).setBatteryPromptDismissed()
        }
    }

    // Backgrounds the app via the Home button, then relaunches it directly rather than trying to
    // tap through the real recent-apps UI, which is too OS/launcher-dependent to script reliably.
    private fun backgroundThenReturn(backgroundMillis: Long) {
        device.pressHome()
        Thread.sleep(backgroundMillis)
        app().packageManager.getLaunchIntentForPackage(app().packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            app().startActivity(intent)
        }
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.workout_pause)).assertExists()
        }
    }

    private fun stopCurrentWorkout() {
        composeRule.onNodeWithTag("workout_stop_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("workout_stop_confirm_button").assertExists()
        }
        composeRule.onNodeWithTag("workout_stop_confirm_button").performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }
    }

    private fun startDay(dayTag: String) {
        composeRule.onNodeWithText(string(R.string.program_c25k)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag(dayTag).assertExists()
        }
        composeRule.onNodeWithTag(dayTag).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_preview_start)).assertExists()
        }
        composeRule.onNodeWithText(string(R.string.program_preview_start)).performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.workout_pause)).assertExists()
        }
    }

    @Test
    fun recordForegroundServiceDemo() {
        val prefs = UserPreferences(app())

        // --- FOREGROUND_SERVICE_LOCATION: GPS-tracked outdoor workout ---
        runBlocking { prefs.setTreadmillMode(false) }
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.program_c25k)).assertExists()
        }
        startDay("day_1_1")
        // Let the simulated GPS movement fed by record-demo-video.sh acquire a lock and
        // accumulate visible distance on screen before backgrounding. Generous margin here —
        // record-demo-video.sh now builds/installs before recording starts, so this no longer
        // competes with compilation for the screen-recording time budget.
        composeRule.waitUntilAssertion(timeoutMillis = 25_000) {
            composeRule.onNodeWithText(string(R.string.workout_acquiring_gps)).assertDoesNotExist()
        }
        Thread.sleep(18_000)
        backgroundThenReturn(backgroundMillis = 8_000)
        stopCurrentWorkout()

        // --- FOREGROUND_SERVICE_HEALTH: treadmill workout, no GPS ---
        runBlocking { prefs.setTreadmillMode(true) }
        startDay("day_1_2")
        Thread.sleep(10_000)
        backgroundThenReturn(backgroundMillis = 8_000)
        stopCurrentWorkout()
    }
}
