package com.hackerapps.c2k.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.ui.screen.history.GpxExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxExportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val session = WorkoutSessionEntity(
        programId = "c25k", week = 1, day = 1, startedAt = 0L,
        durationSeconds = 600, distanceMeters = 1000f, completed = true
    )

    @Test
    fun share_intent_carries_a_readable_gpx_file() {
        val gpx = "<?xml version=\"1.0\"?><gpx/>"
        val file = GpxExport.writeFile(context, session, gpx)
        val intent = GpxExport.shareIntent(context, file, "subject")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/gpx+xml", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        val read = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        assertEquals(gpx, read)
    }

    @Test
    fun a_new_export_replaces_the_previous_one() {
        val first = GpxExport.writeFile(context, session, "a")
        val second = GpxExport.writeFile(context, session.copy(day = 2), "b")
        assertTrue(!first.exists())
        assertEquals(listOf(second.name), second.parentFile!!.list()!!.toList())
    }
}
