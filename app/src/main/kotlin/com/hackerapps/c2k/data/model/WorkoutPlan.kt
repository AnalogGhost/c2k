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
    val weeks: List<List<WorkoutDay>>,
    val prerequisite: String? = null
) {
    val totalWeeks: Int get() = weeks.size

    /**
     * The (week, day) to suggest next: the first uncompleted day *after* the latest completed
     * one, in plan order. Scanning for the earliest gap instead would point users who
     * deliberately skip ahead (e.g. start at week 3 because weeks 1-2 are too easy) back at
     * week 1 day 1 forever. Null when nothing remains after the latest completed day.
     */
    fun nextWorkout(completedDays: Set<Pair<Int, Int>>): Pair<Int, Int>? {
        val ordered = weeks.flatten().map { it.week to it.day }
        val lastCompletedIdx = ordered.indexOfLast { it in completedDays }
        return ordered.drop(lastCompletedIdx + 1).firstOrNull { it !in completedDays }
    }
}
