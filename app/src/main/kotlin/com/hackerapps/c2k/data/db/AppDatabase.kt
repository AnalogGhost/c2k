package com.hackerapps.c2k.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.hackerapps.c2k.data.db.dao.RoutePointDao
import com.hackerapps.c2k.data.db.dao.WorkoutSessionDao
import com.hackerapps.c2k.data.db.entity.RoutePointEntity
import com.hackerapps.c2k.data.db.entity.WorkoutSessionEntity

/** The schema version. Bumping it needs a Migration in [AppDatabase.MIGRATIONS] (enforced by tests). */
const val DATABASE_VERSION = 1

@Database(
    entities = [WorkoutSessionEntity::class, RoutePointEntity::class],
    version = DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): WorkoutSessionDao
    abstract fun routePointDao(): RoutePointDao

    companion object {
        const val NAME = "c2k.db"

        /**
         * Every schema upgrade, one per version step: from N to N+1 for each N below
         * [DATABASE_VERSION]. When changing an entity, bump the version, add the step here, and
         * commit the schema JSON the build writes to app/schemas. DatabaseMigrationRulesTest fails
         * the build if a step is missing, and MigrationTest runs every step on a database
         * holding real rows.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                // No fallbackToDestructiveMigration(): with backups disabled, a missing upgrade
                // step would have silently deleted every runner's whole history. Without it a
                // missing step fails loudly instead, and the tests above keep it from shipping.
                // A downgrade (installing an older build over a newer one) still starts fresh,
                // as before: an older build can't read a newer schema.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
