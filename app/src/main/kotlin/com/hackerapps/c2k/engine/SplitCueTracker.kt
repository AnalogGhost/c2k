package com.hackerapps.c2k.engine

import com.hackerapps.c2k.engine.tts.TtsAnnouncement

/**
 * Decides when to announce distance and pace: once each time the run's total distance passes
 * another [splitMeters]. Pace is the average over the split just finished (moving time per
 * kilometre), which is far steadier than the GPS's instantaneous speed.
 *
 * Fed the cumulative GPS distance and the session's elapsed time, both of which exclude pauses,
 * so a split spanning a pause still gets a true pace.
 */
class SplitCueTracker(private val splitMeters: Int) {

    init {
        require(splitMeters > 0) { "splitMeters must be positive" }
    }

    private var splitsAnnounced = 0
    private var lastSplitElapsedSeconds = 0
    private var lastSplitMeters = 0

    /**
     * Returns the announcement for a split passed since the last call, or null. If one GPS jump
     * passes several splits at once, only the latest is announced, with the pace averaged over all
     * of them.
     */
    fun onProgress(totalMeters: Float, elapsedSeconds: Int): TtsAnnouncement.DistanceSplit? {
        val splits = (totalMeters / splitMeters).toInt()
        if (splits <= splitsAnnounced) return null

        val meters = splits * splitMeters
        val coveredMeters = meters - lastSplitMeters
        val seconds = elapsedSeconds - lastSplitElapsedSeconds
        splitsAnnounced = splits
        lastSplitMeters = meters
        lastSplitElapsedSeconds = elapsedSeconds

        // A pace from a few seconds (GPS catching up after a gap) or slower than a slow walk
        // (~30 min/km) says nothing useful; announce the distance alone.
        val paceSecondsPerKm = (seconds * 1000L / coveredMeters).toInt()
            .takeIf { seconds > 0 && it in MIN_PACE_SECONDS_PER_KM..MAX_PACE_SECONDS_PER_KM }
        return TtsAnnouncement.DistanceSplit(meters, paceSecondsPerKm)
    }

    private companion object {
        // Faster than 2:00/km is a GPS error, not a beginner runner.
        const val MIN_PACE_SECONDS_PER_KM = 120
        const val MAX_PACE_SECONDS_PER_KM = 30 * 60
    }
}
