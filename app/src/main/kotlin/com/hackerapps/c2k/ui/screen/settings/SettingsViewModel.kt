package com.hackerapps.c2k.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.tts.TtsManager

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    val ttsEnabled = prefs.ttsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val gpsEnabled = prefs.gpsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val countdownWarnings = prefs.countdownWarnings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val keepScreenOn = prefs.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val vibrationEnabled = prefs.vibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val ttsSpeechRate = prefs.ttsSpeechRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val ttsVolume = prefs.ttsVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val midIntervalCues = prefs.midIntervalCues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val ttsAvailableOnDevice = TtsManager.isAvailableOnDevice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsManager.isAvailableOnDevice.value)

    fun setTtsEnabled(v: Boolean)        { viewModelScope.launch { prefs.setTtsEnabled(v) } }
    fun setGpsEnabled(v: Boolean)        { viewModelScope.launch { prefs.setGpsEnabled(v) } }
    fun setCountdownWarnings(v: Boolean) { viewModelScope.launch { prefs.setCountdownWarnings(v) } }
    fun setKeepScreenOn(v: Boolean)      { viewModelScope.launch { prefs.setKeepScreenOn(v) } }
    fun setVibrationEnabled(v: Boolean)  { viewModelScope.launch { prefs.setVibrationEnabled(v) } }
    fun setTtsSpeechRate(v: Float)       { viewModelScope.launch { prefs.setTtsSpeechRate(v) } }
    fun setTtsVolume(v: Float)           { viewModelScope.launch { prefs.setTtsVolume(v) } }
    fun setMidIntervalCues(v: Boolean)   { viewModelScope.launch { prefs.setMidIntervalCues(v) } }

    val treadmillMode = prefs.treadmillMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTreadmillMode(v: Boolean)     { viewModelScope.launch { prefs.setTreadmillMode(v) } }
}
