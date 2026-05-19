package org.c2k

import android.app.Application
import org.c2k.data.db.AppDatabase
import org.c2k.data.repository.SessionRepository

class C2KApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }

    override fun onCreate() {
        super.onCreate()
    }
}
