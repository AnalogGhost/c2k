package com.hackerapps.c2k.ui.screen.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.prefs.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Resets every setting to the same defaults SettingsViewModel falls back to, so tests are
    // isolated regardless of execution order (DataStore persists across tests within a run).
    @Before
    fun resetPreferences() {
        // Block body, not `= runBlocking { ... }`: DataStore.edit() returns Preferences (not
        // Unit), so an expression-bodied function here infers a non-void return type, which
        // JUnit rejects for @Before with "should be void".
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val prefs = UserPreferences(app)
            prefs.setTtsEnabled(true)
            prefs.setGpsEnabled(true)
            prefs.setCountdownWarnings(true)
            prefs.setKeepScreenOn(true)
            prefs.setVibrationEnabled(false)
            prefs.setTtsSpeechRate(1.0f)
            prefs.setTtsVolume(1.0f)
            prefs.setMidIntervalCues(true)
            prefs.setTreadmillMode(false)
        }
    }

    private fun setContent(onBack: () -> Unit = {}) {
        composeRule.setContent {
            val app = ApplicationProvider.getApplicationContext<Application>()
            SettingsScreen(onBack = onBack, vm = SettingsViewModel(app))
        }
    }

    private fun string(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun all_toggle_labels_are_displayed() {
        setContent()
        composeRule.onNodeWithText(string(R.string.settings_tts_enabled)).assertIsOn()
        composeRule.onNodeWithText(string(R.string.settings_gps_enabled)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_countdown_warnings)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_vibration_enabled)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_treadmill_mode)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_keep_screen_on)).assertExists()
    }

    @Test
    fun clicking_a_toggle_switches_its_state() {
        setContent()
        composeRule.onNodeWithTag("toggle_gps_enabled").assertIsOn()
        composeRule.onNodeWithTag("toggle_gps_enabled").performClick()
        composeRule.onNodeWithTag("toggle_gps_enabled").assertIsOff()
    }

    @Test
    fun disabling_tts_hides_speed_and_volume_sliders() {
        setContent()
        composeRule.onNodeWithText(string(R.string.settings_tts_speed)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_volume)).assertExists()

        composeRule.onNodeWithTag("toggle_tts_enabled").performClick()

        composeRule.onNodeWithText(string(R.string.settings_tts_speed)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.settings_tts_volume)).assertDoesNotExist()
    }

    @Test
    fun disabling_tts_also_disables_dependent_toggles() {
        setContent()
        composeRule.onNodeWithTag("toggle_countdown_warnings").assertIsEnabled()
        composeRule.onNodeWithTag("toggle_mid_interval_cues").assertIsEnabled()

        composeRule.onNodeWithTag("toggle_tts_enabled").performClick()

        composeRule.onNodeWithTag("toggle_countdown_warnings").assertIsNotEnabled()
        composeRule.onNodeWithTag("toggle_mid_interval_cues").assertIsNotEnabled()
    }

    @Test
    fun enabling_treadmill_mode_disables_gps_toggle() {
        setContent()
        composeRule.onNodeWithTag("toggle_gps_enabled").assertIsEnabled()

        composeRule.onNodeWithTag("toggle_treadmill_mode").performClick()

        composeRule.onNodeWithTag("toggle_gps_enabled").assertIsNotEnabled()
    }

    @Test
    fun back_button_triggers_callback() {
        var backClicked = false
        setContent(onBack = { backClicked = true })

        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        assertTrue(backClicked)
    }
}
