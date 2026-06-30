package com.hackerapps.c2k.data.model

object Programs {

    const val ID_PRE_C25K = "PRE_C25K"
    const val ID_C25K   = "C25K"
    const val ID_C210K  = "C210K"
    const val ID_B210K  = "B210K"
    const val ID_OHR    = "OHR"
    const val ID_5KI    = "5KI"

    val PreC25K:       WorkoutPlan by lazy { buildPreC25K() }
    val C25K:          WorkoutPlan by lazy { buildC25K() }
    val C210K:         WorkoutPlan by lazy { buildC210K() }
    val B210K:         WorkoutPlan by lazy { buildB210K() }
    val OneHourRunner: WorkoutPlan by lazy { buildOneHourRunner() }
    val FiveKImprover: WorkoutPlan by lazy { buildFiveKImprover() }

    fun byId(id: String): WorkoutPlan = when (id) {
        ID_PRE_C25K -> PreC25K
        ID_C25K  -> C25K
        ID_C210K -> C210K
        ID_B210K -> B210K
        ID_OHR   -> OneHourRunner
        ID_5KI   -> FiveKImprover
        else     -> error("Unknown program: $id")
    }

    fun all(): List<WorkoutPlan> = listOf(PreC25K, C25K, C210K, B210K, OneHourRunner, FiveKImprover)

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun warmup()   = Interval(IntervalType.WARMUP,   300)
    private fun cooldown() = Interval(IntervalType.COOLDOWN, 300)
    private fun run(seconds: Int)  = Interval(IntervalType.RUN,  seconds)
    private fun walk(seconds: Int) = Interval(IntervalType.WALK, seconds)

    private fun repeatRunWalk(times: Int, runSec: Int, walkSec: Int): List<Interval> =
        buildList {
            add(warmup())
            repeat(times) { add(run(runSec)); add(walk(walkSec)) }
            add(cooldown())
        }

    private fun day(week: Int, day: Int, intervals: List<Interval>) =
        WorkoutDay(week, day, intervals)

    private fun uniformWeek(week: Int, intervals: List<Interval>): List<WorkoutDay> =
        listOf(day(week, 1, intervals), day(week, 2, intervals), day(week, 3, intervals))

    private fun continuousRun(week: Int, runSec: Int): List<WorkoutDay> =
        uniformWeek(week, buildList { add(warmup()); add(run(runSec)); add(cooldown()) })

    // ── Pre-C25K ──────────────────────────────────────────────────────────────
    // Gentler lead-in for absolute beginners who find C25K Week 1 too hard.
    // Graduates to C25K Week 1 by the final week.

    private fun buildPreC25K(): WorkoutPlan {
        val weeks = listOf(
            // W1 — 8 × (run 30s, walk 90s)
            uniformWeek(1, repeatRunWalk(8, 30, 90)),

            // W2 — 8 × (run 45s, walk 90s)
            uniformWeek(2, repeatRunWalk(8, 45, 90)),

            // W3 — 8 × (run 60s, walk 90s): matches C25K Week 1
            uniformWeek(3, repeatRunWalk(8, 60, 90))
        )
        return WorkoutPlan(ID_PRE_C25K, "Pre-C25K",
            "3-week starter for beginners who find Week 1 of C25K too hard. Graduates into C25K.", weeks,
            prerequisite = null)
    }

    // ── C25K ─────────────────────────────────────────────────────────────────

    private fun buildC25K(): WorkoutPlan {
        val weeks = listOf(
            // W1 — 8 × (run 60s, walk 90s)
            uniformWeek(1, repeatRunWalk(8, 60, 90)),

            // W2 — 6 × (run 90s, walk 120s)
            uniformWeek(2, repeatRunWalk(6, 90, 120)),

            // W3 — 2 × (run 90s, walk 90s, run 3min, walk 3min)
            uniformWeek(3, buildList {
                add(warmup())
                repeat(2) {
                    add(run(90));  add(walk(90))
                    add(run(180)); add(walk(180))
                }
                add(cooldown())
            }),

            // W4 — run 3min, walk 90s, run 5min, walk 2.5min, run 3min, walk 90s, run 5min
            uniformWeek(4, buildList {
                add(warmup())
                add(run(180)); add(walk(90))
                add(run(300)); add(walk(150))
                add(run(180)); add(walk(90))
                add(run(300))
                add(cooldown())
            }),

            // W5 — three distinct days
            listOf(
                day(5, 1, repeatRunWalk(3, 300, 180)),
                day(5, 2, buildList {
                    add(warmup())
                    repeat(2) { add(run(480)); add(walk(300)) }
                    add(cooldown())
                }),
                day(5, 3, buildList { add(warmup()); add(run(1200)); add(cooldown()) })
            ),

            // W6 — three distinct days
            listOf(
                // D1: run 5min, walk 3min, run 8min, walk 3min, run 5min
                day(6, 1, buildList {
                    add(warmup())
                    add(run(300)); add(walk(180))
                    add(run(480)); add(walk(180))
                    add(run(300))
                    add(cooldown())
                }),
                day(6, 2, buildList {
                    add(warmup())
                    repeat(2) { add(run(600)); add(walk(180)) }
                    add(cooldown())
                }),
                day(6, 3, buildList { add(warmup()); add(run(1320)); add(cooldown()) })
            ),

            continuousRun(7, 1500),   // 25 min
            continuousRun(8, 1680),   // 28 min
            continuousRun(9, 1800)    // 30 min
        )
        return WorkoutPlan(ID_C25K, "Couch to 5K",
            "9-week program to run 5K. Start here — no fitness required.", weeks,
            prerequisite = null)
    }

    // ── C210K ─────────────────────────────────────────────────────────────────

    private fun buildC210K(): WorkoutPlan {
        val weeks = C25K.weeks.toMutableList() + listOf(
            // W10 — 3 × (run 10min, walk 2min)
            uniformWeek(10, repeatRunWalk(3, 600, 120)),

            // W11 — 2 × (run 15min, walk 3min)
            uniformWeek(11, buildList {
                add(warmup())
                repeat(2) { add(run(900)); add(walk(180)) }
                add(cooldown())
            }),

            // W12 — run 40min, walk 5min, run 10min
            uniformWeek(12, buildList {
                add(warmup()); add(run(2400)); add(walk(300)); add(run(600)); add(cooldown())
            }),

            continuousRun(13, 3000),   // 50 min
            continuousRun(14, 3600)    // 60 min
        )
        return WorkoutPlan(ID_C210K, "Couch to 10K",
            "14-week program to run 10K. Continues where C25K ends.", weeks,
            prerequisite = "After completing C25K")
    }

    // ── B210K — Bridge to 10K ─────────────────────────────────────────────────
    // For runners who finished C25K and want to build to 10K distance before
    // starting C210K. Begins at 30-min running and graduates to a full 60-min run.

    private fun buildB210K(): WorkoutPlan {
        val weeks = listOf(
            // W1 — 3 × (run 10min, walk 90s): re-establish the habit
            uniformWeek(1, repeatRunWalk(3, 600, 90)),

            // W2 — run 15min, walk 2min, run 15min
            uniformWeek(2, buildList {
                add(warmup()); add(run(900)); add(walk(120)); add(run(900)); add(cooldown())
            }),

            // W3 — run 20min, walk 2min, run 15min
            uniformWeek(3, buildList {
                add(warmup()); add(run(1200)); add(walk(120)); add(run(900)); add(cooldown())
            }),

            // W4 — run 25min, walk 2min, run 20min
            uniformWeek(4, buildList {
                add(warmup()); add(run(1500)); add(walk(120)); add(run(1200)); add(cooldown())
            }),

            // W5 — run 30min, walk 2min, run 20min
            uniformWeek(5, buildList {
                add(warmup()); add(run(1800)); add(walk(120)); add(run(1200)); add(cooldown())
            }),

            continuousRun(6, 3600)   // 60 min — 10K pace for most runners
        )
        return WorkoutPlan(ID_B210K, "Bridge to 10K",
            "6-week bridge for C25K graduates not ready to jump straight to C210K.", weeks,
            prerequisite = "After completing C25K")
    }

    // ── One Hour Runner ───────────────────────────────────────────────────────
    // 13-week program from 30 min to 60 min continuous running.
    // Suitable after completing C25K or B210K.

    private fun buildOneHourRunner(): WorkoutPlan {
        val runDurations = listOf(
            1980,   // W1  — 33 min
            2100,   // W2  — 35 min
            2220,   // W3  — 37 min
            2400,   // W4  — 40 min
            2580,   // W5  — 43 min
            2700,   // W6  — 45 min
            2820,   // W7  — 47 min
            3000,   // W8  — 50 min
            3120,   // W9  — 52 min
            3300,   // W10 — 55 min
            3420,   // W11 — 57 min
            3480,   // W12 — 58 min
            3600    // W13 — 60 min
        )
        val weeks = runDurations.mapIndexed { i, secs -> continuousRun(i + 1, secs) }
        return WorkoutPlan(ID_OHR, "One Hour Runner",
            "13-week progression from 30 to 60 minutes of continuous running.", weeks,
            prerequisite = "After completing B210K or C25K")
    }

    // ── 5K Improver ──────────────────────────────────────────────────────────
    // 8-week program for runners who can complete 5K and want to improve speed
    // and stamina. Weeks 1-4 use short intervals to build leg turnover;
    // weeks 5-8 shift to longer continuous efforts.

    private fun buildFiveKImprover(): WorkoutPlan {
        val weeks = listOf(
            // W1 — 5 × (run 3min, walk 90s): short hard efforts
            uniformWeek(1, repeatRunWalk(5, 180, 90)),

            // W2 — 4 × (run 5min, walk 2min)
            uniformWeek(2, repeatRunWalk(4, 300, 120)),

            // W3 — 3 × (run 7min, walk 2min)
            uniformWeek(3, repeatRunWalk(3, 420, 120)),

            // W4 — 2 × (run 10min, walk 3min) + run 5min
            uniformWeek(4, buildList {
                add(warmup())
                repeat(2) { add(run(600)); add(walk(180)) }
                add(run(300))
                add(cooldown())
            }),

            // W5 — run 15min, walk 3min, run 12min
            uniformWeek(5, buildList {
                add(warmup()); add(run(900)); add(walk(180)); add(run(720)); add(cooldown())
            }),

            // W6 — 2 × (run 15min, walk 3min)
            uniformWeek(6, repeatRunWalk(2, 900, 180)),

            // W7 — run 20min, walk 3min, run 12min
            uniformWeek(7, buildList {
                add(warmup()); add(run(1200)); add(walk(180)); add(run(720)); add(cooldown())
            }),

            // W8 — run 30min continuous: target 5K race effort
            continuousRun(8, 1800)
        )
        return WorkoutPlan(ID_5KI, "5K Improver",
            "8-week speed and stamina program for runners who can already complete 5K.", weeks,
            prerequisite = "For runners who can already complete 5K")
    }
}
