package com.hackerapps.c2k.data.db

/**
 * What's wrong with a set of upgrade steps (from version to version) for a database at
 * [version]: every step from 1 up to it must exist exactly once, one version at a time, and
 * none may go past it. Empty means every installed version can upgrade without losing data.
 */
internal fun migrationProblems(version: Int, steps: List<Pair<Int, Int>>): List<String> {
    val problems = mutableListOf<String>()
    steps.filter { (from, to) -> to != from + 1 }
        .forEach { (from, to) -> problems += "Migration $from→$to skips versions; add one step per version" }
    steps.filter { (_, to) -> to > version }
        .forEach { (from, to) -> problems += "Migration $from→$to goes past the database version $version" }
    for (from in 1 until version) {
        when (steps.count { it == from to from + 1 }) {
            0 -> problems += "Missing migration ${from}→${from + 1}"
            1 -> Unit
            else -> problems += "More than one migration ${from}→${from + 1}"
        }
    }
    return problems
}
