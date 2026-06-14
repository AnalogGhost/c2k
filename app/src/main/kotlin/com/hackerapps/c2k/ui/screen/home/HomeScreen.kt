package com.hackerapps.c2k.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.service.WorkoutService
import com.hackerapps.c2k.ui.screen.program.WorkoutPreviewSheet
import com.hackerapps.c2k.ui.theme.RunOrange
import com.hackerapps.c2k.ui.theme.WarmCoolGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSelectProgram: (String) -> Unit,
    onContinueWorkout: (programId: String, week: Int, day: Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Preview sheet state for the "Continue" shortcut
    var showContinuePreview by remember { mutableStateOf(false) }

    state.nextWorkout?.let { next ->
        if (showContinuePreview) {
            WorkoutPreviewSheet(
                week = next.week,
                day = next.day,
                workoutDay = next.workoutDay,
                isCompleted = false,
                onDismiss = { showContinuePreview = false },
                onStart = {
                    showContinuePreview = false
                    onContinueWorkout(next.programId, next.week, next.day)
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.guide_title))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                if (state.streak > 0) {
                    Text(
                        stringResource(R.string.home_streak, state.streak),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RunOrange
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (state.workoutActive) {
                item {
                    ActiveWorkoutBanner(
                        info = state.activeWorkoutInfo,
                        onClick = {
                            state.activeWorkoutInfo?.let {
                                onContinueWorkout(it.programId, it.week, it.day)
                            }
                        }
                    )
                }
            }

            // "Continue" shortcut — opens preview before starting
            state.nextWorkout?.let { next ->
                item {
                    ContinueWorkoutCard(
                        next = next,
                        onClick = { showContinuePreview = true }
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.home_choose_program),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
            }

            items(state.programs) { plan ->
                ProgramCard(plan = plan, onClick = { onSelectProgram(plan.programId) })
            }

            if (state.recentSessions.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_recent_workouts),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(state.recentSessions.take(5)) { session ->
                    RecentSessionRow(session)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProgramCard(plan: WorkoutPlan, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(plan.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.home_program_weeks, plan.totalWeeks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (plan.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (plan.prerequisite != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_program_prerequisite, plan.prerequisite),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ActiveWorkoutBanner(
    info: WorkoutService.WorkoutInfo?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = WarmCoolGreen.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = WarmCoolGreen)
            Text(
                stringResource(R.string.home_workout_active),
                style = MaterialTheme.typography.bodyLarge,
                color = WarmCoolGreen
            )
        }
    }
}

@Composable
private fun ContinueWorkoutCard(next: NextWorkout, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                stringResource(R.string.home_continue_workout, next.displayName, next.week, next.day),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun RecentSessionRow(session: WorkoutSessionEntity) {
    val displayName = Programs.all()
        .find { it.programId == session.programId }?.displayName ?: session.programId
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        .format(Date(session.startedAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Week ${session.week}, Day ${session.day}  •  $displayName")
        Text(date, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}
