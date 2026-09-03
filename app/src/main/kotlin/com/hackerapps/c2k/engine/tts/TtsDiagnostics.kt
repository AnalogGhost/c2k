package com.hackerapps.c2k.engine.tts

import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale

sealed interface TtsDiagnosticResult {
    data object NoEngineInstalled : TtsDiagnosticResult
    data object VoiceUnavailable : TtsDiagnosticResult
    data object SynthesisOrServiceFailure : TtsDiagnosticResult
    data object AudioOutputFailure : TtsDiagnosticResult
    data object Success : TtsDiagnosticResult
}

interface DiagnosticTts {
    fun diagnose(
        text: String,
        onSpeaking: () -> Unit,
        onResult: (TtsDiagnosticResult) -> Unit
    )

    fun shutdown()
}

internal object TtsAudioPolicy {
    const val USAGE = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
    const val CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_SPEECH
    const val FOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
}

internal fun isTtsLanguageAvailable(result: Int): Boolean =
    result >= TextToSpeech.LANG_AVAILABLE

internal fun selectTtsLanguage(
    deviceLocale: Locale,
    setLanguage: (Locale) -> Int
): Boolean {
    if (isTtsLanguageAvailable(setLanguage(deviceLocale))) return true
    return isTtsLanguageAvailable(setLanguage(Locale.ENGLISH))
}

internal fun diagnosticResultForError(errorCode: Int): TtsDiagnosticResult = when (errorCode) {
    TextToSpeech.ERROR_OUTPUT -> TtsDiagnosticResult.AudioOutputFailure
    TextToSpeech.ERROR_NOT_INSTALLED_YET -> TtsDiagnosticResult.VoiceUnavailable
    else -> TtsDiagnosticResult.SynthesisOrServiceFailure
}

internal fun diagnosticResultForInitializationFailure(
    hasInstalledEngine: Boolean
): TtsDiagnosticResult = if (hasInstalledEngine) {
    TtsDiagnosticResult.SynthesisOrServiceFailure
} else {
    TtsDiagnosticResult.NoEngineInstalled
}
