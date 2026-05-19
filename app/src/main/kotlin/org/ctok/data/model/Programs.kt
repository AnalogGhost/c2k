package org.ctok.data.model

object Programs {

    const val ID_C25K = "C25K"
    const val ID_C210K = "C210K"

    val C25K: WorkoutPlan by lazy { buildC25K() }
    val C210K: WorkoutPlan by lazy { buildC210K() }

    fun byId(id: String): WorkoutPlan = when (id) {
        ID_C25K  -> C25K
        ID_C210K -> C210K
        else     -> error("Unknown program: $id")
    }

    fun all(): List<WorkoutPlan> = listOf(C25K, C210K)

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun warmup() = Interval(IntervalType.WARMUP, 300)
    private fun cooldown() = Interval(IntervalType.COOLDOWN, 300)
    private fun run(seconds: Int) = Interval(IntervalType.RUN, seconds)
    private fun walk(seconds: Int) = Interval(IntervalType.WALK, seconds)

    /** Alternate run/walk N times, prepend warm-up, append cool-down. */
    private fun repeatRunWalk(
        times: Int,
        runSec: Int,
        walkSec: Int
    ): List<Interval> = buildList {
        add(warmup())
        repeat(times) {
            add(run(runSec))
            add(walk(walkSec))
        }
        add(cooldown())
    }

    private fun day(week: Int, day: Int, intervals: List<Interval>) =
        WorkoutDay(week, day, intervals)

    /** Build a week where all three days share the same interval pattern. */
    private fun uniformWeek(week: Int, intervals: List<Interval>): List<WorkoutDay> =
        listOf(
            day(week, 1, intervals),
            day(week, 2, intervals),
            day(week, 3, intervals)
        )

    // ── C25K ─────────────────────────────────────────────────────────────────

    private fun buildC25K(): WorkoutPlan {
        val weeks = listOf(
            // W1 — 8 × (run 60s, walk 90s)
            uniformWeek(1, repeatRunWalk(8, 60, 90)),

            // W2 — 6 × (run 90s, walk 120s)
            uniformWeek(2, repeatRunWalk(6, 90, 120)),

            // W3 — 2 × (run 90s, walk 90s, run 180s, walk 180s)
            uniformWeek(3, buildList {
                add(warmup())
                repeat(2) {
                    add(run(90)); add(walk(90))
                    add(run(180)); add(walk(180))
                }
                add(cooldown())
            }),

            // W4 — single pattern
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
                day(5, 3, buildList {
                    add(warmup()); add(run(1200)); add(cooldown())
                })
            ),

            // W6 — three distinct days
            listOf(
                day(6, 1, repeatRunWalk(3, 300, 180)),
                day(6, 2, buildList {
                    add(warmup())
                    repeat(2) { add(run(600)); add(walk(180)) }
                    add(cooldown())
                }),
                day(6, 3, buildList {
                    add(warmup()); add(run(1320)); add(cooldown())
                })
            ),

            // W7 — 25 min run
            uniformWeek(7, buildList {
                add(warmup()); add(run(1500)); add(cooldown())
            }),

            // W8 — 28 min run
            uniformWeek(8, buildList {
                add(warmup()); add(run(1680)); add(cooldown())
            }),

            // W9 — 30 min run
            uniformWeek(9, buildList {
                add(warmup()); add(run(1800)); add(cooldown())
            })
        )
        return WorkoutPlan(ID_C25K, "Couch to 5K", weeks)
    }

    // ── C210K ─────────────────────────────────────────────────────────────────

    private fun buildC210K(): WorkoutPlan {
        // Reuse C25K weeks 1-9
        val c25kWeeks = C25K.weeks.toMutableList()

        val extraWeeks = listOf(
            // W10 — 3 × (run 10 min, walk 2 min)
            uniformWeek(10, repeatRunWalk(3, 600, 120)),

            // W11 — 2 × (run 15 min, walk 3 min)
            uniformWeek(11, buildList {
                add(warmup())
                repeat(2) { add(run(900)); add(walk(180)) }
                add(cooldown())
            }),

            // W12 — 40 min + 5 min walk + 10 min
            uniformWeek(12, buildList {
                add(warmup())
                add(run(2400)); add(walk(300)); add(run(600))
                add(cooldown())
            }),

            // W13 — 50 min continuous
            uniformWeek(13, buildList {
                add(warmup()); add(run(3000)); add(cooldown())
            }),

            // W14 — 60 min continuous
            uniformWeek(14, buildList {
                add(warmup()); add(run(3600)); add(cooldown())
            })
        )

        return WorkoutPlan(ID_C210K, "Couch to 10K", c25kWeeks + extraWeeks)
    }
}
