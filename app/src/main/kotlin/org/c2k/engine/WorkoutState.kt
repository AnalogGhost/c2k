package org.c2k.engine

import org.c2k.data.model.Interval

sealed class WorkoutState {

    object Idle : WorkoutState()

    data class Active(
        val currentInterval: Interval,
        val intervalIndex: Int,
        val totalIntervals: Int,
        val secondsRemainingInInterval: Int,
        val elapsedSessionSeconds: Int,
        val sessionId: Long
    ) : WorkoutState()

    data class Paused(val snapshot: Active) : WorkoutState()

    data class Completed(val sessionId: Long) : WorkoutState()
}
