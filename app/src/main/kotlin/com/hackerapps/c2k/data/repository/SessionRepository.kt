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

    suspend fun getRoutePoints(sessionId: Long): List<RoutePointEntity> =
        db.routePointDao().getRoute(sessionId)

    fun observeCompletedDays(programId: String): Flow<Set<Pair<Int, Int>>> =
        db.sessionDao().observeCompletedDays(programId)
            .map { list -> list.map { it.week to it.day }.toSet() }

    fun observeManualDays(programId: String): Flow<Set<Pair<Int, Int>>> =
        db.sessionDao().observeManualDays(programId)
            .map { list -> list.map { it.week to it.day }.toSet() }

    // Marks a day complete without an actual run (counts toward progress/streak; zero duration
    // and distance so it stays out of personal-best and distance/time stats).
    suspend fun markDayDone(programId: String, week: Int, day: Int) {
        val now = System.currentTimeMillis()
        db.sessionDao().insert(
            WorkoutSessionEntity(
                programId = programId,
                week = week,
                day = day,
                startedAt = now,
                completedAt = now,
                durationSeconds = 0,
                distanceMeters = 0f,
                completed = true,
                manual = true
            )
        )
    }

    // Removes only manual marks for a day; a real completed run for that day is left intact.
    suspend fun unmarkDay(programId: String, week: Int, day: Int) =
        db.sessionDao().deleteManualByDay(programId, week, day)

    suspend fun getBestForDay(programId: String, week: Int, day: Int): WorkoutSessionEntity? =
        db.sessionDao().getBestByDay(programId, week, day)

    suspend fun deleteSession(sessionId: Long) =
        db.sessionDao().deleteById(sessionId)

    suspend fun resetProgress(programId: String) =
        db.sessionDao().deleteByProgramId(programId)
}
