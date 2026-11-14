package com.hackerapps.c2k

import com.hackerapps.c2k.data.prefs.CueStyle
import com.hackerapps.c2k.service.WorkoutService
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceCuesActiveTest {

    @Test
    fun only_spoken_cues_with_gps_tracking_and_the_setting_on_announce_splits() {
        for (tts in listOf(true, false)) for (style in CueStyle.entries)
            for (setting in listOf(true, false)) for (gps in listOf(true, false)) {
                val expected = tts && style == CueStyle.VOICE && setting && gps
                assertEquals(
                    "tts=$tts style=$style setting=$setting gps=$gps",
                    expected,
                    WorkoutService.distanceCuesActive(tts, style, setting, gpsTracking = gps)
                )
            }
    }
}
