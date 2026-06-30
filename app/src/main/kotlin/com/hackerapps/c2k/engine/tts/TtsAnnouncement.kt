package com.hackerapps.c2k.engine.tts

import com.hackerapps.c2k.data.model.Interval

sealed class TtsAnnouncement {
    data class IntervalStart(val interval: Interval)    : TtsAnnouncement()
    data class CountdownWarning(val secondsRemaining: Int) : TtsAnnouncement()
    data class NextInterval(val interval: Interval)     : TtsAnnouncement()
    data class IntervalMidpoint(val phraseIndex: Int)   : TtsAnnouncement()
    object WorkoutComplete  : TtsAnnouncement()
    object Halfway          : TtsAnnouncement()
    object LastRunInterval  : TtsAnnouncement()
}
