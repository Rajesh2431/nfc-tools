package com.tapandgo.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tapandgo.automation.actions.BackgroundRun
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Scheduling seam so the repository stays testable without WorkManager. */
interface WorkflowScheduler {
    fun schedule(id: Long, minutes: Int)
    fun cancel(id: Long)
}

/**
 * Periodic workflow runs via WorkManager. Android decides the exact time
 * (Doze, battery saver); the interval is a minimum, not a promise, and the UI
 * says so. Runs are marked [BackgroundRun] so actions that need a screen
 * report Unsupported instead of failing silently.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(@ApplicationContext private val context: Context) : WorkflowScheduler {
    override fun schedule(id: Long, minutes: Int) {
        val request = PeriodicWorkRequestBuilder<WorkflowWorker>(minutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setInputData(workDataOf(WorkflowWorker.KEY_ID to id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(name(id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(name(id))
    }

    companion object {
        const val TAG = "workflow-schedule"
        fun name(id: Long) = "workflow-$id"
    }
}

class WorkflowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun workflows(): WorkflowRepository
        fun entitlements(): com.tapandgo.billing.EntitlementRepository
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, 0L)
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val repo = deps.workflows()
        val source = inputData.getString(KEY_SOURCE)?.let { runCatching { RunSource.valueOf(it) }.getOrNull() } ?: RunSource.SCHEDULE
        if (source == RunSource.CONTEXT) {
            // Wi-Fi / location trigger (Premium, checked again here).
            if (deps.entitlements().current().allows(com.tapandgo.billing.PremiumFeature.CONTEXT_TRIGGERS) && repo.load(id) != null) {
                withContext(BackgroundRun) { repo.run(id, RunSource.CONTEXT) }
            }
            return Result.success()
        }
        // Scheduling is Premium; without it the schedule stays saved but nothing runs.
        if (!deps.entitlements().current().allows(com.tapandgo.billing.PremiumFeature.SCHEDULED_WORKFLOWS)) return Result.success()
        val stored = repo.load(id)
        if (stored == null || stored.first.scheduleMinutes <= 0) {
            // Deleted or unscheduled since: stop this periodic work.
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WorkManagerScheduler.name(id))
            return Result.success()
        }
        withContext(BackgroundRun) { repo.run(id, RunSource.SCHEDULE) }
        // A failed workflow is recorded in its history; retrying would repeat side effects.
        return Result.success()
    }

    companion object {
        const val KEY_ID = "workflow_id"
        const val KEY_SOURCE = "source"
    }
}
