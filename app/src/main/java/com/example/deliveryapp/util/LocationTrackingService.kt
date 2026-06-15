package com.rodgers.routist.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.time.LocalDate

class LocationTrackingService : Service() {

    companion object {
        private const val CHANNEL_ID   = "routist_location"
        private const val NOTIF_ID     = 9001
        private const val ACTION_START = "START"
        private const val ACTION_STOP  = "STOP"
        private const val PREFS        = "kado_settings"
        private const val KEY_DIST     = "gps_distance_km"
        private const val KEY_DATE     = "gps_tracking_date"

        fun start(ctx: Context) {
            val i = Intent(ctx, LocationTrackingService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(
                Intent(ctx, LocationTrackingService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun getTodayDistanceKm(ctx: Context): Float {
            val p    = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val date = p.getString(KEY_DATE, null) ?: return 0f
            return if (date == LocalDate.now().toString()) p.getFloat(KEY_DIST, 0f) else 0f
        }
    }

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var accumulatedKm = 0f
    private var lastLocation: Location? = null
    private lateinit var locationManager: LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val today     = LocalDate.now().toString()
            val savedDate = prefs.getString(KEY_DATE, null)
            if (savedDate != today) {
                accumulatedKm = 0f
                lastLocation  = null
                prefs.edit().putString(KEY_DATE, today).putFloat(KEY_DIST, 0f).apply()
            }
            lastLocation?.let { prev ->
                val r = FloatArray(1)
                Location.distanceBetween(
                    prev.latitude, prev.longitude,
                    location.latitude, location.longitude, r
                )
                if (r[0] >= 10f) {
                    accumulatedKm += r[0] / 1000f
                    prefs.edit().putFloat(KEY_DIST, accumulatedKm).apply()
                    updateNotification()
                }
            }
            lastLocation = location
        }

        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        val today     = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_DATE, null)
        accumulatedKm = if (savedDate == today) prefs.getFloat(KEY_DIST, 0f) else 0f
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 30_000L, 50f, listener
                    )
                } catch (_: SecurityException) {}
            }
            ACTION_STOP -> {
                locationManager.removeUpdates(listener)
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Routist 走行距離追跡中")
        .setContentText("本日の走行距離: ${"%.1f".format(accumulatedKm)} km")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "走行距離追跡", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "乗務前後点呼に連動して走行距離を計測します" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
