package dev.nfcstudio.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database. Schemas are exported to app/schemas and every version bump
 * ships a [androidx.room.migration.Migration] plus a MigrationTestHelper test.
 * Destructive fallback is never enabled (AGENTS rule 4).
 */
@Database(
    entities = [ScanEntryEntity::class, TemplateEntity::class, TaskProfileEntity::class, TaskLogEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun templateDao(): TemplateDao
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "nfc_studio.db"

        /** All migrations, in order. Shared with migration tests. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
