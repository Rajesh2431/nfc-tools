package com.tapandgo.data.library

import android.content.Context
import android.net.Uri
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.data.database.SavedTagDao
import com.tapandgo.data.database.SavedTagEntity
import com.tapandgo.data.database.escapeLike
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: SavedTagDao,
    @ApplicationContext private val context: Context,
) {
    private val now: () -> Long = System::currentTimeMillis
    private val photoDir get() = File(context.filesDir, "tag-photos").apply { mkdirs() }

    fun observe(f: LibraryFilter): Flow<List<SavedTagEntity>> = dao.observe(escapeLike(f.query.trim()), f.folder, f.favouritesOnly, f.sort.name)
    fun observeFolders() = dao.observeFolders()
    fun observe(id: Long) = dao.observeById(id)
    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun all() = dao.all()

    /** Existing entries with the same UID or identical NDEF content. */
    suspend fun duplicatesOf(snapshot: TagSnapshot, meta: LibraryMeta): List<SavedTagEntity> {
        val e = Library.entityFrom(snapshot, meta, 0)
        return dao.duplicates(e.uidHex, e.payloadHex)
    }

    suspend fun save(snapshot: TagSnapshot, meta: LibraryMeta): Long? {
        if (meta.validate() != null) return null
        return dao.insert(Library.entityFrom(snapshot, meta, now()))
    }

    suspend fun updateMeta(id: Long, meta: LibraryMeta): Boolean {
        if (meta.validate() != null) return false
        val e = dao.byId(id) ?: return false
        dao.update(Library.withMeta(e, meta, now()))
        return true
    }

    suspend fun setFavourite(id: Long, favourite: Boolean) {
        dao.byId(id)?.let { dao.update(it.copy(favourite = favourite, updatedAt = now())) }
    }

    suspend fun delete(id: Long) {
        dao.byId(id)?.photoFile?.let { File(photoDir, it).delete() }
        dao.delete(id)
    }

    /**
     * Copies a user-picked image into app-private storage (max 10 MiB) so the
     * library never depends on, or leaks, external URIs.
     */
    suspend fun attachPhoto(id: Long, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val e = dao.byId(id) ?: return@withContext false
        val name = UUID.randomUUID().toString() + ".img"
        val target = File(photoDir, name)
        val ok = runCatching {
            context.contentResolver.openInputStream(source)!!.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 10L * 1024 * 1024) throw IllegalStateException("too large")
                        out.write(buf, 0, n)
                    }
                }
            }
        }.isSuccess
        if (!ok) { target.delete(); return@withContext false }
        e.photoFile?.let { File(photoDir, it).delete() }
        dao.update(e.copy(photoFile = name, updatedAt = now()))
        true
    }

    fun photoFile(e: SavedTagEntity): File? = e.photoFile?.let { File(photoDir, it) }?.takeIf { it.exists() }

    suspend fun insertAll(tags: List<SavedTagEntity>) = dao.insertAll(tags)
}
