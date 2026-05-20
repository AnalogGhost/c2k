package com.hackerapps.c2k.ui.screen.workout

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.service.WorkoutService
import com.hackerapps.c2k.ui.component.RequestLocationPermission
import com.hackerapps.c2k.ui.screen.workout.components.IntervalRing
import com.hackerapps.c2k.ui.theme.RunOrange
import com.hackerapps.c2k.ui.theme.WalkBlue
import com.hackerapps.c2k.ui.theme.WarmCoolGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    programId: String,
    week: Int,
    day: Int,
    onFinished: () -> Unit,
    vm: WorkoutViewModel = viewModel()
) {
    val context = LocalContext.current
    val workoutState by vm.workoutState.collectAsStateWithLifecycle()
    val distanceMeters by vm.distanceMeters.collectAsStateWithLifecycle()
    val pace by vm.currentPaceMinPerKm.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val showBatteryPrompt by vm.showBatteryPrompt.collectAsStateWithLifecycle()
    val serviceRunning by WorkoutService.isRunning.collectAsStateWithLifecycle()

    var permissionResolved by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }

    val programName = remember(programId) {
        runCatching { Programs.byId(programId).displayName }.getOrDefault(programId)
    }

    // Request location permission before starting the workout
    if (!permissionResolved) {
        RequestLocationPermission { permissionResolved = true }
    }

    LaunchedEffect(permissionResolved) {
        if (permissionResolved) vm.startWorkout(programId, week, day)
    }

    // System back → show stop dialog instead of navigating away
    BackHandler {
        if (workoutState is WorkoutState.Completed) onFinished()
        else showStopDialog = true
    }

    DisposableEffect(keepScreenOn) {
        val window = (context as? android.app.Activity)?.window
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (showBatteryPrompt) {
        AlertDialog(
            onDismissRequest = { vm.dismissBatteryPrompt() },
            title = { Text(stringResource(R.string.battery_opt_title)) },
            text = { Text(stringResource(R.string.battery_opt_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissBatteryPrompt()
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }) { Text(stringResource(R.string.battery_opt_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissBatteryPrompt() }) {
                    Text(stringResource(R.string.battery_opt_dismiss))
                }
            }
        )
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.workout_stop_title)) },
            text = { Text(stringResource(R.string.workout_stop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.stop()
                    showStopDialog = false
                    onFinished()
                }) { Text(stringResource(R.string.workout_stop_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.workout_stop_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("$programName · Week $week, Day $day") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = workoutState) {
                is WorkoutState.Active -> ActiveWorkoutContent(
                    state = s,
                    distanceMeters = distanceMeters,
                    pace = pace,
                    onPause = { vm.pause() },
                    onStop = { showStopDialog = true }
                )
                is WorkoutState.Paused -> PausedWorkoutContent(
                    state = s.snapshot,
                    onResume = { vm.resume() },
                    onStop = { showStopDialog = true }
                )
                is WorkoutState.Completed -> CompletedContent(
                    state = s,
                    distanceMeters = distanceMeters,
                    onDone = onFinished
                )
                else -> Text(if (serviceRunning) "Reconnecting…" else "Starting…")
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    state: WorkoutState.Active,
    distanceMeters: Float,
    pace: String?,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    val ringColor = intervalColor(state.currentInterval.type)
    val label = intervalLabel(state.currentInterval.type)
    val intervalProgress = 1f - state.secondsRemainingInInterval.toFloat() /
            state.currentInterval.durationSeconds.toFloat()

    LinearProgressIndicator(
        progress = { state.intervalIndex.toFloat() / state.totalIntervals },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))

    IntervalRing(
        progress = intervalProgress.coerceIn(0f, 1f),
        ringColor = ringColor,
        contentDescription = "$label: ${formatTime(state.secondsRemainingInInterval)} remaining"
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = ringColor)
            Spacer(Modifier.height(4.dp))
            Text(
                formatTime(state.secondsRemainingInInterval),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Text(
        "Elapsed: ${formatTime(state.elapsedSessionSeconds)}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
    Text(
        "Interval ${state.intervalIndex + 1} of ${state.totalIntervals}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    )

    if (distanceMeters > 0f) {
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.workout_distance_km, distanceMeters / 1000f),
                style = MaterialTheme.typography.bodyLarge
            )
            if (pace != null) {
                Text(
                    stringResource(R.string.workout_pace, pace),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    Spacer(Modifier.height(32.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalButton(onClick = onPause) {
            Icon(Icons.Default.Pause, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.workout_pause))
        }
        OutlinedButton(onClick = onStop) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.workout_stop))
        }
    }
}

@Composable
private fun PausedWorkoutContent(
    state: WorkoutState.Active,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val ringColor = intervalColor(state.currentInterval.type)
    val label = intervalLabel(state.currentInterval.type)
    val intervalProgress = 1f - state.secondsRemainingInInterval.toFloat() /
            state.currentInterval.durationSeconds.toFloat()

    LinearProgressIndicator(
        progress = { state.intervalIndex.toFloat() / state.totalIntervals },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))

    Box(contentAlignment = Alignment.Center) {
        IntervalRing(
            progress = intervalProgress.coerceIn(0f, 1f),
            ringColor = ringColor,
            contentDescription = "Paused: $label"
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.titleLarge, color = ringColor)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTime(state.secondsRemainingInInterval),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("PAUSED", style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    Spacer(Modifier.height(8.dp))
    Text(
        "Elapsed: ${formatTime(state.elapsedSessionSeconds)}",
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(32.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onResume) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.workout_resume))
        }
        OutlinedButton(onClick = onStop) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.workout_stop))
        }
    }
}

@Composable
private fun CompletedContent(
    state: WorkoutState.Completed,
    distanceMeters: Float,
    onDone: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.workout_complete_title),
            style = MaterialTheme.typography.headlineLarge,
            color = WarmCoolGreen
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.workout_complete_message),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Time: ${formatTime(state.elapsedSessionSeconds)}",
            style = MaterialTheme.typography.titleLarge
        )
        if (distanceMeters > 0f) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.workout_distance_km, distanceMeters / 1000f),
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone) {
            Text("Done")
        }
    }
}

private fun intervalColor(type: IntervalType) = when (type) {
    IntervalType.RUN      -> RunOrange
    IntervalType.WALK     -> WalkBlue
    IntervalType.WARMUP,
    IntervalType.COOLDOWN -> WarmCoolGreen
}

@Composable
private fun intervalLabel(type: IntervalType) = when (type) {
    IntervalType.RUN      -> stringResource(R.string.workout_interval_run)
    IntervalType.WALK     -> stringResource(R.string.workout_interval_walk)
    IntervalType.WARMUP   -> stringResource(R.string.workout_interval_warmup)
    IntervalType.COOLDOWN -> stringResource(R.string.workout_interval_cooldown)
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
