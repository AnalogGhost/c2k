package com.hackerapps.c2k.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A runner's workout history as a file they keep, so it survives a reinstall or a new phone.
 * The same format is read and written by the iOS app, so a history can also move between the two.
 * Times are milliseconds since the epoch. Settings aren't included.
 */
data class Backup(val exportedAt: Long, val sessions: List<Session>) {

    data class Session(
        val programId: String,
        val week: Int,
        val day: Int,
        val startedAt: Long,
        val completedAt: Long?,
        val durationSeconds: Int,
        val distanceMeters: Double,
        val completed: Boolean,
        val route: List<Point>
    )

    data class Point(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double?,
        val speedMps: Double?,
        val recordedAt: Long
    )

    /** Why a file couldn't be read as a backup. */
    class InvalidBackupException(message: String) : Exception(message)

    /** A backup written by a newer version of the app, in a format this one doesn't know. */
    class NewerBackupException : Exception("backup is from a newer version")

    companion object {
        const val FORMAT = "c2k-backup"
        const val VERSION = 1
        const val MIME_TYPE = "application/json"

        fun fileName(exportedAt: java.time.LocalDate): String = "c2k-backup-$exportedAt.json"

        fun decode(text: String): Backup {
            val root = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw InvalidBackupException("not JSON")
            }
            if (root.optString("format") != FORMAT) throw InvalidBackupException("not a C2K backup")
            val version = root.optInt("version", -1)
            if (version < 1) throw InvalidBackupException("no version")
            if (version > VERSION) throw NewerBackupException()
            return try {
                val sessions = root.getJSONArray("sessions")
                Backup(
                    exportedAt = root.optLong("exportedAt", 0L),
                    sessions = (0 until sessions.length()).map { decodeSession(sessions.getJSONObject(it)) }
                )
            } catch (e: JSONException) {
                throw InvalidBackupException(e.message ?: "malformed")
            }
        }

        private fun decodeSession(o: JSONObject): Session {
            val route = o.optJSONArray("route") ?: JSONArray()
            return Session(
                programId = o.getString("programId"),
                week = o.getInt("week"),
                day = o.getInt("day"),
                startedAt = o.getLong("startedAt"),
                completedAt = if (o.isNull("completedAt")) null else o.getLong("completedAt"),
                durationSeconds = o.getInt("durationSeconds"),
                distanceMeters = o.getDouble("distanceMeters"),
                completed = o.getBoolean("completed"),
                route = (0 until route.length()).map { decodePoint(route.getJSONObject(it)) }
            )
        }

        private fun decodePoint(o: JSONObject) = Point(
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            altitudeMeters = if (o.isNull("altitudeMeters")) null else o.getDouble("altitudeMeters"),
            speedMps = if (o.isNull("speedMps")) null else o.getDouble("speedMps"),
            recordedAt = o.getLong("recordedAt")
        )
    }

    fun encode(): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("sessions", JSONArray(sessions.map { it.toJson() }))
        .toString(2)

    private fun Session.toJson() = JSONObject()
        .put("programId", programId)
        .put("week", week)
        .put("day", day)
        .put("startedAt", startedAt)
        .put("completedAt", completedAt ?: JSONObject.NULL)
        .put("durationSeconds", durationSeconds)
        .put("distanceMeters", distanceMeters)
        .put("completed", completed)
        .put("route", JSONArray(route.map { it.toJson() }))

    private fun Point.toJson() = JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("altitudeMeters", altitudeMeters ?: JSONObject.NULL)
        .put("speedMps", speedMps ?: JSONObject.NULL)
        .put("recordedAt", recordedAt)
}
