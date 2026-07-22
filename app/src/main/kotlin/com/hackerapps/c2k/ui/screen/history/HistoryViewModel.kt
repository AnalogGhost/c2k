package com.hackerapps.c2k.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.engine.CalorieCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryStats(
    val totalSessions: Int,
    val completedSessions: Int,
    val totalKm: Float,
    val totalTimeSeconds: Int,
    val totalCalories: Int?,
    val fastestPaceSecPerKm: Float?,
    val longestRunMeters: Float?
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as C2KApp).sessionRepository
    private val prefs = UserPreferences(app)

    val sessions: StateFlow<List<WorkoutSessionEntity>> = repo.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weightKg: StateFlow<Float?> = prefs.weightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<HistoryStats> = combine(sessions, weightKg) { list, kg -> computeStats(list, kg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryStats(0, 0, 0f, 0, null, null, null))

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }

    fun buildGpx(session: WorkoutSessionEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val points = repo.getRoutePoints(session.id)
            onResult(generateGpx(session, points))
        }
    }

    companion object {
        internal fun computeStats(sessions: List<WorkoutSessionEntity>, weightKg: Float? = null): HistoryStats {
            val eligible = sessions.filter { it.completed && it.distanceMeters > 0f }
            return HistoryStats(
                totalSessions       = sessions.size,
                completedSessions   = sessions.count { it.completed },
                totalKm             = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
                totalTimeSeconds    = sessions.sumOf { it.durationSeconds },
                totalCalories       = weightKg?.let { kg ->
                    sessions.sumOf { s -> CalorieCalculator.estimateCalories(s.distanceMeters, s.durationSeconds, kg) ?: 0 }
                },
                fastestPaceSecPerKm = eligible.minOfOrNull { it.durationSeconds / (it.distanceMeters / 1000f) },
                longestRunMeters    = eligible.maxOfOrNull { it.distanceMeters }
            )
        }

        internal fun generateGpx(session: WorkoutSessionEntity, points: List<RoutePointEntity>): String {
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
}
