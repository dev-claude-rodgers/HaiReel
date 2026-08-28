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

    fun resetAllData(onReset: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                db.workRecordDao().deleteAll()
                db.deliveryDao().deleteAll()
                db.deliveryGroupDao().deleteAll()
                db.tenkoDao().deleteAll()
                db.geocodingCacheDao().deleteAll()
                db.fuelRecordDao().deleteAll()
                db.vehicleDao().deleteAll()
                db.knownAddressDao().deleteAll()

                for (prefs in listOf(
                    AppSettings.PREFS, "delivery_prefs", "report_patterns",
                    AppSettings.HAIREEL_PREFS, "fuel_settings",
                    "ui_hints", "sos_settings"
                )) {
                    appContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().apply()
                }
                AppSettings.clearSensitiveData(appContext)

                for (type in listOf(SignatureStorage.TYPE_DRIVER, SignatureStorage.TYPE_CLIENT)) {
                    SignatureStorage.fileFor(appContext, type).delete()
                }

                withContext(Dispatchers.Main) { onReset() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "データの初期化に失敗しました。")
                }
            }
        }
    }

    // 初期化完了ダイアログでユーザーが確認した後に呼び出す再起動処理
    fun restartApp() {
        scope.launch {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
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
}
