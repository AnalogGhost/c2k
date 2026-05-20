package com.hackerapps.c2k.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerapps.c2k.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val ttsEnabled       by vm.ttsEnabled.collectAsStateWithLifecycle()
    val gpsEnabled       by vm.gpsEnabled.collectAsStateWithLifecycle()
    val countdownWarnings by vm.countdownWarnings.collectAsStateWithLifecycle()
    val keepScreenOn     by vm.keepScreenOn.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsToggle(
                label = stringResource(R.string.settings_tts_enabled),
                checked = ttsEnabled,
                onCheckedChange = vm::setTtsEnabled
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_countdown_warnings),
                checked = countdownWarnings,
                onCheckedChange = vm::setCountdownWarnings
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_gps_enabled),
                checked = gpsEnabled,
                onCheckedChange = vm::setGpsEnabled
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                onCheckedChange = vm::setKeepScreenOn
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
