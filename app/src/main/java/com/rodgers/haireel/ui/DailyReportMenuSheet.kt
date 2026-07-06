package com.rodgers.haireel.ui

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rodgers.haireel.util.PatternStorage
import com.rodgers.haireel.util.SignatureStorage
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.viewmodel.ReportViewModel

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

    // 集計期間を計算してメニューのサブタイトルに使う
    val ym = reportViewModel.yearMonth.value
    val cd = reportViewModel.closingDay.value
    val (pStart, pEnd) = ReportViewModel.computePeriod(ym, cd)
    val ps = java.time.LocalDate.parse(pStart)
    val pe = java.time.LocalDate.parse(pEnd)
    val cdLabel = if (cd >= 31) "月末締め" else "${cd}日締め"
    val periodLabel = "${ps.monthValue}/${ps.dayOfMonth}〜${pe.monthValue}/${pe.dayOfMonth}"

    root.addMenuHeader("日報メニュー", dp, onSurfaceColor, onSurfaceVariant, outlineVariant) { sheet.dismiss() }

    fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) =
        root.addMenuRow(emoji, title, sub, dp, color, onSurfaceVariant, ripple, { sheet.dismiss() }, action)

    fun divider() = root.addMenuDivider(dp, outlineVariant)

    fun sectionLabel(text: String) = root.addView(android.widget.TextView(ctx).apply {
        this.text = text; textSize = 11f
        setTextColor(onSurfaceVariant)
        setPadding((84 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (2 * dp).toInt())
    })

    row("📅", "今日の日報を記録", "今日の行を開いて入力する") { openTodayDialog() }
    divider()
    // ── 帳票設定
    val currentPatternName = currentPattern().title
    row("📋", "帳票パターンを選択", "現在: $currentPatternName") { showPatternListDialog() }
    row("⛽", "燃料費設定", "車種・ガソリン単価・燃費を設定する") { showFareCalculationDialog() }
    row("🛢️", "給油記録", "給油量・単価を記録して実燃費を計算") { showFuelRecordSheet(fuelViewModel) }
    row("🖊️", "作業者署名を設定", "Excelに印刷する作業者の署名") { showSignatureDialog(SignatureStorage.TYPE_DRIVER, "作業者") }
    row("🤝", "取引先署名を設定", "Excelに印刷する取引先の署名") { showSignatureDialog(SignatureStorage.TYPE_CLIENT, "取引先") }
    divider()
    // ── 集計
    row("📈", "案件別集計", "表示月の案件ごとの稼働・収入を確認") { showAssignmentSummarySheet() }
    divider()
    // ── 締め日ベース出力
    sectionLabel("$cdLabel での出力（$periodLabel）")
    row("📊", "Excel出力", "帳票パターンの締め日で集計して出力") { exportExcel() }
    row("📤", "テキストで共有", "帳票パターンの締め日で集計して共有") { shareReportText() }

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
