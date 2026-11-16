package com.hackerapps.c2k.ui.screen.history

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shares a route as a real .gpx file. Map apps (OsmAnd, Organic Maps, CoMaps) only import a
 * GPX attachment; the route used to go out as message text, which they could not open.
 */
object GpxExport {
    const val MIME_TYPE = "application/gpx+xml"
    private const val DIR = "exports"

    /** e.g. `c2k-route-w3d2-2026-11-16-0730.gpx`, in the runner's local time. */
    fun fileName(session: WorkoutSessionEntity, zone: TimeZone = TimeZone.getDefault()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).apply { timeZone = zone }
            .format(Date(session.startedAt))
        return "c2k-route-w${session.week}d${session.day}-$stamp.gpx"
    }

    /** Writes [gpx] to the shared cache folder, replacing earlier exports. Call off the main thread. */
    fun writeFile(context: Context, session: WorkoutSessionEntity, gpx: String): File {
        val dir = File(context.cacheDir, DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        return File(dir, fileName(session)).apply { writeText(gpx) }
    }

    fun shareIntent(context: Context, file: File, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            // ClipData carries the read grant through the chooser to the app the runner picks.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
