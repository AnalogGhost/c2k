package com.hackerapps.c2k.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.repository.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real Android 14+ failure: Physical activity denied and no GPS, so the OS refuses the
 * workout's foreground service. Needs the permission genuinely denied, which only holds when
 * this class runs before anything in the same run grants it; otherwise it's skipped.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutServiceStartRejectedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        assumeTrue("Needs Android 14+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        assumeTrue(
            "Physical activity was already granted earlier in this run",
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        )
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        WorkoutService.testSessionRepositoryOverride = SessionRepository(db)
        WorkoutService.startRejected.value = false
    }

    @After
    fun tearDown() {
        WorkoutService.testSessionRepositoryOverride = null
        if (::db.isInitialized) db.close()
    }

    @Test
    fun a_refused_start_is_reported_and_the_app_survives() {
        context.startForegroundService(
            Intent(context, WorkoutService::class.java).apply {
                action = WorkoutService.ACTION_START
                putExtra(WorkoutService.EXTRA_PROGRAM_ID, Programs.ID_C25K)
                putExtra(WorkoutService.EXTRA_WEEK, 1)
                putExtra(WorkoutService.EXTRA_DAY, 1)
                putExtra(WorkoutService.EXTRA_TREADMILL_MODE, true)  // no location type either
            }
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (!WorkoutService.startRejected.value && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("The refusal should be reported", WorkoutService.startRejected.value)
        assertFalse(WorkoutService.isRunning.value)
        // Past Android's 10 s foreground deadline: the process must still be alive (this test
        // runs in it), so the abandoned start didn't crash the app.
        Thread.sleep(12_000)
        assertEquals(0, runBlocking { db.sessionDao().getAll().size })
    }
}
