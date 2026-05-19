package org.c2k.ui.screen.workout

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
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
import org.c2k.R
import org.c2k.data.model.IntervalType
import org.c2k.engine.WorkoutState
import org.c2k.ui.screen.workout.components.IntervalRing
import org.c2k.ui.theme.RunOrange
import org.c2k.ui.theme.WalkBlue
import org.c2k.ui.theme.WarmCoolGreen

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

    var showStopDialog by remember { mutableStateOf(false) }

    // Start workout and bind on first composition
    LaunchedEffect(Unit) {
        vm.startWorkout(context, programId, week, day)
    }

    // Navigate away when completed
    LaunchedEffect(workoutState) {
        if (workoutState is WorkoutState.Completed) onFinished()
    }

    // Keep screen on during workout
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop workout?") },
            text = { Text("Your progress for this session will be saved as incomplete.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.stop(context)
                    showStopDialog = false
                    onFinished()
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Week $week, Day $day") })
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
                    onPause = { vm.pause(context) },
                    onStop = { showStopDialog = true }
                )
                is WorkoutState.Paused -> PausedWorkoutContent(
                    state = s.snapshot,
                    onResume = { vm.resume(context) },
                    onStop = { showStopDialog = true }
                )
                is WorkoutState.Completed -> {
                    // Navigation triggered via LaunchedEffect above
                    Text("Workout complete!")
                }
                else -> Text("Starting…")
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    state: WorkoutState.Active,
    distanceMeters: Float,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    val ringColor = when (state.currentInterval.type) {
        IntervalType.RUN      -> RunOrange
        IntervalType.WALK     -> WalkBlue
        IntervalType.WARMUP,
        IntervalType.COOLDOWN -> WarmCoolGreen
    }
    val label = when (state.currentInterval.type) {
        IntervalType.RUN      -> stringResource(R.string.workout_interval_run)
        IntervalType.WALK     -> stringResource(R.string.workout_interval_walk)
        IntervalType.WARMUP   -> stringResource(R.string.workout_interval_warmup)
        IntervalType.COOLDOWN -> stringResource(R.string.workout_interval_cooldown)
    }
    val progress = 1f - state.secondsRemainingInInterval.toFloat() /
            state.currentInterval.durationSeconds.toFloat()

    IntervalRing(progress = progress.coerceIn(0f, 1f), ringColor = ringColor) {
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

    // Overall session progress
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
        Text(
            stringResource(R.string.workout_distance_km, distanceMeters / 1000f),
            style = MaterialTheme.typography.bodyLarge
        )
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
    Text("PAUSED", style = MaterialTheme.typography.headlineLarge)
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

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
