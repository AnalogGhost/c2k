package com.hackerapps.c2k.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.dao.CompletedDay
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

class SessionRepository(private val db: AppDatabase) {

    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> =
        db.sessionDao().observeAll()

    fun observeRecentSessions(): Flow<List<WorkoutSessionEntity>> =
        db.sessionDao().observeRecent()

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

    fun observeCompletedDays(programId: String): Flow<Set<Pair<Int, Int>>> =
        db.sessionDao().observeCompletedDays(programId)
            .map { list -> list.map { it.week to it.day }.toSet() }

    suspend fun deleteSession(sessionId: Long) =
        db.sessionDao().deleteById(sessionId)
}
