package dev.nfcstudio.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nfcstudio.app.data.database.AppDatabase
import dev.nfcstudio.app.data.database.MIGRATION_1_2
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
    fun everyVersionHasAMigrationPath() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 2, true, *AppDatabase.MIGRATIONS).close()
    }
}
