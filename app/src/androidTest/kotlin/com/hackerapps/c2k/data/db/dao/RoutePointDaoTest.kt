package com.hackerapps.c2k.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePointDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var routeDao: RoutePointDao
    private lateinit var sessionDao: WorkoutSessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        routeDao = db.routePointDao()
        sessionDao = db.sessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun insertSession(): Long = sessionDao.insert(
        WorkoutSessionEntity(
            programId = "c25k",
            week = 1,
            day = 1,
            startedAt = 0L,
            durationSeconds = 600,
            distanceMeters = 1000f,
            completed = true
        )
    )

    private fun point(sessionId: Long, recordedAt: Long, lat: Double = 40.0, lon: Double = -3.0) =
        RoutePointEntity(
            sessionId = sessionId,
            latitude = lat,
            longitude = lon,
            altitudeMeters = null,
            speedMps = null,
            recordedAt = recordedAt
        )

    @Test
    fun getRoute_orders_points_by_recordedAt_ascending() = runTest {
        val sessionId = insertSession()
        routeDao.insert(point(sessionId, recordedAt = 3000L))
        routeDao.insert(point(sessionId, recordedAt = 1000L))
        routeDao.insert(point(sessionId, recordedAt = 2000L))

        val route = routeDao.getRoute(sessionId)
        assertEquals(listOf(1000L, 2000L, 3000L), route.map { it.recordedAt })
    }

    @Test
    fun getRoute_only_returns_points_for_the_given_session() = runTest {
        val session1 = insertSession()
        val session2 = insertSession()
        routeDao.insert(point(session1, recordedAt = 1000L))
        routeDao.insert(point(session2, recordedAt = 2000L))

        val route = routeDao.getRoute(session1)
        assertEquals(1, route.size)
        assertEquals(session1, route.first().sessionId)
    }

    @Test
    fun insertAll_inserts_every_point() = runTest {
        val sessionId = insertSession()
        routeDao.insertAll(
            listOf(
                point(sessionId, recordedAt = 1000L),
                point(sessionId, recordedAt = 2000L),
                point(sessionId, recordedAt = 3000L)
            )
        )

        assertEquals(3, routeDao.countForSession(sessionId))
    }

    @Test
    fun countForSession_is_zero_when_no_points_exist() = runTest {
        val sessionId = insertSession()
        assertEquals(0, routeDao.countForSession(sessionId))
    }

    @Test
    fun observeRoute_reflects_inserted_points() = runTest {
        val sessionId = insertSession()
        routeDao.insert(point(sessionId, recordedAt = 1000L))

        val route = routeDao.observeRoute(sessionId).first()
        assertEquals(1, route.size)
    }

    @Test
    fun deleting_a_session_cascades_to_its_route_points() = runTest {
        val sessionId = insertSession()
        routeDao.insert(point(sessionId, recordedAt = 1000L))
        routeDao.insert(point(sessionId, recordedAt = 2000L))

        sessionDao.deleteById(sessionId)

        assertEquals(0, routeDao.countForSession(sessionId))
    }
}
