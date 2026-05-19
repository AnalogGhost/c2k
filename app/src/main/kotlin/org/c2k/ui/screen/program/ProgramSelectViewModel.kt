package org.c2k.ui.screen.program

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.c2k.C2KApp
import org.c2k.data.model.Programs
import org.c2k.data.model.WorkoutPlan

data class ProgramSelectUiState(
    val plan: WorkoutPlan? = null,
    val completedDays: Set<Pair<Int, Int>> = emptySet()  // (week, day) pairs
)

class ProgramSelectViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val programId: String = savedStateHandle["programId"]!!
    private val repo = (app as C2KApp).sessionRepository

    private val _uiState = MutableStateFlow(ProgramSelectUiState())
    val uiState: StateFlow<ProgramSelectUiState> = _uiState.asStateFlow()

    init {
        val plan = Programs.byId(programId)
        _uiState.value = _uiState.value.copy(plan = plan)
        viewModelScope.launch { loadCompletedDays(plan) }
    }

    private suspend fun loadCompletedDays(plan: WorkoutPlan) {
        val completed = mutableSetOf<Pair<Int, Int>>()
        plan.weeks.forEachIndexed { wi, week ->
            week.forEachIndexed { di, _ ->
                val w = wi + 1; val d = di + 1
                if (repo.isCompleted(programId, w, d)) completed.add(w to d)
            }
        }
        _uiState.value = _uiState.value.copy(completedDays = completed)
    }
}
