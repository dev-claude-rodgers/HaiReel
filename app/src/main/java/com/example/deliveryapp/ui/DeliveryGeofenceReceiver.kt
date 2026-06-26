package com.rodgers.routist.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rodgers.routist.db.AppDatabase
import com.rodgers.routist.db.DeliveryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** ジオフェンス進入イベントを受け取り、配達到着通知を表示する */
class DeliveryGeofenceReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID        = "delivery_arrival"
        const val ACTION_COMPLETE   = "com.rodgers.routist.ACTION_DELIVERY_COMPLETE"
        const val ACTION_SKIP       = "com.rodgers.routist.ACTION_DELIVERY_SKIP"
        const val EXTRA_DELIVERY_ID = "delivery_id"
        const val EXTRA_GROUP_ID    = "group_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeredIds = event.triggeringGeofences?.map { it.requestId } ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db  = AppDatabase.getInstance(context)
                val all = db.deliveryDao().getAll()
                triggeredIds.forEach { id ->
                    val entity = all.firstOrNull { it.id == id && !it.isCompleted } ?: return@forEach
                    showArrivalNotification(context, entity)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showArrivalNotification(ctx: Context, entity: DeliveryEntity) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "配達先到着通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "配達先に近づいたときに通知します" }
            )
        }

        val displayText = if (!entity.name.isNullOrBlank())
            "${entity.order}. ${entity.name} - ${entity.address}"
        else
            "${entity.order}. ${entity.address}"

        fun actionPending(action: String): PendingIntent {
            val i = Intent(ctx, DeliveryActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_DELIVERY_ID, entity.id)
                putExtra(EXTRA_GROUP_ID, entity.groupId)
            }
            return PendingIntent.getBroadcast(
                ctx,
                (entity.id + action).hashCode(),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("📦 配達先に到着")
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(0, "✅ 配達完了", actionPending(ACTION_COMPLETE))
            .addAction(0, "⏭ スキップ",  actionPending(ACTION_SKIP))
            .build()

        nm.notify(entity.id.hashCode(), notif)
    }
}
