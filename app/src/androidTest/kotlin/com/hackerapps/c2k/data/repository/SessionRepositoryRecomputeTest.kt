package com.hackerapps.c2k.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRepositoryRecomputeTest {

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

    private suspend fun insertSession(distanceMeters: Float): Long = db.sessionDao().insert(
        WorkoutSessionEntity(
            programId = "C25K",
            week = 1,
            day = 1,
            startedAt = 0L,
            durationSeconds = 1800,
            distanceMeters = distanceMeters,
            completed = true
        )
    )

    // ~0.0001° latitude ≈ 11.1 m; at 5 s intervals that's a normal running pace.
    private fun point(sessionId: Long, atSeconds: Long, lat: Double) = RoutePointEntity(
        sessionId = sessionId,
        latitude = lat,
        longitude = 0.0,
        altitudeMeters = null,
        speedMps = null,
        recordedAt = atSeconds * 1000
    )

    @Test
    fun recompute_strips_teleporting_distance_from_stored_session() = runTest {
        // The issue #30 shape: a session whose stored distance was inflated by a faulty GPS
        // lock, with the spike still present in its recorded route.
        val id = insertSession(distanceMeters = 584_000f)
        db.routePointDao().insertAll(
            listOf(
                point(id, 0, 50.0000),
                point(id, 5, 50.0001),
                point(id, 10, 51.0000), // spike: ~111 km in 5 s
                point(id, 15, 50.0002),
                point(id, 20, 50.0003)
            )
        )

        repo.recomputeSessionDistances()

        // Only the two clean segments survive, ~11.1 m each
        assertEquals(22.2f, db.sessionDao().findById(id)!!.distanceMeters, 1f)
    }

    @Test
    fun recompute_leaves_sessions_without_a_route_untouched() = runTest {
        // Treadmill mode / GPS off: no route points, distance must not be zeroed
        val id = insertSession(distanceMeters = 5_000f)

        repo.recomputeSessionDistances()

        assertEquals(5_000f, db.sessionDao().findById(id)!!.distanceMeters, 0.01f)
    }

    @Test
    fun recompute_preserves_clean_route_distance() = runTest {
        val id = insertSession(distanceMeters = 44.5f)
        db.routePointDao().insertAll(
            (0..4).map { i -> point(id, i * 5L, 50.0 + i * 0.0001) }
        )

        repo.recomputeSessionDistances()

        assertEquals(44.5f, db.sessionDao().findById(id)!!.distanceMeters, 1f)
    }
}
