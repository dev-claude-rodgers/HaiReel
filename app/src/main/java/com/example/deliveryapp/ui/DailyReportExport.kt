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
import com.rodgers.routist.viewmodel.ReportViewModel
import kotlinx.coroutines.launch

internal fun DailyReportFragment.askOrientation(ctx: android.content.Context, format: String = "ファイル", onSelected: (Boolean) -> Unit) {
    showOrientationSheet(ctx, format, onSelected)
}

internal fun DailyReportFragment.exportExcel() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = reportViewModel.yearMonth.value
    val groups = deliveryViewModel.groups.value
    if (groups.size <= 1) {
        askOrientation(ctx, "Excel") { portrait ->
            exportNippo(ctx, ym, reportViewModel.assignmentId.value, deliveryViewModel.currentGroup()?.name ?: "", portrait)
        }
        return
    }
    val options = (listOf("すべての案件") + groups.map { it.name }).toTypedArray()
    MaterialAlertDialogBuilder(ctx)
        .setTitle("出力する案件を選択")
        .setItems(options) { _, which ->
            val (id, name) = if (which == 0) Pair("", "全案件")
                             else { val g = groups[which - 1]; Pair(g.id, g.name) }
            askOrientation(ctx, "Excel") { portrait ->
                exportNippo(ctx, ym, id, name, portrait)
            }
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.exportNippo(ctx: android.content.Context, ym: String, assignmentId: String, assignmentName: String, portrait: Boolean = false) {
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
            val file = ExcelGenerator(ctx).generate(records, ym, pattern, driverSig, clientSig, assignmentName, portrait)
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
        }, "Excelを共有"))
}

internal fun DailyReportFragment.exportReportPdf() {
    if (!isAdded) return
    val ctx    = requireContext()
    val ym     = reportViewModel.yearMonth.value
    val groups = deliveryViewModel.groups.value
    if (groups.size <= 1) {
        askOrientation(ctx, "PDF") { portrait ->
            doExportReportPdf(ctx, ym, reportViewModel.assignmentId.value, deliveryViewModel.currentGroup()?.name ?: "", portrait)
        }
        return
    }
    val options = (listOf("すべての案件") + groups.map { it.name }).toTypedArray()
    MaterialAlertDialogBuilder(ctx)
        .setTitle("出力する案件を選択")
        .setItems(options) { _, which ->
            val (id, name) = if (which == 0) Pair("", "全案件")
                             else { val g = groups[which - 1]; Pair(g.id, g.name) }
            askOrientation(ctx, "PDF") { portrait ->
                doExportReportPdf(ctx, ym, id, name, portrait)
            }
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.doExportReportPdf(ctx: android.content.Context, ym: String, assignmentId: String, assignmentName: String, portrait: Boolean = false) {
    val group   = if (assignmentId.isNotBlank()) (deliveryViewModel.groups.value).find { it.id == assignmentId } else null
    val pid     = group?.patternId ?: -1
    val pattern = if (pid != -1) PatternStorage.get(ctx, pid) ?: PatternStorage.ensureDefault(ctx)
                  else PatternStorage.ensureDefault(ctx)
    lifecycleScope.launch {
        try {
            val records = reportViewModel.recordsForPeriodWithAssignment(
                "${ym}-01", "${ym}-31", assignmentId
            ).filter { it.date.startsWith(ym) }
            val file = PdfGenerator.generateReportPdf(ctx, records, ym, assignmentName, portrait, pattern)
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
