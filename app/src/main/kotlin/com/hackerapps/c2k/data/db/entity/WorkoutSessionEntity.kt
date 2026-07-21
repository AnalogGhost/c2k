package com.hackerapps.c2k.data.db.entity

import androidx.room.ColumnInfo
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
    val completed: Boolean,
    // True for a day the user manually marked done (not an actual run). Counts toward program
    // progress and streak like a real day, but is excluded from personal-best and distance/time
    // stats (it has no duration/distance). defaultValue keeps the entity schema in sync with the
    // v1->2 migration's ADD COLUMN ... DEFAULT 0.
    @ColumnInfo(defaultValue = "0") val manual: Boolean = false
)
