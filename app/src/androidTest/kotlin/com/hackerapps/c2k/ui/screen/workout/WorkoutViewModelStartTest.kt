package com.hackerapps.c2k.ui.screen.workout

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.service.WorkoutService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The workout screen asks its ViewModel to start the workout every time its composition is
 * created, including after a rotation or theme change and after process death. Only the first
 * request may start a workout; before the fix, finishing a workout and then rotating the phone
 * silently started another one.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutViewModelStartTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = UserPreferences(app)
    private lateinit var db: AppDatabase
    private val stores = mutableListOf<ViewModelStore>()
    private var savedLastProgramId: String? = null

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        WorkoutService.testSessionRepositoryOverride = SessionRepository(db)
        WorkoutService.testWorkoutDayOverride =
            WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 1)))
        runBlocking {
            savedLastProgramId = prefs.lastProgramId.first()
        }
    }

    @After
    fun tearDown() {
        onMain { stores.forEach { it.clear() } }  // unbinds each ViewModel from the service
        if (WorkoutService.isRunning.value) {
            app.startService(Intent(app, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP))
        }
        waitUntil(5_000) { !WorkoutService.isRunning.value && !workoutServiceIsAlive() }
        Thread.sleep(500)
        WorkoutService.testSessionRepositoryOverride = null
        WorkoutService.testWorkoutDayOverride = null
        // startWorkout() records the program as the last one used; put back what was there.
        runBlocking { savedLastProgramId?.let { prefs.setLastProgramId(it) } }
        db.close()
    }

    @Test
    fun a_repeated_start_after_completion_does_not_start_another_workout() {
        val vm = newViewModel(SavedStateHandle())
        assertEquals(StartDecision.START, onMain { vm.startWorkout(Programs.ID_C25K, 1, 1) })
        assertTrue("Workout should complete", waitUntil(10_000) { vm.workoutState.value is WorkoutState.Completed })
        assertTrue(waitUntil(10_000) { !WorkoutService.isRunning.value })

        // What the recreated screen does after a rotation on the Completed screen.
        assertEquals(StartDecision.RECONNECT, onMain { vm.startWorkout(Programs.ID_C25K, 1, 1) })

        Thread.sleep(2_000)
        assertFalse("No second workout may start", WorkoutService.isRunning.value)
        assertEquals(1, sessionCount())
        assertTrue(vm.workoutState.value is WorkoutState.Completed)
    }

    @Test
    fun a_repeated_start_during_the_workout_reconnects_to_it() {
        WorkoutService.testWorkoutDayOverride =
            WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 600)))
        val vm = newViewModel(SavedStateHandle())
        assertEquals(StartDecision.START, onMain { vm.startWorkout(Programs.ID_C25K, 1, 1) })
        assertTrue(waitUntil(10_000) { vm.workoutState.value is WorkoutState.Active })

        assertEquals(StartDecision.RECONNECT, onMain { vm.startWorkout(Programs.ID_C25K, 1, 1) })
        Thread.sleep(1_000)
        assertTrue(vm.workoutState.value is WorkoutState.Active)
        assertEquals(1, sessionCount())
    }

    @Test
    fun a_viewmodel_restored_after_process_death_does_not_restart_the_lost_workout() {
        assertFalse(WorkoutService.isRunning.value)
        val restored = newViewModel(SavedStateHandle(mapOf(WorkoutViewModel.KEY_STARTED to true)))

        assertEquals(StartDecision.LEAVE, onMain { restored.startWorkout(Programs.ID_C25K, 1, 1) })
        Thread.sleep(1_500)
        assertFalse(WorkoutService.isRunning.value)
        assertEquals(0, sessionCount())
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun newViewModel(handle: SavedStateHandle): WorkoutViewModel = onMain {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutViewModel(app, handle) as T
        }
        ViewModelProvider(store, factory)[WorkoutViewModel::class.java]
    }

    private fun sessionCount() = runBlocking { db.sessionDao().getAll().size }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    @Suppress("DEPRECATION") // Still returns the caller's own services, which is all this needs.
    private fun workoutServiceIsAlive(): Boolean =
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == WorkoutService::class.java.name }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
