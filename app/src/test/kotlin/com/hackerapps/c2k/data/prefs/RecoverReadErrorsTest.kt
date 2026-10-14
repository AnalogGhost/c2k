package com.hackerapps.c2k.data.prefs

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class RecoverReadErrorsTest {

    private val key = booleanPreferencesKey("flag")

    @Test
    fun a_read_racing_the_files_creation_is_retried() {
        var attempts = 0
        val racing = flow<Preferences> {
            attempts++
            if (attempts == 1) throw FileNotFoundException("c2k_prefs.preferences_pb: open failed: ENOENT")
            emit(preferencesOf(key to true))
        }
        assertEquals(true, runBlocking { racing.recoverReadErrors().first()[key] })
        assertEquals(2, attempts)
    }

    @Test
    fun an_io_error_that_persists_falls_back_to_defaults() {
        val broken = flow<Preferences> { throw IOException("disk error") }
        assertNull(runBlocking { broken.recoverReadErrors().first()[key] })
    }

    @Test
    fun other_errors_are_not_swallowed() {
        val failing = flow<Preferences> { throw IllegalStateException("bug") }
        val thrown = runCatching { runBlocking { failing.recoverReadErrors().first() } }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
    }
}
