package com.hackerapps.c2k.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryStats(
    val totalSessions: Int,
    val completedSessions: Int,
    val totalKm: Float,
    val totalTimeSeconds: Int
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as C2KApp).sessionRepository

    val sessions: StateFlow<List<WorkoutSessionEntity>> = repo.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<HistoryStats> = sessions.map { list ->
        HistoryStats(
            totalSessions     = list.size,
            completedSessions = list.count { it.completed },
            totalKm           = list.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
            totalTimeSeconds  = list.sumOf { it.durationSeconds }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryStats(0, 0, 0f, 0))

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }

    fun buildGpx(session: WorkoutSessionEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val points = repo.getRoutePoints(session.id)
            onResult(generateGpx(session, points))
        }
    }

    private fun generateGpx(session: WorkoutSessionEntity, points: List<RoutePointEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val name = "C2K W${session.week}D${session.day} ${dateFormat.format(Date(session.startedAt))}"
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="C2K" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("""  <trk><name>$name</name><trkseg>""")
        for (pt in points) {
            val time = dateFormat.format(Date(pt.recordedAt))
            val ele = if (pt.altitudeMeters != null) "\n      <ele>${pt.altitudeMeters}</ele>" else ""
            sb.appendLine("""    <trkpt lat="${pt.latitude}" lon="${pt.longitude}">$ele
      <time>$time</time>
    </trkpt>""")
        }
        sb.appendLine("""  </trkseg></trk>""")
        sb.appendLine("""</gpx>""")
        return sb.toString()
    }
}
