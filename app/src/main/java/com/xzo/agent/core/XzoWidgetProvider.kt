package com.xzo.agent.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xzo.agent.MainActivity
import com.xzo.agent.R

/**
 * Home-screen widget: one tap to a new chat, one tap to dictate, one tap to the
 * prompt library. Plain RemoteViews — no Glance dependency, so it costs ~0 KB.
 */
class XzoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_xzo).apply {
                setOnClickPendingIntent(R.id.widget_root, activity(context, "com.xzo.agent.NEW_CHAT", 1))
                setOnClickPendingIntent(R.id.widget_voice, activity(context, "com.xzo.agent.VOICE", 2))
                setOnClickPendingIntent(R.id.widget_library, activity(context, "com.xzo.agent.OPEN_LIBRARY", 3))
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun activity(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
