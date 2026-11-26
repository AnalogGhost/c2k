package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.model.Programs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationCheckTest {

    private val finished = WorkoutState.Completed(sessionId = 1, elapsedSessionSeconds = 1800)
    private val skipped = WorkoutState.Completed(sessionId = 1, elapsedSessionSeconds = 1800, skippedRun = true)

    /** The week-3 flag, as the preference would hold it, and how often it was written. */
    private var weekThreeCelebrated = false
    private var writes = 0

    private fun newCheck() = CelebrationCheck(
        isWeekThreeCelebrated = { weekThreeCelebrated },
        markWeekThreeCelebrated = { weekThreeCelebrated = true; writes++ }
    )

    @Test
    fun `finishing week 3 is celebrated and recorded`() = runTest {
        assertEquals(Celebration.WEEK_THREE, newCheck().check(finished, Programs.C25K, 3, 3))
        assertTrue(weekThreeCelebrated)
    }

    @Test
    fun `week 3 is not celebrated once the preference is set`() = runTest {
        weekThreeCelebrated = true
        assertNull(newCheck().check(finished, Programs.C210K, 3, 3))
        assertEquals(0, writes)
    }

    @Test
    fun `a second completed frame returns the first answer without writing again`() = runTest {
        val check = newCheck()
        assertEquals(Celebration.WEEK_THREE, check.check(finished, Programs.C25K, 3, 3))
        assertEquals(Celebration.WEEK_THREE, check.check(finished, Programs.C25K, 3, 3))
        assertEquals(1, writes)
    }

    @Test
    fun `finishing a program records week 3 too`() = runTest {
        assertEquals(Celebration.PROGRAM_FINISHED, newCheck().check(finished, Programs.C25K, 9, 3))
        assertTrue(weekThreeCelebrated)
    }

    @Test
    fun `a skipped run neither celebrates nor records anything`() = runTest {
        assertNull(newCheck().check(skipped, Programs.C25K, 3, 3))
        assertFalse(weekThreeCelebrated)
    }

    @Test
    fun `an ordinary workout leaves the preference alone`() = runTest {
        assertNull(newCheck().check(finished, Programs.C25K, 4, 1))
        assertEquals(0, writes)
    }
}
