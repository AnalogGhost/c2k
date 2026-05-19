package org.ctok.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.ctok.CtoKApp
import org.ctok.data.db.entity.WorkoutSessionEntity

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    val sessions = (app as CtoKApp).sessionRepository
        .observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<WorkoutSessionEntity>())
}
