package com.rodgers.haireel.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        fun reschedule(isBefore: Boolean) {
            val enabled = if (isBefore) AppSettings.getReminderBeforeEnabled(context)
                          else           AppSettings.getReminderAfterEnabled(context)
            val hour    = if (isBefore) AppSettings.getReminderBeforeHour(context)
                          else           AppSettings.getReminderAfterHour(context)
            val minute  = if (isBefore) AppSettings.getReminderBeforeMinute(context)
                          else           AppSettings.getReminderAfterMinute(context)

            val reqCode = if (isBefore) 3001 else 3002
            val pi = PendingIntent.getBroadcast(
                context, reqCode,
                Intent(context, ReminderReceiver::class.java).apply {
                    putExtra(ReminderReceiver.EXTRA_TITLE,
                        if (isBefore) "乗務前点呼のお時間です" else "乗務後点呼のお時間です")
                    putExtra(ReminderReceiver.EXTRA_TEXT,
                        if (isBefore) "出発前に点呼を記録してください" else "帰着後に点呼を記録してください")
                    putExtra(ReminderReceiver.EXTRA_NOTIF_ID,
                        if (isBefore) ReminderReceiver.NOTIF_BEFORE else ReminderReceiver.NOTIF_AFTER)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            am.cancel(pi)
            if (!enabled) return

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        }

        reschedule(isBefore = true)
        reschedule(isBefore = false)
    }
}
