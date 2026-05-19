package org.ctok.ui.screen.program

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import org.ctok.R
import org.ctok.ui.theme.WarmCoolGreen

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
            item { Spacer(Modifier.height(4.dp)) }

            itemsIndexed(plan.weeks) { weekIdx, days ->
                val week = weekIdx + 1
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.program_week_label, week),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            days.forEachIndexed { dayIdx, _ ->
                                val day = dayIdx + 1
                                val done = (week to day) in state.completedDays
                                DayButton(
                                    day = day,
                                    completed = done,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStartWorkout(week, day) }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (completed) {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WarmCoolGreen
            )
            Text("  Day $day", color = WarmCoolGreen)
        }
    } else {
        Button(onClick = onClick, modifier = modifier) {
            Text(stringResource(R.string.program_day_label, day))
        }
    }
}
