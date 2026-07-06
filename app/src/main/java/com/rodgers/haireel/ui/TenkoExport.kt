package com.rodgers.haireel.ui

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.rodgers.haireel.excel.TenkoExcelGenerator
import kotlinx.coroutines.launch

internal fun TenkoFragment.exportTenko() {
    val ctx = requireContext()
    val ym  = viewModel.yearMonth.value
    val (y, m) = ym.split("-").map { it.toInt() }
    lifecycleScope.launch {
        try {
            val records = viewModel.recordsForMonth(ym)
            if (records.isEmpty()) {
                android.widget.Toast.makeText(ctx, "この月の点呼記録はまだありません", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = TenkoExcelGenerator(ctx).generate(records, ym, portrait = true)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "${y}年${m}月 点呼簿を共有"))
        } catch (e: Exception) {
            ctx.showErrorDialog("出力エラー", e.localizedMessage ?: "点呼簿の出力に失敗しました。\nストレージの空き容量を確認してください。")
        }
    }
}

