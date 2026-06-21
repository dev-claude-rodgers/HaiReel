package com.rodgers.routist.ui

import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rodgers.routist.R
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.themeColor

internal fun TenkoFragment.showTenkoMenu() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val sheet = BottomSheetDialog(ctx)

    val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
    val redColor         = ContextCompat.getColor(ctx, R.color.colorActionRed)

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceColor)
    }

    val ripple = android.util.TypedValue().also {
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
    }.resourceId

    root.addMenuHeader("点呼メニュー", dp, onSurfaceColor, onSurfaceVariant, outlineVariant) { sheet.dismiss() }

    fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) =
        root.addMenuRow(emoji, title, sub, dp, color, onSurfaceVariant, ripple, { sheet.dismiss() }, action)

    fun divider() = root.addMenuDivider(dp, outlineVariant)

    // ── よく使う操作
    val timerState = AppSettings.getDriveTimerState(ctx)
    val timerLabel = when (timerState) {
        "DRIVING"  -> "🚗 運転中 — タップで状態確認"
        "ON_BREAK" -> "☕ 休憩中 — タップで状態確認"
        else       -> "未開始 — タップで運転開始"
    }
    row("⏱", "連続運転タイマー", timerLabel) { sheet.dismiss(); show430TimerDialog() }
    divider()
    // ── 設定
    row("⚙️", "点呼設定", "乗務員名・確認者名・車両・表示設定") { showTenkoSettings() }
    row("🔔", "点呼リマインダー", "乗務前後の通知時刻を設定") { showReminderDialog() }
    divider()
    // ── 出力・共有
    row("📊", "Excel出力", "表示月の点呼簿をExcelで保存・共有") { exportTenko() }
    row("📄", "PDF出力", "表示月の点呼簿をPDFで保存・共有") { exportTenkoPdf() }
    row("📤", "テキストで共有", "LINEやメールで表示月の点呼記録を送る") { shareMonthText() }
    row("📈", "表示月の集計", "完了率・乗務時間・アルコール検知") { showMonthSummary() }
    divider()
    row("🗑", "表示月の点呼データを削除", "削除直後は取り消し可能", redColor) { confirmDeleteMonth() }

    root.addView(View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
    })

    val scrollView = android.widget.ScrollView(ctx).apply { addView(root) }
    sheet.setContentView(scrollView)
    sheet.setOnShowListener {
        val bs = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.behavior.isDraggable = false
    }
    sheet.show()
}
