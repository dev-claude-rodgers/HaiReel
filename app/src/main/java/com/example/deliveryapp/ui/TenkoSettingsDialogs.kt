package com.rodgers.routist.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.ReminderReceiver
import com.rodgers.routist.util.themeColor
import java.util.Calendar

internal fun TenkoFragment.show430TimerDialog() {
    if (!isAdded) return
    val ctx = requireContext()
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val state = AppSettings.getDriveTimerState(ctx)
    val totalMin = AppSettings.getBreakAlarmMinutes(ctx)
    val totalMs = totalMin * 60_000L

    fun fmtHm(ms: Long): String {
        val m = ms / 60000
        return "%d時間%02d分".format(m / 60, m % 60)
    }

    fun cancelAlarms() {
        listOf(4301, 4302).forEach { code ->
            val pi = PendingIntent.getBroadcast(ctx, code,
                Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            pi?.let { am.cancel(it) }
        }
    }

    fun scheduleAlarms(startMs: Long) {
        if (totalMin > 30) {
            val warnMs = (totalMin - 30) * 60_000L
            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_TITLE,    "休憩の準備をしてください")
                putExtra(ReminderReceiver.EXTRA_TEXT,     "連続運転が${fmtHm(warnMs)}経過しました。あと30分で休憩が必要です。")
                putExtra(ReminderReceiver.EXTRA_NOTIF_ID, 4301)
            }
            val pi = PendingIntent.getBroadcast(ctx, 4301, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMs + warnMs, pi)
        }
        val intent2 = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE,    "休憩してください")
            putExtra(ReminderReceiver.EXTRA_TEXT,     "連続運転が${fmtHm(totalMs)}経過しました。休憩を取ってください。")
            putExtra(ReminderReceiver.EXTRA_NOTIF_ID, 4302)
        }
        val pi2 = PendingIntent.getBroadcast(ctx, 4302, intent2,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMs + totalMs, pi2)
    }

    val startMs = AppSettings.getDriveSegmentStartMs(ctx)
    val elapsedMs = when (state) {
        "DRIVING" -> AppSettings.getDriveAccumulatedMs(ctx) + (System.currentTimeMillis() - startMs)
        else      -> AppSettings.getDriveAccumulatedMs(ctx)
    }

    val message = when (state) {
        "IDLE" -> "停車中\n設定時間: ${fmtHm(totalMs)}\n\n運転を開始すると、設定した時間に休憩を通知します。"
        "DRIVING" -> "運転中\n\n経過: ${fmtHm(elapsedMs)}\n残り: ${fmtHm((totalMs - elapsedMs).coerceAtLeast(0))}"
        "ON_BREAK" -> {
            val breakMs = AppSettings.getBreakAccumulatedMs(ctx) + (System.currentTimeMillis() - AppSettings.getBreakSegmentStartMs(ctx))
            "☕ 休憩中\n\n運転経過: ${fmtHm(elapsedMs)}\n休憩経過: ${fmtHm(breakMs)}"
        }
        else -> ""
    }

    val builder = MaterialAlertDialogBuilder(ctx).setTitle("連続運転タイマー").setMessage(message)

    when (state) {
        "IDLE" -> {
            builder.setPositiveButton("🚗 運転開始") { _, _ ->
                val now = System.currentTimeMillis()
                AppSettings.setDriveTimerState(ctx, "DRIVING")
                AppSettings.setDriveSegmentStartMs(ctx, now)
                AppSettings.setDriveAccumulatedMs(ctx, 0L)
                AppSettings.setBreakAccumulatedMs(ctx, 0L)
                scheduleAlarms(now)
                Toast.makeText(ctx, "運転を開始しました。${fmtHm(totalMs)}後に休憩を通知します。", Toast.LENGTH_LONG).show()
            }
            builder.setNeutralButton("⚙ 時間を変更") { _, _ ->
                showBreakAlarmSettingDialog()
            }
        }
        "DRIVING" -> {
            builder.setPositiveButton("☕ 休憩を記録") { _, _ ->
                val now = System.currentTimeMillis()
                val driveElapsed = now - AppSettings.getDriveSegmentStartMs(ctx)
                AppSettings.setDriveAccumulatedMs(ctx, AppSettings.getDriveAccumulatedMs(ctx) + driveElapsed)
                AppSettings.setDriveTimerState(ctx, "ON_BREAK")
                AppSettings.setBreakSegmentStartMs(ctx, now)
            }
            builder.setNeutralButton("↺ リセット") { _, _ ->
                cancelAlarms()
                AppSettings.setDriveTimerState(ctx, "IDLE")
                AppSettings.setDriveAccumulatedMs(ctx, 0L)
                AppSettings.setBreakAccumulatedMs(ctx, 0L)
                Toast.makeText(ctx, "タイマーをリセットしました", Toast.LENGTH_SHORT).show()
            }
        }
        "ON_BREAK" -> {
            builder.setPositiveButton("🚗 運転再開") { _, _ ->
                val now = System.currentTimeMillis()
                val breakElapsed = now - AppSettings.getBreakSegmentStartMs(ctx)
                AppSettings.setBreakAccumulatedMs(ctx, AppSettings.getBreakAccumulatedMs(ctx) + breakElapsed)
                AppSettings.setDriveTimerState(ctx, "DRIVING")
                AppSettings.setDriveSegmentStartMs(ctx, now)
                val totalDriveMs = AppSettings.getDriveAccumulatedMs(ctx)
                val fakeStart = now - totalDriveMs
                scheduleAlarms(fakeStart)
            }
            builder.setNeutralButton("↺ リセット") { _, _ ->
                cancelAlarms()
                AppSettings.setDriveTimerState(ctx, "IDLE")
                AppSettings.setDriveAccumulatedMs(ctx, 0L)
                AppSettings.setBreakAccumulatedMs(ctx, 0L)
                Toast.makeText(ctx, "タイマーをリセットしました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    builder.setNegativeButton("閉じる", null).show()
}

internal fun TenkoFragment.showBreakAlarmSettingDialog() {
    if (!isAdded) return
    val ctx = requireContext()
    val currentMin = AppSettings.getBreakAlarmMinutes(ctx)

    val layout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(64, 40, 64, 16)
    }

    val npHour = NumberPicker(ctx).apply {
        minValue = 0; maxValue = 8
        displayedValues = Array(9) { "${it}時間" }
        value = currentMin / 60
    }
    val npMin = NumberPicker(ctx).apply {
        minValue = 0; maxValue = 11
        displayedValues = Array(12) { "%02d分".format(it * 5) }
        value = (currentMin % 60) / 5
    }
    layout.addView(npHour)
    layout.addView(npMin)

    MaterialAlertDialogBuilder(ctx)
        .setTitle("アラーム時間を設定")
        .setView(layout)
        .setPositiveButton("OK") { _, _ ->
            val totalMin = npHour.value * 60 + npMin.value * 5
            if (totalMin < 5) {
                Toast.makeText(ctx, "連続運転のアラームは5分以上で設定してください", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            AppSettings.setBreakAlarmMinutes(ctx, totalMin)
            show430TimerDialog()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun TenkoFragment.showTenkoSettings() {
    if (!isAdded) return
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val scroll = android.widget.ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
    }
    scroll.addView(root)

    fun label(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 13f; setTextColor(android.graphics.Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (14 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    }
    fun field(value: String, hint: String) = android.widget.EditText(ctx).apply {
        setText(value); this.hint = hint; inputType = InputType.TYPE_CLASS_TEXT
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    root.addView(label("乗務員名"))
    val etDriver  = field(AppSettings.getDriverName(ctx), "例: 〇〇 〇〇")
    root.addView(etDriver)

    root.addView(label("確認者名（運行管理者）"))
    val etChecker = field(AppSettings.getCheckerName(ctx), "乗務員本人（自己点呼時）")
    root.addView(etChecker)

    root.addView(label("事業者名"))
    val etCompany = field(AppSettings.getCompanyName(ctx), "例: 〇〇運送")
    root.addView(etCompany)

    val savedVehicles = AppSettings.getVehicles(ctx)
    root.addView(label("使用車両 1"))
    val etVehicle1 = field(savedVehicles[0], "例: 〇〇 100 あ 1234")
    root.addView(etVehicle1)
    root.addView(label("使用車両 2"))
    val etVehicle2 = field(savedVehicles[1], "例: 練馬 200 い 5678")
    root.addView(etVehicle2)
    root.addView(label("使用車両 3"))
    val etVehicle3 = field(savedVehicles[2], "使用しない場合は空欄のまま")
    root.addView(etVehicle3)

    root.addView(label("一覧の右端に表示する情報"))
    val displayOptions = listOf("アルコール検知値", "乗務時刻", "表示しない")
    val displayValues  = listOf("alcohol", "time", "none")
    val currentDisplay = AppSettings.getTenkoRightDisplay(ctx)
    val rbGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
    val radioButtons = displayOptions.mapIndexed { i, lbl ->
        android.widget.RadioButton(ctx).apply {
            text = lbl; id = android.view.View.generateViewId()
            if (displayValues[i] == currentDisplay) isChecked = true
        }.also { rbGroup.addView(it) }
    }
    root.addView(rbGroup)

    MaterialAlertDialogBuilder(ctx)
        .setTitle("点呼設定")
        .setView(scroll)
        .setPositiveButton("保存") { _, _ ->
            AppSettings.setDriverName(ctx,   etDriver.text.toString().trim())
            AppSettings.setCheckerName(ctx,  etChecker.text.toString().trim())
            AppSettings.setCompanyName(ctx,  etCompany.text.toString().trim())
            AppSettings.setVehicles(ctx, listOf(
                etVehicle1.text.toString().trim(),
                etVehicle2.text.toString().trim(),
                etVehicle3.text.toString().trim()
            ))
            val selectedIdx = radioButtons.indexOfFirst { it.isChecked }.coerceAtLeast(0)
            AppSettings.setTenkoRightDisplay(ctx, displayValues[selectedIdx])
            adapter.notifyDataSetChanged()
            Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun TenkoFragment.showReminderDialog() {
    if (!isAdded) return
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(ctx,
            android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
    }

    var beforeH = AppSettings.getReminderBeforeHour(ctx)
    var beforeM = AppSettings.getReminderBeforeMinute(ctx)
    var afterH  = AppSettings.getReminderAfterHour(ctx)
    var afterM  = AppSettings.getReminderAfterMinute(ctx)

    val scroll = android.widget.ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
    }
    scroll.addView(root)

    val onSurface = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)

    fun sectionHeader(text: String) = root.addView(TextView(ctx).apply {
        this.text = text; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (20 * dp).toInt() }
    })

    fun switchRow(label: String, checked: Boolean): Switch {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, (56 * dp).toInt())
                .also { it.topMargin = (10 * dp).toInt() }
        }
        row.addView(TextView(ctx).apply {
            text = label; textSize = 16f; setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        val sw = Switch(ctx).apply { isChecked = checked }
        row.addView(sw)
        root.addView(row)
        return sw
    }

    fun timeButton(hour: Int, minute: Int): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            isAllCaps = false; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            text = "%02d:%02d".format(hour, minute)
            setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(MATCH, (72 * dp).toInt())
                .also { it.topMargin = (8 * dp).toInt() }
            insetTop = 0; insetBottom = 0
        }
    }

    sectionHeader("乗務前点呼リマインダー")
    val switchBefore = switchRow("通知を有効にする", AppSettings.getReminderBeforeEnabled(ctx))
    val btnBefore = timeButton(beforeH, beforeM)
    btnBefore.setOnClickListener {
        TimePickerDialog(ctx, { _, h, min ->
            beforeH = h; beforeM = min
            btnBefore.text = "%02d:%02d".format(h, min)
        }, beforeH, beforeM, true).show()
    }
    root.addView(btnBefore)

    sectionHeader("乗務後点呼リマインダー")
    val switchAfter = switchRow("通知を有効にする", AppSettings.getReminderAfterEnabled(ctx))
    val btnAfter = timeButton(afterH, afterM)
    btnAfter.setOnClickListener {
        TimePickerDialog(ctx, { _, h, min ->
            afterH = h; afterM = min
            btnAfter.text = "%02d:%02d".format(h, min)
        }, afterH, afterM, true).show()
    }
    root.addView(btnAfter)

    MaterialAlertDialogBuilder(ctx)
        .setTitle("点呼リマインダー設定")
        .setView(scroll)
        .setPositiveButton("保存") { _, _ ->
            AppSettings.setReminderBeforeEnabled(ctx, switchBefore.isChecked)
            AppSettings.setReminderBeforeHour(ctx, beforeH)
            AppSettings.setReminderBeforeMinute(ctx, beforeM)
            AppSettings.setReminderAfterEnabled(ctx, switchAfter.isChecked)
            AppSettings.setReminderAfterHour(ctx, afterH)
            AppSettings.setReminderAfterMinute(ctx, afterM)

            applyReminder(ctx, true, beforeH, beforeM, switchBefore.isChecked)
            applyReminder(ctx, false, afterH, afterM, switchAfter.isChecked)

            val nowMin = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
            fun dayLabel(h: Int, m: Int) = if (h * 60 + m <= nowMin) "明日" else "今日"

            val parts = mutableListOf<String>()
            if (switchBefore.isChecked)
                parts.add("乗務前 %02d:%02d（%sから毎日）".format(beforeH, beforeM, dayLabel(beforeH, beforeM)))
            if (switchAfter.isChecked)
                parts.add("乗務後 %02d:%02d（%sから毎日）".format(afterH, afterM, dayLabel(afterH, afterM)))
            val msg = if (parts.isEmpty()) "通知をOFFにしました" else "以下の時刻に通知を設定しました:\n" + parts.joinToString("\n")
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun TenkoFragment.applyReminder(ctx: Context, isBefore: Boolean, hour: Int, minute: Int, enable: Boolean) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val reqCode = if (isBefore) 3001 else 3002
    val intent = Intent(ctx, ReminderReceiver::class.java).apply {
        putExtra(ReminderReceiver.EXTRA_TITLE, if (isBefore) "乗務前点呼の時間です" else "乗務後点呼の時間です")
        putExtra(ReminderReceiver.EXTRA_TEXT,  if (isBefore) "出発前に点呼を記録してください" else "帰着後に点呼を記録してください")
        putExtra(ReminderReceiver.EXTRA_NOTIF_ID, if (isBefore) ReminderReceiver.NOTIF_BEFORE else ReminderReceiver.NOTIF_AFTER)
    }
    val pi = PendingIntent.getBroadcast(ctx, reqCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    am.cancel(pi)
    if (!enable) return

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
    }
    am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
}
