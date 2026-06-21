package com.rodgers.routist.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rodgers.routist.R

object LicenseNotificationHelper {

    private const val CHANNEL_ID = "license_reminder"
    private const val NOTIF_ID   = 9001
    private const val PREF_LAST_NOTIF = "license_notif_last_day"

    fun checkAndNotify(ctx: Context) {
        if (AppSettings.isLicenseValid(ctx)) {
            checkLicenseExpiry(ctx)
        } else if (!AppSettings.isInTrial(ctx)) {
            // 試用期間も切れていてライセンスもない → 通知は起動時のダイアログで対応済み
        } else {
            checkTrialExpiry(ctx)
        }
    }

    private fun checkLicenseExpiry(ctx: Context) {
        val expiry = AppSettings.getLicenseExpiry(ctx)
        val daysLeft = ((expiry - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt()
        when {
            daysLeft in 1..30 && shouldNotify(ctx, daysLeft) -> {
                val msg = when {
                    daysLeft <= 7  -> "ライセンスの有効期限まであと${daysLeft}日です。更新をお忘れなく。"
                    daysLeft <= 30 -> "ライセンスの有効期限まであと${daysLeft}日です。"
                    else -> return
                }
                showNotification(ctx, "ライセンス期限のお知らせ", msg)
                saveNotifiedDay(ctx, daysLeft)
            }
        }
    }

    private fun checkTrialExpiry(ctx: Context) {
        val daysLeft = AppSettings.trialDaysLeft(ctx)
        when {
            daysLeft in 1..2 && shouldNotify(ctx, daysLeft + 1000) -> {
                showNotification(ctx, "試用期間まもなく終了",
                    "試用期間の残り${daysLeft}日です。継続利用にはライセンスキーが必要です。")
                saveNotifiedDay(ctx, daysLeft + 1000)
            }
        }
    }

    private fun shouldNotify(ctx: Context, key: Int): Boolean {
        val lastKey = ctx.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE)
            .getInt(PREF_LAST_NOTIF, -1)
        return lastKey != key
    }

    private fun saveNotifiedDay(ctx: Context, key: Int) {
        ctx.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE)
            .edit().putInt(PREF_LAST_NOTIF, key).apply()
    }

    private fun showNotification(ctx: Context, title: String, message: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "ライセンス・試用期間", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "ライセンス期限・試用期間終了のお知らせ"
        }
        nm.createNotificationChannel(channel)

        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = PendingIntent.getActivity(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_settings)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}
