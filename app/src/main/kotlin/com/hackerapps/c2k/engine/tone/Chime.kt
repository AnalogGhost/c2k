package com.hackerapps.c2k.engine.tone

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** One pitched note of a chime: it rings for [durationMs], then [gapAfterMs] of silence follows. */
internal data class ChimeNote(
    val frequencyHz: Double,
    val durationMs: Int,
    val gapAfterMs: Int = 0
)

private const val A4 = 440.00
private const val C5 = 523.25
private const val E5 = 659.25
private const val G5 = 783.99
private const val C6 = 1046.50
private const val E6 = 1318.51
private const val TICK = 1760.00

/**
 * The non-verbal cues played in Tones mode (issue #42). Each is a short phrase that can be told
 * apart without looking at the phone: a run leaps up quickly and high, a walk steps down slowly
 * and low, the warm-up is one low note, the cool-down steps down, countdown warnings are high
 * ticks, and completion is a rising arpeggio.
 */
internal enum class Chime(val notes: List<ChimeNote>) {
    // Run and walk differ in direction, register, tempo and interval at once, so missing one
    // of those mid-run still leaves the others.
    RUN(listOf(ChimeNote(E5, 90, gapAfterMs = 30), ChimeNote(E6, 220))),
    WALK(listOf(ChimeNote(E5, 220, gapAfterMs = 80), ChimeNote(A4, 420))),
    WARMUP(listOf(ChimeNote(C5, 400))),
    COOLDOWN(
        listOf(
            ChimeNote(G5, 160, gapAfterMs = 40),
            ChimeNote(E5, 160, gapAfterMs = 40),
            ChimeNote(C5, 360)
        )
    ),
    COUNTDOWN_FIRST(listOf(ChimeNote(TICK, 70))),
    COUNTDOWN_SECOND(listOf(ChimeNote(TICK, 70, gapAfterMs = 90), ChimeNote(TICK, 70))),
    COMPLETE(
        listOf(
            ChimeNote(C5, 140, gapAfterMs = 30),
            ChimeNote(E5, 140, gapAfterMs = 30),
            ChimeNote(G5, 140, gapAfterMs = 30),
            ChimeNote(C6, 500)
        )
    )
}

/**
 * Renders chimes as 16-bit mono PCM, so Tones mode needs no audio files. Pure Kotlin with no
 * Android dependencies, which keeps it unit-testable.
 */
internal object ChimeSynth {
    const val SAMPLE_RATE = 44_100

    // Fraction of full scale the loudest possible sample can reach, leaving headroom so the
    // partials can never clip.
    const val PEAK = 0.8

    private const val ATTACK_MS = 5
    private const val RELEASE_MS = 8

    // How fast each note fades over its length; higher partials fade proportionally faster, like
    // a struck bell.
    private const val DECAY = 3.0

    // A soft bell: the fundamental plus a quieter octave and twelfth.
    private val PARTIALS = listOf(1.0 to 1.0, 2.0 to 0.4, 3.0 to 0.15)

    fun render(chime: Chime, sampleRate: Int = SAMPLE_RATE): ShortArray = render(chime.notes, sampleRate)

    fun render(notes: List<ChimeNote>, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val out = ShortArray(notes.sumOf { samples(it.durationMs, sampleRate) + samples(it.gapAfterMs, sampleRate) })
        var offset = 0
        for (note in notes) {
            val length = samples(note.durationMs, sampleRate)
            renderNote(note.frequencyHz, length, sampleRate, out, offset)
            // The gap is left as the array's initial zeros.
            offset += length + samples(note.gapAfterMs, sampleRate)
        }
        return out
    }

    fun samples(ms: Int, sampleRate: Int = SAMPLE_RATE): Int = ms * sampleRate / 1000

    private fun renderNote(frequencyHz: Double, length: Int, sampleRate: Int, out: ShortArray, offset: Int) {
        val attack = samples(ATTACK_MS, sampleRate)
        val release = samples(RELEASE_MS, sampleRate)
        val scale = PEAK * Short.MAX_VALUE / PARTIALS.sumOf { it.second }
        for (i in 0 until length) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / length
            var value = 0.0
            for ((ratio, amplitude) in PARTIALS) {
                value += amplitude * exp(-DECAY * ratio * progress) * sin(2 * PI * frequencyHz * ratio * t)
            }
            // Ramp in and out so every note starts and ends at zero: no clicks at the edges.
            val envelope = minOf(1.0, i.toDouble() / attack, (length - 1 - i).toDouble() / release)
            out[offset + i] = (value * envelope * scale).roundToInt().toShort()
        }
    }
}
