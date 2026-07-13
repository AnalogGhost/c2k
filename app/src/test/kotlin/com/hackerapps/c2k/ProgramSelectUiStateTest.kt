package com.hackerapps.c2k

import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.ui.screen.program.ProgramSelectUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramSelectUiStateTest {

    private fun plan(weeks: Int, daysPerWeek: Int = 3): WorkoutPlan = WorkoutPlan(
        programId = "test",
        displayName = "Test Plan",
        description = "",
        weeks = (1..weeks).map { w ->
            (1..daysPerWeek).map { d ->
                WorkoutDay(week = w, day = d, intervals = listOf(Interval(IntervalType.RUN, 60)))
            }
        }
    )

    @Test
    fun next_incomplete_day_is_null_when_plan_is_null() {
        val state = ProgramSelectUiState(plan = null, completedDays = emptySet())
        assertNull(state.nextIncompleteDay)
    }

    @Test
    fun next_incomplete_day_is_week1_day1_when_nothing_completed() {
        val state = ProgramSelectUiState(plan = plan(2), completedDays = emptySet())
        assertEquals(1 to 1, state.nextIncompleteDay)
    }

    @Test
    fun next_incomplete_day_skips_completed_days_within_week() {
        val state = ProgramSelectUiState(plan = plan(2), completedDays = setOf(1 to 1, 1 to 2))
        assertEquals(1 to 3, state.nextIncompleteDay)
    }

    @Test
    fun next_incomplete_day_crosses_into_next_week() {
        val state = ProgramSelectUiState(plan = plan(2), completedDays = setOf(1 to 1, 1 to 2, 1 to 3))
        assertEquals(2 to 1, state.nextIncompleteDay)
    }

    @Test
    fun next_incomplete_day_is_null_when_all_days_completed() {
        val completed = setOf(1 to 1, 1 to 2, 1 to 3, 2 to 1, 2 to 2, 2 to 3)
        val state = ProgramSelectUiState(plan = plan(2), completedDays = completed)
        assertNull(state.nextIncompleteDay)
    }
}
