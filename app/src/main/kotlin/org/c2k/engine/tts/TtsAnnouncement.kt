package org.c2k.engine.tts

import org.c2k.data.model.Interval

sealed class TtsAnnouncement {
    data class IntervalStart(val interval: Interval) : TtsAnnouncement()
    data class CountdownWarning(val secondsRemaining: Int) : TtsAnnouncement()
    object WorkoutComplete : TtsAnnouncement()
}
