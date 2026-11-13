package com.tapandgo.feature.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tapandgo.R
import com.tapandgo.billing.Entitlement
import com.tapandgo.billing.EntitlementRepository
import com.tapandgo.billing.EntitlementSource
import com.tapandgo.billing.PremiumFeature
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(private val repo: EntitlementRepository) : ViewModel() {
    val entitlement: StateFlow<Entitlement?> = repo.entitlement.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val billingConfigured get() = repo.billingConfigured
    val devAvailable get() = repo.devModeAvailable
    fun setDev(on: Boolean) = viewModelScope.launch { repo.setDevelopmentPremium(on) }
}

/** Free vs Premium comparison; rows are (title, free, premium). */
private val COMPARISON = listOf(
    Triple(R.string.prem_row_nfc, true, true),
    Triple(R.string.prem_row_library, true, true),
    Triple(R.string.prem_row_profiles, true, true),
    Triple(R.string.prem_row_workflows, true, true),
    Triple(R.string.prem_row_unlimited, false, true),
    Triple(R.string.prem_row_schedule, false, true),
    Triple(R.string.prem_row_shortcuts, false, true),
    Triple(R.string.prem_row_external, false, true),
    Triple(R.string.prem_row_context, false, true),
    Triple(R.string.prem_row_emulation, false, true),
    Triple(R.string.prem_row_raw, false, true),
)

@Composable
fun PremiumScreen(modifier: Modifier = Modifier) {
    val vm: PremiumViewModel = hiltViewModel()
    val e by vm.entitlement.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.WorkspacePremium, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.prem_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        when (e?.source) {
                            EntitlementSource.PLAY_BILLING -> R.string.prem_status_active
                            EntitlementSource.DEVELOPMENT -> R.string.prem_status_dev
                            else -> R.string.prem_status_free
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (e?.source == EntitlementSource.DEVELOPMENT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(stringResource(R.string.prem_compare)) {
            Row { Text("", Modifier.weight(1f)); Text(stringResource(R.string.prem_free), Modifier.padding(horizontal = 12.dp)); Text(stringResource(R.string.prem_title)) }
            COMPARISON.forEach { (title, free, premium) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Mark(free, Modifier.padding(horizontal = 20.dp))
                    Mark(premium, Modifier.padding(end = 16.dp))
                }
            }
        }

        if (!vm.billingConfigured) {
            MessageCard(stringResource(R.string.prem_billing_off), body = stringResource(R.string.prem_billing_off_detail))
        }
        Button(onClick = {}, enabled = false) { Text(stringResource(R.string.prem_buy_unavailable)) }

        if (vm.devAvailable) {
            MessageCard(stringResource(R.string.prem_dev_title), body = stringResource(R.string.prem_dev_detail), tone = Tone.ERROR) {
                val on = e?.source == EntitlementSource.DEVELOPMENT
                Row(Modifier.fillMaxWidth().toggleable(on, role = Role.Switch, onValueChange = vm::setDev), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.prem_dev_switch), Modifier.weight(1f))
                    Switch(on, null)
                }
            }
        }
    }
}

@Composable
private fun Mark(included: Boolean, modifier: Modifier) {
    Icon(
        if (included) Icons.Filled.CheckCircle else Icons.Filled.Lock,
        contentDescription = stringResource(if (included) R.string.prem_included else R.string.prem_not_included),
        tint = if (included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

/**
 * Shows [content] when [feature] is unlocked, otherwise a locked state that
 * links to the Premium screen. Never pretends the feature ran.
 */
@Composable
fun PremiumGate(feature: PremiumFeature, onOpenPremium: () -> Unit, content: @Composable () -> Unit) {
    val vm: PremiumViewModel = hiltViewModel()
    val e by vm.entitlement.collectAsStateWithLifecycle()
    when {
        e == null -> Unit
        e!!.allows(feature) -> content()
        else -> PremiumLocked(onOpenPremium)
    }
}

@Composable
fun PremiumLocked(onOpenPremium: () -> Unit) {
    MessageCard(stringResource(R.string.prem_locked), body = stringResource(R.string.prem_locked_detail)) {
        Button(onClick = onOpenPremium) { Text(stringResource(R.string.prem_see)) }
    }
}
