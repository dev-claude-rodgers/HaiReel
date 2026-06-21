package com.rodgers.routist.ui

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.excel.ExcelGenerator
import com.rodgers.routist.excel.TenkoExcelGenerator
import com.rodgers.routist.model.ReportPattern
import com.rodgers.routist.model.WorkRecord
import com.rodgers.routist.pdf.PdfGenerator
import com.rodgers.routist.util.BackupManager
import com.rodgers.routist.util.PatternStorage
import com.rodgers.routist.util.SignatureStorage
import com.rodgers.routist.viewmodel.*
import com.rodgers.routist.viewmodel.ReportViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal fun DailyReportFragment.exportExcel() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = reportViewModel.yearMonth.value
    val groups = deliveryViewModel.groups.value
    if (groups.size <= 1) {
        exportNippo(ctx, ym, reportViewModel.assignmentId.value, deliveryViewModel.currentGroup()?.name ?: "")
        return
    }
    val options = (listOf("すべての案件") + groups.map { it.name }).toTypedArray()
    MaterialAlertDialogBuilder(ctx)
        .setTitle("出力する案件を選択")
        .setItems(options) { _, which ->
            val (id, name) = if (which == 0) Pair("", "全案件")
                             else { val g = groups[which - 1]; Pair(g.id, g.name) }
            exportNippo(ctx, ym, id, name)
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.exportNippo(ctx: android.content.Context, ym: String, assignmentId: String, assignmentName: String) {
    val group   = if (assignmentId.isNotBlank()) (deliveryViewModel.groups.value).find { it.id == assignmentId } else null
    val pid     = group?.patternId?.takeIf { it != -1 }
                  ?: PatternStorage.getActiveId(ctx).takeIf { it != -1 }
    val pattern = if (pid != null) PatternStorage.get(ctx, pid) ?: PatternStorage.ensureDefault(ctx) else PatternStorage.ensureDefault(ctx)
    lifecycleScope.launch {
        try {
            val (startDate, endDate) = ReportViewModel.computePeriod(ym, pattern.closingDay)
            val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, assignmentId)
            if (records.isEmpty()) {
                Toast.makeText(ctx, "この期間の記録がまだありません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val driverSig = SignatureStorage.fileFor(ctx, SignatureStorage.TYPE_DRIVER).takeIf { it.exists() }
            val clientSig = SignatureStorage.fileFor(ctx, SignatureStorage.TYPE_CLIENT).takeIf { it.exists() }
            val file = ExcelGenerator(ctx).generate(records, ym, pattern, driverSig, clientSig, assignmentName, portrait = true)
            shareExcel(ctx, file)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Excel出力エラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun DailyReportFragment.exportTenko(ctx: android.content.Context, ym: String) {
    lifecycleScope.launch {
        try {
            val records = tenkoViewModel.recordsForMonth(ym)
            val file = TenkoExcelGenerator(ctx).generate(records, ym)
            shareExcel(ctx, file)
        } catch (e: Exception) {
            Toast.makeText(ctx, "点呼簿出力エラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun DailyReportFragment.shareExcel(ctx: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "稼働報告書Excelを共有"))
}

internal fun DailyReportFragment.exportReportPdf() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = reportViewModel.yearMonth.value
    val groups = deliveryViewModel.groups.value
    if (groups.size <= 1) {
        doExportReportPdf(ctx, ym, reportViewModel.assignmentId.value, deliveryViewModel.currentGroup()?.name ?: "")
        return
    }
    val options = (listOf("すべての案件") + groups.map { it.name }).toTypedArray()
    MaterialAlertDialogBuilder(ctx)
        .setTitle("出力する案件を選択")
        .setItems(options) { _, which ->
            val (id, name) = if (which == 0) Pair("", "全案件")
                             else { val g = groups[which - 1]; Pair(g.id, g.name) }
            doExportReportPdf(ctx, ym, id, name)
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.doExportReportPdf(ctx: android.content.Context, ym: String, assignmentId: String, assignmentName: String) {
    val group   = if (assignmentId.isNotBlank()) (deliveryViewModel.groups.value).find { it.id == assignmentId } else null
    val pid     = group?.patternId?.takeIf { it != -1 }
                  ?: PatternStorage.getActiveId(ctx).takeIf { it != -1 }
    val pattern = if (pid != null) PatternStorage.get(ctx, pid) ?: PatternStorage.ensureDefault(ctx)
                  else PatternStorage.ensureDefault(ctx)
    lifecycleScope.launch {
        try {
            val (startDate, endDate) = ReportViewModel.computePeriod(ym, pattern.closingDay)
            val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, assignmentId)
            if (records.isEmpty()) {
                Toast.makeText(ctx, "この期間の記録がまだありません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = PdfGenerator.generateReportPdf(ctx, records, ym, assignmentName, portrait = true, pattern)
            sharePdf(ctx, file, "${ym.replace("-", "年").plus("月")}日報PDF")
        } catch (e: Exception) {
            Toast.makeText(ctx, "PDF出力エラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun DailyReportFragment.sharePdf(ctx: android.content.Context, file: java.io.File, label: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, label))
}

internal fun DailyReportFragment.shareReportText() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = reportViewModel.yearMonth.value
    val (y, m) = ym.split("-").map { it.toInt() }
    lifecycleScope.launch {
        val pattern = PatternStorage.getActiveId(ctx).let {
            if (it != -1) PatternStorage.get(ctx, it) else null
        } ?: PatternStorage.ensureDefault(ctx)
        val (startDate, endDate) = ReportViewModel.computePeriod(ym, pattern.closingDay)
        val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, "")
        if (records.isEmpty()) {
            Toast.makeText(ctx, "この期間の記録がまだありません", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val text = buildString {
            appendLine("【${y}年${m}月 稼働報告】")
            appendLine()
            records.forEach { r ->
                append(r.date)
                if (r.deliveryCount > 0) append("  ${pattern.deliveryLabel}:${r.deliveryCount}件")
                if (r.income > 0) append("  収入:${"%,d".format(r.income)}円")
                if (r.distanceKm > 0f) append("  距離:%.0fkm".format(r.distanceKm))
                if (r.fuelCost > 0) append("  燃料費:${"%,d".format(r.fuelCost)}円")
                if (r.remarks.isNotBlank()) append("  📝${r.remarks}")
                appendLine()
            }
            appendLine()
            val totalDeliv = records.sumOf { it.deliveryCount }
            val totalIncome = records.sumOf { it.income }
            val totalDist = records.sumOf { it.distanceKm.toDouble() }
            val workDays = records.sumOf { 1 + it.endDateOffset }
            appendLine("稼働日数: ${workDays}日")
            if (totalDeliv > 0) appendLine("合計${pattern.deliveryLabel}: ${totalDeliv}件")
            if (totalIncome > 0) appendLine("合計収入: ${"%,d".format(totalIncome)}円")
            if (totalDist > 0) appendLine("合計距離: %.0fkm".format(totalDist))
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${y}年${m}月 稼働報告")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "稼働報告をテキストで共有"))
    }
}

// ── 任意期間を選択して出力 ────────────────────────────────────────────────

internal fun DailyReportFragment.showPeriodExportDialog() {
    if (!isAdded) return
    val ctx     = requireContext()
    val ym      = reportViewModel.yearMonth.value
    val pattern = PatternStorage.getActiveId(ctx).let {
        if (it != -1) PatternStorage.get(ctx, it) else null
    } ?: PatternStorage.ensureDefault(ctx)

    // 締め日に基づく集計期間をデフォルト値として設定
    val (defaultStart, defaultEnd) = ReportViewModel.computePeriod(ym, pattern.closingDay)
    val zone    = ZoneId.systemDefault()
    // MaterialDatePicker はUTC深夜0時ベースのミリ秒を使うため、UTC換算でエポック日×86400000
    val toUtcMs = { d: String -> LocalDate.parse(d).toEpochDay() * 86_400_000L }
    val startMs = toUtcMs(defaultStart)
    val endMs   = toUtcMs(defaultEnd)

    // 今日以降は選択不可
    val constraints = CalendarConstraints.Builder()
        .setValidator(DateValidatorPointBackward.now())
        .build()

    val picker = MaterialDatePicker.Builder.dateRangePicker()
        .setTitleText("出力期間を選択（1ヶ月以内）")
        .setSelection(androidx.core.util.Pair(startMs, endMs))
        .setCalendarConstraints(constraints)
        .build()

    picker.addOnPositiveButtonClickListener { selection ->
        val utc   = ZoneId.of("UTC")
        val start = Instant.ofEpochMilli(selection.first).atZone(utc).toLocalDate()
        val end   = Instant.ofEpochMilli(selection.second).atZone(utc).toLocalDate()
        if (ChronoUnit.DAYS.between(start, end) > 31) {
            Toast.makeText(ctx, "1ヶ月以内の期間を選択してください", Toast.LENGTH_LONG).show()
            return@addOnPositiveButtonClickListener
        }
        val startStr = start.toString()
        val endStr   = end.toString()
        val label    = "${start.monthValue}/${start.dayOfMonth}〜${end.monthValue}/${end.dayOfMonth}"
        MaterialAlertDialogBuilder(ctx)
            .setTitle("出力形式を選択")
            .setMessage("期間: $label")
            .setItems(arrayOf("📤 テキストで共有", "📊 Excel出力", "📄 PDF出力")) { _, which ->
                when (which) {
                    0 -> shareReportTextForPeriod(ctx, startStr, endStr, label, pattern)
                    1 -> exportNippoForPeriod(ctx, startStr, endStr, label, pattern)
                    2 -> exportPdfForPeriod(ctx, startStr, endStr, label, pattern)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
    picker.show(parentFragmentManager, "period_picker")
}

private fun DailyReportFragment.shareReportTextForPeriod(
    ctx: android.content.Context, startDate: String, endDate: String, label: String, pattern: ReportPattern
) {
    lifecycleScope.launch {
        val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, "")
        if (records.isEmpty()) {
            Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show(); return@launch
        }
        val text = buildReportText(label, records, pattern)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "稼働報告 $label")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "稼働報告をテキストで共有"))
    }
}

private fun DailyReportFragment.exportNippoForPeriod(
    ctx: android.content.Context, startDate: String, endDate: String, label: String, pattern: ReportPattern
) {
    lifecycleScope.launch {
        try {
            val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, "")
            if (records.isEmpty()) {
                Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show(); return@launch
            }
            val driverSig = SignatureStorage.fileFor(ctx, SignatureStorage.TYPE_DRIVER).takeIf { it.exists() }
            val clientSig = SignatureStorage.fileFor(ctx, SignatureStorage.TYPE_CLIENT).takeIf { it.exists() }
            val file = ExcelGenerator(ctx).generateForPeriod(records, startDate, endDate, label, pattern, driverSig, clientSig, portrait = true)
            shareExcel(ctx, file)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Excel出力エラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun DailyReportFragment.exportPdfForPeriod(
    ctx: android.content.Context, startDate: String, endDate: String, label: String, pattern: ReportPattern
) {
    lifecycleScope.launch {
        try {
            val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, "")
            if (records.isEmpty()) {
                Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show(); return@launch
            }
            val file = PdfGenerator.generateReportPdfForPeriod(ctx, records, startDate, endDate, label, portrait = true, pattern)
            sharePdf(ctx, file, "稼働報告書PDF $label")
        } catch (e: Exception) {
            Toast.makeText(ctx, "PDF出力エラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun buildReportText(label: String, records: List<WorkRecord>, pattern: ReportPattern): String =
    buildString {
        appendLine("【稼働報告 $label】")
        appendLine()
        records.forEach { r ->
            append(r.date)
            if (r.deliveryCount > 0) append("  ${pattern.deliveryLabel}:${r.deliveryCount}件")
            if (r.income > 0) append("  収入:${"%,d".format(r.income)}円")
            if (r.distanceKm > 0f) append("  距離:%.0fkm".format(r.distanceKm))
            if (r.fuelCost > 0) append("  燃料費:${"%,d".format(r.fuelCost)}円")
            if (r.remarks.isNotBlank()) append("  📝${r.remarks}")
            appendLine()
        }
        appendLine()
        val workDays    = records.sumOf { 1 + it.endDateOffset }
        val totalDeliv  = records.sumOf { it.deliveryCount }
        val totalIncome = records.sumOf { it.income }
        val totalDist   = records.sumOf { it.distanceKm.toDouble() }
        appendLine("稼働日数: ${workDays}日")
        if (totalDeliv  > 0) appendLine("合計${pattern.deliveryLabel}: ${totalDeliv}件")
        if (totalIncome > 0) appendLine("合計収入: ${"%,d".format(totalIncome)}円")
        if (totalDist   > 0) appendLine("合計距離: %.0fkm".format(totalDist))
    }

internal fun DailyReportFragment.backupData() {
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
