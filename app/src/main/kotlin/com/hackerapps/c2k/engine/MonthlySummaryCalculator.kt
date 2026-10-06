package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MonthlySummary(val thisMonth: PeriodTotals, val lastMonth: PeriodTotals)

/**
 * Aggregates sessions into the current calendar month and the one before it, so the History
 * screen can show a "this month" summary with a last-month comparison. See [periodTotals] for
 * what's included.
 */
object MonthlySummaryCalculator {

    fun compute(
        sessions: List<WorkoutSessionEntity>,
        nowMillis: Long,
        weightKg: Float? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): MonthlySummary {
        // Boundaries are local midnights of the 1st, not a fixed day count, so a DST change
        // inside the month or a short/long month (28-31 days, or a year rollover at December)
        // can't misplace a session.
        val thisYearMonth = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        fun startMillis(monthsAgo: Long) =
            thisYearMonth.minusMonths(monthsAgo).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val thisStart = startMillis(0)
        val lastStart = startMillis(1)
        val nextStart = startMillis(-1)

        return MonthlySummary(
            thisMonth = periodTotals(sessions.filter { it.startedAt in thisStart until nextStart }, weightKg),
            lastMonth = periodTotals(sessions.filter { it.startedAt in lastStart until thisStart }, weightKg)
        )
    }
}
