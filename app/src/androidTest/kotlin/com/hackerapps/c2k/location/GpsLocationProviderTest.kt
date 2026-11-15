package com.hackerapps.c2k.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.hackerapps.c2k.ui.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GPS tracking through a mock GPS provider, so the test controls when Location is on and where
 * the runner is. Before the fix, starting with Location off never registered for updates at all,
 * so switching it on mid-run still recorded 0 km.
 */
@RunWith(AndroidJUnit4::class)
class GpsLocationProviderTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val gps = LocationManager.GPS_PROVIDER
    private var provider: GpsLocationProvider? = null
    // Android delivers no fixes to an app in the background without background-location access.
    // The real app tracks from a foreground service; here, keeping its activity open makes the
    // test process foreground the same way.
    private var activity: ActivityScenario<MainActivity>? = null

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    private fun addMockGps() = locationManager.addTestProvider(
        gps, false, false, false, false, true, true, true,
        ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE
    )

    @Before
    fun installMockGps() {
        shell("appops set ${context.packageName} android:mock_location allow")
        Thread.sleep(300)
        activity = ActivityScenario.launch(MainActivity::class.java)
        addMockGps()
    }

    @After
    fun removeMockGps() {
        provider?.stop()
        activity?.close()
        runCatching { locationManager.removeTestProvider(gps) }
        shell("appops set ${context.packageName} android:mock_location default")
    }

    private fun fix(latitude: Double) = Location(gps).apply {
        this.latitude = latitude
        longitude = 0.0
        accuracy = 4f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    /**
     * Sets the mock GPS on or off before the provider under test starts, and waits until the
     * platform reports it. On some emulators (CI's API 34 image, in a full suite run) the switch
     * is ignored: re-sending it didn't help, so the mock provider is reinstalled once. If the
     * platform still won't apply it, the test is skipped: that's the emulator, not the app.
     */
    private fun setGpsEnabled(enabled: Boolean) {
        fun applied() = waitUntil(5_000) {
            locationManager.setTestProviderEnabled(gps, enabled)
            locationManager.isProviderEnabled(gps) == enabled
        }
        if (applied()) return
        runCatching { locationManager.removeTestProvider(gps) }
        addMockGps()
        assumeTrue(
            "This emulator won't switch the mock GPS ${if (enabled) "on" else "off"}; skipping",
            applied()
        )
    }

    @Test
    fun switching_location_on_after_the_start_still_tracks_the_run() {
        setGpsEnabled(false)
        val gpsProvider = GpsLocationProvider(context).also { provider = it }
        gpsProvider.start()
        assertFalse("Location is off at the start", gpsProvider.availability.value)

        locationManager.setTestProviderEnabled(gps, true)
        assertTrue("Availability should follow Location being switched on", waitUntil { gpsProvider.availability.value })

        // Two fixes ~11 m apart, a jogging pace apart in time.
        locationManager.setTestProviderLocation(gps, fix(51.5))
        assertTrue("The first fix should be delivered", waitUntil { gpsProvider.hasValidFix })
        Thread.sleep(2_500)
        locationManager.setTestProviderLocation(gps, fix(51.5001))
        assertTrue(
            "Expected distance once Location came on, got ${gpsProvider.totalDistanceMeters} m",
            waitUntil { gpsProvider.totalDistanceMeters > 10f }
        )
    }

    @Test
    fun switching_location_off_mid_run_is_reported() {
        setGpsEnabled(true)
        val gpsProvider = GpsLocationProvider(context).also { provider = it }
        gpsProvider.start()
        assertTrue(gpsProvider.availability.value)

        locationManager.setTestProviderEnabled(gps, false)
        assertTrue("Availability should follow Location being switched off", waitUntil { !gpsProvider.availability.value })
    }
}
