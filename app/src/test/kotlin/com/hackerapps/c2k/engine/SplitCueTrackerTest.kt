package com.hackerapps.c2k.engine

import com.hackerapps.c2k.engine.tts.TtsAnnouncement.DistanceSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitCueTrackerTest {

    @Test
    fun nothing_is_announced_before_the_first_split() {
        val tracker = SplitCueTracker(1000)
        assertNull(tracker.onProgress(0f, 0))
        assertNull(tracker.onProgress(999.9f, 400))
    }

    @Test
    fun the_first_split_announces_distance_and_its_average_pace() {
        val tracker = SplitCueTracker(1000)
        tracker.onProgress(600f, 240)
        assertEquals(DistanceSplit(1000, 400), tracker.onProgress(1003f, 400))  // 6:40 /km
    }

    @Test
    fun each_split_is_announced_once() {
        val tracker = SplitCueTracker(1000)
        tracker.onProgress(1000f, 400)
        assertNull(tracker.onProgress(1001f, 401))
        assertNull(tracker.onProgress(1999f, 800))
    }

    @Test
    fun pace_covers_only_the_split_just_finished() {
        val tracker = SplitCueTracker(1000)
        tracker.onProgress(1000f, 400)                                         // 6:40
        assertEquals(DistanceSplit(2000, 360), tracker.onProgress(2000f, 760))  // then 6:00
    }

    @Test
    fun half_kilometre_splits_report_pace_per_kilometre() {
        val tracker = SplitCueTracker(500)
        assertEquals(DistanceSplit(500, 420), tracker.onProgress(500f, 210))
        assertEquals(DistanceSplit(1000, 400), tracker.onProgress(1000f, 410))
        assertEquals(DistanceSplit(1500, 380), tracker.onProgress(1500f, 600))
    }

    @Test
    fun a_jump_across_several_splits_announces_only_the_latest() {
        val tracker = SplitCueTracker(500)
        // GPS reconnects after a gap and the distance leaps from 400 m to 1.6 km.
        assertEquals(DistanceSplit(1500, 400), tracker.onProgress(1600f, 600))
        assertNull(tracker.onProgress(1700f, 640))
        assertEquals(DistanceSplit(2000, 400), tracker.onProgress(2000f, 800))
    }

    @Test
    fun an_implausible_pace_is_left_out_but_the_distance_is_still_announced() {
        val tooFast = SplitCueTracker(1000)
        assertEquals(DistanceSplit(1000, null), tooFast.onProgress(1000f, 60))     // 1:00 /km: GPS error
        val tooSlow = SplitCueTracker(1000)
        assertEquals(DistanceSplit(1000, null), tooSlow.onProgress(1000f, 2400))   // 40:00 /km
        val noTime = SplitCueTracker(1000)
        assertEquals(DistanceSplit(1000, null), noTime.onProgress(1000f, 0))
    }

    @Test
    fun the_pace_bounds_are_inclusive() {
        assertEquals(DistanceSplit(1000, 120), SplitCueTracker(1000).onProgress(1000f, 120))
        assertEquals(DistanceSplit(1000, 1800), SplitCueTracker(1000).onProgress(1000f, 1800))
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_zero_split_is_rejected() {
        SplitCueTracker(0)
    }
}
