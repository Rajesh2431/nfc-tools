package com.tapandgo.navigation

import androidx.lifecycle.ViewModel
import com.tapandgo.automation.LaunchRequest
import com.tapandgo.automation.RunSource
import com.tapandgo.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Activity-level state: resolves trigger tokens delivered by intents. */
@HiltViewModel
class AppViewModel @Inject constructor(private val tasks: TaskRepository) : ViewModel() {
    /**
     * Route for a launch request. Task tags may belong to a task profile
     * (checked first) or a workflow; everything else targets workflows. The
     * workflow run screen re-checks the token and the launch policy.
     */
    suspend fun routeFor(request: LaunchRequest): String {
        if (request.source == RunSource.NFC_TAG) {
            tasks.byTaskToken(request.token)?.let { return "tasks/run/${it.id}?auto=${it.autoRun}" }
        }
        return "workflow-launch?token=${request.token}&source=${request.source.name}"
    }
}
