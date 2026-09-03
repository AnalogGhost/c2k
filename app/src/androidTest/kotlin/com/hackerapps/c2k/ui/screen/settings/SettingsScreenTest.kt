package com.hackerapps.c2k.ui.screen.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.prefs.VibrationStrength
import com.hackerapps.c2k.data.prefs.WeightUnit
import com.hackerapps.c2k.engine.tts.DiagnosticTts
import com.hackerapps.c2k.engine.tts.TtsDiagnosticResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Clicking a toggle round-trips through the ViewModel and DataStore (real disk I/O) before the
// resulting recomposition lands, so assertions right after performClick() can race ahead of it.
// Poll instead of asserting once immediately.
private fun ComposeTestRule.waitUntilAssertion(timeoutMillis: Long = 10_000, assertion: () -> Unit) {
    waitUntil(timeoutMillis) {
        try {
            assertion()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    private class FakeDiagnosticTts : DiagnosticTts {
        var shutdownCount = 0
        var diagnosedText: String? = null
        var speakingCallback: (() -> Unit)? = null
        var resultCallback: ((TtsDiagnosticResult) -> Unit)? = null

        override fun diagnose(
            text: String,
            onSpeaking: () -> Unit,
            onResult: (TtsDiagnosticResult) -> Unit
        ) {
            diagnosedText = text
            speakingCallback = onSpeaking
            resultCallback = onResult
        }

        override fun shutdown() {
            shutdownCount++
        }
    }

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
            prefs.setCountdownWarning1(10)
            prefs.setCountdownWarning2(5)
            prefs.setKeepScreenOn(true)
            prefs.setVibrationEnabled(false)
            prefs.setVibrationStrength(VibrationStrength.MEDIUM)
            prefs.setTtsSpeechRate(1.0f)
            prefs.setTtsVolume(1.0f)
            prefs.setMidIntervalCues(true)
            prefs.setTreadmillMode(false)
            prefs.setWeightKg(70f)
            prefs.setWeightUnit(WeightUnit.KG)
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
        composeRule.onNodeWithText(string(R.string.settings_tts_enabled)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_gps_enabled)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_countdown_warnings)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_vibration_enabled)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_treadmill_mode)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_keep_screen_on)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_weight)).assertExists()
    }

    @Test
    fun clicking_a_toggle_switches_its_state() {
        setContent()
        composeRule.onNodeWithTag("toggle_gps_enabled").assertIsOn()

        composeRule.onNodeWithTag("toggle_gps_enabled").performScrollTo().performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("toggle_gps_enabled").assertIsOff()
        }
    }

    @Test
    fun vibration_strength_is_shown_when_enabled_and_selection_is_persisted() {
        setContent()
        composeRule.onNodeWithText(string(R.string.settings_vibration_strength)).assertDoesNotExist()

        composeRule.onNodeWithTag("toggle_vibration_enabled").performScrollTo().performClick()
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.settings_vibration_strength)).assertExists()
        }
        composeRule.onNodeWithTag("button_vibration_strength").performScrollTo().performClick()
        composeRule.onNodeWithTag("vibration_strength_strong").performClick()

        composeRule.waitUntil {
            runBlocking {
                val app = ApplicationProvider.getApplicationContext<Application>()
                UserPreferences(app).vibrationStrength.first() == VibrationStrength.STRONG
            }
        }
    }

    @Test
    fun disabling_tts_hides_speed_and_volume_sliders() {
        setContent()
        composeRule.onNodeWithText(string(R.string.settings_tts_speed)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_volume)).assertExists()
        composeRule.onNodeWithTag("button_test_voice").assertExists()

        composeRule.onNodeWithTag("toggle_tts_enabled").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.settings_tts_speed)).assertDoesNotExist()
        }
        composeRule.onNodeWithText(string(R.string.settings_tts_volume)).assertDoesNotExist()
        composeRule.onNodeWithTag("button_test_voice").assertDoesNotExist()
    }

    @Test
    fun voice_test_is_disabled_while_initializing_or_speaking() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                VoiceTestControl(VoiceTestState.Initializing, {}, {})
                VoiceTestControl(VoiceTestState.Speaking, {}, {})
            }
        }
        composeRule.onAllNodesWithTag("button_test_voice")[0].assertIsNotEnabled()
        composeRule.onAllNodesWithTag("button_test_voice")[1].assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_tts_test_initializing)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_test_speaking)).assertExists()
        composeRule.onAllNodesWithTag("text_voice_test_status")[0].assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
    }

    @Test
    fun voice_test_displays_success_and_every_failure_with_settings_actions() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                VoiceTestControl(VoiceTestState.Completed, {}, {})
                VoiceTestControl(VoiceTestState.Failed(TtsDiagnosticResult.NoEngineInstalled), {}, {})
                VoiceTestControl(VoiceTestState.Failed(TtsDiagnosticResult.VoiceUnavailable), {}, {})
                VoiceTestControl(VoiceTestState.Failed(TtsDiagnosticResult.SynthesisOrServiceFailure), {}, {})
                VoiceTestControl(VoiceTestState.Failed(TtsDiagnosticResult.AudioOutputFailure), {}, {})
            }
        }
        composeRule.onNodeWithText(string(R.string.settings_tts_test_completed)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_test_no_engine)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_test_voice_unavailable)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_test_synthesis_failure)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_tts_test_output_failure)).assertExists()
        composeRule.onAllNodesWithTag("button_open_tts_settings").assertCountEquals(4)
    }

    @Test
    fun repeated_voice_tests_replace_and_clean_up_temporary_tts() {
        val instances = mutableListOf<FakeDiagnosticTts>()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SettingsViewModel(app) { _, _, _ ->
            FakeDiagnosticTts().also(instances::add)
        }

        vm.testVoice()
        vm.testVoice()

        assertTrue(instances[0].shutdownCount == 1)
        instances[1].speakingCallback?.invoke()
        assertTrue(vm.voiceTestState.value == VoiceTestState.Speaking)
        instances[0].resultCallback?.invoke(TtsDiagnosticResult.AudioOutputFailure)
        assertTrue(vm.voiceTestState.value == VoiceTestState.Speaking)
        assertTrue(instances[1].shutdownCount == 0)
        instances[1].resultCallback?.invoke(TtsDiagnosticResult.Success)
        assertTrue(instances[1].shutdownCount == 1)
        assertTrue(vm.voiceTestState.value == VoiceTestState.Completed)
    }

    @Test
    fun leaving_settings_shuts_down_temporary_tts() {
        val fake = FakeDiagnosticTts()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SettingsViewModel(app) { _, _, _ -> fake }
        lateinit var showSettings: androidx.compose.runtime.MutableState<Boolean>
        composeRule.setContent {
            showSettings = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(true)
            }
            if (showSettings.value) SettingsScreen(onBack = {}, vm = vm)
        }
        composeRule.runOnIdle { vm.testVoice() }
        composeRule.runOnIdle { showSettings.value = false }
        composeRule.waitForIdle()

        assertTrue(fake.shutdownCount == 1)
        fake.resultCallback?.invoke(TtsDiagnosticResult.AudioOutputFailure)
        assertTrue(vm.voiceTestState.value == VoiceTestState.Initializing)
    }

    @Test
    fun voice_test_uses_selected_rate_volume_and_localized_phrase() {
        var capturedRate: Float? = null
        var capturedVolume: Float? = null
        val fake = FakeDiagnosticTts()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SettingsViewModel(app) { _, rate, volume ->
            capturedRate = rate
            capturedVolume = volume
            fake
        }
        composeRule.setContent { SettingsScreen(onBack = {}, vm = vm) }

        composeRule.onNodeWithTag("slider_tts_speed")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }
        composeRule.onNodeWithTag("slider_tts_volume")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        composeRule.waitUntil {
            vm.ttsSpeechRate.value == 0.7f && vm.ttsVolume.value == 0.2f
        }
        composeRule.onNodeWithTag("button_test_voice").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(0.7f, capturedRate)
            assertEquals(0.2f, capturedVolume)
            assertEquals(string(R.string.settings_tts_test_phrase), fake.diagnosedText)
        }
    }

    @Test
    fun tts_settings_action_falls_back_when_dedicated_destination_is_unavailable() {
        assertEquals(TTS_SETTINGS_ACTION, preferredTtsSettingsAction(true))
        assertEquals(android.provider.Settings.ACTION_SETTINGS, preferredTtsSettingsAction(false))
    }

    @Test
    fun countdown_warning_sliders_shown_with_default_values() {
        setContent()
        composeRule.onNodeWithText(string(R.string.settings_countdown_warning_1)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_countdown_warning_2)).assertExists()
        composeRule.onNodeWithText("10 s").assertExists()
        composeRule.onNodeWithText("5 s").assertExists()
    }

    @Test
    fun disabling_countdown_warnings_hides_the_sliders() {
        setContent()
        composeRule.onNodeWithTag("slider_countdown_warning_1").assertExists()

        composeRule.onNodeWithTag("toggle_countdown_warnings").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("slider_countdown_warning_1").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("slider_countdown_warning_2").assertDoesNotExist()
    }

    @Test
    fun disabling_tts_also_hides_countdown_warning_sliders() {
        setContent()
        composeRule.onNodeWithTag("slider_countdown_warning_1").assertExists()

        composeRule.onNodeWithTag("toggle_tts_enabled").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("slider_countdown_warning_1").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("slider_countdown_warning_2").assertDoesNotExist()
    }

    @Test
    fun dragging_a_countdown_warning_slider_updates_its_displayed_value() {
        setContent()
        composeRule.onNodeWithText("10 s").assertExists()

        composeRule.onNodeWithTag("slider_countdown_warning_1")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText("20 s").assertExists()
        }
    }

    @Test
    fun disabling_tts_also_disables_dependent_toggles() {
        setContent()
        composeRule.onNodeWithTag("toggle_countdown_warnings").assertIsEnabled()
        composeRule.onNodeWithTag("toggle_mid_interval_cues").assertIsEnabled()

        composeRule.onNodeWithTag("toggle_tts_enabled").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("toggle_countdown_warnings").assertIsNotEnabled()
        }
        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("toggle_mid_interval_cues").assertIsNotEnabled()
        }
    }

    @Test
    fun enabling_treadmill_mode_disables_gps_toggle() {
        setContent()
        composeRule.onNodeWithTag("toggle_gps_enabled").assertIsEnabled()

        composeRule.onNodeWithTag("toggle_treadmill_mode").performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithTag("toggle_gps_enabled").assertIsNotEnabled()
        }
    }

    @Test
    fun weight_field_shown_with_current_value() {
        setContent()
        composeRule.onNodeWithTag("field_weight").assertExists()
        composeRule.onNodeWithText("70").assertExists()
    }

    @Test
    fun typing_a_weight_updates_the_field() {
        setContent()
        composeRule.onNodeWithText("70").assertExists()

        composeRule.onNodeWithTag("field_weight").performScrollTo().performTextReplacement("65")

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText("65").assertExists()
        }
    }

    @Test
    fun switching_weight_unit_converts_displayed_value() {
        setContent()
        composeRule.onNodeWithTag("button_weight_unit").performScrollTo().performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText(string(R.string.weight_unit_lb)).assertExists()
        }
        composeRule.onNodeWithText(string(R.string.weight_unit_lb)).performClick()

        composeRule.waitUntilAssertion {
            composeRule.onNodeWithText("154.3").assertExists()
        }
    }

    @Test
    fun back_button_triggers_callback() {
        var backClicked = false
        setContent(onBack = { backClicked = true })

        composeRule.onNodeWithContentDescription(string(R.string.nav_back)).performClick()

        assertTrue(backClicked)
    }
}
