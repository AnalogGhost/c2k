package org.ctok.engine.tts

import org.ctok.data.model.Interval

sealed class TtsAnnouncement {
    data class IntervalStart(val interval: Interval) : TtsAnnouncement()
    data class CountdownWarning(val secondsRemaining: Int) : TtsAnnouncement()
    object WorkoutComplete : TtsAnnouncement()
}
