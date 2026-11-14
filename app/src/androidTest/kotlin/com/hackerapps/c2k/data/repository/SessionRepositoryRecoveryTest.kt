package com.hackerapps.c2k.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Saving a running workout's progress, and recovering it after the app was closed mid-run. */
@RunWith(AndroidJUnit4::class)
class SessionRepositoryRecoveryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = SessionRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ~0.0001° latitude ≈ 11.1 m; every 5 s that's a steady jog.
    private fun point(sessionId: Long, atSeconds: Long, lat: Double, startedAt: Long) = RoutePointEntity(
        sessionId = sessionId, latitude = lat, longitude = 0.0, altitudeMeters = null, speedMps = null,
        recordedAt = startedAt + atSeconds * 1000
    )

    private suspend fun addRoute(sessionId: Long, startedAt: Long, points: Int) {
        for (i in 0 until points) db.routePointDao().insert(point(sessionId, i * 5L, i * 0.0001, startedAt))
    }

    private suspend fun session(id: Long) = db.sessionDao().findById(id)

    @Test
    fun a_checkpoint_saves_a_running_workouts_progress() = runTest {
        val id = repo.startSession("c25k", 2, 1)
        repo.checkpointSession(id, durationSeconds = 645, distanceMeters = 1234f)
        val saved = session(id)!!
        assertEquals(645, saved.durationSeconds)
        assertEquals(1234f, saved.distanceMeters, 0.001f)
        assertNull(saved.completedAt)
    }

    @Test
    fun a_late_checkpoint_never_overwrites_a_finished_workout() = runTest {
        val id = repo.startSession("c25k", 2, 1)
        repo.finishSession(id, durationSeconds = 1800, distanceMeters = 3000f, completed = true)
        repo.checkpointSession(id, durationSeconds = 1785, distanceMeters = 2950f)
        val saved = session(id)!!
        assertEquals(1800, saved.durationSeconds)
        assertEquals(3000f, saved.distanceMeters, 0.001f)
    }

    @Test
    fun a_closed_run_keeps_its_time_and_gets_its_full_route_distance() = runTest {
        val id = repo.startSession("c25k", 2, 1)
        val startedAt = session(id)!!.startedAt
        // Last checkpoint at 10 min / 1 km, then the route kept recording until the app closed.
        repo.checkpointSession(id, durationSeconds = 600, distanceMeters = 1000f)
        addRoute(id, startedAt, points = 121)  // 120 steps of ~11.1 m ≈ 1334 m

        assertEquals(1, repo.recoverInterruptedSessions(beforeMillis = startedAt + 1))

        val recovered = session(id)!!
        assertEquals(600, recovered.durationSeconds)
        assertEquals(1334f, recovered.distanceMeters, 5f)
        assertEquals(false, recovered.completed)
        assertNull("Still marked as not ended by the runner", recovered.completedAt)
    }

    @Test
    fun a_run_closed_in_its_first_seconds_is_removed() = runTest {
        val id = repo.startSession("c25k", 2, 1)
        assertEquals(0, repo.recoverInterruptedSessions(beforeMillis = session(id)!!.startedAt + 1))
        assertNull(session(id))
    }

    @Test
    fun a_checkpointed_run_without_a_route_is_kept_as_saved() = runTest {
        // Treadmill mode: time but no GPS.
        val id = repo.startSession("c25k", 2, 1)
        repo.checkpointSession(id, durationSeconds = 900, distanceMeters = 0f)
        repo.recoverInterruptedSessions(beforeMillis = session(id)!!.startedAt + 1)
        assertEquals(900, session(id)!!.durationSeconds)
    }

    @Test
    fun a_workout_started_after_launch_is_left_alone() = runTest {
        val id = repo.startSession("c25k", 2, 1)
        val startedAt = session(id)!!.startedAt
        // The cutoff is the launch time; this workout started at or after it.
        assertEquals(0, repo.recoverInterruptedSessions(beforeMillis = startedAt))
        assertEquals(id, session(id)?.id)
    }

    @Test
    fun ended_workouts_are_never_touched() = runTest {
        val finished = repo.startSession("c25k", 2, 1)
        repo.finishSession(finished, durationSeconds = 1800, distanceMeters = 3000f, completed = true)
        val stopped = repo.startSession("c25k", 2, 2)
        repo.finishSession(stopped, durationSeconds = 0, distanceMeters = 0f, completed = false)

        assertEquals(0, repo.recoverInterruptedSessions(beforeMillis = Long.MAX_VALUE))
        assertEquals(3000f, session(finished)!!.distanceMeters, 0.001f)
        assertEquals(stopped, session(stopped)?.id)  // stopped at once by the runner: kept
    }

    @Test
    fun recovering_again_changes_nothing() = runTest {
        val id = repo.startSession("c25k", 2, 1)
        val startedAt = session(id)!!.startedAt
        repo.checkpointSession(id, durationSeconds = 600, distanceMeters = 1000f)
        addRoute(id, startedAt, points = 121)
        repo.recoverInterruptedSessions(beforeMillis = startedAt + 1)
        val first = session(id)

        repo.recoverInterruptedSessions(beforeMillis = startedAt + 1)
        assertEquals(first, session(id))
    }
}
