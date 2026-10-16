package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import java.time.Instant
import java.time.ZoneId

// Shared by the Home and History screens so both always show the same streak.
object StreakCalculator {

    // Local calendar, not a raw UTC epoch-millis division: HistoryScreen already displays
    // session dates via SimpleDateFormat(..., Locale.getDefault()), which is local-timezone
    // aware. Bucketing streaks by UTC instead would disagree with what the user sees there,
    // and — for any non-UTC timezone — can misplace a session near a week boundary into the
    // "wrong" week, silently breaking or inflating the streak.
    // Weeks are ISO (Monday-start): epoch day -3 was a Monday (1969-12-29), so shifting by 3
    // aligns the floor division to Monday boundaries.
    private fun localWeekNumber(millis: Long): Long {
        val epochDay = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        return Math.floorDiv(epochDay + 3, 7)
    }

    // Weekly, not daily (issue #29): every program schedules ~3 runs a week with rest days
    // between them, so a day-based streak was guaranteed to reset on every rest day. A week
    // counts toward the streak if it has at least one completed workout, and the streak is
    // alive as long as the latest such week is the current or the previous one (the current
    // week gets a grace period — its workout may simply not have happened yet).
    fun compute(sessions: List<WorkoutSessionEntity>, nowMillis: Long): Int {
        val completedWeeks = sessions
            .filter { it.completed }
            .map { localWeekNumber(it.startedAt) }
            .toSortedSet()

        if (completedWeeks.isEmpty()) return 0

        val thisWeek = localWeekNumber(nowMillis)
        val lastWeek = thisWeek - 1

        if (completedWeeks.last() < lastWeek) return 0

        var streak = 1
        var expected = completedWeeks.last() - 1
        for (weekNum in completedWeeks.toList().reversed().drop(1)) {
            if (weekNum == expected) { streak++; expected-- }
            else if (weekNum < expected) break
        }
        return streak
    }
}
