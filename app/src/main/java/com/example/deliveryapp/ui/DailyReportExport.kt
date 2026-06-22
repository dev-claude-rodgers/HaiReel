package com.rodgers.routist.ui

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.excel.ExcelGenerator
import com.rodgers.routist.excel.TenkoExcelGenerator
import com.rodgers.routist.pdf.PdfGenerator
import com.rodgers.routist.util.BackupManager
import com.rodgers.routist.util.PatternStorage
import com.rodgers.routist.util.SignatureStorage
import com.rodgers.routist.viewmodel.*
import com.rodgers.routist.viewmodel.ReportViewModel
import kotlinx.coroutines.launch

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
                Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show()
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
        // exportNippo と同様に案件パターンを優先して取得
        val group = deliveryViewModel.currentGroup()
        val pid   = group?.patternId?.takeIf { it != -1 }
                    ?: PatternStorage.getActiveId(ctx).takeIf { it != -1 }
        val pattern = if (pid != null) PatternStorage.get(ctx, pid) ?: PatternStorage.ensureDefault(ctx)
                      else PatternStorage.ensureDefault(ctx)
        val (startDate, endDate) = ReportViewModel.computePeriod(ym, pattern.closingDay)
        val assignmentId = group?.id ?: reportViewModel.assignmentId.value
        val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, assignmentId)
        if (records.isEmpty()) {
            Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show()
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

