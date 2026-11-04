package com.tapandgo.di

import android.content.Context
import com.tapandgo.core.nfc.CurrentTagStore
import com.tapandgo.core.nfc.PlatformTagGateway
import com.tapandgo.core.nfc.TagGateway
import com.tapandgo.data.database.AppDatabase
import com.tapandgo.data.database.ScanDao
import com.tapandgo.data.database.TaskDao
import com.tapandgo.data.database.TemplateDao
import com.tapandgo.data.export.BackupService
import com.tapandgo.data.repository.HistoryRepository
import com.tapandgo.data.repository.TaskRepository
import com.tapandgo.data.repository.TemplateRepository
import com.tapandgo.data.settings.SettingsRepository
import com.tapandgo.domain.task.ActionPerformer
import com.tapandgo.feature.tasks.IntentActionPerformer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** Process-wide bindings. Platform-dependent pieces are behind interfaces for tests. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides fun tagGateway(): TagGateway = PlatformTagGateway

    @Provides @Singleton fun database(@ApplicationContext c: Context): AppDatabase = AppDatabase.build(c)
    @Provides fun scanDao(db: AppDatabase): ScanDao = db.scanDao()
    @Provides fun templateDao(db: AppDatabase): TemplateDao = db.templateDao()
    @Provides fun taskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun savedTagDao(db: AppDatabase): com.tapandgo.data.database.SavedTagDao = db.savedTagDao()

    @Provides @Singleton fun settings(@ApplicationContext c: Context) = SettingsRepository(c)
    @Provides @Singleton fun history(dao: ScanDao, s: SettingsRepository) = HistoryRepository(dao, s)
    @Provides @Singleton fun templates(dao: TemplateDao) = TemplateRepository(dao)
    @Provides @Singleton fun tasks(dao: TaskDao) = TaskRepository(dao)
    @Provides @Singleton fun backup(db: AppDatabase, s: SettingsRepository, @ApplicationContext c: Context) = BackupService(db, s, c.contentResolver)
    @Provides @Singleton fun tagStore() = CurrentTagStore()
    @Provides @Singleton fun pendingWrite() = PendingWriteStore()

    /** Intents are started with FLAG_ACTIVITY_NEW_TASK, so the application context is valid. */
    @Provides fun performer(@ApplicationContext c: Context): IntentActionPerformer = IntentActionPerformer(c)
    @Provides fun actionPerformer(p: IntentActionPerformer): ActionPerformer = p
}
