package com.hackerapps.c2k.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.prefs.VibrationStrength
import com.hackerapps.c2k.data.prefs.WeightUnit
import com.hackerapps.c2k.engine.tts.TtsManager
import com.hackerapps.c2k.service.VibrationPatterns
import com.hackerapps.c2k.service.VibrationPlayer
import com.hackerapps.c2k.engine.tts.DiagnosticTts
import com.hackerapps.c2k.engine.tts.TtsDiagnosticResult
import com.hackerapps.c2k.R

sealed interface VoiceTestState {
    data object Idle : VoiceTestState
    data object Initializing : VoiceTestState
    data object Speaking : VoiceTestState
    data object Completed : VoiceTestState
    data class Failed(val result: TtsDiagnosticResult) : VoiceTestState
}

class SettingsViewModel @JvmOverloads constructor(
    app: Application,
    private val diagnosticTtsFactory: (Application, Float, Float) -> DiagnosticTts =
        { application, rate, volume -> TtsManager(application, rate, volume) }
) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    val ttsEnabled = prefs.ttsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val gpsEnabled = prefs.gpsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val countdownWarnings = prefs.countdownWarnings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val countdownWarning1 = prefs.countdownWarning1
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    val countdownWarning2 = prefs.countdownWarning2
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    val keepScreenOn = prefs.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val vibrationEnabled = prefs.vibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val vibrationStrength = prefs.vibrationStrength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VibrationStrength.MEDIUM)

    val ttsSpeechRate = prefs.ttsSpeechRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val ttsVolume = prefs.ttsVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val midIntervalCues = prefs.midIntervalCues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val ttsAvailableOnDevice = TtsManager.isAvailableOnDevice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsManager.isAvailableOnDevice.value)

    private val _voiceTestState = MutableStateFlow<VoiceTestState>(VoiceTestState.Idle)
    val voiceTestState: StateFlow<VoiceTestState> = _voiceTestState.asStateFlow()
    private var diagnosticTts: DiagnosticTts? = null
    private var voiceTestGeneration = 0

    fun testVoice() {
        stopVoiceTest()
        val generation = ++voiceTestGeneration
        _voiceTestState.value = VoiceTestState.Initializing
        val manager = diagnosticTtsFactory(getApplication(), ttsSpeechRate.value, ttsVolume.value)
        diagnosticTts = manager
        manager.diagnose(
            text = getApplication<Application>().getString(R.string.settings_tts_test_phrase),
            onSpeaking = {
                if (generation == voiceTestGeneration) _voiceTestState.value = VoiceTestState.Speaking
            },
            onResult = { result ->
                if (generation != voiceTestGeneration) return@diagnose
                _voiceTestState.value = if (result == TtsDiagnosticResult.Success) {
                    VoiceTestState.Completed
                } else {
                    VoiceTestState.Failed(result)
                }
                diagnosticTts?.shutdown()
                diagnosticTts = null
            }
        )
    }

    fun stopVoiceTest() {
        voiceTestGeneration++
        diagnosticTts?.shutdown()
        diagnosticTts = null
    }

    fun setTtsEnabled(v: Boolean)        { viewModelScope.launch { prefs.setTtsEnabled(v) } }
    fun setGpsEnabled(v: Boolean)        { viewModelScope.launch { prefs.setGpsEnabled(v) } }
    fun setCountdownWarnings(v: Boolean) { viewModelScope.launch { prefs.setCountdownWarnings(v) } }
    fun setCountdownWarning1(v: Int)     { viewModelScope.launch { prefs.setCountdownWarning1(v) } }
    fun setCountdownWarning2(v: Int)     { viewModelScope.launch { prefs.setCountdownWarning2(v) } }
    fun setKeepScreenOn(v: Boolean)      { viewModelScope.launch { prefs.setKeepScreenOn(v) } }
    fun setVibrationEnabled(v: Boolean)  {
        viewModelScope.launch {
            prefs.setVibrationEnabled(v)
            if (v) previewVibration(vibrationStrength.value)
        }
    }
    fun setVibrationStrength(v: VibrationStrength) {
        viewModelScope.launch {
            prefs.setVibrationStrength(v)
            previewVibration(v)
        }
    }
    fun setTtsSpeechRate(v: Float)       { viewModelScope.launch { prefs.setTtsSpeechRate(v) } }
    fun setTtsVolume(v: Float)           { viewModelScope.launch { prefs.setTtsVolume(v) } }
    fun setMidIntervalCues(v: Boolean)   { viewModelScope.launch { prefs.setMidIntervalCues(v) } }

    val treadmillMode = prefs.treadmillMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTreadmillMode(v: Boolean)     { viewModelScope.launch { prefs.setTreadmillMode(v) } }

    val weightKg = prefs.weightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightUnit = prefs.weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    fun setWeightKg(v: Float)            { viewModelScope.launch { prefs.setWeightKg(v) } }
    fun setWeightUnit(v: WeightUnit)      { viewModelScope.launch { prefs.setWeightUnit(v) } }

    private fun previewVibration(strength: VibrationStrength) {
        VibrationPlayer.play(
            getApplication(),
            VibrationPatterns.forInterval(IntervalType.RUN, strength)
        )
    }

    override fun onCleared() {
        stopVoiceTest()
        super.onCleared()
    }
}
