package com.rodgers.haireel.ui

import android.app.AlarmManager
import android.app.PendingIntent
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

    fun doRestore(uri: Uri, password: String? = null) {
        Toast.makeText(appContext, "復元中...", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                BackupManager.restoreBackup(appContext, uri, password)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "復元しました。アプリを再起動します。", Toast.LENGTH_LONG).show()
                }
                delay(1500)
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    // AlarmManagerで500ms後に起動予約してからkillProcessする。
                    // startActivity直後にkillすると起動前にプロセスが死ぬため。
                    val pi = PendingIntent.getActivity(
                        appContext, 0, launchIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    appContext.getSystemService(AlarmManager::class.java).set(
                        AlarmManager.RTC,
                        System.currentTimeMillis() + 500L,
                        pi
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "アプリをランチャーから開いてください", Toast.LENGTH_LONG).show()
                    }
                    delay(2000)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    appContext.showErrorDialog(
                        "復元エラー",
                        e.localizedMessage ?: "不明なエラーが発生しました。\nバックアップファイルを確認してください。"
                    )
                }
            }
        }
    }

    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        BackupManager.createBackup(appContext)
    }
}
