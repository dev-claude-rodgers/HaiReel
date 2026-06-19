package com.rodgers.routist.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val prefs = context.getSharedPreferences("gf_info", Context.MODE_PRIVATE)
        event.triggeringGeofences?.forEach { geofence ->
            val name = prefs.getString("name_${geofence.requestId}", null) ?: return@forEach
            val note = prefs.getString("note_${geofence.requestId}", "").orEmpty()
            showNotification(context, geofence.requestId.hashCode(), name, note)
        }
    }

    private fun showNotification(ctx: Context, id: Int, name: String, note: String) {
        val channelId = "geofence_arrival"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "目的地到着通知", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "目的地の近くに来たときに通知します" }
        )

        val body = if (note.isNotBlank()) note else "近くに来ました"
        val notif = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle("📍 $name")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(id, notif)
    }
}
