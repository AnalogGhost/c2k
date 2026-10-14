package com.hackerapps.c2k.engine.tone

import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.engine.tts.TtsAnnouncement

/**
 * Which chime, if any, stands in for each spoken announcement in Tones mode. Cues that only make
 * sense as words (look-ahead, halfway, last run, encouragement, periodic time remaining) have no
 * chime and are dropped: a tone for each would be one more sound to learn for little gain.
 */
internal object ToneCues {

    /**
     * [countdownThresholds] are the active countdown warnings, as the engine fires them (see
     * `countdownWarningThresholds`). With two of them, the later one gets the double tick.
     */
    fun chimeFor(announcement: TtsAnnouncement, countdownThresholds: List<Int>): Chime? =
        when (announcement) {
            is TtsAnnouncement.IntervalStart -> when (announcement.interval.type) {
                IntervalType.WARMUP -> Chime.WARMUP
                IntervalType.RUN -> Chime.RUN
                IntervalType.WALK -> Chime.WALK
                IntervalType.COOLDOWN -> Chime.COOLDOWN
            }
            is TtsAnnouncement.CountdownWarning ->
                if (countdownThresholds.size > 1 && announcement.secondsRemaining == countdownThresholds.last()) {
                    Chime.COUNTDOWN_SECOND
                } else {
                    Chime.COUNTDOWN_FIRST
                }
            TtsAnnouncement.WorkoutComplete -> Chime.COMPLETE
            is TtsAnnouncement.NextInterval,
            is TtsAnnouncement.IntervalMidpoint,
            is TtsAnnouncement.PeriodicTimeRemaining,
            TtsAnnouncement.Halfway,
            TtsAnnouncement.LastRunInterval -> null
        }
}
