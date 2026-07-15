package com.hackerapps.c2k.ui.screen.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.ui.programNameRes
import com.hackerapps.c2k.ui.theme.WarmCoolGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel()
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = {
                            val csv = buildCsv(sessions)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "C2K Workout History")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.history_export_chooser))
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.history_export))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.history_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // Aggregate stats card
                item {
                    StatsCard(stats)
                }

                item { Spacer(Modifier.height(4.dp)) }

                items(sessions, key = { it.id }) { session ->
                    SwipeToDeleteSession(
                        session = session,
                        onDelete = { vm.deleteSession(session.id) },
                        onExportGpx = { hasRoute ->
                            if (hasRoute) {
                                vm.buildGpx(session) { gpx ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/gpx+xml"
                                        putExtra(Intent.EXTRA_SUBJECT, "C2K route W${session.week}D${session.day}")
                                        putExtra(Intent.EXTRA_TEXT, gpx)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, context.getString(R.string.history_export_gpx_chooser))
                                    )
                                }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: HistoryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = stats.completedSessions.toString(),
                label = stringResource(R.string.history_stats_workouts)
            )
            StatItem(
                value = "%.1f".format(stats.totalKm),
                label = stringResource(R.string.history_stats_km)
            )
            StatItem(
                value = formatDuration(stats.totalTimeSeconds),
                label = stringResource(R.string.history_stats_time)
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteSession(
    session: WorkoutSessionEntity,
    onDelete: () -> Unit,
    onExportGpx: (hasRoute: Boolean) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) showConfirm = true
            false
        }
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) {
                    Text(stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.history_delete_cancel))
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.history_delete_confirm),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        SessionCard(
            session = session,
            onExportGpx = { onExportGpx(session.distanceMeters > 0f) }
        )
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSessionEntity,
    onExportGpx: () -> Unit
) {
    val nameRes = programNameRes(session.programId)
    val displayName = nameRes?.let { stringResource(it) } ?: session.programId
    val date = SimpleDateFormat("EEE d MMM yyyy  HH:mm", Locale.getDefault())
        .format(Date(session.startedAt))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (session.completed) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarmCoolGreen)
                    }
                    Text(
                        "  $displayName  •  ${stringResource(R.string.history_week_day, session.week, session.day)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                // GPX export button — only shown when the session has GPS distance recorded
                if (session.distanceMeters > 0f) {
                    IconButton(onClick = onExportGpx) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = stringResource(R.string.history_export_gpx),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                date,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (session.distanceMeters > 0f) {
                Text(
                    "%.2f km  •  ${formatDuration(session.durationSeconds)}".format(
                        session.distanceMeters / 1000f
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(formatDuration(session.durationSeconds), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun buildCsv(sessions: List<WorkoutSessionEntity>): String {
    val header = "Program,Week,Day,Date,Duration,Distance_m,Completed"
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val rows = sessions.map { s ->
        val name = Programs.all().find { it.programId == s.programId }?.displayName ?: s.programId
        val date = dateFormat.format(Date(s.startedAt))
        "$name,${s.week},${s.day},$date,${formatDuration(s.durationSeconds)},${s.distanceMeters.toInt()},${s.completed}"
    }
    return (listOf(header) + rows).joinToString("\n")
}

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
