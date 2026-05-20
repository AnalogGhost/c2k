package com.hackerapps.c2k.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.data.prefs.UserPreferences

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

    fun setTtsEnabled(v: Boolean)        { viewModelScope.launch { prefs.setTtsEnabled(v) } }
    fun setGpsEnabled(v: Boolean)        { viewModelScope.launch { prefs.setGpsEnabled(v) } }
    fun setCountdownWarnings(v: Boolean) { viewModelScope.launch { prefs.setCountdownWarnings(v) } }
    fun setKeepScreenOn(v: Boolean)      { viewModelScope.launch { prefs.setKeepScreenOn(v) } }
}
