package com.rodgers.routist.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.rodgers.routist.model.Delivery

object GeofenceManager {

    private const val RADIUS_METERS = 200f
    private const val PREFS = "gf_info"

    fun sync(ctx: Context, deliveries: List<Delivery>) {
        val client = LocationServices.getGeofencingClient(ctx)
        val geocoded = deliveries.filter { it.hasLocation }

        // 不要になったジオフェンスを削除
        val newIds = geocoded.map { it.id }.toSet()
        val oldIds = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith("name_") }.map { it.removePrefix("name_") }.toSet()
        val toRemove = (oldIds - newIds).toList()
        if (toRemove.isNotEmpty()) {
            client.removeGeofences(toRemove)
            val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            toRemove.forEach { edit.remove("name_$it").remove("note_$it") }
            edit.apply()
        }

        if (geocoded.isEmpty()) return

        // 名前・メモを保存（BroadcastReceiverが参照する）
        val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        geocoded.forEach { d ->
            edit.putString("name_${d.id}", d.name ?: d.address)
            edit.putString("note_${d.id}", d.note ?: "")
        }
        edit.apply()

        val geofences = geocoded.map { d ->
            Geofence.Builder()
                .setRequestId(d.id)
                .setCircularRegion(d.lat, d.lng, RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setLoiteringDelay(30_000)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()

        try {
            client.addGeofences(request, pendingIntent(ctx))
        } catch (_: SecurityException) {}
    }

    fun removeAll(ctx: Context) {
        LocationServices.getGeofencingClient(ctx).removeGeofences(pendingIntent(ctx))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
