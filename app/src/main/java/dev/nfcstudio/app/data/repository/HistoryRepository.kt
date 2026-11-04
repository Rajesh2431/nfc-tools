package dev.nfcstudio.app.data.repository

import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.data.database.ScanDao
import dev.nfcstudio.app.data.database.ScanEntryEntity
import dev.nfcstudio.app.data.database.escapeLike
import dev.nfcstudio.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** History search/filter parameters (spec 4.5). */
data class HistoryFilter(
    val query: String = "",
    val favouritesOnly: Boolean = false,
    val period: Period = Period.ALL,
    val result: ResultFilter = ResultFilter.ALL,
    val technology: String? = null,
) {
    enum class Period(val days: Int?) { ALL(null), DAY(1), WEEK(7), MONTH(30) }
    enum class ResultFilter(val code: String) { ALL(""), SUCCESS("OK"), FAILED("ERROR") }
}

class HistoryRepository(
    private val dao: ScanDao,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observe(filter: HistoryFilter, limit: Int = 500): Flow<List<ScanEntryEntity>> {
        val since = filter.period.days?.let { now() - TimeUnit.DAYS.toMillis(it.toLong()) } ?: 0L
        return dao.observe(
            query = escapeLike(filter.query.trim()),
            favouritesOnly = filter.favouritesOnly,
            since = since,
            result = filter.result.code,
            technology = filter.technology?.let(::escapeLike).orEmpty(),
            limit = limit,
        )
    }

    fun observeRecent(limit: Int = 3) = dao.observeRecent(limit)

    fun observeCount() = dao.observeCount()

    fun observeTechnologies(): Flow<List<String>> = dao.observeTechnologyLists().map { lists ->
        lists.flatMap { it.split(',') }.filter { it.isNotBlank() }.distinct().sorted()
    }

    /** Records a scan when history is enabled. @return the new row id, or null. */
    suspend fun record(result: NfcResult<TagSnapshot>): Long? {
        val entry = ScanEntryFactory.create(result, settings.current(), now()) ?: return null
        return dao.insert(entry)
    }

    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun setFavourite(id: Long, favourite: Boolean) = dao.setFavourite(id, favourite)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()

    /** Applies the retention setting; favourites are kept. @return rows removed. */
    suspend fun prune(): Int {
        val days = settings.current().retentionDays
        if (days <= 0) return 0
        return dao.deleteOlderThan(now() - TimeUnit.DAYS.toMillis(days.toLong()))
    }
}
