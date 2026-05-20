package com.hackerapps.c2k.engine.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.hackerapps.c2k.data.model.IntervalType
import java.util.Locale

class TtsManager(context: Context) : TtsInterface, TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingAnnouncement: TtsAnnouncement? = null

    override var isAvailable: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH)
            }
            ready = true
            isAvailable = true
            pendingAnnouncement?.let { announce(it) }
            pendingAnnouncement = null
        } else {
            Log.w("TtsManager", "TextToSpeech initialization failed (status=$status)")
        }
    }

    override fun announce(announcement: TtsAnnouncement) {
        if (!ready) {
            pendingAnnouncement = announcement
            return
        }
        val text = buildText(announcement)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "c2k_${System.nanoTime()}")
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
        ready = false
    }

    private fun buildText(announcement: TtsAnnouncement): String = when (announcement) {
        is TtsAnnouncement.IntervalStart -> when (announcement.interval.type) {
            IntervalType.WARMUP   -> "Begin warm-up walk"
            IntervalType.COOLDOWN -> "Begin cool-down walk"
            IntervalType.RUN, IntervalType.WALK -> announcement.interval.announcement
        }
        is TtsAnnouncement.CountdownWarning ->
            "${announcement.secondsRemaining} seconds remaining"
        TtsAnnouncement.WorkoutComplete ->
            "Workout complete. Great job!"
    }
}
