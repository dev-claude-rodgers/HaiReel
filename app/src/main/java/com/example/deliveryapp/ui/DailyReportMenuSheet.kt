package com.rodgers.routist.ui

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rodgers.routist.util.SignatureStorage
import com.rodgers.routist.util.themeColor

internal fun DailyReportFragment.showReportMenu() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

    val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceColor)
    }

    val ripple = android.util.TypedValue().also {
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
    }.resourceId

    root.addMenuHeader("日報メニュー", dp, onSurfaceColor, onSurfaceVariant, outlineVariant) { sheet.dismiss() }

    fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) =
        root.addMenuRow(emoji, title, sub, dp, color, onSurfaceVariant, ripple, { sheet.dismiss() }, action)

    fun divider() = root.addMenuDivider(dp, outlineVariant)

    row("📅", "今日の日報を記録", "今日の行を開いて入力する") { openTodayDialog() }
    divider()
    // ── 帳票設定
    val currentPatternName = currentPattern().title
    row("📋", "帳票パターンを選択", "現在: $currentPatternName") { showPatternListDialog() }
    row("🖊️", "作業者署名を設定", "Excelに印刷する作業者の署名") { showSignatureDialog(SignatureStorage.TYPE_DRIVER, "作業者") }
    row("🤝", "取引先署名を設定", "Excelに印刷する取引先の署名") { showSignatureDialog(SignatureStorage.TYPE_CLIENT, "取引先") }
    divider()
    // ── 出力・集計
    row("📊", "Excel出力", "集計期間の稼働報告書を保存・共有") { exportExcel() }
    row("📄", "PDF出力", "表示月の日報をPDFで保存・共有") { exportReportPdf() }
    row("📈", "案件別集計", "表示月の案件ごとの稼働・収入を確認") { showAssignmentSummarySheet() }

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
