package com.hackerapps.c2k.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.data.prefs.RepeatedWeek
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.ProgressionAdvisor
import com.hackerapps.c2k.engine.ProgressionSuggestion
import com.hackerapps.c2k.engine.StreakCalculator
import com.hackerapps.c2k.service.WorkoutService

data class NextWorkout(
    val programId: String,
    val displayName: String,
    val week: Int,
    val day: Int,
    val workoutDay: WorkoutDay
)

data class HomeUiState(
    val programs: List<WorkoutPlan> = Programs.all(),
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val workoutActive: Boolean = false,
    val activeWorkoutInfo: WorkoutService.WorkoutInfo? = null,
    val nextWorkout: NextWorkout? = null,
    val suggestion: ProgressionSuggestion? = null,
    val streak: Int = 0
)

private data class Progress(
    val nextWorkout: NextWorkout? = null,
    val suggestion: ProgressionSuggestion? = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as C2KApp).sessionRepository
    private val prefs = UserPreferences(app)

    private val progressFlow = combine(
        prefs.lastProgramId,
        prefs.repeatedWeek,
        prefs.progressionDismissedAt,
        repo.observeAllSessions()
    ) { lastProgId, repeated, dismissedAt, sessions ->
        val plan = Programs.all().find { it.programId == lastProgId } ?: return@combine Progress()
        val completedDays = sessions
            .filter { it.programId == plan.programId && it.completed }
            .map { it.week to it.day }
            .toSet()
        val next = computeNextWorkout(plan, completedDays, repeated, sessions)
        Progress(
            nextWorkout = next,
            suggestion = next?.let {
                ProgressionAdvisor.suggest(plan, it.week, it.day, sessions, dismissedAt)
            }
        )
    }

    val uiState = combine(
        repo.observeAllSessions(),
        WorkoutService.isRunning,
        WorkoutService.currentWorkout,
        progressFlow
    ) { allSessions, active, workoutInfo, progress ->
        HomeUiState(
            recentSessions = allSessions.take(5),
            streak = StreakCalculator.compute(allSessions, System.currentTimeMillis()),
            workoutActive = active,
            activeWorkoutInfo = workoutInfo,
            nextWorkout = if (active) null else progress.nextWorkout,
            suggestion = if (active) null else progress.suggestion
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun repeatWeek(programId: String, week: Int) {
        viewModelScope.launch {
            prefs.setRepeatedWeek(RepeatedWeek(programId, week, System.currentTimeMillis()))
        }
    }

    fun tryPreC25K() {
        viewModelScope.launch { prefs.setLastProgramId(Programs.ID_PRE_C25K) }
    }

    fun dismissSuggestion() {
        viewModelScope.launch { prefs.setProgressionDismissedAt(System.currentTimeMillis()) }
    }

    companion object {
        internal fun computeNextWorkout(
            plan: WorkoutPlan,
            completedDays: Set<Pair<Int, Int>>,
            repeated: RepeatedWeek? = null,
            sessions: List<WorkoutSessionEntity> = emptyList()
        ): NextWorkout? {
            // A week the runner chose to repeat comes first; once it's done again, the plan's
            // usual next workout (the day they were stuck on) takes over.
            val (week, day) = ProgressionAdvisor.nextInRepeatedWeek(plan, repeated, sessions)
                ?: plan.nextWorkout(completedDays)
                ?: return null
            return NextWorkout(plan.programId, plan.displayName, week, day,
                plan.weeks[week - 1][day - 1])
        }
    }
}
