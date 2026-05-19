package org.c2k.data.model

data class Interval(
    val type: IntervalType,
    val durationSeconds: Int,
    val announcement: String = defaultAnnouncement(type, durationSeconds)
)

private fun defaultAnnouncement(type: IntervalType, durationSeconds: Int): String {
    val mins = durationSeconds / 60
    val secs = durationSeconds % 60
    val duration = when {
        mins > 0 && secs > 0 -> "$mins minute${if (mins > 1) "s" else ""} and $secs seconds"
        mins > 0 -> "$mins minute${if (mins > 1) "s" else ""}"
        else -> "$secs seconds"
    }
    return when (type) {
        IntervalType.WARMUP   -> "Begin warm-up walk"
        IntervalType.RUN      -> "Start running for $duration"
        IntervalType.WALK     -> "Walk for $duration"
        IntervalType.COOLDOWN -> "Begin cool-down walk"
    }
}
