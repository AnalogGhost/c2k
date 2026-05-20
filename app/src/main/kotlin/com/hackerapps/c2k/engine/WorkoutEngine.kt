package com.hackerapps.c2k.engine

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.hackerapps.c2k.data.model.IntervalType
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.engine.tts.TtsAnnouncement
import com.hackerapps.c2k.engine.tts.TtsInterface

class WorkoutEngine(
    private val day: WorkoutDay,
    private val tts: TtsInterface,
    private val ttsEnabled: Boolean,
    private val countdownWarnings: Boolean,
    private val scope: CoroutineScope,
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

    private val warnedCountdowns = mutableSetOf<Int>()

    fun start(sessionId: Long) {
        this.sessionId = sessionId
        intervalIndex = 0
        sessionStartMs = clock()
        intervalStartMs = sessionStartMs
        isPaused = false
        warnedCountdowns.clear()
        announceInterval(intervalIndex)
        tickJob = scope.launch { runLoop() }
    }

    fun pause() {
        if (isPaused) return
        val current = _state.value as? WorkoutState.Active ?: return
        isPaused = true
        pausedAt = clock()
        _state.value = WorkoutState.Paused(current)
    }

    fun resume() {
        if (!isPaused) return
        val pauseDuration = clock() - pausedAt
        sessionStartMs += pauseDuration
        intervalStartMs += pauseDuration
        isPaused = false
        val snapshot = (_state.value as? WorkoutState.Paused)?.snapshot ?: return
        _state.value = snapshot
    }

    fun stop() {
        tickJob?.cancel()
        // Intentionally does NOT emit WorkoutState.Idle — callers handle their own cleanup
        // to avoid a race between the service's state collector and handleStop().
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
                    _state.value = WorkoutState.Completed(sessionId, sessionElapsed)
                    tickJob?.cancel()
                    return
                }
                intervalStartMs = now
                warnedCountdowns.clear()
                announceInterval(intervalIndex)
                continue
            }

            if (countdownWarnings && ttsEnabled && remaining !in warnedCountdowns) {
                when (remaining) {
                    10 -> {
                        warnedCountdowns.add(10)
                        tts.announce(TtsAnnouncement.CountdownWarning(10))
                    }
                    5 -> {
                        warnedCountdowns.add(5)
                        tts.announce(TtsAnnouncement.CountdownWarning(5))
                        // Look-ahead: announce what interval is coming next
                        if (intervalIndex + 1 < intervals.size) {
                            tts.announce(
                                TtsAnnouncement.NextInterval(intervals[intervalIndex + 1]),
                                queueAdd = true
                            )
                        }
                    }
                }
            }

            _state.value = WorkoutState.Active(
                currentInterval = intervals[intervalIndex],
                nextInterval = intervals.getOrNull(intervalIndex + 1),
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
        if (index == 0) return

        val isLastRun = intervals[index].type == IntervalType.RUN &&
            intervals.drop(index + 1).none { it.type == IntervalType.RUN }
        if (isLastRun) {
            tts.announce(TtsAnnouncement.LastRunInterval, queueAdd = true)
            return
        }

        if (index == intervals.size / 2) {
            tts.announce(TtsAnnouncement.Halfway, queueAdd = true)
        }
    }
}
