package com.rodgers.haireel.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rodgers.haireel.MainActivity
import com.rodgers.haireel.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HaiReelWidget : AppWidgetProvider() {

    companion object {
        private const val PREFS = "widget_prefs"

        fun saveStats(context: Context, groupName: String, done: Int, total: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("group", groupName)
                .putInt("done", done)
                .putInt("total", total)
                .apply()
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, HaiReelWidget::class.java))
            if (ids.isNotEmpty()) {
                HaiReelWidget().onUpdate(context, manager, ids)
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val total = prefs.getInt("total", 0)
            val done = prefs.getInt("done", 0)
            val groupName = prefs.getString("group", "配達リスト") ?: "配達リスト"

            val views = RemoteViews(context.packageName, R.layout.widget_haireel)
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE))
            views.setTextViewText(R.id.widgetDate, date)
            views.setTextViewText(R.id.widgetRoute, groupName)
            views.setTextViewText(
                R.id.widgetStats,
                if (total > 0) "$done / $total 件完了" else "配達データなし"
            )

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)
            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, buildViews(context)) }
    }
}
