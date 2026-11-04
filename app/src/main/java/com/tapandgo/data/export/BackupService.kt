package com.tapandgo.data.export

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.tapandgo.data.database.AppDatabase
import com.tapandgo.data.settings.SettingsRepository
import java.io.IOException

/**
 * Writes/reads export files through the Storage Access Framework URIs the
 * user picked. Import inserts new rows only — it never overwrites or deletes
 * existing data (AGENTS rule 4).
 */
class BackupService(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Section { HISTORY, TEMPLATES, PROFILES, LIBRARY }

    sealed interface Outcome {
        data class Exported(val history: Int, val templates: Int, val profiles: Int, val library: Int = 0, val encrypted: Boolean = false) : Outcome

        /** The file is encrypted; ask for the password and call import again. */
        data object NeedsPassword : Outcome
        data object WrongPassword : Outcome
        data class Imported(val bundle: ImportBundle) : Outcome
        data class Rejected(val reason: ImportResult) : Outcome
        data object IoFailed : Outcome
    }

    suspend fun export(target: Uri, sections: Set<Section>, password: CharArray? = null): Outcome = try {
        val history = if (Section.HISTORY in sections) db.scanDao().all() else emptyList()
        val templates = if (Section.TEMPLATES in sections) db.templateDao().all() else emptyList()
        val profiles = if (Section.PROFILES in sections) db.taskDao().allProfiles() else emptyList()
        val library = if (Section.LIBRARY in sections) db.savedTagDao().all() else emptyList()
        val plain = ExportCodec.encode(history, templates, now(), profiles, library)
        val text = if (password != null) com.tapandgo.core.security.BackupCrypto.encrypt(plain, password) else plain
        resolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("no output stream")
        Outcome.Exported(history.size, templates.size, profiles.size, library.size, encrypted = password != null)
    } catch (_: IOException) {
        Outcome.IoFailed
    } catch (_: SecurityException) {
        Outcome.IoFailed
    }

    suspend fun import(source: Uri, password: CharArray? = null): Outcome {
        val raw = try {
            resolver.openInputStream(source)?.use { input ->
                val bytes = input.readBounded(ExportCodec.MAX_FILE_BYTES + 1)
                if (bytes.size > ExportCodec.MAX_FILE_BYTES) return Outcome.Rejected(ImportResult.TooLarge)
                bytes.toString(Charsets.UTF_8)
            } ?: return Outcome.IoFailed
        } catch (_: IOException) {
            return Outcome.IoFailed
        } catch (_: SecurityException) {
            return Outcome.IoFailed
        }
        val text = if (com.tapandgo.core.security.BackupCrypto.isEncrypted(raw)) {
            if (password == null) return Outcome.NeedsPassword
            when (val d = com.tapandgo.core.security.BackupCrypto.decrypt(raw, password)) {
                is com.tapandgo.core.security.BackupCrypto.Result.Ok -> d.plaintext
                com.tapandgo.core.security.BackupCrypto.Result.NotEncrypted -> raw
                else -> return Outcome.WrongPassword
            }
        } else {
            raw
        }
        val s = settings.current()
        return when (val r = ExportCodec.decode(text, now(), keepPayload = s.savePayload, keepUid = s.storeUid)) {
            is ImportResult.Ok -> {
                db.withTransaction {
                    db.scanDao().insertAll(r.bundle.history)
                    db.templateDao().insertAll(r.bundle.templates)
                    db.taskDao().insertProfiles(r.bundle.profiles)
                    db.savedTagDao().insertAll(r.bundle.library)
                }
                Outcome.Imported(r.bundle)
            }

            else -> Outcome.Rejected(r)
        }
    }
}

/** Reads at most [limit] bytes (API 26 compatible; InputStream.readNBytes is API 33+). */
internal fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
