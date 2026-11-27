package com.hackerapps.c2k.ui.share

import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/** What the share image shows for one workout. */
data class ShareCardStats(
    val programId: String,
    val week: Int,
    val day: Int,
    /** The program's last workout, finished: the image leads with the program instead of the day. */
    val finishedProgram: Boolean,
    val durationSeconds: Int,
    val distanceMeters: Float,
    val startedAt: Long
) {
    /** Average pace over the whole workout, walks included, or null without a usable distance. */
    val paceSecondsPerKm: Int?
        get() = if (distanceMeters < 100f || durationSeconds <= 0) null
        else (durationSeconds / (distanceMeters / 1000.0)).roundToInt()

    companion object {
        fun of(session: WorkoutSessionEntity): ShareCardStats {
            val plan = Programs.byId(session.programId)
            val lastDay = session.week == plan.totalWeeks && session.day == plan.weeks.last().last().day
            return ShareCardStats(
                programId = session.programId, week = session.week, day = session.day,
                finishedProgram = session.completed && lastDay,
                durationSeconds = session.durationSeconds, distanceMeters = session.distanceMeters,
                startedAt = session.startedAt
            )
        }
    }
}

/** A point of [RouteOutline], in the unit square. */
data class OutlinePoint(val x: Double, val y: Double)

/** A run's GPS track flattened into a shape for the share image: no map, nothing fetched. */
object RouteOutline {
    /**
     * The points scaled into the unit square with their shape kept, centred on the shorter side,
     * north up, and thinned to at most [maxPoints]. Empty when there's no distance to draw.
     */
    fun normalized(latitudes: List<Double>, longitudes: List<Double>, maxPoints: Int = 400): List<OutlinePoint> {
        val count = minOf(latitudes.size, longitudes.size)
        if (count < 2) return emptyList()
        val meanLat = latitudes.take(count).average()
        val lonScale = cos(Math.toRadians(meanLat))
        var points = (0 until count).map { OutlinePoint(longitudes[it] * lonScale, -latitudes[it]) }
        if (count > maxPoints) {
            val step = (count - 1).toDouble() / (maxPoints - 1)
            val all = points
            points = (0 until maxPoints).map { all[(it * step).roundToInt()] }
        }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val span = max(maxX - minX, maxY - minY)
        if (span <= 1e-7) return emptyList() // about a centimetre: standing still
        val offsetX = (span - (maxX - minX)) / 2
        val offsetY = (span - (maxY - minY)) / 2
        return points.map { OutlinePoint((it.x - minX + offsetX) / span, (it.y - minY + offsetY) / span) }
    }

    fun normalized(route: List<RoutePointEntity>, maxPoints: Int = 400): List<OutlinePoint> =
        normalized(route.map { it.latitude }, route.map { it.longitude }, maxPoints)
}

/** The share image's backdrop. */
sealed interface ShareBackground {
    /** One of the bundled CC0 photos, numbered from 1 (see CREDITS.md). */
    data class Photo(val number: Int) : ShareBackground

    /** A colour gradient drawn in code, numbered from 0. */
    data class Gradient(val number: Int) : ShareBackground

    /** A photo the runner picked. */
    data object Custom : ShareBackground

    /** Nothing: a transparent image to lay over another photo. */
    data object Sticker : ShareBackground

    companion object {
        const val PHOTO_COUNT = 13
        const val GRADIENT_COUNT = 3
        val builtIn: List<ShareBackground> =
            (0 until GRADIENT_COUNT).map(::Gradient) + (1..PHOTO_COUNT).map(::Photo)
    }
}
