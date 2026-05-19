package org.c2k.engine.tts

interface TtsInterface {
    val isAvailable: Boolean
    fun announce(announcement: TtsAnnouncement)
    fun shutdown()
}
