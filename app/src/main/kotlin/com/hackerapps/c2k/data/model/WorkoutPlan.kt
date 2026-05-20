package com.hackerapps.c2k.data.model

data class WorkoutDay(
    val week: Int,
    val day: Int,
    val intervals: List<Interval>
) {
    val totalDurationSeconds: Int get() = intervals.sumOf { it.durationSeconds }
}

data class WorkoutPlan(
    val programId: String,
    val displayName: String,
    val description: String,
    val weeks: List<List<WorkoutDay>>
) {
    val totalWeeks: Int get() = weeks.size
}
