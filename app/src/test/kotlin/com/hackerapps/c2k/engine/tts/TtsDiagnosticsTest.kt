package com.hackerapps.c2k.engine.tts

import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsDiagnosticsTest {

    @Test
    fun language_results_accept_available_variants_and_reject_missing_voice_data() {
        assertTrue(isTtsLanguageAvailable(TextToSpeech.LANG_AVAILABLE))
        assertTrue(isTtsLanguageAvailable(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        assertTrue(isTtsLanguageAvailable(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertFalse(isTtsLanguageAvailable(TextToSpeech.LANG_MISSING_DATA))
        assertFalse(isTtsLanguageAvailable(TextToSpeech.LANG_NOT_SUPPORTED))
    }

    @Test
    fun language_selection_uses_device_locale_when_available() {
        val requested = mutableListOf<Locale>()
        val available = selectTtsLanguage(Locale.GERMANY) {
            requested += it
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        }

        assertTrue(available)
        assertEquals(listOf(Locale.GERMANY), requested)
    }

    @Test
    fun language_selection_falls_back_to_english_and_reports_when_both_fail() {
        val requested = mutableListOf<Locale>()
        val available = selectTtsLanguage(Locale.GERMANY) {
            requested += it
            if (it == Locale.ENGLISH) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_MISSING_DATA
        }
        assertTrue(available)
        assertEquals(listOf(Locale.GERMANY, Locale.ENGLISH), requested)

        assertFalse(selectTtsLanguage(Locale.GERMANY) { TextToSpeech.LANG_NOT_SUPPORTED })
    }

    @Test
    fun missing_voice_data_error_maps_to_voice_unavailable() {
        assertEquals(
            TtsDiagnosticResult.VoiceUnavailable,
            diagnosticResultForError(TextToSpeech.ERROR_NOT_INSTALLED_YET)
        )
    }

    @Test
    fun output_error_maps_to_audio_output_failure() {
        assertEquals(
            TtsDiagnosticResult.AudioOutputFailure,
            diagnosticResultForError(TextToSpeech.ERROR_OUTPUT)
        )
    }

    @Test
    fun synthesis_and_service_errors_map_to_service_failure() {
        assertEquals(
            TtsDiagnosticResult.SynthesisOrServiceFailure,
            diagnosticResultForError(TextToSpeech.ERROR_SYNTHESIS)
        )
        assertEquals(
            TtsDiagnosticResult.SynthesisOrServiceFailure,
            diagnosticResultForError(TextToSpeech.ERROR_SERVICE)
        )
    }

    @Test
    fun initialization_failure_only_reports_no_engine_when_none_is_installed() {
        assertEquals(
            TtsDiagnosticResult.NoEngineInstalled,
            diagnosticResultForInitializationFailure(hasInstalledEngine = false)
        )
        assertEquals(
            TtsDiagnosticResult.SynthesisOrServiceFailure,
            diagnosticResultForInitializationFailure(hasInstalledEngine = true)
        )
    }

    @Test
    fun audio_policy_uses_navigation_speech_and_transient_ducking() {
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, TtsAudioPolicy.USAGE)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, TtsAudioPolicy.CONTENT_TYPE)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, TtsAudioPolicy.FOCUS_GAIN)
    }
}
