package org.c2k.engine

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.c2k.data.model.WorkoutDay
import org.c2k.engine.tts.TtsAnnouncement
import org.c2k.engine.tts.TtsInterface

class WorkoutEngine(
    private val day: WorkoutDay,
    private val tts: TtsInterface,
    private val ttsEnabled: Boolean,
    private val countdownWarnings: Boolean,
    private val scope: CoroutineScope,
    // Injectable clock — defaults to system monotonic clock; override in tests
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var sessionId: Long = -1

    private var sessionStartMs: Long = 0
    private var intervalStartMs: Long = 0
    private var pausedAt: Long = 0
    private var isPaused = false

    private var intervalIndex = 0
    private val intervals = day.intervals

    fun start(sessionId: Long) {
        this.sessionId = sessionId
        intervalIndex = 0
        sessionStartMs = clock()
        intervalStartMs = sessionStartMs
        isPaused = false
        announceInterval(intervalIndex)
        tickJob = scope.launch { runLoop() }
    }

    fun pause() {
        if (isPaused) return
        isPaused = true
        pausedAt = clock()
        val current = _state.value as? WorkoutState.Active ?: return
        _state.value = WorkoutState.Paused(current)
    }

    fun resume() {
        if (!isPaused) return
        val pauseDuration = clock() - pausedAt
        sessionStartMs += pauseDuration
        intervalStartMs += pauseDuration
        isPaused = false
    }

    fun stop() {
        tickJob?.cancel()
        _state.value = WorkoutState.Idle
    }

    private suspend fun runLoop() {
        while (true) {
            delay(200)
            if (isPaused) continue

            val now = clock()
            val sessionElapsed = ((now - sessionStartMs) / 1000).toInt()
            val intervalElapsed = ((now - intervalStartMs) / 1000).toInt()

            val currentInterval = intervals[intervalIndex]
            val remaining = currentInterval.durationSeconds - intervalElapsed

            if (remaining <= 0) {
                intervalIndex++
                if (intervalIndex >= intervals.size) {
                    if (ttsEnabled) tts.announce(TtsAnnouncement.WorkoutComplete)
                    _state.value = WorkoutState.Completed(sessionId)
                    tickJob?.cancel()
                    return
                }
                intervalStartMs = now
                announceInterval(intervalIndex)
                continue
            }

            if (countdownWarnings && ttsEnabled && (remaining == 10 || remaining == 5)) {
                tts.announce(TtsAnnouncement.CountdownWarning(remaining))
            }

            _state.value = WorkoutState.Active(
                currentInterval = intervals[intervalIndex],
                intervalIndex = intervalIndex,
                totalIntervals = intervals.size,
                secondsRemainingInInterval = remaining,
                elapsedSessionSeconds = sessionElapsed,
                sessionId = sessionId
            )
        }
    }

    private fun announceInterval(index: Int) {
        if (!ttsEnabled) return
        tts.announce(TtsAnnouncement.IntervalStart(intervals[index]))
    }
}
