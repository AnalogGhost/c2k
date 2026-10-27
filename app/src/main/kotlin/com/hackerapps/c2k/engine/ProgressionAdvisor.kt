package com.hackerapps.c2k.engine

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.data.model.Programs
import com.hackerapps.c2k.data.model.WorkoutPlan
import com.hackerapps.c2k.data.prefs.RepeatedWeek

/** What the Home screen offers a runner who keeps stopping the same workout early. */
sealed interface ProgressionSuggestion {
    val earlyStops: Int

    /** Go back and run [week] again, the usual C25K advice for a week that feels too hard. */
    data class RepeatWeek(val week: Int, override val earlyStops: Int) : ProgressionSuggestion

    /** C25K week 1 has no earlier week to repeat, so offer the gentler starter plan instead. */
    data class TryPreC25K(override val earlyStops: Int) : ProgressionSuggestion
}

// Only suggests; nothing changes until the runner accepts. Stopping early is the one signal the
// app has: it can't tell whether someone walked during a run interval and finished anyway.
object ProgressionAdvisor {

    const val EARLY_STOPS_TO_SUGGEST = 2

    // Shorter attempts are usually an accidental start or a quick restart, not a struggle.
    const val MIN_ATTEMPT_SECONDS = 120

    /**
     * Suggests stepping back when the runner has stopped their next workout ([week], [day]) early
     * at least [EARLY_STOPS_TO_SUGGEST] times. Only stops since the last workout they finished in
     * this program count, so early stops from before a repeated week don't trigger it again once
     * they're back, and only stops after [dismissedAtMillis], so "Not now" holds until they
     * struggle again.
     */
    fun suggest(
        plan: WorkoutPlan,
        week: Int,
        day: Int,
        sessions: List<WorkoutSessionEntity>,
        dismissedAtMillis: Long?
    ): ProgressionSuggestion? {
        val inPlan = sessions.filter { it.programId == plan.programId }
        val lastFinished = inPlan.filter { it.completed }.maxOfOrNull { it.startedAt } ?: Long.MIN_VALUE
        val since = maxOf(lastFinished, dismissedAtMillis ?: Long.MIN_VALUE)
        val earlyStops = inPlan.count {
            !it.completed && it.week == week && it.day == day &&
                it.durationSeconds >= MIN_ATTEMPT_SECONDS && it.startedAt > since
        }
        if (earlyStops < EARLY_STOPS_TO_SUGGEST) return null
        return when {
            week > 1 -> ProgressionSuggestion.RepeatWeek(week - 1, earlyStops)
            plan.programId == Programs.ID_C25K -> ProgressionSuggestion.TryPreC25K(earlyStops)
            // Week 1 of any other program: the runner is already kept on this day, so there is
            // nothing gentler within the plan to offer.
            else -> null
        }
    }

    /**
     * The next day of [repeated] the runner hasn't finished since choosing to repeat it, or null
     * once they've finished the whole week again (or it belongs to another program). Then the
     * plan's usual next workout takes over, which is the day they were stuck on.
     */
    fun nextInRepeatedWeek(
        plan: WorkoutPlan,
        repeated: RepeatedWeek?,
        sessions: List<WorkoutSessionEntity>
    ): Pair<Int, Int>? {
        if (repeated == null || repeated.programId != plan.programId) return null
        val days = plan.weeks.getOrNull(repeated.week - 1) ?: return null
        val redone = sessions
            .filter {
                it.programId == plan.programId && it.completed &&
                    it.week == repeated.week && it.startedAt >= repeated.sinceMillis
            }
            .map { it.day }
            .toSet()
        return days.firstOrNull { it.day !in redone }?.let { it.week to it.day }
    }
}
