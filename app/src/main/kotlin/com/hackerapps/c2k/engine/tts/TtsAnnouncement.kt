package com.hackerapps.c2k.engine.tts

import com.hackerapps.c2k.data.model.Interval

sealed class TtsAnnouncement {
    data class IntervalStart(val interval: Interval)    : TtsAnnouncement()
    data class CountdownWarning(val secondsRemaining: Int) : TtsAnnouncement()
    data class NextInterval(val interval: Interval)     : TtsAnnouncement()
    data class IntervalMidpoint(val phraseIndex: Int)   : TtsAnnouncement()
    data class PeriodicTimeRemaining(val secondsRemaining: Int) : TtsAnnouncement()
    /** Distance covered so far, and the average pace over the last split (null when unreliable). */
    data class DistanceSplit(val meters: Int, val paceSecondsPerKm: Int?) : TtsAnnouncement()
    object WorkoutComplete  : TtsAnnouncement()
    object Halfway          : TtsAnnouncement()
    object LastRunInterval  : TtsAnnouncement()
}
