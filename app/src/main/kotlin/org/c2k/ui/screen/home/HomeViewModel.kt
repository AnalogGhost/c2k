package org.c2k.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.c2k.C2KApp
import org.c2k.data.db.entity.WorkoutSessionEntity
import org.c2k.data.model.Programs
import org.c2k.data.model.WorkoutPlan

data class HomeUiState(
    val programs: List<WorkoutPlan> = Programs.all(),
    val recentSessions: List<WorkoutSessionEntity> = emptyList()
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as C2KApp).sessionRepository

    val uiState = repo.observeRecentSessions()
        .map { sessions -> HomeUiState(recentSessions = sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
