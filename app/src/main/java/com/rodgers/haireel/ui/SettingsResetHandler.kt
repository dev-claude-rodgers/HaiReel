package com.rodgers.haireel.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.rodgers.haireel.db.AppDatabase
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.SignatureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsResetHandler(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun resetAllData(onError: (String) -> Unit) {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                db.workRecordDao().deleteAll()
                db.deliveryDao().deleteAll()
                db.deliveryGroupDao().deleteAll()
                db.tenkoDao().deleteAll()
                db.geocodingCacheDao().deleteAll()

                for (prefs in listOf(AppSettings.PREFS, "delivery_prefs", "report_patterns")) {
                    appContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().apply()
                }
                AppSettings.clearSensitiveData(appContext)

                for (type in listOf(SignatureStorage.TYPE_DRIVER, SignatureStorage.TYPE_CLIENT)) {
                    SignatureStorage.fileFor(appContext, type).delete()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "初期化が完了しました。アプリを再起動します。", Toast.LENGTH_LONG).show()
                }
                delay(1500)
                appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?.let { appContext.startActivity(it) }
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "データの初期化に失敗しました。")
                }
            }
        }
    }
}
