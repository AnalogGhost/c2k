package com.hackerapps.c2k.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.backup.Backup
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRepositoryBackupTest {

    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before
    fun createDbs() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDbs() {
        source.close()
        target.close()
    }

    private suspend fun AppDatabase.addSession(
        startedAt: Long, completed: Boolean = true, completedAt: Long? = startedAt + 1_800_000,
        programId: String = "C25K", points: Int = 0
    ): Long {
        val id = sessionDao().insert(
            WorkoutSessionEntity(
                programId = programId, week = 2, day = 3, startedAt = startedAt, completedAt = completedAt,
                durationSeconds = 1800, distanceMeters = 3210.5f, completed = completed
            )
        )
        routePointDao().insertAll((0 until points).map {
            RoutePointEntity(
                sessionId = id, latitude = 51.5 + it * 0.0001, longitude = -0.12,
                altitudeMeters = if (it == 0) null else 20.0, speedMps = if (it == 0) null else 2.5f,
                recordedAt = startedAt + it * 5_000L
            )
        })
        return id
    }

    /** Export from [source], through the file format, into [target]. */
    private suspend fun transfer(): Int =
        SessionRepository(target).importBackup(Backup.decode(SessionRepository(source).exportBackup().encode()))

    @Test
    fun a_history_survives_export_and_import() = runTest {
        source.addSession(startedAt = 1_000_000L, points = 3)
        source.addSession(startedAt = 2_000_000L, completed = false, completedAt = null)

        assertEquals(2, transfer())

        val sessions = target.sessionDao().getAll().sortedBy { it.startedAt }
        assertEquals(2, sessions.size)
        val first = sessions[0]
        assertEquals("C25K", first.programId)
        assertEquals(2, first.week)
        assertEquals(3, first.day)
        assertEquals(2_800_000L, first.completedAt)
        assertEquals(1800, first.durationSeconds)
        assertEquals(3210.5f, first.distanceMeters)
        assertTrue(first.completed)
        // A run the app was closed during keeps its missing end time.
        assertNull(sessions[1].completedAt)

        val route = target.routePointDao().getRoute(first.id)
        assertEquals(3, route.size)
        assertNull(route[0].altitudeMeters)
        assertNull(route[0].speedMps)
        assertEquals(20.0, route[1].altitudeMeters!!, 0.0)
        assertEquals(2.5f, route[1].speedMps!!, 0f)
        assertEquals(1_005_000L, route[1].recordedAt)
    }

    @Test
    fun importing_twice_adds_nothing_the_second_time() = runTest {
        source.addSession(startedAt = 1_000_000L, points = 2)
        assertEquals(1, transfer())
        assertEquals(0, transfer())
        assertEquals(1, target.sessionDao().getAll().size)
        assertEquals(2, target.routePointDao().countForSession(target.sessionDao().getAll()[0].id))
    }

    @Test
    fun import_merges_with_workouts_already_here() = runTest {
        source.addSession(startedAt = 1_000_000L)
        source.addSession(startedAt = 2_000_000L)
        target.addSession(startedAt = 2_000_000L)
        target.addSession(startedAt = 3_000_000L)

        assertEquals(1, transfer())
        assertEquals(listOf(1_000_000L, 2_000_000L, 3_000_000L),
            target.sessionDao().getAll().map { it.startedAt }.sorted())
    }

    @Test
    fun workouts_from_an_unknown_program_are_skipped() = runTest {
        source.addSession(startedAt = 1_000_000L, programId = "SOMETHING_NEW")
        source.addSession(startedAt = 2_000_000L)
        assertEquals(1, transfer())
    }

    @Test
    fun a_file_that_is_not_a_backup_is_rejected() {
        assertThrows(Backup.InvalidBackupException::class.java) { Backup.decode("not json") }
        assertThrows(Backup.InvalidBackupException::class.java) { Backup.decode("""{"sessions": []}""") }
        assertThrows(Backup.InvalidBackupException::class.java) {
            Backup.decode("""{"format": "c2k-backup", "version": 1, "sessions": [{"programId": "C25K"}]}""")
        }
    }

    @Test
    fun a_backup_from_a_newer_version_is_rejected() {
        assertThrows(Backup.NewerBackupException::class.java) {
            Backup.decode("""{"format": "c2k-backup", "version": 2, "sessions": []}""")
        }
    }

    @Test
    fun a_backup_written_by_the_ios_app_is_read() {
        // The iOS app writes the same format; this is its output for one workout.
        val backup = Backup.decode(
            """
            {"format":"c2k-backup","version":1,"exportedAt":1763600000000,"sessions":[
              {"programId":"C210K","week":1,"day":2,"startedAt":1763500000000,"completedAt":1763501800000,
               "durationSeconds":1800,"distanceMeters":4000.25,"completed":true,
               "route":[{"latitude":51.5,"longitude":-0.12,"altitudeMeters":null,"speedMps":3.1,"recordedAt":1763500005000}]}
            ]}
            """
        )
        val session = backup.sessions.single()
        assertEquals("C210K", session.programId)
        assertEquals(1_763_501_800_000L, session.completedAt)
        assertEquals(3.1, session.route.single().speedMps!!, 1e-9)
    }
}
