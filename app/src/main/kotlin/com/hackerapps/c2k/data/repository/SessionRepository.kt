package com.hackerapps.c2k.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.db.dao.CompletedDay
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.location.DistanceCalculator

// Open so instrumented tests can substitute a subclass that fails finishSession() on demand,
// to verify WorkoutService's completion teardown still runs when the DB write throws.
open class SessionRepository(private val db: AppDatabase) {

    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> =
        db.sessionDao().observeAll()

    open suspend fun startSession(
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

    open suspend fun finishSession(
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

    /** Saves a running workout's progress so far, so it survives the app being closed. */
    open suspend fun checkpointSession(sessionId: Long, durationSeconds: Int, distanceMeters: Float) =
        db.sessionDao().checkpoint(sessionId, durationSeconds, distanceMeters)

    /**
     * Recovers workouts the app was closed during (by Android or a crash), started before
     * [beforeMillis] so a workout starting right now is never touched. Each keeps its last saved
     * time, with the distance rebuilt from its recorded route, which runs right up to the moment
     * the app closed; its completedAt stays null, marking it as not ended by the runner. One with
     * nothing to keep (closed within its first seconds) is deleted. Safe to run on every launch.
     * Returns how many workouts were recovered.
     */
    suspend fun recoverInterruptedSessions(beforeMillis: Long): Int {
        var recovered = 0
        for (session in db.sessionDao().getUnended(beforeMillis)) {
            val points = db.routePointDao().getRoute(session.id)
            if (session.durationSeconds == 0 && points.isEmpty()) {
                db.sessionDao().deleteById(session.id)
                continue
            }
            recovered++
            if (points.size >= 2) {
                val routed = DistanceCalculator.filteredDistanceMeters(points)
                if (routed > session.distanceMeters) {
                    db.sessionDao().update(session.copy(distanceMeters = routed))
                }
            }
        }
        return recovered
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

    /**
     * Re-derives every session's distance from its stored route points, applying the same
     * implied-speed filter the live tracker now uses, so sessions recorded before that
     * filter existed lose their teleporting-GPS kilometres (issue #30). Sessions without a
     * route (treadmill mode, GPS off) are left untouched. Idempotent, so it's safe to re-run
     * if interrupted.
     */
    suspend fun recomputeSessionDistances() {
        for (session in db.sessionDao().getAll()) {
            val points = db.routePointDao().getRoute(session.id)
            if (points.size < 2) continue
            val filtered = DistanceCalculator.filteredDistanceMeters(points)
            // 1 m tolerance: haversine vs the platform's geodesic distance differ by
            // fractions of a metre, which shouldn't rewrite every clean session.
            if (kotlin.math.abs(filtered - session.distanceMeters) > 1f) {
                db.sessionDao().update(session.copy(distanceMeters = filtered))
            }
        }
    }

    suspend fun deleteSession(sessionId: Long) =
        db.sessionDao().deleteById(sessionId)

    suspend fun resetProgress(programId: String) =
        db.sessionDao().deleteByProgramId(programId)
}
