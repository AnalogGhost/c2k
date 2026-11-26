package com.hackerapps.c2k.utils

import android.content.ActivityNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** How the Rate C2K link falls back when no app opens the store link. */
@RunWith(AndroidJUnit4::class)
class OpenFirstAvailableTest {

    private val store = "market://details?id=com.hackerapps.c2k"
    private val web = "https://play.google.com/store/apps/details?id=com.hackerapps.c2k"

    @Test
    fun the_store_app_is_used_when_it_is_installed() {
        val tried = mutableListOf<String>()
        assertTrue(openFirstAvailable(listOf(store, web)) { tried += it })
        assertEquals(listOf(store), tried)
    }

    @Test
    fun the_web_page_is_opened_when_no_store_app_is_installed() {
        val tried = mutableListOf<String>()
        assertTrue(openFirstAvailable(listOf(store, web)) {
            tried += it
            if (it == store) throw ActivityNotFoundException()
        })
        assertEquals(listOf(store, web), tried)
    }

    @Test
    fun nothing_crashes_when_neither_can_be_opened() {
        assertFalse(openFirstAvailable(listOf(store, web)) { throw ActivityNotFoundException() })
    }
}
