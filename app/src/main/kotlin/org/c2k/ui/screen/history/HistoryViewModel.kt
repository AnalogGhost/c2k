package org.c2k.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.c2k.C2KApp
import org.c2k.data.db.entity.WorkoutSessionEntity

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    val sessions = (app as C2KApp).sessionRepository
        .observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<WorkoutSessionEntity>())
}
