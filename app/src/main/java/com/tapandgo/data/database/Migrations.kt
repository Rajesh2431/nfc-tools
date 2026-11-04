package com.tapandgo.data.database

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

/** v2 → v3: opt-in auto-run flag for task profiles; existing profiles stay manual. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `task_profiles` ADD COLUMN `autoRun` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v3 → v4: tag library table. Additive; SQL copied from schemas/4.json. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `notes` TEXT NOT NULL, `icon` TEXT NOT NULL, `folder` TEXT NOT NULL, `labels` TEXT NOT NULL, " +
                "`favourite` INTEGER NOT NULL, `uidHex` TEXT, `technologies` TEXT NOT NULL, `chipName` TEXT, `ndefType` TEXT, " +
                "`capacityBytes` INTEGER, `writable` INTEGER, `payloadHex` TEXT, `photoFile` TEXT, `scannedAt` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_tags_folder` ON `saved_tags` (`folder`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_tags_updatedAt` ON `saved_tags` (`updatedAt`)")
    }
}
