package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.data.prefs.RepeatedWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionAdvisorTest {

    private fun plan(id: String = "test", weeks: Int = 4): WorkoutPlan = WorkoutPlan(
        programId = id,
        displayName = "Test Plan",
        description = "",
        weeks = (1..weeks).map { w ->
            (1..3).map { d -> WorkoutDay(w, d, listOf(Interval(IntervalType.RUN, 600))) }
        }
    )

    private fun session(
        week: Int,
        day: Int,
        startedAt: Long,
        completed: Boolean,
        durationSeconds: Int = 600,
        programId: String = "test"
    ) = WorkoutSessionEntity(
        programId = programId, week = week, day = day, startedAt = startedAt,
        durationSeconds = durationSeconds, distanceMeters = 0f, completed = completed
    )

    private fun stoppedEarly(week: Int, day: Int, startedAt: Long, durationSeconds: Int = 600, programId: String = "test") =
        session(week, day, startedAt, completed = false, durationSeconds = durationSeconds, programId = programId)

    // --- suggest ---

    @Test
    fun one_early_stop_is_not_enough() {
        val sessions = listOf(stoppedEarly(3, 2, startedAt = 10))
        assertNull(ProgressionAdvisor.suggest(plan(), 3, 2, sessions, dismissedAtMillis = null))
    }

    @Test
    fun two_early_stops_suggest_repeating_the_previous_week() {
        val sessions = listOf(stoppedEarly(3, 2, startedAt = 10), stoppedEarly(3, 2, startedAt = 20))
        assertEquals(
            ProgressionSuggestion.RepeatWeek(week = 2, earlyStops = 2),
            ProgressionAdvisor.suggest(plan(), 3, 2, sessions, dismissedAtMillis = null)
        )
    }

    @Test
    fun attempts_shorter_than_the_minimum_do_not_count() {
        val short = ProgressionAdvisor.MIN_ATTEMPT_SECONDS - 1
        val sessions = listOf(
            stoppedEarly(3, 2, startedAt = 10, durationSeconds = short),
            stoppedEarly(3, 2, startedAt = 20)
        )
        assertNull(ProgressionAdvisor.suggest(plan(), 3, 2, sessions, dismissedAtMillis = null))
    }

    @Test
    fun early_stops_on_other_days_or_programs_do_not_count() {
        val sessions = listOf(
            stoppedEarly(3, 2, startedAt = 10),
            stoppedEarly(3, 1, startedAt = 20),
            stoppedEarly(3, 2, startedAt = 30, programId = "other")
        )
        assertNull(ProgressionAdvisor.suggest(plan(), 3, 2, sessions, dismissedAtMillis = null))
    }

    @Test
    fun early_stops_before_the_last_finished_workout_do_not_count() {
        // The runner struggled, repeated week 2 (finishing it at 30), and is back on week 3 day 2.
        val sessions = listOf(
            stoppedEarly(3, 2, startedAt = 10),
            stoppedEarly(3, 2, startedAt = 20),
            session(2, 3, startedAt = 30, completed = true)
        )
        assertNull(ProgressionAdvisor.suggest(plan(), 3, 2, sessions, dismissedAtMillis = null))
    }

    @Test
    fun dismissing_holds_until_the_runner_struggles_again() {
        val before = listOf(stoppedEarly(3, 2, startedAt = 10), stoppedEarly(3, 2, startedAt = 20))
        assertNull(ProgressionAdvisor.suggest(plan(), 3, 2, before, dismissedAtMillis = 25))

        val after = before + stoppedEarly(3, 2, startedAt = 30) + stoppedEarly(3, 2, startedAt = 40)
        assertEquals(
            ProgressionSuggestion.RepeatWeek(week = 2, earlyStops = 2),
            ProgressionAdvisor.suggest(plan(), 3, 2, after, dismissedAtMillis = 25)
        )
    }

    @Test
    fun c25k_week_one_suggests_pre_c25k() {
        val sessions = listOf(
            stoppedEarly(1, 1, startedAt = 10, programId = Programs.ID_C25K),
            stoppedEarly(1, 1, startedAt = 20, programId = Programs.ID_C25K)
        )
        assertEquals(
            ProgressionSuggestion.TryPreC25K(earlyStops = 2),
            ProgressionAdvisor.suggest(Programs.C25K, 1, 1, sessions, dismissedAtMillis = null)
        )
    }

    @Test
    fun week_one_of_another_program_has_nothing_to_suggest() {
        val sessions = listOf(stoppedEarly(1, 1, startedAt = 10), stoppedEarly(1, 1, startedAt = 20))
        assertNull(ProgressionAdvisor.suggest(plan(), 1, 1, sessions, dismissedAtMillis = null))
    }

    // --- nextInRepeatedWeek ---

    private val repeatWeek2 = RepeatedWeek("test", week = 2, sinceMillis = 100)

    @Test
    fun repeated_week_starts_at_its_first_day_even_though_it_was_finished_before() {
        val sessions = (1..3).map { session(2, it, startedAt = it.toLong(), completed = true) }
        assertEquals(2 to 1, ProgressionAdvisor.nextInRepeatedWeek(plan(), repeatWeek2, sessions))
    }

    @Test
    fun repeated_week_moves_on_as_days_are_finished_again() {
        val sessions = listOf(
            session(2, 1, startedAt = 110, completed = true),
            session(2, 2, startedAt = 120, completed = false)
        )
        assertEquals(2 to 2, ProgressionAdvisor.nextInRepeatedWeek(plan(), repeatWeek2, sessions))
    }

    @Test
    fun repeated_week_ends_once_every_day_is_finished_again() {
        val sessions = (1..3).map { session(2, it, startedAt = 100L + it, completed = true) }
        assertNull(ProgressionAdvisor.nextInRepeatedWeek(plan(), repeatWeek2, sessions))
    }

    @Test
    fun repeated_week_of_another_program_is_ignored() {
        assertNull(ProgressionAdvisor.nextInRepeatedWeek(plan(id = "other"), repeatWeek2, emptyList()))
    }
}
