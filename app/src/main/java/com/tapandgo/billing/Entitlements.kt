package com.tapandgo.billing

import com.tapandgo.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Features unlocked by Premium. Features that shipped free stay free. */
enum class PremiumFeature {
    UNLIMITED_WORKFLOWS,
    SCHEDULED_WORKFLOWS,
    SHORTCUTS_AND_TILE,
    EXTERNAL_TRIGGERS,
    NDEF_EMULATION,
    RAW_NDEF_EDITOR,
    CONTEXT_TRIGGERS,
}

/** Where Premium came from. DEVELOPMENT exists only in debug builds and is labelled as such. */
enum class EntitlementSource { NONE, PLAY_BILLING, DEVELOPMENT }

data class Entitlement(val source: EntitlementSource) {
    val premium: Boolean get() = source != EntitlementSource.NONE
    fun allows(@Suppress("UNUSED_PARAMETER") f: PremiumFeature): Boolean = premium

    companion object {
        const val FREE_WORKFLOW_LIMIT = 3
    }
}

/**
 * Store-billing seam. This build has no Google Play Billing integration, so
 * [NotConfiguredBilling] reports that truthfully; a real implementation can
 * replace it without touching features (they only see [EntitlementRepository]).
 */
interface BillingProvider {
    val configured: Boolean
    /** Verified, acknowledged purchase state from the store. */
    val purchased: Flow<Boolean>
}

class NotConfiguredBilling : BillingProvider {
    override val configured = false
    override val purchased: Flow<Boolean> = MutableStateFlow(false)
}

/** Pure resolution so it can be unit tested. */
object EntitlementResolver {
    fun resolve(purchased: Boolean, devAvailable: Boolean, devEnabled: Boolean): Entitlement = Entitlement(
        when {
            purchased -> EntitlementSource.PLAY_BILLING
            devAvailable && devEnabled -> EntitlementSource.DEVELOPMENT
            else -> EntitlementSource.NONE
        },
    )
}

/** Single source of truth for Premium. Features observe [entitlement]; nothing hardcodes "unlocked". */
@javax.inject.Singleton
class EntitlementRepository @javax.inject.Inject constructor(
    private val billing: BillingProvider,
    private val settings: com.tapandgo.data.settings.SettingsRepository,
) {
    val billingConfigured: Boolean get() = billing.configured
    val devModeAvailable: Boolean get() = BuildConfig.DEV_ENTITLEMENT

    val entitlement: Flow<Entitlement> = kotlinx.coroutines.flow.combine(billing.purchased, settings.devPremium) { bought, dev ->
        EntitlementResolver.resolve(bought, BuildConfig.DEV_ENTITLEMENT, dev)
    }

    suspend fun current(): Entitlement = entitlement.first()

    /** Testing-only switch; ignored (and not shown) in release builds. */
    suspend fun setDevelopmentPremium(on: Boolean) = settings.setDevPremium(on && BuildConfig.DEV_ENTITLEMENT)
}
