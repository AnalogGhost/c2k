package com.hackerapps.c2k.data.model

object CoachingTips {

    private val c25k = mapOf(
        1 to "Run at a pace where you can still talk. The walk breaks are part of the training.",
        2 to "You made it through week 1 — your body is already adapting. Keep the pace easy.",
        3 to "Longer intervals this week. Focus on steady breathing: in for 2 steps, out for 2.",
        4 to "Four different run lengths today. This builds mental toughness as much as fitness.",
        5 to "Day 3 this week is the famous milestone: 20 minutes of continuous running. You've earned it.",
        6 to "You're over the halfway mark. The long runs are building your aerobic base.",
        7 to "25 minutes continuous. Not everyone gets this far — you're a runner now.",
        8 to "28 minutes. The goal is almost in reach. Don't speed up — save it for next week.",
        9 to "This is graduation week. 30 minutes, then celebrate!"
    )

    private val c210k = mapOf(
        10 to "Back to intervals to rebuild your base for the longer distance. Stay easy.",
        11 to "Longer combined efforts. Keep the pace conversational throughout.",
        12 to "40 minutes of running with a short break. Relax and don't race.",
        13 to "50 minutes. If you can do this, a 10K finish is within reach.",
        14 to "60 minutes — your 10K graduation run. Go get it!"
    )

    private val b210k = mapOf(
        1 to "Re-establishing the habit after C25K. Run easy, no need to push yet.",
        2 to "Two longer blocks with a short rest. Aim for an even pace across both runs.",
        3 to "Increasing total volume. Focus on consistent effort across both intervals.",
        4 to "Nearly 45 minutes of running. You're building real 10K fitness.",
        5 to "50 minutes this week. The full 60-minute graduation run is one week away.",
        6 to "60 minutes — the B210K finish line. Well done!"
    )

    private val ohr = mapOf(
        1 to "33 minutes of comfortable, easy running. Don't worry about pace, just finish.",
        4 to "40 minutes. Each week you're building genuine endurance.",
        7 to "Nearly 50 minutes. You're well past the average runner's comfort zone.",
        10 to "55 minutes. The one-hour goal is one good week away.",
        13 to "60 minutes — this is it. You are an hour runner!"
    )

    private val fiveKi = mapOf(
        1 to "Short, hard efforts build leg speed. Push a little harder than your usual 5K pace.",
        2 to "5-minute intervals are the sweet spot for building aerobic power.",
        4 to "Combination workout: longer intervals with a finishing run. This builds race stamina.",
        6 to "Two 15-minute runs — solid aerobic work that will translate directly to a faster 5K.",
        8 to "Graduation run: 30 minutes at 5K effort. See how far you get — it should feel good."
    )

    fun tip(programId: String, week: Int): String? = when (programId) {
        Programs.ID_C25K  -> c25k[week]
        Programs.ID_C210K -> c210k[week]
        Programs.ID_B210K -> b210k[week]
        Programs.ID_OHR   -> ohr[week]
        Programs.ID_5KI   -> fiveKi[week]
        else -> null
    }
}
