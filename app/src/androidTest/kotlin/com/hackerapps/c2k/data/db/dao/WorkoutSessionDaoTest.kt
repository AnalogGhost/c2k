package com.hackerapps.c2k.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutSessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.sessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun session(
        programId: String = "c25k",
        week: Int = 1,
        day: Int = 1,
        startedAt: Long = 0L,
        durationSeconds: Int = 600,
        completed: Boolean = true
    ) = WorkoutSessionEntity(
        programId = programId,
        week = week,
        day = day,
        startedAt = startedAt,
        durationSeconds = durationSeconds,
        distanceMeters = 1000f,
        completed = completed
    )

    @Test
    fun insert_and_findById_returns_the_same_session() = runTest {
        val id = dao.insert(session(programId = "c25k", week = 2, day = 3))
        val loaded = dao.findById(id)
        assertEquals("c25k", loaded?.programId)
        assertEquals(2, loaded?.week)
        assertEquals(3, loaded?.day)
    }

    @Test
    fun findById_returns_null_for_unknown_id() = runTest {
        assertNull(dao.findById(999L))
    }

    @Test
    fun observeAll_orders_sessions_newest_first() = runTest {
        dao.insert(session(startedAt = 1000L))
        dao.insert(session(startedAt = 3000L))
        dao.insert(session(startedAt = 2000L))

        val all = dao.observeAll().first()
        assertEquals(listOf(3000L, 2000L, 1000L), all.map { it.startedAt })
    }

    @Test
    fun observeCompletedDays_only_includes_completed_sessions_for_the_given_program() = runTest {
        dao.insert(session(programId = "c25k", week = 1, day = 1, completed = true))
        dao.insert(session(programId = "c25k", week = 1, day = 2, completed = false))
        dao.insert(session(programId = "c210k", week = 1, day = 1, completed = true))

        val completed = dao.observeCompletedDays("c25k").first()
        assertEquals(setOf(1 to 1), completed.map { it.week to it.day }.toSet())
    }

    @Test
    fun observeCompletedDays_dedupes_multiple_sessions_for_the_same_day() = runTest {
        dao.insert(session(programId = "c25k", week = 1, day = 1, startedAt = 100L, completed = true))
        dao.insert(session(programId = "c25k", week = 1, day = 1, startedAt = 200L, completed = true))

        val completed = dao.observeCompletedDays("c25k").first()
        assertEquals(1, completed.size)
    }

    @Test
    fun getBestByDay_returns_the_fastest_completed_session() = runTest {
        dao.insert(session(week = 1, day = 1, durationSeconds = 700, completed = true))
        dao.insert(session(week = 1, day = 1, durationSeconds = 500, completed = true))
        dao.insert(session(week = 1, day = 1, durationSeconds = 600, completed = true))

        val best = dao.getBestByDay("c25k", 1, 1)
        assertEquals(500, best?.durationSeconds)
    }

    @Test
    fun getBestByDay_ignores_incomplete_sessions() = runTest {
        dao.insert(session(week = 1, day = 1, durationSeconds = 100, completed = false))
        dao.insert(session(week = 1, day = 1, durationSeconds = 500, completed = true))

        val best = dao.getBestByDay("c25k", 1, 1)
        assertEquals(500, best?.durationSeconds)
    }

    @Test
    fun getBestByDay_returns_null_when_no_completed_sessions_exist() = runTest {
        dao.insert(session(week = 1, day = 1, completed = false))
        assertNull(dao.getBestByDay("c25k", 1, 1))
    }

    @Test
    fun update_modifies_an_existing_session() = runTest {
        val id = dao.insert(session(durationSeconds = 600))
        val existing = dao.findById(id)!!
        dao.update(existing.copy(durationSeconds = 900, completed = true))

        val updated = dao.findById(id)
        assertEquals(900, updated?.durationSeconds)
    }

    @Test
    fun deleteById_removes_only_that_session() = runTest {
        val id1 = dao.insert(session(startedAt = 1L))
        val id2 = dao.insert(session(startedAt = 2L))

        dao.deleteById(id1)

        assertNull(dao.findById(id1))
        assertTrue(dao.findById(id2) != null)
    }

    @Test
    fun deleteByProgramId_removes_only_sessions_for_that_program() = runTest {
        dao.insert(session(programId = "c25k"))
        dao.insert(session(programId = "c25k"))
        dao.insert(session(programId = "c210k"))

        dao.deleteByProgramId("c25k")

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals("c210k", remaining.first().programId)
    }
}
