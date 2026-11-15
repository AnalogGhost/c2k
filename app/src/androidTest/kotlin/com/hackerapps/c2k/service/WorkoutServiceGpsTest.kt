package com.hackerapps.c2k.service

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.prefs.CueStyle
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
import com.hackerapps.c2k.engine.WorkoutState
import com.hackerapps.c2k.engine.tts.CuePlayer
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import com.hackerapps.c2k.location.LocationProvider
import com.hackerapps.c2k.location.LocationUpdate
import com.hackerapps.c2k.ui.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * What the workout service does with GPS: records the route, saves the distance with the run,
 * speaks each split, and keeps the GPS indicator current. GPS and the voice are fakes, so the
 * test sets the distance and sees exactly which cues were played; everything between them is the
 * real service, engine and database code.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutServiceGpsTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = UserPreferences(context)
    private lateinit var db: AppDatabase
    private lateinit var gps: FakeLocationProvider
    private lateinit var voice: RecordingCuePlayer
    private var connection: ServiceConnection? = null
    private var serviceWasStarted = false
    // Android 14+ only lets a foreground service use location while the app is in use; the open
    // activity stands in for the runner having just tapped Start.
    private var activity: ActivityScenario<MainActivity>? = null

    private data class SavedPrefs(
        val tts: Boolean, val gps: Boolean, val treadmill: Boolean,
        val cueStyle: CueStyle, val distanceCues: Boolean, val split: Int,
    )
    private lateinit var savedPrefs: SavedPrefs

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        gps = FakeLocationProvider()
        voice = RecordingCuePlayer()
        WorkoutService.testSessionRepositoryOverride = SessionRepository(db)
        WorkoutService.testLocationProviderOverride = gps
        WorkoutService.testCuePlayerOverride = voice
        WorkoutService.testWorkoutDayOverride = longDay
        runBlocking {
            savedPrefs = SavedPrefs(
                prefs.ttsEnabled.first(), prefs.gpsEnabled.first(), prefs.treadmillMode.first(),
                prefs.cueStyle.first(), prefs.distanceCues.first(), prefs.distanceCueSplitMeters.first(),
            )
            prefs.setTtsEnabled(true)
            prefs.setGpsEnabled(true)
            prefs.setTreadmillMode(false)
            prefs.setCueStyle(CueStyle.VOICE)
            prefs.setDistanceCues(true)
            prefs.setDistanceCueSplitMeters(500)
        }
        activity = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (serviceWasStarted) {
            sendAction(WorkoutService.ACTION_STOP)
            waitUntil { !WorkoutService.isRunning.value }
        }
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        waitUntil(timeoutMs = 5_000) { !workoutServiceIsAlive() }
        Thread.sleep(500)
        activity?.close()
        WorkoutService.testWorkoutDayOverride = null
        WorkoutService.testSessionRepositoryOverride = null
        WorkoutService.testLocationProviderOverride = null
        WorkoutService.testCuePlayerOverride = null
        runBlocking {
            with(savedPrefs) {
                prefs.setTtsEnabled(tts)
                prefs.setGpsEnabled(gps)
                prefs.setTreadmillMode(treadmill)
                prefs.setCueStyle(cueStyle)
                prefs.setDistanceCues(distanceCues)
                prefs.setDistanceCueSplitMeters(split)
            }
        }
        db.close()
    }

    @Test
    fun each_split_is_spoken_once_as_the_distance_passes_it() {
        startAndWaitForGps()

        gps.moveTo(400f)
        assertTrue(waitUntil { routePoints() == 1 })
        assertEquals("No split before 500 m", emptyList<Int>(), splitsSpoken())

        gps.moveTo(520f)
        assertTrue("Expected the 500 m split", waitUntil { splitsSpoken() == listOf(500) })

        gps.moveTo(700f)
        gps.moveTo(1010f)
        assertTrue("Expected the 1 km split", waitUntil { splitsSpoken() == listOf(500, 1000) })
        assertEquals("Every fix is recorded", 4, routePoints())
        assertTrue(
            "Split cues queue behind interval cues instead of cutting them off",
            voice.announced.filter { it.first is TtsAnnouncement.DistanceSplit }.all { it.second }
        )
    }

    @Test
    fun no_split_is_spoken_when_distance_cues_are_off() {
        runBlocking { prefs.setDistanceCues(false) }
        startAndWaitForGps()

        gps.moveTo(1200f)
        assertTrue("The fix should still be recorded", waitUntil { routePoints() == 1 })
        Thread.sleep(500)
        assertEquals(emptyList<Int>(), splitsSpoken())
    }

    @Test
    fun pausing_saves_the_distance_so_far() {
        val binder = startAndWaitForGps()

        gps.moveTo(300f)
        gps.moveTo(640f)
        assertTrue(waitUntil { routePoints() == 2 })

        sendAction(WorkoutService.ACTION_PAUSE)
        assertTrue(waitUntil { binder.getWorkoutState().value is WorkoutState.Paused })
        assertTrue("Pausing should stop GPS collection", waitUntil { gps.paused })
        assertTrue(
            "Expected the paused run's 640 m saved, got ${session()?.distanceMeters}",
            waitUntil { session()?.distanceMeters == 640f }
        )

        sendAction(WorkoutService.ACTION_RESUME)
        assertTrue(waitUntil { binder.getWorkoutState().value is WorkoutState.Active })
        assertFalse("Resuming should restart GPS collection", gps.paused)
    }

    @Test
    fun a_finished_run_is_saved_with_its_gps_distance() {
        WorkoutService.testWorkoutDayOverride = WorkoutDay(
            week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 4))
        )
        val binder = startAndWaitForGps()

        gps.moveTo(23.5f)
        assertTrue(waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Completed })
        assertTrue(
            "Expected a completed run of 23.5 m, got ${session()}",
            waitUntil(10_000) { session()?.let { it.completed && it.distanceMeters == 23.5f } == true }
        )
        assertTrue("GPS should be released when the run ends", waitUntil(10_000) { gps.stopped })
    }

    @Test
    fun the_gps_indicator_follows_location_being_switched_off_and_on() {
        val binder = startAndWaitForGps()
        assertTrue(binder.getGpsActive().value)

        gps.availability.value = false
        assertTrue("Indicator should go off", waitUntil { !binder.getGpsActive().value })
        gps.availability.value = true
        assertTrue("Indicator should come back", waitUntil { binder.getGpsActive().value })
    }

    @Test
    fun treadmill_mode_uses_no_gps_and_speaks_no_splits() {
        runBlocking { prefs.setTreadmillMode(true) }
        val binder = bind()
        startWorkout()
        assertTrue(waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Active })

        assertFalse("GPS must not start in treadmill mode", gps.started)
        assertFalse(binder.getGpsActive().value)
        Thread.sleep(500)
        assertEquals(emptyList<Int>(), splitsSpoken())
    }

    // ── Fakes ────────────────────────────────────────────────────────────────────

    /** GPS whose distance the test sets; each [moveTo] also delivers one fix, as real GPS does. */
    private class FakeLocationProvider : LocationProvider {
        private val fixes = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)
        override val updates = fixes
        override val availability = MutableStateFlow(true)
        @Volatile override var totalDistanceMeters = 0f
            private set
        override val hasValidFix get() = totalDistanceMeters > 0f
        @Volatile var started = false
        @Volatile var paused = false
        @Volatile var stopped = false
        val listening get() = fixes.subscriptionCount.value > 0

        fun moveTo(meters: Float) {
            totalDistanceMeters = meters
            // ~111 km per degree of latitude.
            check(fixes.tryEmit(LocationUpdate(latitude = meters / 111_000.0, longitude = 0.0, altitudeMeters = null, speedMps = null)))
        }

        override fun start() { started = true; paused = false }
        override fun stop() { stopped = true }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
    }

    /** Records each cue and whether it was queued (true) or interrupted what was playing (false). */
    private class RecordingCuePlayer : CuePlayer {
        val announced = CopyOnWriteArrayList<Pair<TtsAnnouncement, Boolean>>()
        override val isAvailable = true
        override val speaking = MutableStateFlow(false)
        override fun announce(announcement: TtsAnnouncement, queueAdd: Boolean) {
            announced += announcement to queueAdd
        }
        override fun shutdown() {}
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private val longDay = WorkoutDay(week = 1, day = 1, intervals = listOf(Interval(IntervalType.RUN, 600)))

    private fun splitsSpoken(): List<Int> =
        voice.announced.mapNotNull { (it.first as? TtsAnnouncement.DistanceSplit)?.meters }

    private fun session() = runBlocking { db.sessionDao().getAll().singleOrNull() }

    private fun routePoints(): Int = session()?.let { runBlocking { db.routePointDao().countForSession(it.id) } } ?: 0

    /** Starts a workout and waits until it's running and listening for GPS fixes. */
    private fun startAndWaitForGps(): WorkoutService.LocalBinder {
        val binder = bind()
        startWorkout()
        assertTrue("Workout should start", waitUntil(10_000) { binder.getWorkoutState().value is WorkoutState.Active })
        assertTrue("The service should use the GPS", waitUntil { gps.started && gps.listening })
        return binder
    }

    private fun startWorkout() {
        serviceWasStarted = true
        context.startForegroundService(
            Intent(context, WorkoutService::class.java).apply {
                action = WorkoutService.ACTION_START
                putExtra(WorkoutService.EXTRA_PROGRAM_ID, Programs.ID_C25K)
                putExtra(WorkoutService.EXTRA_WEEK, 1)
                putExtra(WorkoutService.EXTRA_DAY, 1)
            }
        )
    }

    private fun sendAction(action: String) {
        context.startService(Intent(context, WorkoutService::class.java).setAction(action))
    }

    private fun bind(): WorkoutService.LocalBinder {
        val latch = CountDownLatch(1)
        var binder: WorkoutService.LocalBinder? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service as WorkoutService.LocalBinder
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        context.bindService(Intent(context, WorkoutService::class.java), conn, Context.BIND_AUTO_CREATE)
        connection = conn
        assertTrue("Could not bind to WorkoutService", latch.await(5, TimeUnit.SECONDS))
        return binder!!
    }

    @Suppress("DEPRECATION") // Still returns the caller's own services, which is all this needs.
    private fun workoutServiceIsAlive(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == WorkoutService::class.java.name }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
