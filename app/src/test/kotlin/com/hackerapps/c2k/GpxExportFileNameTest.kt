package com.hackerapps.c2k

import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity
import com.hackerapps.c2k.ui.screen.history.GpxExport
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class GpxExportFileNameTest {

    private fun session(startedAt: Long) = WorkoutSessionEntity(
        programId = "c25k", week = 3, day = 2, startedAt = startedAt,
        durationSeconds = 1800, distanceMeters = 3000f, completed = true
    )

    @Test
    fun file_name_has_week_day_and_local_start_time_with_gpx_extension() {
        // 2026-11-16T10:30:00Z
        val name = GpxExport.fileName(session(1_794_825_000_000L), TimeZone.getTimeZone("America/Sao_Paulo"))
        assertEquals("c2k-route-w3d2-2026-11-16-0730.gpx", name)
    }
}
