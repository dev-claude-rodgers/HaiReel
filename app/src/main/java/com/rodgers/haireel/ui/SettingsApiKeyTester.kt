package com.rodgers.haireel.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun testApiKey(
    ctx: Context,
    scope: CoroutineScope,
    onStatusChanged: (String) -> Unit,
    onShowWizard: () -> Unit
) {
    val loadingDlg = MaterialAlertDialogBuilder(ctx)
        .setTitle("🔍 APIキーを確認中...")
        .setMessage("東京都千代田区への接続テストを実行しています。")
        .setCancelable(false)
        .create()
    loadingDlg.show()

    scope.launch {
        val result = try {
            withContext(Dispatchers.IO) {
                com.rodgers.haireel.util.GeocodingClient.geocode("東京都千代田区")
            }
        } catch (_: Exception) { null }

        loadingDlg.dismiss()

        if (result != null) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("✅ APIキーは正常に動作しています")
                .setMessage("住所検索・地図機能がご利用いただけます。\n\nテスト結果: ${result.formattedAddress}")
                .setPositiveButton("OK", null)
                .show()
            onStatusChanged("設定済み・動作確認済み")
        } else {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("❌ APIキーが機能していません")
                .setMessage(
                    "以下を確認してください。\n\n" +
                    "• APIキーが正しくコピーされているか\n" +
                    "• Geocoding APIが有効化されているか\n" +
                    "• Google Cloudの課金設定が完了しているか\n\n" +
                    "設定を修正してから再度お試しください。"
                )
                .setPositiveButton("再設定する") { _, _ -> onShowWizard() }
                .setNegativeButton("閉じる", null)
                .show()
            onStatusChanged("設定済み・接続エラー（要確認）")
        }
    }
}
