package com.hackerapps.c2k.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.hackerapps.c2k.data.backup.SessionWithRoute
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.dao.CompletedDay
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

data class ImportResult(val added: Int, val skipped: Int)

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

    suspend fun getBestForDay(programId: String, week: Int, day: Int): WorkoutSessionEntity? =
        db.sessionDao().getBestByDay(programId, week, day)

    suspend fun deleteSession(sessionId: Long) =
        db.sessionDao().deleteById(sessionId)

    suspend fun resetProgress(programId: String) =
        db.sessionDao().deleteByProgramId(programId)

    // ── Backup export / import ──────────────────────────────────────────────

    suspend fun getAllSessionsWithRoutes(): List<SessionWithRoute> =
        db.sessionDao().getAll().map { session ->
            SessionWithRoute(session, db.routePointDao().getRoute(session.id))
        }

    /**
     * Merges imported sessions into the database, skipping any that already exist (matched by
     * program/week/day/startedAt) so re-importing the same backup can't create duplicates.
     * Each new session is inserted first, then its route points are re-parented to the new id.
     */
    suspend fun importSessions(data: List<SessionWithRoute>): ImportResult {
        val existingKeys = db.sessionDao().getAll().map { signature(it) }.toHashSet()
        var added = 0
        var skipped = 0
        for (swr in data) {
            val key = signature(swr.session)
            if (!existingKeys.add(key)) { skipped++; continue }
            val newId = db.sessionDao().insert(swr.session.copy(id = 0))
            if (swr.routePoints.isNotEmpty()) {
                db.routePointDao().insertAll(
                    swr.routePoints.map { it.copy(id = 0, sessionId = newId) }
                )
            }
            added++
        }
        return ImportResult(added, skipped)
    }

    private fun signature(s: WorkoutSessionEntity) =
        "${s.programId}|${s.week}|${s.day}|${s.startedAt}"
}
