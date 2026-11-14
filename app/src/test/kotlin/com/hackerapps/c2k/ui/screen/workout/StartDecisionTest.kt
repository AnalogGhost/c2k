package com.hackerapps.c2k.ui.screen.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDecisionTest {

    @Test
    fun a_fresh_screen_starts_the_workout() {
        assertEquals(StartDecision.START, startDecision(startedHere = false, startedBefore = false, serviceRunning = false))
    }

    @Test
    fun a_recreated_screen_reconnects_instead_of_starting_again() {
        // Rotation, theme or font change: the ViewModel survives, the composition doesn't.
        // Holds whether the workout is live, still starting, or already completed.
        for (running in listOf(true, false)) {
            assertEquals(StartDecision.RECONNECT, startDecision(startedHere = true, startedBefore = true, serviceRunning = running))
        }
    }

    @Test
    fun after_process_death_a_live_workout_is_reconnected() {
        assertEquals(StartDecision.RECONNECT, startDecision(startedHere = false, startedBefore = true, serviceRunning = true))
    }

    @Test
    fun after_process_death_a_lost_workout_is_not_restarted() {
        assertEquals(StartDecision.LEAVE, startDecision(startedHere = false, startedBefore = true, serviceRunning = false))
    }
}
