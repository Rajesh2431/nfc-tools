package com.tapandgo.automation

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.tapandgo.MainActivity
import com.tapandgo.R
import com.tapandgo.billing.EntitlementRepository
import com.tapandgo.billing.PremiumFeature
import com.tapandgo.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget: a Scan button and up to four workflow buttons. Buttons
 * open the run screen through the non-exported alias (visible run, result
 * shown), exactly like pinned shortcuts.
 */
class WorkflowWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun workflows(): WorkflowRepository
        fun settings(): SettingsRepository
        fun entitlements(): EntitlementRepository
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val BUTTONS = intArrayOf(R.id.widget_wf1, R.id.widget_wf2, R.id.widget_wf3, R.id.widget_wf4)

        /** Re-renders all widgets (after workflows or widget choices change). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WorkflowWidget::class.java))
            if (ids.isNotEmpty()) scope.launch { render(context.applicationContext, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
            val premium = deps.entitlements().current().allows(PremiumFeature.SHORTCUTS_AND_TILE)
            val chosen = deps.settings().widgetWorkflows.first()
            val all = deps.workflows().observeAll().first().associateBy { it.id }
            val items = if (premium) chosen.mapNotNull { id -> all[id]?.takeIf { it.workflow != null } } else emptyList()
            val tokens = items.map { it to deps.workflows().ensureToken(it.id) }

            val views = RemoteViews(context.packageName, R.layout.widget_workflows)
            val scan = Intent(Intent.ACTION_VIEW, "tapandgo://read".toUri(), context, MainActivity::class.java)
            views.setOnClickPendingIntent(R.id.widget_scan, PendingIntent.getActivity(context, 1, scan, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            views.setTextViewText(
                R.id.widget_empty,
                context.getString(if (!premium) R.string.widget_premium else R.string.widget_empty),
            )
            views.setViewVisibility(R.id.widget_empty, if (tokens.isEmpty()) View.VISIBLE else View.GONE)
            BUTTONS.forEachIndexed { i, b ->
                val pair = tokens.getOrNull(i)
                val token = pair?.second
                if (pair == null || token == null) {
                    views.setViewVisibility(b, View.GONE)
                } else {
                    views.setViewVisibility(b, View.VISIBLE)
                    views.setTextViewText(b, pair.first.name)
                    val intent = LaunchIntents.internal(context, token, RunSource.WIDGET)
                    views.setOnClickPendingIntent(b, PendingIntent.getActivity(context, 100 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
