package com.hackerapps.c2k

import android.app.Application
import com.hackerapps.c2k.data.db.AppDatabase
import com.hackerapps.c2k.data.prefs.UserPreferences
import com.hackerapps.c2k.data.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class C2KApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // One-time cleanup of distances inflated by faulty GPS data recorded before the
        // implied-speed filter existed (issue #30). Flag is set only after a full pass, so
        // an interrupted run simply retries on next launch.
        appScope.launch {
            val prefs = UserPreferences(this@C2KApp)
            if (!prefs.gpsDistancesRecomputed.first()) {
                sessionRepository.recomputeSessionDistances()
                prefs.setGpsDistancesRecomputed()
            }
        }
    }
}
