package com.hackerapps.c2k.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as C2KApp).sessionRepository

    val sessions = repo.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<WorkoutSessionEntity>())

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }
}
