package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.ProgramSummary
import com.hackerapps.c2k.data.model.Programs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramSummaryTest {

    private fun session(
        id: Long,
        seconds: Int,
        meters: Float,
        programId: String = "C25K",
        completed: Boolean = true
    ) = WorkoutSessionEntity(
        id = id, programId = programId, week = 1, day = 1, startedAt = 0L,
        durationSeconds = seconds, distanceMeters = meters, completed = completed
    )

    @Test
    fun `adds the finished workout to the program's earlier ones`() {
        val earlier = listOf(session(1, 1800, 3000f), session(2, 1500, 2500f))
        assertEquals(
            ProgramSummary(workouts = 3, durationSeconds = 5300, distanceMeters = 9500f),
            ProgramSummary.of(earlier, "C25K", currentId = 3, durationSeconds = 2000, distanceMeters = 4000f)
        )
    }

    @Test
    fun `leaves out early stops and other programs`() {
        val sessions = listOf(session(1, 600, 900f, completed = false), session(2, 1800, 3000f, programId = "C210K"))
        assertEquals(
            ProgramSummary(workouts = 1, durationSeconds = 2000, distanceMeters = 0f),
            ProgramSummary.of(sessions, "C25K", currentId = 3, durationSeconds = 2000, distanceMeters = 0f)
        )
    }

    @Test
    fun `the finished workout is not counted twice once saved`() {
        val sessions = listOf(session(3, 2000, 4000f), session(1, 1800, 3000f))
        val summary = ProgramSummary.of(sessions, "C25K", currentId = 3, durationSeconds = 2000, distanceMeters = 4000f)
        assertEquals(2, summary.workouts)
        assertEquals(3800, summary.durationSeconds)
    }

    @Test
    fun `every program but One Hour Runner suggests another`() {
        val ids = Programs.all().map { it.programId }
        for (id in ids) {
            val next = Programs.nextPrograms(after = id)
            assertFalse(id, id in next)
            assertEquals(id, id == Programs.ID_OHR, next.isEmpty())
            assertTrue(id, ids.containsAll(next))
        }
    }

    @Test
    fun `Pre-C25K leads to Couch to 5K`() {
        assertEquals(listOf(Programs.ID_C25K), Programs.nextPrograms(after = Programs.ID_PRE_C25K))
    }
}
