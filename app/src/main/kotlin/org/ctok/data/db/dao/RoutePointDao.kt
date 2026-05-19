package org.ctok.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ctok.data.db.entity.RoutePointEntity

@Dao
interface RoutePointDao {

    @Insert
    suspend fun insert(point: RoutePointEntity)

    @Insert
    suspend fun insertAll(points: List<RoutePointEntity>)

    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY recordedAt ASC")
    fun observeRoute(sessionId: Long): Flow<List<RoutePointEntity>>

    @Query("SELECT COUNT(*) FROM route_points WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int
}
