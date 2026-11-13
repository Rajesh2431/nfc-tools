package com.tapandgo.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tapandgo.data.database.AppDatabase
import com.tapandgo.data.database.MIGRATION_1_2
import com.tapandgo.data.database.MIGRATION_2_3
import com.tapandgo.data.database.MIGRATION_3_4
import com.tapandgo.data.database.MIGRATION_4_5
import com.tapandgo.data.database.MIGRATION_5_6
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** AGENTS rule 4: every schema change is migrated and tested; user data survives. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsHistoryAndTemplates() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO scan_entries (createdAt, summary, technologies, recordCount, contentKind, result, ndefType, uidHex, payloadHex, favourite) " +
                    "VALUES (1, 'https://example.com', 'NfcA,Ndef', 1, 'RECORDS', 'OK', NULL, NULL, NULL, 1)",
            )
            execSQL("INSERT INTO templates (name, description, recordsJson, createdAt, updatedAt) VALUES ('t', '', '[]', 1, 1)")
            close()
        }
        // Validates the migrated schema against schemas/2.json.
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val scan = db.scanDao().all().single()
            assertEquals("https://example.com", scan.summary)
            assertEquals(true, scan.favourite)
            assertEquals("t", db.templateDao().all().single().name)
            assertEquals(0, db.taskDao().observeProfiles().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate2To3KeepsProfilesManual() = runTest {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO task_profiles (name, enabled, actionsJson, policy, triggerJson, createdAt, updatedAt) " +
                    "VALUES ('Door', 1, '[{\"type\":\"display_text\",\"text\":\"Hi\"}]', 'EXTERNAL_ONLY', NULL, 1, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val row = db.taskDao().allProfiles().single()
            assertEquals("Door", row.name)
            assertEquals(false, row.autoRun) // existing profiles never become auto-run
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate3To4AddsEmptyLibraryAndKeepsData() = runTest {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO templates (name, description, recordsJson, createdAt, updatedAt) VALUES ('t', '', '[]', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            assertEquals("t", db.templateDao().all().single().name)
            assertEquals(0, db.savedTagDao().all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate4To5AddsEmptyWorkflowTables() = runTest {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO templates (name, description, recordsJson, createdAt, updatedAt) VALUES ('t', '', '[]', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).close()
    }

    @Test
    fun migrate5To6KeepsWorkflowsWithTriggersOff() = runTest {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO workflows (name, description, icon, category, workflowJson, createdAt, updatedAt) " +
                    "VALUES ('w', '', 'BOLT', '', '{\"name\":\"w\",\"blocks\":[]}', 1, 1)",
            )
            execSQL("INSERT INTO workflow_runs (workflowId, workflowName, startTime, endTime, status, errorMessage, detailsJson) VALUES (1, 'w', 1, 2, 'SUCCESS', NULL, '[]')")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val w = db.workflowDao().all().single()
            assertEquals(null, w.triggerToken)
            assertEquals(false, w.autoRun)
            assertEquals(false, w.allowExternal)
            assertEquals(0, w.scheduleMinutes)
            assertEquals(0, db.workflowDao().scheduled().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun everyVersionHasAMigrationPath() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).close()
    }
}
