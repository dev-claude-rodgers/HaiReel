package com.rodgers.routist.ui

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rodgers.routist.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 通知ボタン（✅完了 / ⏭スキップ）のタップを処理する */
class DeliveryActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val deliveryId = intent.getStringExtra(DeliveryGeofenceReceiver.EXTRA_DELIVERY_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DeliveryGeofenceReceiver.ACTION_COMPLETE -> {
                        val db = AppDatabase.getInstance(context)
                        val entity = db.deliveryDao().getAll()
                            .firstOrNull { it.id == deliveryId } ?: return@launch
                        db.deliveryDao().upsert(entity.copy(isCompleted = true))
                    }
                    DeliveryGeofenceReceiver.ACTION_SKIP -> {
                        // スキップは通知を消すだけ（配達先はそのまま残る）
                    }
                }
            } finally {
                // 通知を消す
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(deliveryId.hashCode())
                pendingResult.finish()
            }
        }
    }
}
