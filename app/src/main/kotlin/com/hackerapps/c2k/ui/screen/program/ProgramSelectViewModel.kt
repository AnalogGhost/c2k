package com.hackerapps.c2k.ui.screen.program

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutPlan

data class ProgramSelectUiState(
    val plan: WorkoutPlan? = null,
    val completedDays: Set<Pair<Int, Int>> = emptySet()
)

class ProgramSelectViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val programId: String = savedStateHandle["programId"]!!
    private val plan = Programs.byId(programId)

    val uiState: StateFlow<ProgramSelectUiState> =
        (app as C2KApp).sessionRepository
            .observeCompletedDays(programId)
            .map { completed -> ProgramSelectUiState(plan = plan, completedDays = completed) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ProgramSelectUiState(plan = plan)
            )
}
