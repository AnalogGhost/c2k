package com.hackerapps.c2k.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

data class CompletedDay(
    @ColumnInfo(name = "week") val week: Int,
    @ColumnInfo(name = "day") val day: Int
)

@Dao
interface WorkoutSessionDao {

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun findById(id: Long): WorkoutSessionEntity?

    @Query("SELECT DISTINCT week, day FROM workout_sessions WHERE programId = :programId AND completed = 1")
    fun observeCompletedDays(programId: String): Flow<List<CompletedDay>>

    // Days completed only by a manual mark (used to offer "mark as not done").
    @Query("SELECT DISTINCT week, day FROM workout_sessions WHERE programId = :programId AND completed = 1 AND manual = 1")
    fun observeManualDays(programId: String): Flow<List<CompletedDay>>

    // Personal best excludes manual marks — they have no real duration (0s would always win).
    @Query("""
        SELECT * FROM workout_sessions
        WHERE programId = :programId AND week = :week AND day = :day AND completed = 1 AND manual = 0
        ORDER BY durationSeconds ASC
        LIMIT 1
    """)
    suspend fun getBestByDay(programId: String, week: Int, day: Int): WorkoutSessionEntity?

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Remove only manual marks for a day — never deletes a real completed run.
    @Query("DELETE FROM workout_sessions WHERE programId = :programId AND week = :week AND day = :day AND manual = 1")
    suspend fun deleteManualByDay(programId: String, week: Int, day: Int)

    @Query("DELETE FROM workout_sessions WHERE programId = :programId")
    suspend fun deleteByProgramId(programId: String)
}
