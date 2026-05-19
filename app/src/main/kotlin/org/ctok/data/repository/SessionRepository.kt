package org.ctok.data.repository

import kotlinx.coroutines.flow.Flow
import org.ctok.data.db.AppDatabase
import org.ctok.data.db.entity.RoutePointEntity
import org.ctok.data.db.entity.WorkoutSessionEntity

class SessionRepository(private val db: AppDatabase) {

    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> =
        db.sessionDao().observeAll()

    fun observeRecentSessions(): Flow<List<WorkoutSessionEntity>> =
        db.sessionDao().observeRecent()

    suspend fun isCompleted(programId: String, week: Int, day: Int): Boolean =
        db.sessionDao().findCompleted(programId, week, day) != null

    suspend fun startSession(
        programId: String, week: Int, day: Int
    ): Long {
        val entity = WorkoutSessionEntity(
            programId = programId,
            week = week,
            day = day,
            startedAt = System.currentTimeMillis(),
            durationSeconds = 0,
            distanceMeters = 0f,
            completed = false
        )
        return db.sessionDao().insert(entity)
    }

    suspend fun finishSession(
        sessionId: Long,
        durationSeconds: Int,
        distanceMeters: Float,
        completed: Boolean
    ) {
        val existing = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().update(
            existing.copy(
                completedAt = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                completed = completed
            )
        )
    }

    suspend fun addRoutePoint(point: RoutePointEntity) =
        db.routePointDao().insert(point)

    fun observeRoute(sessionId: Long): Flow<List<RoutePointEntity>> =
        db.routePointDao().observeRoute(sessionId)
}
