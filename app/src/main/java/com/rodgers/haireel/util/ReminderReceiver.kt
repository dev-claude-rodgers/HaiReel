package com.rodgers.haireel.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rodgers.haireel.R

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID   = "tenko_reminder"
        const val EXTRA_TITLE  = "title"
        const val EXTRA_TEXT   = "text"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val NOTIF_BEFORE = 2001
        const val NOTIF_AFTER  = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title   = intent.getStringExtra(EXTRA_TITLE)   ?: "点呼リマインダー"
        val text    = intent.getStringExtra(EXTRA_TEXT)    ?: "点呼を記録してください"
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NOTIF_BEFORE)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "点呼リマインダー", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_tenko)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(notifId, notif)
    }
}
