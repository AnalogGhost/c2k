package org.ctok.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.ctok.data.db.dao.RoutePointDao
import org.ctok.data.db.dao.WorkoutSessionDao
import org.ctok.data.db.entity.RoutePointEntity
import org.ctok.data.db.entity.WorkoutSessionEntity

@Database(
    entities = [WorkoutSessionEntity::class, RoutePointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): WorkoutSessionDao
    abstract fun routePointDao(): RoutePointDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ctok.db").build()
    }
}
