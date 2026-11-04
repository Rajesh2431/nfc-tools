package com.tapandgo.navigation

import androidx.lifecycle.ViewModel
import com.tapandgo.data.repository.TaskRepository
import com.tapandgo.domain.task.TaskProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Activity-level state: resolves task-tag tokens delivered by NDEF_DISCOVERED intents. */
@HiltViewModel
class AppViewModel @Inject constructor(private val tasks: TaskRepository) : ViewModel() {
    suspend fun profileForToken(token: String): TaskProfile? = tasks.byTaskToken(token)
}
