package com.hackerapps.c2k.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerapps.c2k.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val ttsEnabled           by vm.ttsEnabled.collectAsStateWithLifecycle()
    val gpsEnabled           by vm.gpsEnabled.collectAsStateWithLifecycle()
    val countdownWarnings    by vm.countdownWarnings.collectAsStateWithLifecycle()
    val midIntervalCues      by vm.midIntervalCues.collectAsStateWithLifecycle()
    val treadmillMode        by vm.treadmillMode.collectAsStateWithLifecycle()
    val keepScreenOn         by vm.keepScreenOn.collectAsStateWithLifecycle()
    val vibrationEnabled     by vm.vibrationEnabled.collectAsStateWithLifecycle()
    val ttsSpeechRate        by vm.ttsSpeechRate.collectAsStateWithLifecycle()
    val ttsVolume            by vm.ttsVolume.collectAsStateWithLifecycle()
    val ttsAvailableOnDevice by vm.ttsAvailableOnDevice.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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

            // TTS unavailable warning
            if (ttsEnabled && ttsAvailableOnDevice == false) {
                Text(
                    stringResource(R.string.tts_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_countdown_warnings),
                checked = countdownWarnings,
                enabled = ttsEnabled,
                onCheckedChange = vm::setCountdownWarnings
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_mid_interval_cues),
                checked = midIntervalCues,
                enabled = ttsEnabled,
                onCheckedChange = vm::setMidIntervalCues
            )
            HorizontalDivider()

            // Voice speed slider (only shown when TTS enabled)
            if (ttsEnabled) {
                ListItem(
                    headlineContent = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.settings_tts_speed))
                                Text(
                                    "%.1f×".format(ttsSpeechRate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Slider(
                                value = ttsSpeechRate,
                                onValueChange = { vm.setTtsSpeechRate(it) },
                                valueRange = 0.7f..1.3f,
                                steps = 5
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.settings_tts_speed_slow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    stringResource(R.string.settings_tts_speed_fast),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.settings_tts_volume))
                                Text(
                                    "%.0f%%".format(ttsVolume * 100),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Slider(
                                value = ttsVolume,
                                onValueChange = { vm.setTtsVolume(it) },
                                valueRange = 0.2f..1.0f,
                                steps = 3
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.settings_tts_volume_quiet),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    stringResource(R.string.settings_tts_volume_loud),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            SettingsToggle(
                label = stringResource(R.string.settings_vibration_enabled),
                checked = vibrationEnabled,
                onCheckedChange = vm::setVibrationEnabled
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_treadmill_mode),
                checked = treadmillMode,
                onCheckedChange = vm::setTreadmillMode
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_gps_enabled),
                checked = gpsEnabled,
                enabled = !treadmillMode,
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    )
}
