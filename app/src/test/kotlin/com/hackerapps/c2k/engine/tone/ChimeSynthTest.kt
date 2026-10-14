package com.hackerapps.c2k.engine.tone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

class ChimeSynthTest {

    private fun peakOf(pcm: ShortArray, from: Int = 0, to: Int = pcm.size): Int =
        (from until to).maxOfOrNull { abs(pcm[it].toInt()) } ?: 0

    // Goertzel: the signal's power at one frequency, enough to tell which of two notes dominates.
    private fun powerAt(pcm: ShortArray, from: Int, to: Int, frequencyHz: Double): Double {
        val coefficient = 2 * cos(2 * PI * frequencyHz / ChimeSynth.SAMPLE_RATE)
        var previous = 0.0
        var beforePrevious = 0.0
        for (i in from until to) {
            val current = pcm[i] + coefficient * previous - beforePrevious
            beforePrevious = previous
            previous = current
        }
        return previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious
    }

    // Start sample and length of each note within a rendered chime.
    private fun noteSpans(chime: Chime): List<Pair<Int, Int>> {
        var offset = 0
        return chime.notes.map { note ->
            val length = ChimeSynth.samples(note.durationMs)
            (offset to length).also { offset += length + ChimeSynth.samples(note.gapAfterMs) }
        }
    }

    @Test
    fun every_chime_renders_exactly_its_notes_and_gaps() {
        Chime.entries.forEach { chime ->
            val expected = chime.notes.sumOf {
                ChimeSynth.samples(it.durationMs) + ChimeSynth.samples(it.gapAfterMs)
            }
            assertEquals(chime.name, expected, ChimeSynth.render(chime).size)
        }
    }

    @Test
    fun notes_start_and_end_at_zero_and_gaps_are_silent() {
        Chime.entries.forEach { chime ->
            val pcm = ChimeSynth.render(chime)
            noteSpans(chime).forEachIndexed { index, (start, length) ->
                assertEquals("${chime.name} note $index first sample", 0, pcm[start].toInt())
                assertEquals("${chime.name} note $index last sample", 0, pcm[start + length - 1].toInt())
                val gapEnd = start + length + ChimeSynth.samples(chime.notes[index].gapAfterMs)
                assertEquals("${chime.name} gap after note $index", 0, peakOf(pcm, start + length, gapEnd))
            }
        }
    }

    @Test
    fun chimes_are_loud_but_never_exceed_the_headroom() {
        val limit = (ChimeSynth.PEAK * Short.MAX_VALUE).toInt() + 1
        Chime.entries.forEach { chime ->
            val peak = peakOf(ChimeSynth.render(chime))
            assertTrue("${chime.name} peak $peak exceeds $limit", peak <= limit)
            assertTrue("${chime.name} peak $peak is too quiet", peak > limit / 2)
        }
    }

    @Test
    fun run_rises_and_walk_falls() {
        // In each rendered note, the chime's own pitch for that note outweighs its other note's.
        fun soundsAsWritten(chime: Chime) {
            val pcm = ChimeSynth.render(chime)
            val (first, second) = chime.notes.map { it.frequencyHz }
            noteSpans(chime).forEachIndexed { index, (start, length) ->
                val (own, other) = if (index == 0) first to second else second to first
                assertTrue(
                    "${chime.name} note $index should sound at $own Hz",
                    powerAt(pcm, start, start + length, own) > powerAt(pcm, start, start + length, other)
                )
            }
        }
        assertTrue("run should rise", Chime.RUN.notes.last().frequencyHz > Chime.RUN.notes.first().frequencyHz)
        assertTrue("walk should fall", Chime.WALK.notes.last().frequencyHz < Chime.WALK.notes.first().frequencyHz)
        soundsAsWritten(Chime.RUN)
        soundsAsWritten(Chime.WALK)
    }

    @Test
    fun run_is_quicker_and_higher_than_walk() {
        // Direction alone is easy to miss mid-run, so run and walk also differ in tempo and register.
        val run = Chime.RUN.notes
        val walk = Chime.WALK.notes
        assertTrue(run.sumOf { it.durationMs + it.gapAfterMs } < walk.sumOf { it.durationMs + it.gapAfterMs })
        assertTrue(run.last().frequencyHz > 2 * walk.last().frequencyHz)
    }

    @Test
    fun every_chime_is_distinct_and_short() {
        assertEquals(Chime.entries.size, Chime.entries.map { it.notes }.toSet().size)
        Chime.entries.forEach { chime ->
            val ms = chime.notes.sumOf { it.durationMs + it.gapAfterMs }
            // The shortest countdown warning is 3 s; a chime must be long over well before the next cue.
            assertTrue("${chime.name} lasts $ms ms", ms <= 1_200)
        }
    }

    @Test
    fun the_second_countdown_warning_is_two_ticks_of_the_first() {
        val first = Chime.COUNTDOWN_FIRST.notes.single()
        val second = Chime.COUNTDOWN_SECOND.notes
        assertEquals(2, second.size)
        second.forEach { assertEquals(first.frequencyHz, it.frequencyHz, 0.0) }
    }
}
