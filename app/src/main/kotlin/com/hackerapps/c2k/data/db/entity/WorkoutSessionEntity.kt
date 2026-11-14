package com.hackerapps.c2k.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: String,
    val week: Int,
    val day: Int,
    val startedAt: Long,
    /**
     * When the workout ended, finished or stopped by the runner. Null while it runs, and stays null
     * for a workout the app was closed during (by Android, or a crash): that run is recovered on
     * the next launch with the time and distance saved so far, but the runner didn't end it.
     */
    val completedAt: Long? = null,
    val durationSeconds: Int,
    val distanceMeters: Float,
    val completed: Boolean
)
