package dev.nfcstudio.app.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (M5): adds task profiles and the execution log. Purely additive;
 * history and templates are untouched. SQL is copied from schemas/2.json.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `actionsJson` TEXT NOT NULL, `policy` TEXT NOT NULL, " +
                "`triggerJson` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, `profileName` TEXT NOT NULL, `actionIndex` INTEGER NOT NULL, " +
                "`actionType` TEXT NOT NULL, `outcome` TEXT NOT NULL, `reason` TEXT, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_log_createdAt` ON `task_log` (`createdAt`)")
    }
}
