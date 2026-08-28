package com.rodgers.haireel.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.BillingManager
import com.rodgers.haireel.util.WebLicenseManager
import kotlinx.coroutines.launch

fun showLicensePurchaseDialog(ctx: Context, activity: FragmentActivity) {
    val s = AppSettings

    if (s.isSubscriptionActive(ctx)) {
        if (s.getSubscriptionSource(ctx) == "web") {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("プレミアム会員")
                .setMessage("現在HP購入のプレミアムプランをご利用中です。\n\nプランの変更・解約はご購入時にStripeから届いたメールのリンクから行えます。")
                .setPositiveButton("閉じる", null)
                .show()
        } else {
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
        }
        return
    }

    val bm = BillingManager
    (activity as? com.rodgers.haireel.MainActivity)
        ?.buildSubscriptionDialog(
            ctx,
            onYearly    = { bm.launchSubscription(activity, bm.PRODUCT_YEARLY) },
            onMonthly   = { bm.launchSubscription(activity, bm.PRODUCT_MONTHLY) },
            onEnterCode = { showWebLicenseCodeDialog(ctx, activity) }
        )
        ?.show()
}

/** 自社HP（Stripe）で購入したライセンスコードを入力・検証するダイアログ */
fun showWebLicenseCodeDialog(ctx: Context, activity: FragmentActivity) {
    val input = EditText(ctx).apply {
        hint = "例: HR-XXXXXXXX-XXXXXXXX"
        setText(AppSettings.getWebLicenseCode(ctx))
    }

    val dlg = MaterialAlertDialogBuilder(ctx)
        .setTitle("HPで購入したライセンスコード")
        .setMessage("HPの購入完了メールに記載のコードを入力してください。")
        .setView(input)
        .setPositiveButton("確認する", null)
        .setNegativeButton("キャンセル", null)
        .create()

    dlg.setOnShowListener {
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isBlank()) {
                input.error = "コードを入力してください"
                return@setOnClickListener
            }
            activity.lifecycleScope.launch {
                when (val result = WebLicenseManager.verifyLicense(ctx, code)) {
                    is WebLicenseManager.Result.Success -> {
                        if (result.active) {
                            android.widget.Toast.makeText(ctx, "✅ プレミアム会員として認証されました", android.widget.Toast.LENGTH_LONG).show()
                            dlg.dismiss()
                        } else {
                            input.error = "このコードは現在有効ではありません（解約済み、または支払いエラーの可能性）"
                        }
                    }
                    WebLicenseManager.Result.InvalidCode -> input.error = "コードの形式が正しくありません"
                    WebLicenseManager.Result.NetworkError -> android.widget.Toast.makeText(
                        ctx, "通信に失敗しました。電波の良い場所で再度お試しください。", android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    dlg.show()
}
