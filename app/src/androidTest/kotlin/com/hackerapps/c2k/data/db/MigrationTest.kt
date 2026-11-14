package com.hackerapps.c2k.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades databases built from every exported schema version to the current one through
 * AppDatabase.MIGRATIONS, then opens them exactly as the app does on launch. Room used to fall
 * back to deleting everything on a failed upgrade; this proves history survives instead.
 *
 * When adding version N: seed rows for it in a new createDatabase(N) test if its tables changed,
 * and the loop below already upgrades it.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun history_in_the_oldest_schema_survives_upgrading_to_the_current_one() {
        helper.createDatabase(TEST_DB, 1).apply {
            // Version 1 columns, exactly as the first release created them.
            execSQL(
                "INSERT INTO workout_sessions (id, programId, week, day, startedAt, completedAt, " +
                    "durationSeconds, distanceMeters, completed) " +
                    "VALUES (7, 'c25k', 3, 2, 1700000000000, 1700001800000, 1800, 4321.5, 1)"
            )
            execSQL(
                "INSERT INTO workout_sessions (id, programId, week, day, startedAt, completedAt, " +
                    "durationSeconds, distanceMeters, completed) " +
                    "VALUES (8, 'c25k', 3, 3, 1700100000000, NULL, 600, 0.0, 0)"
            )
            execSQL(
                "INSERT INTO route_points (sessionId, latitude, longitude, altitudeMeters, speedMps, recordedAt) " +
                    "VALUES (7, 51.5, -0.12, 11.0, 2.8, 1700000001000)"
            )
            execSQL(
                "INSERT INTO route_points (sessionId, latitude, longitude, altitudeMeters, speedMps, recordedAt) " +
                    "VALUES (7, 51.5001, -0.1201, NULL, NULL, 1700000002000)"
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, DATABASE_VERSION, true, *AppDatabase.MIGRATIONS).close()

        // The production configuration, as on launch: it must open the upgraded file (Room checks
        // its identity hash against the current entities) and read every row back.
        val db = AppDatabase.create(context, TEST_DB)
        try {
            runBlocking {
                val sessions = db.sessionDao().getAll().sortedBy { it.id }
                assertEquals(listOf(7L, 8L), sessions.map { it.id })
                val done = sessions.first()
                assertEquals("c25k", done.programId)
                assertEquals(3, done.week)
                assertEquals(2, done.day)
                assertEquals(1800, done.durationSeconds)
                assertEquals(4321.5f, done.distanceMeters, 0.001f)
                assertTrue(done.completed)
                assertEquals(1700001800000L, done.completedAt)
                assertEquals(null, sessions[1].completedAt)

                val route = db.routePointDao().getRoute(7)
                assertEquals(2, route.size)
                assertEquals(11.0, route.first().altitudeMeters!!, 0.0001)
                assertEquals(null, route.last().speedMps)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun every_past_version_upgrades_to_a_database_the_app_can_open() {
        for (version in 1..DATABASE_VERSION) {
            context.deleteDatabase(TEST_DB)
            helper.createDatabase(TEST_DB, version).close()
            helper.runMigrationsAndValidate(TEST_DB, DATABASE_VERSION, true, *AppDatabase.MIGRATIONS).close()
            val db = AppDatabase.create(context, TEST_DB)
            try {
                runBlocking { assertEquals("v$version", 0, db.sessionDao().getAll().size) }
            } finally {
                db.close()
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
