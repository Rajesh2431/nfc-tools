package dev.nfcstudio.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    /**
     * Search + filters (spec 4.5). [query] and [technology] must be escaped
     * with [escapeLike]; [result] is "", "OK" or "ERROR".
     */
    @Query(
        """
        SELECT * FROM scan_entries
        WHERE (:query = '' OR summary LIKE '%' || :query || '%' ESCAPE '\'
               OR technologies LIKE '%' || :query || '%' ESCAPE '\')
          AND (:favouritesOnly = 0 OR favourite = 1)
          AND createdAt >= :since
          AND (:result = '' OR (:result = 'OK' AND result = 'OK') OR (:result = 'ERROR' AND result != 'OK'))
          AND (:technology = '' OR (',' || technologies || ',') LIKE '%,' || :technology || ',%' ESCAPE '\')
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observe(
        query: String,
        favouritesOnly: Boolean,
        since: Long,
        result: String,
        technology: String,
        limit: Int,
    ): Flow<List<ScanEntryEntity>>

    @Query("SELECT DISTINCT technologies FROM scan_entries")
    fun observeTechnologyLists(): Flow<List<String>>

    @Query("SELECT * FROM scan_entries ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScanEntryEntity>>

    @Query("SELECT * FROM scan_entries ORDER BY createdAt ASC")
    suspend fun all(): List<ScanEntryEntity>

    @Query("SELECT * FROM scan_entries WHERE id = :id")
    suspend fun byId(id: Long): ScanEntryEntity?

    @Insert
    suspend fun insert(entry: ScanEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<ScanEntryEntity>)

    @Query("UPDATE scan_entries SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("DELETE FROM scan_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_entries")
    suspend fun clear()

    /** Retention pruning never removes favourites. */
    @Query("DELETE FROM scan_entries WHERE createdAt < :cutoff AND favourite = 0")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT result FROM scan_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentResults(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM scan_entries")
    fun observeCount(): Flow<Int>
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY createdAt ASC")
    suspend fun all(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun byId(id: Long): TemplateEntity?

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Insert
    suspend fun insertAll(templates: List<TemplateEntity>)

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Escapes LIKE wildcards so user input is matched literally (ESCAPE '\'). */
fun escapeLike(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_profiles ORDER BY name COLLATE NOCASE")
    fun observeProfiles(): Flow<List<TaskProfileEntity>>

    @Query("SELECT * FROM task_profiles ORDER BY createdAt ASC")
    suspend fun allProfiles(): List<TaskProfileEntity>

    @Query("SELECT * FROM task_profiles WHERE id = :id")
    suspend fun profile(id: Long): TaskProfileEntity?

    @Insert
    suspend fun insertProfile(profile: TaskProfileEntity): Long

    @Insert
    suspend fun insertProfiles(profiles: List<TaskProfileEntity>)

    @Update
    suspend fun updateProfile(profile: TaskProfileEntity)

    @Query("UPDATE task_profiles SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM task_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Insert
    suspend fun insertLog(entry: TaskLogEntity)

    @Query("SELECT * FROM task_log ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<TaskLogEntity>>

    @Query("DELETE FROM task_log")
    suspend fun clearLog()
}
