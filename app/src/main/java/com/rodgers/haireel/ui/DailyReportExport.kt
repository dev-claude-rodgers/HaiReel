package com.rodgers.haireel.ui

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.excel.ExcelGenerator
import com.rodgers.haireel.model.ColumnType
import com.rodgers.haireel.excel.TenkoExcelGenerator
import com.rodgers.haireel.util.BackupManager
import com.rodgers.haireel.util.PatternStorage
import com.rodgers.haireel.util.SignatureStorage
import com.rodgers.haireel.viewmodel.*
import kotlinx.coroutines.launch

internal fun DailyReportFragment.exportExcel() {
    if (!isAdded) return
    val ctx      = requireContext()
    val ym       = reportViewModel.yearMonth.value
    val patterns = PatternStorage.getAll(ctx)
    if (patterns.size <= 1) {
        val pattern = currentPattern()
        exportNippo(ctx, ym, pattern.id.toString(), pattern.clientName.ifBlank { pattern.title })
        return
    }
    val options = (listOf("すべての取引先") + patterns.map { it.clientName.ifBlank { it.title } }).toTypedArray()
    MaterialAlertDialogBuilder(ctx)
        .setTitle("出力する取引先を選択")
        .setItems(options) { _, which ->
            val (id, name) = if (which == 0) Pair("", "全取引先")
                             else { val p = patterns[which - 1]; Pair(p.id.toString(), p.clientName.ifBlank { p.title }) }
            exportNippo(ctx, ym, id, name)
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.exportNippo(ctx: android.content.Context, ym: String, assignmentId: String, assignmentName: String) {
    val pattern = assignmentId.toIntOrNull()?.let { PatternStorage.get(ctx, it) } ?: PatternStorage.ensureDefault(ctx)
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
            ctx.showErrorDialog("Excel出力エラー", e.localizedMessage ?: "Excelファイルの作成に失敗しました。\nストレージの空き容量を確認してください。")
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
            ctx.showErrorDialog("点呼簿出力エラー", e.localizedMessage ?: "点呼簿の作成に失敗しました。\nストレージの空き容量を確認してください。")
        }
    }
}

internal fun DailyReportFragment.shareExcel(ctx: android.content.Context, file: java.io.File) {
    shareXlsxFile(file, "稼働報告書Excelを共有")
}

internal fun DailyReportFragment.shareReportText() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = reportViewModel.yearMonth.value
    val (y, m) = ym.split("-").map { it.toInt() }
    lifecycleScope.launch {
        val pattern = currentPattern()
        val (startDate, endDate) = ReportViewModel.computePeriod(ym, pattern.closingDay)
        val records = reportViewModel.recordsForPeriodWithAssignment(startDate, endDate, pattern.id.toString())
        if (records.isEmpty()) {
            Toast.makeText(ctx, "この期間の記録がありません", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val cols = pattern.excelColumns
        val text = buildString {
            appendLine("【${y}年${m}月 稼働報告】")
            appendLine()
            records.forEach { r ->
                append(r.date)
                cols.forEach { col ->
                    val v = when (col.type) {
                        ColumnType.START_TIME     -> r.startTime.takeIf { it.isNotBlank() }?.let { "${col.label}:$it" }
                        ColumnType.END_TIME       -> r.endTime.takeIf { it.isNotBlank() }?.let { "${col.label}:$it" }
                        ColumnType.WORKING_HOURS  -> r.workingHoursText.takeIf { it.isNotBlank() }?.let { "${col.label}:$it" }
                        ColumnType.DELIVERY_COUNT -> if (r.deliveryCount > 0) "${col.label}:${r.deliveryCount}件" else null
                        ColumnType.PACKAGE_COUNT  -> if (r.packageCount > 0) "${col.label}:${r.packageCount}個" else null
                        ColumnType.DISTANCE       -> if (r.distanceKm > 0f) "${col.label}:%.0fkm".format(r.distanceKm) else null
                        ColumnType.FUEL_COST      -> if (r.fuelCost > 0) "${col.label}:${"%,d".format(r.fuelCost)}円" else null
                        ColumnType.METER_START    -> if (r.startMeter > 0) "${col.label}:${r.startMeter}km" else null
                        ColumnType.METER_END      -> if (r.endMeter > 0) "${col.label}:${r.endMeter}km" else null
                        ColumnType.INCOME         -> if (r.income > 0) "${col.label}:${"%,d".format(r.income)}円" else null
                        ColumnType.AREA           -> r.area.takeIf { it.isNotBlank() }?.let { "${col.label}:$it" }
                        ColumnType.REMARKS        -> r.remarks.takeIf { it.isNotBlank() }?.let { "📝$it" }
                        ColumnType.ALC_CHECK      -> r.alcCheck.takeIf { it.isNotBlank() }?.let { "${col.label}:$it" }
                    }
                    if (v != null) append("  $v")
                }
                appendLine()
            }
            appendLine()
            val workRecords = records.filter { !it.noWork }
            val workDays = workRecords.distinctBy { it.date }.size
            appendLine("稼働日数: ${workDays}日")
            cols.forEach { col ->
                when (col.type) {
                    ColumnType.DELIVERY_COUNT -> {
                        val total = workRecords.sumOf { it.deliveryCount }
                        if (total > 0) appendLine("合計${col.label}: ${total}件")
                    }
                    ColumnType.PACKAGE_COUNT  -> {
                        val total = workRecords.sumOf { it.packageCount }
                        if (total > 0) appendLine("合計${col.label}: ${total}個")
                    }
                    ColumnType.INCOME         -> {
                        val total = workRecords.sumOf { it.income }
                        if (total > 0) appendLine("合計${col.label}: ${"%,d".format(total)}円")
                    }
                    ColumnType.FUEL_COST      -> {
                        val total = workRecords.sumOf { it.fuelCost }
                        if (total > 0) appendLine("合計${col.label}: ${"%,d".format(total)}円")
                    }
                    ColumnType.DISTANCE       -> {
                        val total = workRecords.sumOf { it.distanceKm.toDouble() }
                        if (total > 0) appendLine("合計${col.label}: %.0fkm".format(total))
                    }
                    ColumnType.WORKING_HOURS  -> {
                        val totalMin = workRecords.sumOf { it.workingMinutes }
                        if (totalMin > 0) appendLine("合計${col.label}: %d時間%02d分".format(totalMin / 60, totalMin % 60))
                    }
                    else -> {}
                }
            }
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${y}年${m}月 稼働報告")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "稼働報告をテキストで共有"))
    }
}

