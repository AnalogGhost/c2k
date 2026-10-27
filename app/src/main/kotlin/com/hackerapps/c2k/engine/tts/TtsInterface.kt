package com.hackerapps.c2k.engine.tts

import kotlinx.coroutines.flow.StateFlow

interface TtsInterface {
    val isAvailable: Boolean
    fun announce(announcement: TtsAnnouncement, queueAdd: Boolean = false)
    fun shutdown()
}

/** What the workout service plays cues through: the voice, or the chimes of Tones mode. */
interface CuePlayer : TtsInterface {
    // True while a cue is queued or sounding, so a final cue can finish before shutdown.
    val speaking: StateFlow<Boolean>
}
