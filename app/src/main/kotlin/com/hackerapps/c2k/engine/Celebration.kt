package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.model.WorkoutPlan

/** What the completed-workout screen celebrates, if anything. */
enum class Celebration {
    /** The program's last workout, finished with every run done. */
    PROGRAM_FINISHED,

    /** The last workout of week 3, the first time any program gets there. */
    WEEK_THREE
}

object CelebrationTrigger {
    /**
     * The celebration for a workout that has just ended, or null. Only a finished workout with no
     * skipped run counts, so nothing fires mid-workout or for a session that won't advance the
     * program. Pre-C25K's week 3 is also its last, so finishing it is a program celebration.
     */
    fun celebration(
        state: WorkoutState,
        plan: WorkoutPlan,
        week: Int,
        day: Int,
        weekThreeCelebrated: Boolean
    ): Celebration? {
        if (state !is WorkoutState.Completed || state.skippedRun) return null
        if (week == plan.totalWeeks && day == plan.weeks.last().last().day) return Celebration.PROGRAM_FINISHED
        if (!weekThreeCelebrated && plan.weeks.size >= 3 && week == 3 && day == plan.weeks[2].last().day) {
            return Celebration.WEEK_THREE
        }
        return null
    }

    /**
     * Whether showing [celebration] for a workout in [week] uses up the one-off week-3 moment.
     * Finishing a program at week 3 or later does too, so the smaller week-3 card doesn't follow.
     */
    fun usesUpWeekThree(celebration: Celebration?, week: Int): Boolean = celebration != null && week >= 3
}
