package com.rodgers.haireel.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.BackupManager
import com.rodgers.haireel.util.themeColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

internal fun TenkoFragment.showMonthSummary() {
    if (!isAdded) return
    val ctx     = requireContext()
    val ym      = viewModel.yearMonth.value
    val (y, m)  = ym.split("-").map { it.toInt() }
    val records = viewModel.monthRecords.value
    val daysInMonth = YearMonth.of(y, m).lengthOfMonth()

    // 当月表示中は今日までを「有効日数」とし、未来の日付を未記録に含めない
    val today         = LocalDate.now()
    val isCurrentMonth = today.year == y && today.monthValue == m
    val effectiveDays = if (isCurrentMonth) today.dayOfMonth else daysInMonth

    val daysWithBefore    = records.count { it.beforeDone }
    val daysWithAfter     = records.count { it.afterDone }
    val daysWithBoth      = records.count { it.beforeDone && it.afterDone }
    val daysBeforeOnly    = records.count { it.beforeDone && !it.afterDone }
    val daysAfterOnly     = records.count { !it.beforeDone && it.afterDone }
    val recordedDates     = records.map { it.date }.toSet()
    val daysNoRecord      = (effectiveDays - recordedDates.size).coerceAtLeast(0)
    val alcBeforeAbnormal = records.count { (it.beforeAlcohol ?: 0.0) > 0.0 }
    val alcAfterAbnormal  = records.count { (it.afterAlcohol  ?: 0.0) > 0.0 }
    val totalAbnormal     = alcBeforeAbnormal + alcAfterAbnormal
    val completionPct     = if (effectiveDays > 0) (daysWithBoth * 100 / effectiveDays) else 0

    // 乗務時間集計（乗務前・後の両方に時刻が入っている便のみ）
    fun parseMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size < 2) return null
        val h   = parts[0].toIntOrNull() ?: return null
        val min = parts[1].toIntOrNull() ?: return null
        return h * 60 + min
    }
    val workMinutesList = records.mapNotNull { rec ->
        val before = parseMinutes(rec.beforeTime)
        val after  = parseMinutes(rec.afterTime)
        if (before != null && after != null) {
            var diff = after - before
            if (diff < 0) diff += 24 * 60  // 日をまたぐ場合
            diff
        } else null
    }
    val totalMinutes = workMinutesList.sum()
    val avgMinutes   = if (workMinutesList.isNotEmpty()) totalMinutes / workMinutesList.size else 0
    fun Int.toHM() = "${this / 60}時間${this % 60}分"

    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val cGreen  = Color.parseColor("#2E7D32")
    val cRed    = Color.parseColor("#C62828")
    val cOrange = Color.parseColor("#E65100")
    val cBlue   = Color.parseColor("#1565C0")
    val cGray   = Color.parseColor("#AAAAAA")
    val cBg     = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
    val cOnSurf = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

    val scroll = android.widget.ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
    }
    scroll.addView(root)

    val pctColor = when {
        completionPct >= 80 -> cGreen
        completionPct >= 50 -> cOrange
        else                -> cRed
    }
    val banner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(pctColor); cornerRadius = 12*dp }
        setPadding((20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (16*dp).toInt() }
    }
    banner.addView(TextView(ctx).apply {
        text = "前後完了率"; textSize = 14f; setTextColor(Color.parseColor("#DDEEEE"))
        gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })
    banner.addView(TextView(ctx).apply {
        text = "${completionPct}%"; textSize = 56f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })
    banner.addView(TextView(ctx).apply {
        val suffix = if (isCurrentMonth) "（当日まで）" else ""
        text = "${daysWithBoth}日完了 ／ ${effectiveDays}日$suffix"; textSize = 14f
        setTextColor(Color.parseColor("#DDEEEE")); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })
    root.addView(banner)

    fun accentCard(accent: Int, block: LinearLayout.() -> Unit) {
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(cBg); cornerRadius = 10*dp }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (12*dp).toInt() }
        }
        outer.addView(android.view.View(ctx).apply {
            background = GradientDrawable().apply { setColor(accent); cornerRadius = 10*dp }
            layoutParams = LinearLayout.LayoutParams((5*dp).toInt(), MATCH)
        })
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14*dp).toInt(), (14*dp).toInt(), (14*dp).toInt(), (14*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        inner.block()
        outer.addView(inner)
        root.addView(outer)
    }

    fun LinearLayout.cardTitle(title: String, color: Int) {
        addView(TextView(ctx).apply {
            text = title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (10*dp).toInt() }
        })
    }

    fun LinearLayout.statRow(icon: String, label: String, value: String, valueColor: Int = Color.WHITE) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, (44*dp).toInt())
        }
        row.addView(TextView(ctx).apply {
            text = icon; textSize = 18f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((36*dp).toInt(), WRAP)
        })
        row.addView(TextView(ctx).apply {
            text = label; textSize = 14f; setTextColor(cOnSurf)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = value; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(valueColor)
        })
        addView(row)
    }

    fun LinearLayout.progressBar(label: String, done: Int, total: Int, barColor: Int) {
        val pct = if (total > 0) done.toFloat() / total else 0f
        val hdr = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        hdr.addView(TextView(ctx).apply {
            text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        hdr.addView(TextView(ctx).apply {
            text = "${done}日 / ${total}日"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(barColor)
        })
        addView(hdr)
        val barBg = android.widget.FrameLayout(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#444444")); cornerRadius = 6*dp }
            layoutParams = LinearLayout.LayoutParams(MATCH, (10*dp).toInt())
                .also { it.topMargin = (6*dp).toInt(); it.bottomMargin = (10*dp).toInt() }
        }
        barBg.addView(android.view.View(ctx).apply {
            background = GradientDrawable().apply { setColor(barColor); cornerRadius = 6*dp }
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (pct * ctx.resources.displayMetrics.widthPixels * 0.65f).toInt()
                    .coerceAtLeast(if (done > 0) (10*dp).toInt() else 0),
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        addView(barBg)
    }

    fun LinearLayout.divider() {
        addView(android.view.View(ctx).apply {
            setBackgroundColor(Color.parseColor("#444444"))
            layoutParams = LinearLayout.LayoutParams(MATCH, (1*dp).toInt())
                .also { it.topMargin = (4*dp).toInt(); it.bottomMargin = (4*dp).toInt() }
        })
    }

    accentCard(cBlue) {
        cardTitle("📋  記録状況", cBlue)
        progressBar("前後 両方完了", daysWithBoth, effectiveDays,
            if (daysWithBoth == effectiveDays) cGreen else cBlue)
        statRow("🌅", "乗務前 完了", "${daysWithBefore}便")
        statRow("🌆", "乗務後 完了", "${daysWithAfter}便")
        if (daysBeforeOnly > 0 || daysAfterOnly > 0) {
            divider()
            if (daysBeforeOnly > 0)
                statRow("⚠️", "前のみ（後が未記録）", "${daysBeforeOnly}便", cOrange)
            if (daysAfterOnly > 0)
                statRow("⚠️", "後のみ（前が未記録）", "${daysAfterOnly}便", cOrange)
        }
        divider()
        val noRecordLabel = if (isCurrentMonth) "未記録（当日まで）" else "未記録"
        statRow(if (daysNoRecord > 0) "⚠️" else "✅", noRecordLabel,
            if (daysNoRecord == 0) "なし" else "${daysNoRecord}日",
            if (daysNoRecord > 0) cRed else cGreen)
    }

    if (workMinutesList.isNotEmpty()) {
        accentCard(Color.parseColor("#1B5E20")) {
            cardTitle("⏱  乗務時間", Color.parseColor("#66BB6A"))
            statRow("📊", "月合計", totalMinutes.toHM())
            statRow("📈", "1便あたり平均", avgMinutes.toHM())
            statRow("🔢", "集計対象", "${workMinutesList.size}便")
        }
    }

    val alcAccent = if (totalAbnormal == 0) cGreen else cRed
    accentCard(alcAccent) {
        cardTitle("🍺  アルコール検知", alcAccent)
        statRow("🌅", "乗務前",
            if (alcBeforeAbnormal == 0) "検知なし" else "${alcBeforeAbnormal}件",
            if (alcBeforeAbnormal == 0) cGreen else cRed)
        statRow("🌆", "乗務後",
            if (alcAfterAbnormal == 0) "検知なし" else "${alcAfterAbnormal}件",
            if (alcAfterAbnormal == 0) cGreen else cRed)
        divider()
        statRow(if (totalAbnormal == 0) "✅" else "🚨", "合計",
            if (totalAbnormal == 0) "異常なし" else "${totalAbnormal}件",
            alcAccent)
    }

    MaterialAlertDialogBuilder(ctx)
        .setTitle("${y}年${m}月 点呼集計")
        .setView(scroll)
        .setPositiveButton("閉じる", null)
        .show()
}

internal fun TenkoFragment.shareMonthText() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = viewModel.yearMonth.value
    val (y, m) = ym.split("-").map { it.toInt() }
    val records = viewModel.monthRecords.value
    if (records.isEmpty()) {
        Toast.makeText(ctx, "この月の点呼記録がありません", Toast.LENGTH_SHORT).show()
        return
    }
    val text = buildString {
        appendLine("【${y}年${m}月 点呼記録】")
        appendLine()
        records.forEach { r ->
            append(r.date)
            if (r.beforeDone) {
                append("  前:${r.beforeTime}")
                r.beforeAlcohol?.let { append(" ALC%.2f".format(it)) }
            }
            if (r.afterDone) {
                append("  後:${r.afterTime}")
                r.afterAlcohol?.let { append(" ALC%.2f".format(it)) }
            }
            if (!r.note.isNullOrBlank()) append("  📝${r.note}")
            appendLine()
        }
    }
    startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${y}年${m}月 点呼記録")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "点呼記録を共有"))
}

internal fun TenkoFragment.backupData() {
    if (!isAdded) return
    val ctx = requireContext()
    lifecycleScope.launch {
        try {
            val file = BackupManager.createBackup(ctx)
            val uri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "バックアップを共有"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "バックアップエラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun TenkoFragment.confirmDelete(record: TenkoRecord) {
    if (!isAdded) return
    val ctx = requireContext()
    MaterialAlertDialogBuilder(ctx)
        .setTitle("点呼記録を削除")
        .setMessage("${record.date} の点呼記録を削除しますか？")
        .setPositiveButton("削除") { _, _ ->
            viewModel.delete(record)
            Snackbar.make(requireView(), "点呼記録を削除しました", Snackbar.LENGTH_LONG)
                .setDuration(AppSettings.getUndoSeconds(ctx) * 1000)
                .setAction("元に戻す") { viewModel.restore(record) }
                .show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun TenkoFragment.confirmDeleteMonth() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = viewModel.yearMonth.value
    val (y, m) = ym.split("-").map { it.toInt() }
    val undoSec = AppSettings.getUndoSeconds(ctx)
    MaterialAlertDialogBuilder(ctx)
        .setTitle("⚠️  データを削除")
        .setMessage("${y}年${m}月のデータをすべて削除します。\n削除直後${undoSec}秒以内なら元に戻せます。")
        .setPositiveButton("削除する") { _, _ ->
            viewModel.deleteMonthWithUndo(ym) { deleted ->
                if (!isAdded) return@deleteMonthWithUndo
                Snackbar.make(requireView(), "${y}年${m}月のデータを削除しました", Snackbar.LENGTH_LONG)
                    .setDuration(AppSettings.getUndoSeconds(requireContext()) * 1000)
                    .setAction("元に戻す") { viewModel.restoreAll(deleted) }
                    .show()
            }
        }
        .setNegativeButton("キャンセル", null)
        .show()
}
