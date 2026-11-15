package com.hackerapps.c2k

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app recovers workouts cut off by Android or a crash when it next starts. Instrumentation
 * can't kill and relaunch its own process, so this plants an interrupted workout in the app's
 * database and runs the app's start-up again on the live instance. The planted rows use their
 * own program ID and are removed afterwards.
 */
@RunWith(AndroidJUnit4::class)
class LaunchRecoveryTest {

    private val app: C2KApp = ApplicationProvider.getApplicationContext()
    private val sessions = app.database.sessionDao()
    private val route = app.database.routePointDao()
    private val programId = "launch-recovery-test"

    @Before
    @After
    fun removePlantedWorkouts() = runBlocking { sessions.deleteByProgramId(programId) }

    private fun interruptedWorkout(durationSeconds: Int, distanceMeters: Float, startedAt: Long) = runBlocking {
        sessions.insert(
            WorkoutSessionEntity(
                programId = programId, week = 1, day = 1, startedAt = startedAt,
                completedAt = null, durationSeconds = durationSeconds,
                distanceMeters = distanceMeters, completed = false,
            )
        )
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test
    fun starting_the_app_recovers_a_workout_it_was_closed_during() {
        val startedAt = System.currentTimeMillis() - 60 * 60 * 1000
        // Last saved at 10 min / 1 km; the route kept recording to ~1.33 km before the app died.
        val cutOff = interruptedWorkout(durationSeconds = 600, distanceMeters = 1000f, startedAt = startedAt)
        runBlocking {
            for (i in 0..120) {
                route.insert(
                    RoutePointEntity(
                        sessionId = cutOff, latitude = i * 0.0001, longitude = 0.0,
                        altitudeMeters = null, speedMps = null, recordedAt = startedAt + i * 5_000L,
                    )
                )
            }
        }
        // Closed in its first seconds, before anything was saved: nothing worth keeping.
        val empty = interruptedWorkout(durationSeconds = 0, distanceMeters = 0f, startedAt = startedAt)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app)
        }

        assertTrue(
            "Expected the route's full distance restored at start-up",
            waitUntil { runBlocking { sessions.findById(cutOff) }!!.distanceMeters > 1300f }
        )
        assertTrue("Expected the empty workout removed", waitUntil { runBlocking { sessions.findById(empty) } == null })
        val recovered = runBlocking { sessions.findById(cutOff) }!!
        assertEquals(600, recovered.durationSeconds)
        assertNull(recovered.completedAt)
    }
}
