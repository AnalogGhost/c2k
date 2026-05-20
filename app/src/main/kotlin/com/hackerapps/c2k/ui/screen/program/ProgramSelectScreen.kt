package com.hackerapps.c2k.ui.screen.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.CoachingTips
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.ui.theme.WarmCoolGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSelectScreen(
    programId: String,
    onStartWorkout: (week: Int, day: Int) -> Unit,
    onBack: () -> Unit,
    vm: ProgramSelectViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val plan = state.plan ?: return

    val totalDays = plan.weeks.sumOf { it.size }
    var previewDay by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    previewDay?.let { (previewWeek, previewDayNum) ->
        val workoutDay = plan.weeks[previewWeek - 1][previewDayNum - 1]
        val isCompleted = (previewWeek to previewDayNum) in state.completedDays
        WorkoutPreviewSheet(
            week = previewWeek,
            day = previewDayNum,
            workoutDay = workoutDay,
            isCompleted = isCompleted,
            onDismiss = { previewDay = null },
            onStart = {
                previewDay = null
                onStartWorkout(previewWeek, previewDayNum)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                if (plan.description.isNotBlank()) {
                    Text(
                        plan.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (totalDays > 0 && state.completedDays.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { state.completedDays.size.toFloat() / totalDays },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.program_progress, state.completedDays.size, totalDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // "Continue" shortcut to first incomplete day
                state.nextIncompleteDay?.let { (nextWeek, nextDay) ->
                    if (state.completedDays.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { previewDay = nextWeek to nextDay },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                "  " + stringResource(R.string.program_next_workout, nextWeek, nextDay),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            itemsIndexed(plan.weeks) { weekIdx, days ->
                val week = weekIdx + 1
                val weekDone = days.indices.all { dIdx -> (week to dIdx + 1) in state.completedDays }
                val tip = CoachingTips.tip(programId, week)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.program_week_label, week),
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (weekDone) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = WarmCoolGreen
                                )
                            }
                        }
                        if (tip != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            days.forEachIndexed { dayIdx, workoutDay ->
                                val day = dayIdx + 1
                                val done = (week to day) in state.completedDays
                                DayButton(
                                    day = day,
                                    completed = done,
                                    workoutDay = workoutDay,
                                    modifier = Modifier.weight(1f),
                                    onClick = { previewDay = week to day }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DayButton(
    day: Int,
    completed: Boolean,
    workoutDay: WorkoutDay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val durationMin = workoutDay.totalDurationSeconds / 60
    if (completed) {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = WarmCoolGreen
                    )
                    Text("  Day $day", color = WarmCoolGreen)
                }
            }
        }
    } else {
        Button(onClick = onClick, modifier = modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.program_day_label, day))
                Text(
                    "~${durationMin}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
