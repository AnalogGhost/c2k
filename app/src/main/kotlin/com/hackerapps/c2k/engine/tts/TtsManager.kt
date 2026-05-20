package com.hackerapps.c2k.engine.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class TtsManager(
    context: Context,
    private val speechRate: Float = 1.0f
) : TtsInterface, TextToSpeech.OnInitListener {

    companion object {
        val isAvailableOnDevice = MutableStateFlow<Boolean?>(null)
    }

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
            tts.setSpeechRate(speechRate)
            ready = true
            isAvailable = true
            isAvailableOnDevice.value = true
            pendingAnnouncement?.let { announce(it) }
            pendingAnnouncement = null
        } else {
            isAvailableOnDevice.value = false
            Log.w("TtsManager", "TextToSpeech initialization failed (status=$status)")
        }
    }

    override fun announce(announcement: TtsAnnouncement, queueAdd: Boolean) {
        if (!ready) {
            if (!queueAdd) pendingAnnouncement = announcement
            return
        }
        val text = buildText(announcement)
        val mode = if (queueAdd) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        tts.speak(text, mode, null, "c2k_${System.nanoTime()}")
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
        ready = false
    }

    private fun buildText(announcement: TtsAnnouncement): String = when (announcement) {
        is TtsAnnouncement.IntervalStart    -> announcement.interval.announcement
        is TtsAnnouncement.CountdownWarning -> "${announcement.secondsRemaining} seconds remaining"
        is TtsAnnouncement.NextInterval     -> nextIntervalText(announcement.interval)
        TtsAnnouncement.WorkoutComplete     -> "Workout complete. Great job!"
        TtsAnnouncement.Halfway             -> "Halfway there, keep it up!"
        TtsAnnouncement.LastRunInterval     -> "Last run, finish strong!"
    }

    private fun nextIntervalText(interval: com.hackerapps.c2k.data.model.Interval): String =
        when (interval.type) {
            com.hackerapps.c2k.data.model.IntervalType.RUN      -> "Get ready to run"
            com.hackerapps.c2k.data.model.IntervalType.WALK     -> "Get ready to walk"
            com.hackerapps.c2k.data.model.IntervalType.WARMUP   -> "Get ready to warm up"
            com.hackerapps.c2k.data.model.IntervalType.COOLDOWN -> "Begin your cool-down soon"
        }
}
