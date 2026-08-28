package com.rodgers.haireel.util

import android.content.Context

/**
 * ジオコーディングAPIの利用モードを優先順位に沿って決定する。
 * ①自分のAPIキーを設定済み → それを使う
 * ②未設定でも試用中/サブスク中 → 運営プロキシ(Cloudflare Worker)を使う
 * ③どちらもない → 未設定のまま（REQUEST_DENIED相当となりUIで案内）
 */
fun applyGeocodingConfig(ctx: Context, api: GeocodingApi) {
    val userKey = AppSettings.getUserApiKey(ctx)
    when {
        userKey.isNotBlank() -> api.configure(userKey)
        AppSettings.canUseApp(ctx) -> api.configureProxy(ctx)
        else -> api.configure("")
    }
}
