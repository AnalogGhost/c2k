package com.hackerapps.c2k.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.service.WorkoutService

data class NextWorkout(
    val programId: String,
    val displayName: String,
    val week: Int,
    val day: Int,
    val workoutDay: com.hackerapps.c2k.data.model.WorkoutDay
)

data class HomeUiState(
    val programs: List<WorkoutPlan> = Programs.all(),
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val workoutActive: Boolean = false,
    val activeWorkoutInfo: WorkoutService.WorkoutInfo? = null,
    val nextWorkout: NextWorkout? = null,
    val streak: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as C2KApp).sessionRepository
    private val prefs = UserPreferences(app)

    private val nextWorkoutFlow = prefs.lastProgramId
        .flatMapLatest { lastProgId ->
            if (lastProgId == null) flowOf(null)
            else {
                val plan = Programs.all().find { it.programId == lastProgId }
                if (plan == null) flowOf(null)
                else repo.observeCompletedDays(lastProgId).map { completedDays ->
                    computeNextWorkout(plan, completedDays)
                }
            }
        }

    val uiState = combine(
        repo.observeAllSessions(),
        WorkoutService.isRunning,
        WorkoutService.currentWorkout,
        nextWorkoutFlow
    ) { allSessions, active, workoutInfo, nextWorkout ->
        HomeUiState(
            recentSessions = allSessions.take(5),
            streak = computeStreak(allSessions),
            workoutActive = active,
            activeWorkoutInfo = workoutInfo,
            nextWorkout = if (active) null else nextWorkout
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun computeNextWorkout(
        plan: WorkoutPlan,
        completedDays: Set<Pair<Int, Int>>
    ): NextWorkout? {
        for ((weekIdx, days) in plan.weeks.withIndex()) {
            for (dayIdx in days.indices) {
                val week = weekIdx + 1; val day = dayIdx + 1
                if ((week to day) !in completedDays) {
                    return NextWorkout(plan.programId, plan.displayName, week, day,
                        plan.weeks[weekIdx][dayIdx])
                }
            }
        }
        return null
    }

    private fun computeStreak(sessions: List<WorkoutSessionEntity>): Int {
        val completedDays = sessions
            .filter { it.completed }
            .map { it.startedAt / MS_PER_DAY }
            .toSortedSet()

        if (completedDays.isEmpty()) return 0

        val today = System.currentTimeMillis() / MS_PER_DAY
        val yesterday = today - 1

        if (completedDays.last() < yesterday) return 0

        var streak = 1
        var expected = completedDays.last() - 1
        for (dayNum in completedDays.reversed().drop(1)) {
            if (dayNum == expected) { streak++; expected-- }
            else if (dayNum < expected) break
        }
        return streak
    }

    companion object {
        private const val MS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
