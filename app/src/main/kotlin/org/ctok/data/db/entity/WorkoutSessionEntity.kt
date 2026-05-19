package org.ctok.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: String,
    val week: Int,
    val day: Int,
    val startedAt: Long,
    val completedAt: Long? = null,
    val durationSeconds: Int,
    val distanceMeters: Float,
    val completed: Boolean
)
