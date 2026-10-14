package com.hackerapps.c2k.engine.tone

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.model.Interval
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Plays real chimes through AudioTrack, so the speaking signal the service waits on at
// completion is checked against actual playback rather than a fake.
@RunWith(AndroidJUnit4::class)
class TonePlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var player: TonePlayer

    private fun start(type: IntervalType) = TtsAnnouncement.IntervalStart(Interval(type, 60))

    private fun lengthMs(chime: Chime): Long = chime.notes.sumOf { it.durationMs + it.gapAfterMs }.toLong()

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Before
    fun setUp() {
        player = TonePlayer(context, countdownThresholds = listOf(10, 5))
    }

    @After
    fun tearDown() {
        player.shutdown()
    }

    @Test
    fun a_chime_reports_speaking_until_it_has_played() {
        val startedAt = SystemClock.elapsedRealtime()
        player.announce(start(IntervalType.RUN))
        assertTrue("Expected speaking as soon as a chime starts", player.speaking.value)

        assertTrue("Expected speaking to clear once the chime ends", waitUntil { !player.speaking.value })
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        assertTrue("Speaking cleared after $elapsed ms, before the chime could finish", elapsed >= lengthMs(Chime.RUN))
    }

    @Test
    fun word_only_cues_play_nothing() {
        player.announce(TtsAnnouncement.Halfway, queueAdd = true)
        player.announce(TtsAnnouncement.NextInterval(Interval(IntervalType.WALK, 60)), queueAdd = true)
        assertFalse(player.speaking.value)
    }

    @Test
    fun a_new_chime_replaces_the_one_still_ringing() {
        player.announce(start(IntervalType.WALK))
        val replacedAt = SystemClock.elapsedRealtime()
        player.announce(TtsAnnouncement.WorkoutComplete)

        // The walk chime's own release timer must not clear speaking under the completion chime.
        assertTrue(waitUntil { !player.speaking.value })
        val elapsed = SystemClock.elapsedRealtime() - replacedAt
        assertTrue("Speaking cleared after $elapsed ms, mid completion chime", elapsed >= lengthMs(Chime.COMPLETE))
    }

    @Test
    fun shutdown_silences_the_player_for_good() {
        player.announce(TtsAnnouncement.WorkoutComplete)
        player.shutdown()
        assertFalse(player.speaking.value)
        assertFalse(player.isAvailable)

        player.announce(start(IntervalType.RUN))
        assertFalse("A shut-down player must not play again", player.speaking.value)
    }
}
