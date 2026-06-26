package com.rodgers.routist.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.ui.DeliveryGeofenceReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveryGeofenceManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: GeofencingClient
) {
    companion object {
        const val RADIUS_M       = 150f   // 到着判定半径（メートル）
        const val MAX_GEOFENCES  = 99     // Geofencing API上限
        const val PREF_ENABLED   = "geofence_notifications_enabled"
    }

    private val prefs = ctx.getSharedPreferences("haireel_prefs", Context.MODE_PRIVATE)

    fun isEnabled() = prefs.getBoolean(PREF_ENABLED, true)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean(PREF_ENABLED, v).apply()

    fun hasRequiredPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                   PackageManager.PERMISSION_GRANTED
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        else true
        return fine && bg
    }

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, DeliveryGeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /** 未完了配達先のジオフェンスを一括登録（commitDeliveries のたびに呼ぶ） */
    fun register(deliveries: List<Delivery>) {
        if (!isEnabled() || !hasRequiredPermission()) return

        val geofences = deliveries
            .filter { it.hasLocation && !it.isCompleted }
            .take(MAX_GEOFENCES)
            .map { d ->
                Geofence.Builder()
                    .setRequestId(d.id)
                    .setCircularRegion(d.lat, d.lng, RADIUS_M)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setLoiteringDelay(30_000) // 30秒以上いたら反応
                    .build()
            }

        // 既存をクリアしてから登録
        try {
            client.removeGeofences(pendingIntent).addOnCompleteListener {
                if (geofences.isEmpty()) return@addOnCompleteListener
                val req = GeofencingRequest.Builder()
                    .setInitialTrigger(0) // 登録直後は即発火しない
                    .addGeofences(geofences)
                    .build()
                try { client.addGeofences(req, pendingIntent) }
                catch (_: SecurityException) {}
            }
        } catch (_: Exception) {}
    }

    fun unregisterAll() {
        try { client.removeGeofences(pendingIntent) } catch (_: Exception) {}
    }
}
