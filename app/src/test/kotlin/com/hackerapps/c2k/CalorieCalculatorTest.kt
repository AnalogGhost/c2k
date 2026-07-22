package com.hackerapps.c2k

import com.hackerapps.c2k.engine.CalorieCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun returns_null_when_distance_is_zero() {
        assertNull(CalorieCalculator.estimateCalories(0f, 1800, 70f))
    }

    @Test
    fun returns_null_when_duration_is_zero() {
        assertNull(CalorieCalculator.estimateCalories(2000f, 0, 70f))
    }

    @Test
    fun returns_null_when_weight_is_zero_or_negative() {
        assertNull(CalorieCalculator.estimateCalories(2000f, 1800, 0f))
        assertNull(CalorieCalculator.estimateCalories(2000f, 1800, -5f))
    }

    @Test
    fun slow_walk_uses_low_met_value() {
        // 2000m in 1800s = 4 km/h, a slow walk
        val calories = CalorieCalculator.estimateCalories(2000f, 1800, 70f)
        assertTrue(calories != null && calories in 100..200)
    }

    @Test
    fun running_pace_uses_higher_met_than_walking_pace() {
        val distance = 3000f
        val weight = 70f
        val walkCalories = CalorieCalculator.estimateCalories(distance, 2700, weight) // 4 km/h
        val runCalories = CalorieCalculator.estimateCalories(distance, 900, weight)   // 12 km/h
        assertTrue(walkCalories != null && runCalories != null && runCalories > walkCalories)
    }

    @Test
    fun result_scales_with_weight() {
        val lighter = CalorieCalculator.estimateCalories(5000f, 1800, 50f)
        val heavier = CalorieCalculator.estimateCalories(5000f, 1800, 100f)
        assertTrue(lighter != null && heavier != null && heavier > lighter)
    }

    @Test
    fun speed_at_band_boundary_does_not_crash() {
        // Exactly 8.0 km/h average speed sits on a MET band boundary.
        val calories = CalorieCalculator.estimateCalories(8000f, 3600, 70f)
        assertTrue(calories != null && calories > 0)
    }

    @Test
    fun very_fast_speed_uses_open_ended_top_band() {
        // 20 km/h average — well past the last named band.
        val calories = CalorieCalculator.estimateCalories(20000f, 3600, 70f)
        assertEquals(826, calories) // 11.8 MET * 70kg * 1h, rounded
    }
}
