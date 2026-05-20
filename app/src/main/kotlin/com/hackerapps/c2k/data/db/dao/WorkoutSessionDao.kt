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

    @Query("SELECT * FROM workout_sessions WHERE completed = 1 ORDER BY startedAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun findById(id: Long): WorkoutSessionEntity?

    @Query("SELECT DISTINCT week, day FROM workout_sessions WHERE programId = :programId AND completed = 1")
    fun observeCompletedDays(programId: String): Flow<List<CompletedDay>>

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
