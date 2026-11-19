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

/**
 * The countdown warnings that fire before each interval ends, in seconds remaining, sorted
 * descending and deduped so the larger threshold fires first. With the second warning off, the
 * first warning is the only (and therefore last) one.
 */
internal fun countdownWarningThresholds(
    warningSeconds1: Int,
    warningSeconds2: Int,
    warning2Enabled: Boolean
): List<Int> =
    listOfNotNull(warningSeconds1, warningSeconds2.takeIf { warning2Enabled })
        .filter { it > 0 }
        .distinct()
        .sortedDescending()

class WorkoutEngine(
    private val day: WorkoutDay,
    private val tts: TtsInterface,
    private val ttsEnabled: Boolean,
    private val countdownWarnings: Boolean,
    countdownWarningSeconds1: Int = 10,
    countdownWarningSeconds2: Int = 5,
    countdownWarning2Enabled: Boolean = true,
    private val midIntervalCues: Boolean,
    private val periodicTimeCues: Boolean = false,
    private val periodicTimeCueIntervalSeconds: Int = 30,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    // The smallest threshold carries the next-interval look-ahead announcement since it's the last
    // warning before the interval ends.
    private val warningThresholds =
        countdownWarningThresholds(countdownWarningSeconds1, countdownWarningSeconds2, countdownWarning2Enabled)
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

    // Guards every piece of mutable interval/pause state and every _state write. pause()/resume()
    // arrive on the main thread while the tick loop runs on a background dispatcher; without one
    // lock, a tick that had already passed the isPaused check could publish Active over a fresh
    // Paused, leaving the engine paused behind a UI that shows it running (Pause then no-ops).
    private val lock = Any()

    private val warnedCountdowns = mutableSetOf<Int>()
    private var midpointAnnounced = false
    private var lastPeriodicCueElapsed = -1
    // Net intervals to move, set from the service thread and consumed by the tick loop (see
    // skipToNextInterval and skipToPreviousInterval).
    private var pendingSkip = 0
    // Run intervals that ran to their end. Skipping one leaves it out; running it again after
    // going back puts it in, so only runs never finished count against the workout.
    private val finishedRuns = mutableSetOf<Int>()

    fun start(sessionId: Long) {
        synchronized(lock) {
            this.sessionId = sessionId
            intervalIndex = 0
            sessionStartMs = clock()
            intervalStartMs = sessionStartMs
            isPaused = false
            pendingSkip = 0
            finishedRuns.clear()
            warnedCountdowns.clear()
            midpointAnnounced = false
            lastPeriodicCueElapsed = -1
            announceInterval(intervalIndex)
        }
        tickJob = scope.launch { runLoop() }
    }

    /** Pauses a running workout. Returns false, changing nothing, if it wasn't running. */
    fun pause(): Boolean {
        synchronized(lock) {
            if (isPaused) return false
            val current = _state.value as? WorkoutState.Active ?: return false
            isPaused = true
            pausedAt = clock()
            _state.value = WorkoutState.Paused(current)
            return true
        }
    }

    /** Resumes a paused workout. Returns false, changing nothing, if it wasn't paused. */
    fun resume(): Boolean {
        synchronized(lock) {
            if (!isPaused) return false
            val pauseDuration = clock() - pausedAt
            sessionStartMs += pauseDuration
            intervalStartMs += pauseDuration
            isPaused = false
            val snapshot = (_state.value as? WorkoutState.Paused)?.snapshot ?: return true
            _state.value = snapshot
            return true
        }
    }

    /**
     * Ends the current interval and starts the next; on the last interval this completes the
     * workout. Ignored while paused. Only records the request, which the tick loop applies under
     * the engine lock, so a tap landing just as an interval changes naturally moves one interval,
     * not two.
     */
    fun skipToNextInterval() {
        synchronized(lock) {
            if (!isPaused) pendingSkip++
        }
    }

    /**
     * Starts the previous interval again from the beginning, so a runner can repeat a run they
     * had the legs for. Restarts the first interval when there's none before it. Ignored while
     * paused.
     */
    fun skipToPreviousInterval() {
        synchronized(lock) {
            if (!isPaused) pendingSkip--
        }
    }

    fun stop() {
        tickJob?.cancel()
        // Intentionally does NOT emit WorkoutState.Idle — callers handle their own cleanup
        // to avoid a race between the service's state collector and handleStop().
    }

    private suspend fun runLoop() {
        while (true) {
            delay(200)
            if (!synchronized(lock) { tick() }) return
        }
    }

    /** One tick of the loop, run under [lock]. Returns false once the workout has completed. */
    private fun tick(): Boolean {
        if (isPaused) return true

        val now = clock()
        val sessionElapsed = ((now - sessionStartMs) / 1000).toInt()

        var skippedToEnd = false
        if (pendingSkip != 0) {
            val target = intervalIndex + pendingSkip
            pendingSkip = 0
            if (target >= intervals.size) {
                // Past the end: backdate the last interval so the end-of-interval path below
                // completes the workout.
                intervalIndex = intervals.lastIndex
                intervalStartMs = now - intervals[intervalIndex].durationSeconds * 1000L
                skippedToEnd = true
            } else {
                beginInterval(target.coerceAtLeast(0), startMs = now)
            }
        }
        val intervalElapsed = ((now - intervalStartMs) / 1000).toInt()

        val currentInterval = intervals[intervalIndex]
        val remaining = currentInterval.durationSeconds - intervalElapsed

        if (remaining <= 0) {
            if (currentInterval.type == IntervalType.RUN && !skippedToEnd) finishedRuns.add(intervalIndex)
            intervalIndex++
            if (intervalIndex >= intervals.size) {
                // Pin a final Active frame to 0 before completing. When the screen is locked
                // the UI's lifecycle-aware collector is suspended and freezes on the last
                // Active value it received; without this it stays showing 1-3s left (the last
                // tick before the boundary) even though the workout is already done.
                _state.value = WorkoutState.Active(
                    currentInterval = currentInterval,
                    nextInterval = null,
                    intervalIndex = intervalIndex - 1,
                    totalIntervals = intervals.size,
                    remainingRunIntervals = remainingRunIntervals(intervalIndex - 1),
                    secondsRemainingInInterval = 0,
                    elapsedSessionSeconds = sessionElapsed,
                    sessionId = sessionId
                )
                if (ttsEnabled) tts.announce(TtsAnnouncement.WorkoutComplete)
                val skippedRun = intervals.indices.any {
                    intervals[it].type == IntervalType.RUN && it !in finishedRuns
                }
                _state.value = WorkoutState.Completed(sessionId, sessionElapsed, skippedRun)
                return false
            }
            // Start the next interval at the boundary, not at this tick, so the few milliseconds
            // each tick lands late don't add up over a workout (about 2 s on a 30-minute one).
            // After a longer stall (the phone briefly frozen), start it now instead: the runner
            // hears the cue late but still gets the whole interval.
            val boundaryMs = intervalStartMs + currentInterval.durationSeconds * 1000L
            beginInterval(
                intervalIndex,
                startMs = if (now - boundaryMs <= MAX_CARRIED_OVERSHOOT_MS) boundaryMs else now
            )
            return true
        }

        // A threshold equal to the interval's length would fire on the first tick and flush
        // the interval-start cue (warnings don't queue), so only warn once time has passed.
        if (countdownWarnings && ttsEnabled && remaining < currentInterval.durationSeconds &&
            remaining in warningThresholds && remaining !in warnedCountdowns
        ) {
            warnedCountdowns.add(remaining)
            tts.announce(TtsAnnouncement.CountdownWarning(remaining))
            if (remaining == warningThresholds.last()) {
                // Look-ahead: the smallest threshold is the last warning before the interval
                // ends, so it also announces what's coming next.
                if (intervalIndex + 1 < intervals.size) {
                    tts.announce(
                        TtsAnnouncement.NextInterval(intervals[intervalIndex + 1]),
                        queueAdd = true
                    )
                }
            }
        }

        if (periodicTimeCues && ttsEnabled && periodicTimeCueIntervalSeconds > 0 &&
            intervalElapsed > 0 && intervalElapsed % periodicTimeCueIntervalSeconds == 0 &&
            intervalElapsed != lastPeriodicCueElapsed
        ) {
            lastPeriodicCueElapsed = intervalElapsed
            tts.announce(TtsAnnouncement.PeriodicTimeRemaining(remaining), queueAdd = true)
        }

        if (midIntervalCues && ttsEnabled &&
            currentInterval.type == IntervalType.RUN &&
            currentInterval.durationSeconds >= 60 &&
            intervalElapsed >= currentInterval.durationSeconds / 2 &&
            !midpointAnnounced
        ) {
            midpointAnnounced = true
            tts.announce(TtsAnnouncement.IntervalMidpoint(intervalIndex), queueAdd = true)
        }

        _state.value = WorkoutState.Active(
            currentInterval = intervals[intervalIndex],
            nextInterval = intervals.getOrNull(intervalIndex + 1),
            intervalIndex = intervalIndex,
            totalIntervals = intervals.size,
            remainingRunIntervals = remainingRunIntervals(intervalIndex),
            secondsRemainingInInterval = remaining,
            elapsedSessionSeconds = sessionElapsed,
            sessionId = sessionId
        )
        return true
    }

    private companion object {
        // Up to this much lateness at an interval change is taken from the next interval.
        const val MAX_CARRIED_OVERSHOOT_MS = 1_000L
    }

    private fun remainingRunIntervals(index: Int): Int =
        intervals.drop(index).count { it.type == IntervalType.RUN }

    private fun beginInterval(index: Int, startMs: Long) {
        intervalIndex = index
        intervalStartMs = startMs
        warnedCountdowns.clear()
        midpointAnnounced = false
        lastPeriodicCueElapsed = -1
        announceInterval(index)
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
