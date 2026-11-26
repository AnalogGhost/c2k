package com.hackerapps.c2k.data.model

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

/** Totals for every finished workout in one program, shown when the runner completes it. */
data class ProgramSummary(val workouts: Int, val durationSeconds: Int, val distanceMeters: Float) {
    companion object {
        /**
         * Adds the workout just finished, from its own figures, to the program's earlier finished
         * workouts in [sessions]. The workout may not be saved as completed yet when the screen
         * shows, so its saved row ([currentId]) is left out rather than trusted.
         */
        fun of(
            sessions: List<WorkoutSessionEntity>,
            programId: String,
            currentId: Long,
            durationSeconds: Int,
            distanceMeters: Float
        ): ProgramSummary {
            val earlier = sessions.filter { it.programId == programId && it.completed && it.id != currentId }
            return ProgramSummary(
                workouts = earlier.size + 1,
                durationSeconds = durationSeconds + earlier.sumOf { it.durationSeconds },
                distanceMeters = distanceMeters + earlier.map { it.distanceMeters }.sum()
            )
        }
    }
}
