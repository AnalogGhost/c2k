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
import com.hackerapps.c2k.data.model.WorkoutDay
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.service.WorkoutService
import java.time.Instant
import java.time.ZoneId

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
            streak = computeStreak(allSessions, System.currentTimeMillis()),
            workoutActive = active,
            activeWorkoutInfo = workoutInfo,
            nextWorkout = if (active) null else nextWorkout
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        // Local calendar day, not a raw UTC epoch-millis division: HistoryScreen already displays
        // session dates via SimpleDateFormat(..., Locale.getDefault()), which is local-timezone
        // aware. Bucketing streaks by UTC day instead would disagree with what the user sees
        // there, and — for any non-UTC timezone — can misplace a late-evening/early-morning
        // session onto the "wrong" side of the day boundary relative to their actual calendar day,
        // silently breaking or inflating the streak.
        private fun localDayNumber(millis: Long): Long =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        internal fun computeNextWorkout(
            plan: WorkoutPlan,
            completedDays: Set<Pair<Int, Int>>
        ): NextWorkout? {
            val (week, day) = plan.nextWorkout(completedDays) ?: return null
            return NextWorkout(plan.programId, plan.displayName, week, day,
                plan.weeks[week - 1][day - 1])
        }

        internal fun computeStreak(sessions: List<WorkoutSessionEntity>, nowMillis: Long): Int {
            val completedDays = sessions
                .filter { it.completed }
                .map { localDayNumber(it.startedAt) }
                .toSortedSet()

            if (completedDays.isEmpty()) return 0

            val today = localDayNumber(nowMillis)
            val yesterday = today - 1

            if (completedDays.last() < yesterday) return 0

            var streak = 1
            var expected = completedDays.last() - 1
            for (dayNum in completedDays.toList().reversed().drop(1)) {
                if (dayNum == expected) { streak++; expected-- }
                else if (dayNum < expected) break
            }
            return streak
        }
    }
}
