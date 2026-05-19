package org.c2k

import org.c2k.data.model.IntervalType
import org.c2k.data.model.Programs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramsTest {

    @Test
    fun c25k_has_9_weeks() {
        assertEquals(9, Programs.C25K.totalWeeks)
    }

    @Test
    fun c25k_each_week_has_3_days() {
        Programs.C25K.weeks.forEachIndexed { i, week ->
            assertEquals("Week ${i + 1} should have 3 days", 3, week.size)
        }
    }

    @Test
    fun c25k_week1_day1_has_warmup_and_cooldown() {
        val intervals = Programs.C25K.weeks[0][0].intervals
        assertEquals(IntervalType.WARMUP, intervals.first().type)
        assertEquals(IntervalType.COOLDOWN, intervals.last().type)
    }

    @Test
    fun c25k_week1_has_8_run_walk_pairs() {
        val intervals = Programs.C25K.weeks[0][0].intervals
            .filter { it.type == IntervalType.RUN || it.type == IntervalType.WALK }
        assertEquals(16, intervals.size) // 8 run + 8 walk
    }

    @Test
    fun c25k_week1_run_duration_is_60s() {
        val runIntervals = Programs.C25K.weeks[0][0].intervals
            .filter { it.type == IntervalType.RUN }
        runIntervals.forEach { assertEquals(60, it.durationSeconds) }
    }

    @Test
    fun c25k_week9_is_30_min_run() {
        val runIntervals = Programs.C25K.weeks[8][0].intervals
            .filter { it.type == IntervalType.RUN }
        assertEquals(1, runIntervals.size)
        assertEquals(1800, runIntervals[0].durationSeconds)
    }

    @Test
    fun c210k_has_14_weeks() {
        assertEquals(14, Programs.C210K.totalWeeks)
    }

    @Test
    fun c210k_week14_is_60_min_run() {
        val runIntervals = Programs.C210K.weeks[13][0].intervals
            .filter { it.type == IntervalType.RUN }
        assertEquals(1, runIntervals.size)
        assertEquals(3600, runIntervals[0].durationSeconds)
    }

    @Test
    fun c210k_first_9_weeks_match_c25k() {
        (0 until 9).forEach { wi ->
            (0 until 3).forEach { di ->
                val c25kDay = Programs.C25K.weeks[wi][di]
                val c210kDay = Programs.C210K.weeks[wi][di]
                assertEquals(
                    "Week ${wi + 1} Day ${di + 1} interval count mismatch",
                    c25kDay.intervals.size,
                    c210kDay.intervals.size
                )
                assertEquals(
                    "Week ${wi + 1} Day ${di + 1} duration mismatch",
                    c25kDay.totalDurationSeconds,
                    c210kDay.totalDurationSeconds
                )
            }
        }
    }

    @Test
    fun all_days_have_positive_duration() {
        Programs.all().forEach { plan ->
            plan.weeks.forEachIndexed { wi, week ->
                week.forEachIndexed { di, day ->
                    assertTrue(
                        "${plan.programId} W${wi + 1}D${di + 1} must have positive duration",
                        day.totalDurationSeconds > 0
                    )
                }
            }
        }
    }

    @Test
    fun all_intervals_have_non_empty_announcements() {
        Programs.all().forEach { plan ->
            plan.weeks.forEach { week ->
                week.forEach { day ->
                    day.intervals.forEach { interval ->
                        assertTrue(
                            "Empty announcement in ${plan.programId}",
                            interval.announcement.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}
