package com.tapandgo.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapandgo.data.database.AppDatabase
import com.tapandgo.data.database.ScanEntryEntity
import com.tapandgo.data.database.TemplateEntity
import com.tapandgo.data.database.escapeLike
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real SQLite queries (search, filters, pruning) on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class ScanDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(t: Long, summary: String, tech: String = "NfcA,Ndef", result: String = "OK", fav: Boolean = false) =
        ScanEntryEntity(createdAt = t, summary = summary, technologies = tech, recordCount = 1, contentKind = "RECORDS",
            result = result, ndefType = null, uidHex = null, payloadHex = null, favourite = fav)

    private suspend fun query(q: String = "", fav: Boolean = false, since: Long = 0, result: String = "", tech: String = "") =
        db.scanDao().observe(escapeLike(q), fav, since, result, escapeLike(tech), 100).first().map { it.summary }

    @Test
    fun searchFiltersAndOrdering() = runTest {
        val dao = db.scanDao()
        dao.insertAll(
            listOf(
                entry(1, "https://shop.example"),
                entry(2, "100% cotton", tech = "IsoDep"),
                entry(3, "", result = "TAG_LOST", tech = ""),
                entry(4, "Hello", fav = true, tech = "NfcV,Ndef"),
            ),
        )
        assertEquals(listOf("Hello", "", "100% cotton", "https://shop.example"), query())
        assertEquals(listOf("https://shop.example"), query("shop"))
        assertEquals(listOf("100% cotton"), query("0%"))
        assertEquals(emptyList<String>(), query("_"))
        assertEquals(listOf("Hello"), query(fav = true))
        assertEquals(listOf(""), query(result = "ERROR"))
        assertEquals(listOf("Hello", "100% cotton", "https://shop.example"), query(result = "OK"))
        assertEquals(listOf("Hello", ""), query(since = 3))
        assertEquals(listOf("Hello"), query(tech = "NfcV"))
        assertEquals(emptyList<String>(), query(tech = "Nfc"))
    }

    @Test
    fun libraryQueriesSearchFolderSortAndDuplicates() = runTest {
        val dao = db.savedTagDao()
        fun t(name: String, folder: String = "", fav: Boolean = false, uid: String? = null, payload: String? = null, scanned: Long = 1, updated: Long = 1) =
            com.tapandgo.data.database.SavedTagEntity(name = name, folder = folder, favourite = fav, uidHex = uid, payloadHex = payload, scannedAt = scanned, createdAt = 1, updatedAt = updated)
        dao.insertAll(listOf(t("beta", "Home", uid = "04aa", updated = 5), t("Alpha", "Work", fav = true, payload = "d1", scanned = 9), t("gamma_x", notes())))
        suspend fun q(query: String = "", folder: String? = null, fav: Boolean = false, sort: String = "UPDATED") =
            dao.observe(escapeLike(query), folder, fav, sort).first().map { it.name }
        assertEquals(listOf("Alpha", "beta", "gamma_x"), q(sort = "NAME"))
        assertEquals("Alpha", q(sort = "SCANNED").first())
        assertEquals(listOf("beta"), q(folder = "Home"))
        assertEquals(listOf("Alpha"), q(fav = true))
        assertEquals(listOf("gamma_x"), q("_x"))
        assertEquals(listOf("Home", "Work"), dao.observeFolders().first())
        assertEquals(listOf("beta"), dao.duplicates("04aa", null).map { it.name })
        assertEquals(listOf("Alpha"), dao.duplicates(null, "d1").map { it.name })
    }

    private fun notes() = ""

    @Test
    fun pruningKeepsFavourites() = runTest {
        val dao = db.scanDao()
        dao.insertAll(listOf(entry(1, "old"), entry(2, "old fav", fav = true), entry(10, "new")))
        assertEquals(1, dao.deleteOlderThan(5))
        assertEquals(listOf("new", "old fav"), query())
    }

    @Test
    fun favouriteDeleteClearAndTemplates() = runTest {
        val dao = db.scanDao()
        val id = dao.insert(entry(1, "a"))
        dao.setFavourite(id, true)
        assertEquals(true, dao.byId(id)!!.favourite)
        dao.delete(id)
        assertEquals(0, dao.observeCount().first())
        dao.insert(entry(2, "b")); dao.clear()
        assertEquals(0, dao.observeCount().first())

        val t = db.templateDao()
        val tid = t.insert(TemplateEntity(name = "n", description = "", recordsJson = "[]", createdAt = 1, updatedAt = 1))
        t.update(t.byId(tid)!!.copy(name = "m", updatedAt = 2))
        assertEquals("m", t.observeAll().first().single().name)
    }
}
