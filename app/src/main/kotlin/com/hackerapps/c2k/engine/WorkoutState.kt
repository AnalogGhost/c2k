package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.model.Interval

sealed class WorkoutState {

    object Idle : WorkoutState()

    data class Active(
        val currentInterval: Interval,
        val nextInterval: Interval?,
        val intervalIndex: Int,
        val totalIntervals: Int,
        val remainingRunIntervals: Int,
        val secondsRemainingInInterval: Int,
        val elapsedSessionSeconds: Int,
        val sessionId: Long
    ) : WorkoutState()

    data class Paused(val snapshot: Active) : WorkoutState()

    /**
     * [skippedRun]: a run interval was never run to its end, so the workout doesn't count toward
     * the runner's progress.
     */
    data class Completed(
        val sessionId: Long,
        val elapsedSessionSeconds: Int,
        val skippedRun: Boolean = false
    ) : WorkoutState()
}
