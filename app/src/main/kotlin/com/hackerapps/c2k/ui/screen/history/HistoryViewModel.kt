package com.hackerapps.c2k.ui.screen.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hackerapps.c2k.BuildConfig
import com.hackerapps.c2k.C2KApp
import com.hackerapps.c2k.R
import com.hackerapps.c2k.data.backup.HistoryBackup
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

    val stats: StateFlow<HistoryStats> = sessions.map { list -> computeStats(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryStats(0, 0, 0f, 0))

    // One-shot message (export/import result or error) for the UI to surface, then clear.
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    fun clearBackupMessage() { _backupMessage.value = null }

    fun exportHistory(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val data = repo.getAllSessionsWithRoutes()
                val json = HistoryBackup.serialize(data, BuildConfig.VERSION_CODE, System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: throw IllegalStateException("Could not open file for writing")
                }
                _backupMessage.value = app.getString(R.string.history_backup_export_success, data.size)
            } catch (e: Exception) {
                _backupMessage.value = app.getString(R.string.history_backup_error, e.message ?: "")
            }
        }
    }

    fun importHistory(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("Could not open file for reading")
                }
                val parsed = HistoryBackup.parse(text)
                val result = repo.importSessions(parsed)
                _backupMessage.value =
                    app.getString(R.string.history_backup_import_success, result.added, result.skipped)
            } catch (e: Exception) {
                _backupMessage.value = app.getString(R.string.history_backup_error, e.message ?: "")
            }
        }
    }

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
        internal fun computeStats(sessions: List<WorkoutSessionEntity>): HistoryStats = HistoryStats(
            totalSessions     = sessions.size,
            completedSessions = sessions.count { it.completed },
            totalKm           = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
            totalTimeSeconds  = sessions.sumOf { it.durationSeconds }
        )

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
