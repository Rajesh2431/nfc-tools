package com.tapandgo.feature.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tapandgo.R
import com.tapandgo.automation.WorkflowRepository
import com.tapandgo.automation.WorkflowTemplate
import com.tapandgo.automation.WorkflowTemplates
import com.tapandgo.automation.engine.WorkflowEdit
import com.tapandgo.billing.Entitlement
import com.tapandgo.billing.EntitlementRepository
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repo: WorkflowRepository,
    private val entitlements: EntitlementRepository,
) : ViewModel() {
    private val _limit = MutableStateFlow(false)
    val limit: StateFlow<Boolean> = _limit.asStateFlow()

    /** Adds an editable copy; refuses past the free limit. Calls [onAdded] with the new id. */
    fun add(t: WorkflowTemplate, onAdded: (Long) -> Unit) = viewModelScope.launch {
        if (!entitlements.current().premium && repo.count() >= Entitlement.FREE_WORKFLOW_LIMIT) { _limit.value = true; return@launch }
        repo.save(0, t.workflow, t.description)?.let(onAdded)
    }
}

@Composable
fun TemplatesScreen(onAdded: (Long) -> Unit, onOpenPremium: () -> Unit, modifier: Modifier = Modifier) {
    val vm: TemplatesViewModel = hiltViewModel()
    val limit by vm.limit.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.tpl_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (limit) item {
            MessageCard(stringResource(R.string.prem_workflow_limit, Entitlement.FREE_WORKFLOW_LIMIT), tone = Tone.ERROR) {
                Button(onClick = onOpenPremium) { Text(stringResource(R.string.prem_see)) }
            }
        }
        items(WorkflowTemplates.all, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    IconBadge(
                        when (t.category) {
                            WorkflowTemplate.Category.HOME -> Icons.Filled.Home
                            WorkflowTemplate.Category.ROUTINE -> Icons.Filled.Schedule
                            WorkflowTemplate.Category.TRAVEL -> Icons.Filled.DirectionsCar
                            WorkflowTemplate.Category.DEVICE -> Icons.Filled.PhoneAndroid
                            WorkflowTemplate.Category.SMART_HOME -> Icons.Filled.SettingsRemote
                        },
                        container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.workflow.name, style = MaterialTheme.typography.titleSmall)
                        Text(t.description, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.wf_blocks, WorkflowEdit.count(t.workflow.blocks)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        t.connection?.let { Text(stringResource(R.string.tpl_needs_connection, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        Button(onClick = { vm.add(t, onAdded) }) { Text(stringResource(R.string.tpl_add)) }
                    }
                }
            }
        }
    }
}
