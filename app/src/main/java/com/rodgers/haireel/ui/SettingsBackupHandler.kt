package com.rodgers.haireel.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rodgers.haireel.util.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsBackupHandler(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun doRestore(
        uri: Uri,
        password: String? = null,
        onRestored: () -> Unit,
        onError: (String) -> Unit
    ) {
        Toast.makeText(appContext, "復元中...", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                BackupManager.restoreBackup(appContext, uri, password)
                withContext(Dispatchers.Main) { onRestored() }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "不明なエラーが発生しました。\nバックアップファイルを確認してください。")
                }
            }
        }
    }

    // 復元完了ダイアログでユーザーが確認した後に呼び出す再起動処理
    fun restartApp() {
        scope.launch {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                // AlarmManagerの非exactアラームはOSに遅延・無視されることがあるため、
                // プロセスが生きているうちに直接startActivityし、遷移猶予を置いてからkillする。
                appContext.startActivity(launchIntent)
                delay(300)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "アプリをランチャーから開いてください", Toast.LENGTH_LONG).show()
                }
                delay(2000)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        BackupManager.createBackup(appContext)
    }
}
