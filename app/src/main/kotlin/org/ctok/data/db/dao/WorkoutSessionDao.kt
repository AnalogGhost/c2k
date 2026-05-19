package org.ctok.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.ctok.data.db.entity.WorkoutSessionEntity

@Dao
interface WorkoutSessionDao {

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE completed = 1 ORDER BY startedAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<WorkoutSessionEntity>>

    @Query("""
        SELECT * FROM workout_sessions
        WHERE programId = :programId AND week = :week AND day = :day AND completed = 1
        LIMIT 1
    """)
    suspend fun findCompleted(programId: String, week: Int, day: Int): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun findById(id: Long): WorkoutSessionEntity?

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Update
    suspend fun update(session: WorkoutSessionEntity)
}
