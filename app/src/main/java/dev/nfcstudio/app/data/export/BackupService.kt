package dev.nfcstudio.app.data.export

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import dev.nfcstudio.app.data.database.AppDatabase
import dev.nfcstudio.app.data.settings.SettingsRepository
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
    enum class Section { HISTORY, TEMPLATES, PROFILES }

    sealed interface Outcome {
        data class Exported(val history: Int, val templates: Int, val profiles: Int) : Outcome
        data class Imported(val bundle: ImportBundle) : Outcome
        data class Rejected(val reason: ImportResult) : Outcome
        data object IoFailed : Outcome
    }

    suspend fun export(target: Uri, sections: Set<Section>): Outcome = try {
        val history = if (Section.HISTORY in sections) db.scanDao().all() else emptyList()
        val templates = if (Section.TEMPLATES in sections) db.templateDao().all() else emptyList()
        val profiles = if (Section.PROFILES in sections) db.taskDao().allProfiles() else emptyList()
        val text = ExportCodec.encode(history, templates, now(), profiles)
        resolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("no output stream")
        Outcome.Exported(history.size, templates.size, profiles.size)
    } catch (_: IOException) {
        Outcome.IoFailed
    } catch (_: SecurityException) {
        Outcome.IoFailed
    }

    suspend fun import(source: Uri): Outcome {
        val text = try {
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
        val s = settings.current()
        return when (val r = ExportCodec.decode(text, now(), keepPayload = s.savePayload, keepUid = s.storeUid)) {
            is ImportResult.Ok -> {
                db.withTransaction {
                    db.scanDao().insertAll(r.bundle.history)
                    db.templateDao().insertAll(r.bundle.templates)
                    db.taskDao().insertProfiles(r.bundle.profiles)
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
