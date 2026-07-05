package com.rodgers.haireel.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.BillingManager

fun showLicensePurchaseDialog(ctx: Context, activity: FragmentActivity) {
    val s = AppSettings

    if (s.isSubscriptionActive(ctx)) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("プレミアム会員")
            .setMessage("現在プレミアムプランをご利用中です。\n\nプランの変更・解約は Google Play → 定期購入 から行えます。")
            .setPositiveButton("Google Play で管理") { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/account/subscriptions")))
                } catch (_: Exception) {}
            }
            .setNegativeButton("閉じる", null)
            .show()
        return
    }

    val bm = BillingManager
    (activity as? com.rodgers.haireel.MainActivity)
        ?.buildSubscriptionDialog(
            ctx,
            onYearly  = { bm.launchSubscription(activity, bm.PRODUCT_YEARLY) },
            onMonthly = { bm.launchSubscription(activity, bm.PRODUCT_MONTHLY) }
        )
        ?.show()
}
