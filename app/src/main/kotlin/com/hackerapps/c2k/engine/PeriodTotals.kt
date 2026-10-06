package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

data class PeriodTotals(
    val completedSessions: Int,
    val totalKm: Float,
    val totalTimeSeconds: Int,
    val totalCalories: Int?
) {
    val hasActivity: Boolean get() = completedSessions > 0 || totalTimeSeconds > 0
}

/**
 * Shared aggregation for [WeeklySummaryCalculator] and [MonthlySummaryCalculator]: follows the
 * History "Totals" card, where distance and time include every recorded session (an abandoned
 * run still covered ground) while the workout count only includes completed ones. Calories are
 * null when no body weight is set, rather than guessed.
 */
internal fun periodTotals(sessions: List<WorkoutSessionEntity>, weightKg: Float?) = PeriodTotals(
    completedSessions = sessions.count { it.completed },
    totalKm = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
    totalTimeSeconds = sessions.sumOf { it.durationSeconds },
    totalCalories = weightKg?.let { kg ->
        sessions.sumOf { CalorieCalculator.estimateCalories(it.distanceMeters, it.durationSeconds, kg) ?: 0 }
    }
)
