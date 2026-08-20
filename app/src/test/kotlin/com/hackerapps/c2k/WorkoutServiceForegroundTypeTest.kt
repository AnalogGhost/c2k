package com.hackerapps.c2k

import android.content.pm.ServiceInfo
import com.hackerapps.c2k.service.WorkoutService
import org.junit.Assert.assertEquals
import org.junit.Test

// Regression coverage for a Play Store crash: on API 34+, startForeground() validates the
// requested foreground service type against the runtime permission backing it, and throws a
// SecurityException if that permission isn't actually granted. WorkoutService.handleStart() used
// to always request FOREGROUND_SERVICE_TYPE_HEALTH regardless of whether ACTIVITY_RECOGNITION was
// granted — this is the pure type-selection logic that fix depends on, isolated so it can run as
// a plain JVM test instead of an instrumented one (an earlier instrumented version that revoked a
// live permission crashed CI by killing its own shared test process — see WorkoutService.kt).
class WorkoutServiceForegroundTypeTest {

    @Test
    fun requests_no_type_when_neither_permission_is_granted() {
        assertEquals(
            0,
            WorkoutService.foregroundServiceType(hasActivityRecognition = false, hasLocation = false, treadmill = false)
        )
    }

    @Test
    fun requests_health_only_when_only_activity_recognition_is_granted() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            WorkoutService.foregroundServiceType(hasActivityRecognition = true, hasLocation = false, treadmill = false)
        )
    }

    @Test
    fun requests_location_only_when_only_location_is_granted() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            WorkoutService.foregroundServiceType(hasActivityRecognition = false, hasLocation = true, treadmill = false)
        )
    }

    @Test
    fun requests_both_types_when_both_permissions_are_granted_and_not_treadmill() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            WorkoutService.foregroundServiceType(hasActivityRecognition = true, hasLocation = true, treadmill = false)
        )
    }

    @Test
    fun omits_location_type_in_treadmill_mode_even_with_permission_granted() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            WorkoutService.foregroundServiceType(hasActivityRecognition = true, hasLocation = true, treadmill = true)
        )
    }
}
