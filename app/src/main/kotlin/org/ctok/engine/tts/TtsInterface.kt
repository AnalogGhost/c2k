package org.ctok.engine.tts

interface TtsInterface {
    val isAvailable: Boolean
    fun announce(announcement: TtsAnnouncement)
    fun shutdown()
}
