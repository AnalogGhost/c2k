package org.ctok

import android.app.Application
import org.ctok.data.db.AppDatabase
import org.ctok.data.repository.SessionRepository

class CtoKApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }

    override fun onCreate() {
        super.onCreate()
    }
}
