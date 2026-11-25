package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationTriggerTest {

    private val finished = WorkoutState.Completed(sessionId = 1, elapsedSessionSeconds = 1800)
    private val skipped = WorkoutState.Completed(sessionId = 1, elapsedSessionSeconds = 1800, skippedRun = true)
    private val c25k = Programs.C25K

    private fun celebration(
        state: WorkoutState,
        plan: WorkoutPlan,
        week: Int,
        day: Int,
        weekThreeCelebrated: Boolean = false
    ) = CelebrationTrigger.celebration(state, plan, week, day, weekThreeCelebrated)

    // Program completion

    @Test
    fun `the last workout of every program is celebrated`() {
        for (plan in Programs.all()) {
            val last = plan.weeks.last().last()
            assertEquals(plan.programId, Celebration.PROGRAM_FINISHED, celebration(finished, plan, last.week, last.day))
        }
    }

    @Test
    fun `other workouts are not celebrated`() {
        assertNull(celebration(finished, c25k, 1, 1))
        assertNull(celebration(finished, c25k, 9, 2))
        assertNull(celebration(finished, c25k, 3, 2))
    }

    @Test
    fun `a skipped run on the last day is not celebrated`() {
        assertNull(celebration(skipped, c25k, 9, 3))
    }

    @Test
    fun `nothing fires mid-workout`() {
        val active = WorkoutState.Active(
            currentInterval = Interval(IntervalType.COOLDOWN, 300), nextInterval = null,
            intervalIndex = 2, totalIntervals = 3, remainingRunIntervals = 0,
            secondsRemainingInInterval = 1, elapsedSessionSeconds = 1799, sessionId = 1
        )
        assertNull(celebration(active, c25k, 9, 3))
        assertNull(celebration(WorkoutState.Paused(active), c25k, 9, 3))
        assertNull(celebration(WorkoutState.Idle, c25k, 9, 3))
    }

    // Week 3

    @Test
    fun `the last workout of week 3 is celebrated once`() {
        assertEquals(Celebration.WEEK_THREE, celebration(finished, c25k, 3, 3))
        assertNull(celebration(finished, c25k, 3, 3, weekThreeCelebrated = true))
        assertNull(celebration(finished, Programs.C210K, 3, 3, weekThreeCelebrated = true))
    }

    @Test
    fun `Pre-C25K week 3 is its program completion`() {
        assertEquals(Celebration.PROGRAM_FINISHED, celebration(finished, Programs.PreC25K, 3, 3))
    }

    @Test
    fun `a program completion still shows after week 3 was celebrated`() {
        assertEquals(Celebration.PROGRAM_FINISHED, celebration(finished, c25k, 9, 3, weekThreeCelebrated = true))
    }

    @Test
    fun `any celebration from week 3 on uses up the week 3 moment`() {
        assertTrue(CelebrationTrigger.usesUpWeekThree(Celebration.WEEK_THREE, 3))
        assertTrue(CelebrationTrigger.usesUpWeekThree(Celebration.PROGRAM_FINISHED, 3))
        assertTrue(CelebrationTrigger.usesUpWeekThree(Celebration.PROGRAM_FINISHED, 9))
        assertFalse(CelebrationTrigger.usesUpWeekThree(null, 3))
    }
}
