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
        // Local calendar, not a raw UTC epoch-millis division: HistoryScreen already displays
        // session dates via SimpleDateFormat(..., Locale.getDefault()), which is local-timezone
        // aware. Bucketing streaks by UTC instead would disagree with what the user sees there,
        // and — for any non-UTC timezone — can misplace a session near a week boundary into the
        // "wrong" week, silently breaking or inflating the streak.
        // Weeks are ISO (Monday-start): epoch day -3 was a Monday (1969-12-29), so shifting by 3
        // aligns the floor division to Monday boundaries.
        private fun localWeekNumber(millis: Long): Long {
            val epochDay = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            return Math.floorDiv(epochDay + 3, 7)
        }

        internal fun computeNextWorkout(
            plan: WorkoutPlan,
            completedDays: Set<Pair<Int, Int>>
        ): NextWorkout? {
            val (week, day) = plan.nextWorkout(completedDays) ?: return null
            return NextWorkout(plan.programId, plan.displayName, week, day,
                plan.weeks[week - 1][day - 1])
        }

        // Weekly, not daily (issue #29): every program schedules ~3 runs a week with rest days
        // between them, so a day-based streak was guaranteed to reset on every rest day. A week
        // counts toward the streak if it has at least one completed workout, and the streak is
        // alive as long as the latest such week is the current or the previous one (the current
        // week gets a grace period — its workout may simply not have happened yet).
        internal fun computeStreak(sessions: List<WorkoutSessionEntity>, nowMillis: Long): Int {
            val completedWeeks = sessions
                .filter { it.completed }
                .map { localWeekNumber(it.startedAt) }
                .toSortedSet()

            if (completedWeeks.isEmpty()) return 0

            val thisWeek = localWeekNumber(nowMillis)
            val lastWeek = thisWeek - 1

            if (completedWeeks.last() < lastWeek) return 0

            var streak = 1
            var expected = completedWeeks.last() - 1
            for (weekNum in completedWeeks.toList().reversed().drop(1)) {
                if (weekNum == expected) { streak++; expected-- }
                else if (weekNum < expected) break
            }
            return streak
        }
    }
}
