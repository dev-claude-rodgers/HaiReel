package com.rodgers.haireel.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play Billing（IAP サブスクリプション）管理
 *
 * 商品ID（Play Console で作成するサブスクリプション）:
 *   PRODUCT_MONTHLY = "haireel_monthly"  月額300円
 *   PRODUCT_YEARLY  = "haireel_yearly"   年額2,980円（月換算248円）
 *
 * Play Console での設定:
 *   - 無料試用期間: 7日間（アプリ内試用と合算）
 *   - 開始日からの計算: Play Store側で自動管理
 *
 * 試用期間はPlay Consoleで7日間に設定する（アプリ内試用と合わせて一本化）
 */
object BillingManager {

    private const val TAG = "BillingManager"

    // Play Console で作成するサブスクリプションのID
    const val PRODUCT_MONTHLY = "haireel_monthly"
    const val PRODUCT_YEARLY  = "haireel_yearly"

    private var billingClient: BillingClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // サブスクの状態をUI に流す
    private val _subscriptionState = MutableStateFlow(SubscriptionState.UNKNOWN)
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState

    enum class SubscriptionState {
        UNKNOWN,         // 未確認
        SUBSCRIBED,      // 有効なサブスク
        NOT_SUBSCRIBED,  // サブスクなし（試用 or ライセンスキー確認へ）
        PENDING,         // 決済処理中
        ERROR            // 接続エラー
    }

    // ─── 初期化・接続 ─────────────────────────────────────────

    fun init(ctx: Context) {
        if (billingClient?.isReady == true) return

        billingClient = BillingClient.newBuilder(ctx.applicationContext)
            .setListener { billingResult, purchases ->
                handlePurchaseUpdates(ctx, billingResult, purchases)
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        connect(ctx)
    }

    private fun connect(ctx: Context) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing 接続完了")
                    scope.launch { queryExistingPurchases(ctx) }
                } else {
                    Log.w(TAG, "Billing 接続失敗: ${result.debugMessage}")
                    _subscriptionState.value = SubscriptionState.ERROR
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing サービス切断 - 再接続試行")
                scope.launch {
                    delay(3000)
                    connect(ctx)
                }
            }
        })
    }

    // ─── 既存購入の確認 ────────────────────────────────────────

    suspend fun queryExistingPurchases(ctx: Context) {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = client.queryPurchasesAsync(params)
        val purchases = result.purchasesList

        val active = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            (PRODUCT_MONTHLY in purchase.products || PRODUCT_YEARLY in purchase.products)
        }

        if (active) {
            // 未確認の購入を確認（Acknowledge）
            purchases.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
            }.forEach { acknowledgePurchase(it) }

            AppSettings.setSubscriptionActive(ctx, true)
            _subscriptionState.value = SubscriptionState.SUBSCRIBED
            Log.d(TAG, "サブスク有効")
        } else {
            AppSettings.setSubscriptionActive(ctx, false)
            _subscriptionState.value = SubscriptionState.NOT_SUBSCRIBED
            Log.d(TAG, "サブスクなし")
        }
    }

    // ─── 購入フロー起動 ────────────────────────────────────────

    fun launchSubscription(activity: Activity, productId: String) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "Billing 未接続 - 再初期化")
            init(activity.applicationContext)
            withContext_main(activity) { showBillingError(activity) }
            return
        }

        scope.launch {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val result = client.queryProductDetails(params)
            val product = result.productDetailsList?.firstOrNull()

            if (product == null) {
                Log.e(TAG, "商品情報が取得できません: $productId")
                withContext(Dispatchers.Main) { showBillingError(activity) }
                return@launch
            }

            val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                Log.e(TAG, "オファートークンが取得できません")
                withContext(Dispatchers.Main) { showBillingError(activity) }
                return@launch
            }

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()

            withContext(Dispatchers.Main) {
                val billingResult = client.launchBillingFlow(activity, billingFlowParams)
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.e(TAG, "課金フロー起動失敗: ${billingResult.debugMessage}")
                    showBillingError(activity)
                }
            }
        }
    }

    private fun withContext_main(activity: Activity, block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    private fun showBillingError(activity: Activity) {
        // ActivityではなくapplicationContextを使うことでActivity破棄時のクラッシュを防ぐ
        android.widget.Toast.makeText(
            activity.applicationContext,
            "Google Play への接続に失敗しました。\nPlay Store アプリが最新か確認してください。",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    // ─── 購入完了の処理 ────────────────────────────────────────

    private fun handlePurchaseUpdates(
        ctx: Context,
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "購入更新エラー: ${billingResult.debugMessage}")
            return
        }

        scope.launch {
            purchases?.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    val acked = acknowledgePurchase(purchase)
                    if (acked) {
                        AppSettings.setSubscriptionActive(ctx, true)
                        _subscriptionState.value = SubscriptionState.SUBSCRIBED
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    _subscriptionState.value = SubscriptionState.PENDING
                }
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient?.acknowledgePurchase(params)
        Log.d(TAG, "Acknowledge: ${result?.responseCode}")
        return result?.responseCode == BillingClient.BillingResponseCode.OK
    }

    // ─── 商品情報の取得（価格表示用）──────────────────────────

    suspend fun queryProductDetails(): List<ProductDetails> {
        val client = billingClient ?: return emptyList()
        if (!client.isReady) return emptyList()

        val productList = listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        return client.queryProductDetails(params).productDetailsList ?: emptyList()
    }

    fun release() {
        billingClient?.endConnection()
        billingClient = null
        scope.cancel()
    }
}
