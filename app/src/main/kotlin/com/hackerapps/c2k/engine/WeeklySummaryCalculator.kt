package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class PeriodTotals(
    val completedSessions: Int,
    val totalKm: Float,
    val totalTimeSeconds: Int,
    val totalCalories: Int?
) {
    val hasActivity: Boolean get() = completedSessions > 0 || totalTimeSeconds > 0
}

data class WeeklySummary(val thisWeek: PeriodTotals, val lastWeek: PeriodTotals)

/**
 * Aggregates sessions into the current calendar week and the one before it, so the History
 * screen can show a "this week" summary with a last-week comparison.
 *
 * Totals follow the History "Totals" card: distance and time include every recorded session
 * (an abandoned run still covered ground), while the workout count only includes completed
 * ones. Calories are null when no body weight is set, rather than guessed.
 */
object WeeklySummaryCalculator {

    fun compute(
        sessions: List<WorkoutSessionEntity>,
        nowMillis: Long,
        weightKg: Float? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    ): WeeklySummary {
        // Boundaries are local midnights, not fixed 7*24h offsets, so a DST change inside the
        // week can't shift a session into the neighbouring week.
        val thisWeekStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        fun startMillis(weeksAgo: Long) =
            thisWeekStart.minusWeeks(weeksAgo).atStartOfDay(zone).toInstant().toEpochMilli()

        val thisStart = startMillis(0)
        val lastStart = startMillis(1)
        val nextStart = startMillis(-1)

        return WeeklySummary(
            thisWeek = totals(sessions.filter { it.startedAt in thisStart until nextStart }, weightKg),
            lastWeek = totals(sessions.filter { it.startedAt in lastStart until thisStart }, weightKg)
        )
    }

    private fun totals(sessions: List<WorkoutSessionEntity>, weightKg: Float?) = PeriodTotals(
        completedSessions = sessions.count { it.completed },
        totalKm = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
        totalTimeSeconds = sessions.sumOf { it.durationSeconds },
        totalCalories = weightKg?.let { kg ->
            sessions.sumOf { CalorieCalculator.estimateCalories(it.distanceMeters, it.durationSeconds, kg) ?: 0 }
        }
    )
}
