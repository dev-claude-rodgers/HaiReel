package com.rodgers.haireel.ui

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
            shareXlsxFile(file, "${y}年${m}月 点呼簿を共有")
        } catch (e: Exception) {
            ctx.showErrorDialog("出力エラー", e.localizedMessage ?: "点呼簿の出力に失敗しました。\nストレージの空き容量を確認してください。")
        }
    }
}

