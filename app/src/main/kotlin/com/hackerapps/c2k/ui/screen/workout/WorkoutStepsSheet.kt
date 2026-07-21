package com.hackerapps.c2k.ui.screen.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.ui.theme.RunOrange
import com.hackerapps.c2k.ui.theme.WalkBlue
import com.hackerapps.c2k.ui.theme.WarmCoolGreen

// A read-only bottom sheet showing the current jog's interval breakdown, reachable from the
// workout toolbar so the runner can review the steps without leaving (and stopping) the workout.
// Kept self-contained (its own grouping) so this feature doesn't touch the program-screen preview.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutStepsSheet(
    week: Int,
    day: Int,
    workoutDay: WorkoutDay,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.workout_steps_title, week, day),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.program_preview_duration, workoutDay.totalDurationSeconds / 60),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(workoutDay.intervals.grouped()) { group ->
                    StepRow(group)
                }
            }
        }
    }
}

private data class StepGroup(
    val type: IntervalType,
    val durationSeconds: Int,
    val count: Int
)

private fun List<Interval>.grouped(): List<StepGroup> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<StepGroup>()
    var type = first().type
    var duration = first().durationSeconds
    var count = 1
    for (i in 1 until size) {
        val iv = this[i]
        if (iv.type == type && iv.durationSeconds == duration) {
            count++
        } else {
            result.add(StepGroup(type, duration, count))
            type = iv.type; duration = iv.durationSeconds; count = 1
        }
    }
    result.add(StepGroup(type, duration, count))
    return result
}

@Composable
private fun StepRow(group: StepGroup) {
    val color = when (group.type) {
        IntervalType.RUN      -> RunOrange
        IntervalType.WALK     -> WalkBlue
        IntervalType.WARMUP,
        IntervalType.COOLDOWN -> WarmCoolGreen
    }
    val label = when (group.type) {
        IntervalType.RUN      -> stringResource(R.string.workout_interval_run)
        IntervalType.WALK     -> stringResource(R.string.workout_interval_walk)
        IntervalType.WARMUP   -> stringResource(R.string.workout_interval_warmup)
        IntervalType.COOLDOWN -> stringResource(R.string.workout_interval_cooldown)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val countSuffix = if (group.count > 1) " × ${group.count}" else ""
        Text(label + countSuffix, color = color, style = MaterialTheme.typography.bodyLarge)
        Text(formatStepDuration(group.durationSeconds), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatStepDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0 -> "${m} min"
        else -> "${s}s"
    }
}
