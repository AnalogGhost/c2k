package com.hackerapps.c2k.engine

import kotlin.math.roundToInt

/**
 * Estimates calories burned from distance, duration, and body weight using a MET
 * (Metabolic Equivalent of Task) model. MET values are approximate, taken from the public
 * Compendium of Physical Activities, and looked up against the session's average speed —
 * this is a reasonable estimate, not a precise measurement (which would require heart rate
 * or lab equipment).
 *
 * calories = MET * weightKg * durationHours
 */
object CalorieCalculator {

    // Upper-bound average speed (km/h) -> MET value for that band. Bands run from a slow
    // walk up through fast running; the last entry is an open-ended upper bound.
    private val MET_BANDS: List<Pair<Float, Float>> = listOf(
        3.2f to 2.8f,             // slow walk, ~2 mph
        4.8f to 3.3f,             // walk, ~3 mph
        5.6f to 3.8f,             // walk, ~3.5 mph
        6.4f to 5.0f,             // very brisk walk, ~4 mph
        8.0f to 7.0f,             // jogging, general
        9.7f to 8.3f,             // running, ~5 mph
        11.3f to 9.8f,            // running, ~6 mph
        12.9f to 11.0f,           // running, ~7 mph
        Float.MAX_VALUE to 11.8f  // running, ~8 mph+
    )

    private fun metForSpeedKmh(speedKmh: Float): Float =
        MET_BANDS.first { speedKmh <= it.first }.second

    /**
     * Returns the estimated calories burned, or null if there's nothing to estimate
     * (no distance/duration recorded, or no weight set) rather than guessing.
     */
    fun estimateCalories(distanceMeters: Float, durationSeconds: Int, weightKg: Float): Int? {
        if (distanceMeters <= 0f || durationSeconds <= 0 || weightKg <= 0f) return null
        val speedKmh = (distanceMeters / 1000f) / (durationSeconds / 3600f)
        val met = metForSpeedKmh(speedKmh)
        val hours = durationSeconds / 3600f
        return (met * weightKg * hours).roundToInt()
    }
}
