package com.hackerapps.c2k.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against shipping a schema change that can't upgrade existing installs. Room used to
 * fall back to wiping the database here; now a missing step would crash on launch instead, so
 * these fail the build before that can reach anyone.
 */
class DatabaseMigrationRulesTest {

    // Unit tests run from the app module directory.
    private val schemaDir = File("schemas/${AppDatabase::class.java.name}")

    @Test
    fun every_version_step_has_exactly_one_migration() {
        val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(emptyList<String>(), migrationProblems(DATABASE_VERSION, steps))
    }

    @Test
    fun every_version_has_its_schema_committed() {
        // MigrationTest builds each old database from these files, so a missing one means a
        // version that can't be tested. Room writes the file on build; it has to be committed.
        val missing = (1..DATABASE_VERSION).filterNot { File(schemaDir, "$it.json").isFile }
        assertTrue("Missing schema JSON for versions $missing in $schemaDir", missing.isEmpty())
    }

    @Test
    fun no_schema_exists_beyond_the_current_version() {
        val versions = schemaDir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
        assertTrue("Schemas past version $DATABASE_VERSION: $versions", versions.all { it <= DATABASE_VERSION })
    }

    // ── The rule itself ──────────────────────────────────────────────────────────

    @Test
    fun version_one_needs_no_steps() {
        assertEquals(emptyList<String>(), migrationProblems(1, emptyList()))
    }

    @Test
    fun a_version_bump_without_a_migration_is_caught() {
        assertEquals(listOf("Missing migration 1→2"), migrationProblems(2, emptyList()))
    }

    @Test
    fun a_gap_in_the_chain_is_caught() {
        assertEquals(listOf("Missing migration 2→3"), migrationProblems(4, listOf(1 to 2, 3 to 4)))
    }

    @Test
    fun a_complete_chain_passes() {
        assertEquals(emptyList<String>(), migrationProblems(3, listOf(1 to 2, 2 to 3)))
    }

    @Test
    fun skipping_and_overshooting_and_duplicate_steps_are_caught() {
        val problems = migrationProblems(3, listOf(1 to 2, 1 to 2, 2 to 3, 1 to 3, 3 to 4))
        assertEquals(
            listOf(
                "Migration 1→3 skips versions; add one step per version",
                "Migration 3→4 goes past the database version 3",
                "More than one migration 1→2"
            ),
            problems
        )
    }
}
