package com.rodgers.haireel.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自社HP（Stripe経由）で購入したライセンスコードの検証を行う。
 * BillingManager（Google Play）と対になる存在で、どちらの経路でもAppSettingsの
 * サブスク有効フラグを更新する。
 */
object WebLicenseManager {

    // デプロイ後のバックエンドURLに置き換えてください
    private const val API_BASE = "https://REPLACE_WITH_YOUR_WORKER_SUBDOMAIN.workers.dev"

    sealed class Result {
        data class Success(val active: Boolean, val expiresAt: String?) : Result()
        object InvalidCode : Result()
        object NetworkError : Result()
    }

    /** ライセンスコードをサーバーに照会し、結果を AppSettings に反映する */
    suspend fun verifyLicense(ctx: Context, code: String): Result = withContext(Dispatchers.IO) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return@withContext Result.InvalidCode

        try {
            val url = "$API_BASE/api/license/status?code=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
            val connection = URL(url).openConnection() as HttpURLConnection
            val json = try {
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                if (connection.responseCode !in 200..299) return@withContext Result.NetworkError
                JSONObject(connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }

            if (json.optString("reason") == "invalid_code") return@withContext Result.InvalidCode

            val active = json.optBoolean("active", false)
            val expiresAt = json.optString("expiresAt").ifBlank { null }
            AppSettings.setWebLicense(ctx, trimmed, active)
            Result.Success(active, expiresAt)
        } catch (_: Exception) {
            Result.NetworkError
        }
    }

    /** すでに登録済みのライセンスコードを、保存済みの値で再確認する（定期チェック用） */
    suspend fun recheckSavedLicense(ctx: Context): Result {
        val code = AppSettings.getWebLicenseCode(ctx)
        if (code.isBlank()) return Result.InvalidCode
        return verifyLicense(ctx, code)
    }
}
