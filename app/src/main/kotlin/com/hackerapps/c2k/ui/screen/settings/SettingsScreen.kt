package com.hackerapps.c2k.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.prefs.VibrationStrength
import com.hackerapps.c2k.data.prefs.WeightUnit
import com.hackerapps.c2k.engine.tts.TtsDiagnosticResult
import java.text.DecimalFormatSymbols
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
    val countdownWarning1    by vm.countdownWarning1.collectAsStateWithLifecycle()
    val countdownWarning2    by vm.countdownWarning2.collectAsStateWithLifecycle()
    val midIntervalCues      by vm.midIntervalCues.collectAsStateWithLifecycle()
    val treadmillMode        by vm.treadmillMode.collectAsStateWithLifecycle()
    val keepScreenOn         by vm.keepScreenOn.collectAsStateWithLifecycle()
    val vibrationEnabled     by vm.vibrationEnabled.collectAsStateWithLifecycle()
    val vibrationStrength    by vm.vibrationStrength.collectAsStateWithLifecycle()
    val ttsSpeechRate        by vm.ttsSpeechRate.collectAsStateWithLifecycle()
    val ttsVolume            by vm.ttsVolume.collectAsStateWithLifecycle()
    val ttsAvailableOnDevice by vm.ttsAvailableOnDevice.collectAsStateWithLifecycle()
    val voiceTestState       by vm.voiceTestState.collectAsStateWithLifecycle()
    val weightKg             by vm.weightKg.collectAsStateWithLifecycle()
    val weightUnit           by vm.weightUnit.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(vm) {
        onDispose { vm.stopVoiceTest() }
    }

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
                // imePadding() must come before verticalScroll(), not after: applied first, it
                // shrinks this Column's own measured viewport by the keyboard height, so Compose's
                // automatic scroll-into-view-on-focus (used by the weight field below) correctly
                // sees the focused field as out of view and scrolls to it. Applied after scroll
                // instead, it only pads the scrollable content itself — the viewport never shrinks,
                // so nothing looks "out of view" and the keyboard just sits on top, covering fields.
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsToggle(
                label = stringResource(R.string.settings_tts_enabled),
                checked = ttsEnabled,
                testTag = "toggle_tts_enabled",
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
                testTag = "toggle_countdown_warnings",
                onCheckedChange = vm::setCountdownWarnings
            )
            if (ttsEnabled && countdownWarnings) {
                SecondsSlider(
                    label = stringResource(R.string.settings_countdown_warning_1),
                    seconds = countdownWarning1,
                    range = 3f..30f,
                    testTag = "slider_countdown_warning_1",
                    onValueChange = vm::setCountdownWarning1
                )
                SecondsSlider(
                    label = stringResource(R.string.settings_countdown_warning_2),
                    seconds = countdownWarning2,
                    range = 3f..30f,
                    testTag = "slider_countdown_warning_2",
                    onValueChange = vm::setCountdownWarning2
                )
            }
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_mid_interval_cues),
                checked = midIntervalCues,
                enabled = ttsEnabled,
                testTag = "toggle_mid_interval_cues",
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Slider(
                                value = ttsSpeechRate,
                                onValueChange = { vm.setTtsSpeechRate(it) },
                                valueRange = 0.7f..1.3f,
                                steps = 5,
                                modifier = Modifier.testTag("slider_tts_speed")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.settings_tts_speed_slow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    stringResource(R.string.settings_tts_speed_fast),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Slider(
                                value = ttsVolume,
                                onValueChange = { vm.setTtsVolume(it) },
                                valueRange = 0.2f..1.0f,
                                steps = 3,
                                modifier = Modifier.testTag("slider_tts_volume")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.settings_tts_volume_quiet),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    stringResource(R.string.settings_tts_volume_loud),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                )
                VoiceTestControl(
                    state = voiceTestState,
                    onTestVoice = vm::testVoice,
                    onOpenTtsSettings = { openTtsSettings(context) }
                )
                HorizontalDivider()
            }

            SettingsToggle(
                label = stringResource(R.string.settings_vibration_enabled),
                checked = vibrationEnabled,
                testTag = "toggle_vibration_enabled",
                onCheckedChange = vm::setVibrationEnabled
            )
            if (vibrationEnabled) {
                VibrationStrengthSetting(
                    strength = vibrationStrength,
                    onStrengthChange = vm::setVibrationStrength
                )
            }
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_treadmill_mode),
                checked = treadmillMode,
                testTag = "toggle_treadmill_mode",
                onCheckedChange = vm::setTreadmillMode
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_gps_enabled),
                checked = gpsEnabled,
                enabled = !treadmillMode,
                testTag = "toggle_gps_enabled",
                onCheckedChange = vm::setGpsEnabled
            )
            HorizontalDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                testTag = "toggle_keep_screen_on",
                onCheckedChange = vm::setKeepScreenOn
            )
            HorizontalDivider()
            WeightSetting(
                weightKg = weightKg,
                weightUnit = weightUnit,
                onWeightChange = vm::setWeightKg,
                onUnitChange = vm::setWeightUnit
            )
        }
    }
}

@Composable
private fun VibrationStrengthSetting(
    strength: VibrationStrength,
    onStrengthChange: (VibrationStrength) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_vibration_strength)) },
        trailingContent = {
            Box {
                TextButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("button_vibration_strength")
                ) {
                    Text(stringResource(strength.labelRes))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    VibrationStrength.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            onClick = {
                                showMenu = false
                                onStrengthChange(option)
                            },
                            modifier = Modifier.testTag(
                                "vibration_strength_${option.name.lowercase()}"
                            )
                        )
                    }
                }
            }
        }
    )
}

private val VibrationStrength.labelRes: Int
    get() = when (this) {
        VibrationStrength.LIGHT -> R.string.settings_vibration_strength_light
        VibrationStrength.MEDIUM -> R.string.settings_vibration_strength_medium
        VibrationStrength.STRONG -> R.string.settings_vibration_strength_strong
    }

@Composable
internal fun VoiceTestControl(
    state: VoiceTestState,
    onTestVoice: () -> Unit,
    onOpenTtsSettings: () -> Unit
) {
    val running = state == VoiceTestState.Initializing || state == VoiceTestState.Speaking
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedButton(
            onClick = onTestVoice,
            enabled = !running,
            modifier = Modifier.testTag("button_test_voice")
        ) {
            Text(stringResource(R.string.settings_tts_test_voice))
        }

        val status = when (state) {
            VoiceTestState.Idle -> null
            VoiceTestState.Initializing -> R.string.settings_tts_test_initializing
            VoiceTestState.Speaking -> R.string.settings_tts_test_speaking
            VoiceTestState.Completed -> R.string.settings_tts_test_completed
            is VoiceTestState.Failed -> when (state.result) {
                TtsDiagnosticResult.NoEngineInstalled -> R.string.settings_tts_test_no_engine
                TtsDiagnosticResult.VoiceUnavailable -> R.string.settings_tts_test_voice_unavailable
                TtsDiagnosticResult.SynthesisOrServiceFailure -> R.string.settings_tts_test_synthesis_failure
                TtsDiagnosticResult.AudioOutputFailure -> R.string.settings_tts_test_output_failure
                TtsDiagnosticResult.Success -> R.string.settings_tts_test_completed
            }
        }
        if (status != null) {
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.bodySmall,
                color = if (state is VoiceTestState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("text_voice_test_status")
            )
        }
        if (state is VoiceTestState.Failed) {
            TextButton(
                onClick = onOpenTtsSettings,
                modifier = Modifier.testTag("button_open_tts_settings")
            ) {
                Text(stringResource(R.string.settings_tts_open_settings))
            }
        }
    }
}

internal fun openTtsSettings(context: Context) {
    val ttsSettings = Intent(TTS_SETTINGS_ACTION).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val preferredAction = preferredTtsSettingsAction(
        ttsSettings.resolveActivity(context.packageManager) != null
    )
    if (preferredAction == TTS_SETTINGS_ACTION) {
        try {
            context.startActivity(ttsSettings)
            return
        } catch (_: ActivityNotFoundException) {
            // A ROM can advertise an activity that is unavailable when launched.
        } catch (_: SecurityException) {
            // Some ROMs expose the destination but do not allow third-party apps to open it.
        }
    }
    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

internal const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"

internal fun preferredTtsSettingsAction(ttsDestinationAvailable: Boolean): String =
    if (ttsDestinationAvailable) TTS_SETTINGS_ACTION else Settings.ACTION_SETTINGS

@Composable
private fun WeightSetting(
    weightKg: Float?,
    weightUnit: WeightUnit,
    onWeightChange: (Float) -> Unit,
    onUnitChange: (WeightUnit) -> Unit
) {
    var showUnitMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // null = "showing whatever weightKg currently resolves to" (stays reactive to the async
    // DataStore load); becomes non-null the moment the user types, so their in-progress edit
    // (e.g. "6.") isn't clobbered by the round-tripped value. Resets to null on unit switch so
    // the field re-syncs freshly converted into the new unit.
    var pendingText by remember(weightUnit) { mutableStateOf<String?>(null) }
    val weightText = pendingText ?: (weightKg?.let { formatWeight(weightUnit.fromKg(it)) } ?: "")
    ListItem(
        headlineContent = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_weight))
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { newText ->
                            pendingText = newText
                            val parsed = newText.toFloatOrNull()
                            if (parsed != null && parsed > 0f) {
                                onWeightChange(weightUnit.toKg(parsed))
                            }
                        },
                        placeholder = { Text(stringResource(R.string.settings_weight_not_set)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("field_weight")
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        TextButton(
                            onClick = { showUnitMenu = true },
                            modifier = Modifier.testTag("button_weight_unit")
                        ) {
                            Text(stringResource(weightUnit.labelRes))
                        }
                        DropdownMenu(
                            expanded = showUnitMenu,
                            onDismissRequest = { showUnitMenu = false }
                        ) {
                            WeightUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(unit.labelRes)) },
                                    onClick = {
                                        showUnitMenu = false
                                        onUnitChange(unit)
                                    }
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_weight_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    )
}

private fun formatWeight(value: Float): String {
    // "%.1f".format(...) renders using the default locale's decimal separator (a comma in
    // Russian and many others), but this always trimmed a literal '.' — so a whole-number value
    // like 103 became "103,0" -> trimEnd('0') -> "103," -> trimEnd('.') is a no-op on a comma,
    // leaving the dangling separator on screen. Trim whatever character the locale actually used.
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    return "%.1f".format(value).trimEnd('0').trimEnd(separator)
}

@Composable
private fun SecondsSlider(
    label: String,
    seconds: Int,
    range: ClosedFloatingPointRange<Float>,
    testTag: String,
    onValueChange: (Int) -> Unit
) {
    ListItem(
        headlineContent = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label)
                    Text(
                        "%d s".format(seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Slider(
                    value = seconds.toFloat(),
                    onValueChange = { onValueChange(it.roundToInt()) },
                    valueRange = range,
                    steps = (range.endInclusive - range.start).toInt() - 1,
                    modifier = Modifier.testTag(testTag)
                )
            }
        }
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
            )
        }
    )
}
