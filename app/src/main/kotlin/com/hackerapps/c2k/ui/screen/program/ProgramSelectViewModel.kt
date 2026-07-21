package com.hackerapps.c2k.ui.screen.program

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutPlan

data class ProgramSelectUiState(
    val plan: WorkoutPlan? = null,
    val completedDays: Set<Pair<Int, Int>> = emptySet(),
    // Subset of completedDays whose completion came only from a manual mark (offer "un-mark").
    val manualDays: Set<Pair<Int, Int>> = emptySet()
) {
    val nextIncompleteDay: Pair<Int, Int>? get() {
        val p = plan ?: return null
        for ((wIdx, days) in p.weeks.withIndex()) {
            for (dIdx in days.indices) {
                val w = wIdx + 1; val d = dIdx + 1
                if ((w to d) !in completedDays) return w to d
            }
        }
        return null
    }
}

class ProgramSelectViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val programId: String = savedStateHandle["programId"]!!
    private val plan = Programs.byId(programId)
    private val repo = (app as C2KApp).sessionRepository

    val uiState: StateFlow<ProgramSelectUiState> =
        combine(
            repo.observeCompletedDays(programId),
            repo.observeManualDays(programId)
        ) { completed, manual ->
            ProgramSelectUiState(plan = plan, completedDays = completed, manualDays = manual)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramSelectUiState(plan = plan)
        )

    fun markDayDone(week: Int, day: Int) {
        viewModelScope.launch { repo.markDayDone(programId, week, day) }
    }

    fun unmarkDay(week: Int, day: Int) {
        viewModelScope.launch { repo.unmarkDay(programId, week, day) }
    }

    fun resetProgress() {
        viewModelScope.launch { repo.resetProgress(programId) }
    }
}
