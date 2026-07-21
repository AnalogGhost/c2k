package com.hackerapps.c2k.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hackerapps.c2k.data.db.dao.RoutePointDao
import com.hackerapps.c2k.data.db.dao.WorkoutSessionDao
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

@Database(
    entities = [WorkoutSessionEntity::class, RoutePointEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): WorkoutSessionDao
    abstract fun routePointDao(): RoutePointDao

    companion object {
        // Adds the `manual` flag for manually-marked days. A real migration (rather than the
        // destructive fallback) so existing run history survives the upgrade.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "c2k.db")
                .addMigrations(MIGRATION_1_2)
                // Backstop for any unhandled future version jump; the 1->2 path above is used
                // for the current upgrade so history is preserved.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
