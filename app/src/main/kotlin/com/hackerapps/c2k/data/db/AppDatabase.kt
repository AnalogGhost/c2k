package com.hackerapps.c2k.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hackerapps.c2k.data.db.dao.RoutePointDao
import com.hackerapps.c2k.data.db.dao.WorkoutSessionDao
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

@Database(
    entities = [WorkoutSessionEntity::class, RoutePointEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): WorkoutSessionDao
    abstract fun routePointDao(): RoutePointDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "c2k.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
