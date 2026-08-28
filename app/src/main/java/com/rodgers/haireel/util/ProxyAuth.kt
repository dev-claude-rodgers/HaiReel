package com.rodgers.haireel.util

import android.content.Context
import com.rodgers.haireel.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 運営プロキシ(Cloudflare Worker)への認証ヘッダーを組み立てる */
object ProxyAuth {

    fun buildHeaders(ctx: Context): Map<String, String> {
        val deviceId = AppSettings.getOrCreateDeviceId(ctx)
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val entitled = if (AppSettings.canUseApp(ctx)) "1" else "0"
        val signature = hmacSha256Hex("$deviceId|$timestamp|$entitled", BuildConfig.PROXY_CLIENT_SECRET)

        return mapOf(
            "X-HaiReel-Device-Id" to deviceId,
            "X-HaiReel-Timestamp" to timestamp,
            "X-HaiReel-Entitled" to entitled,
            "X-HaiReel-Signature" to signature,
            "X-HaiReel-Package" to BuildConfig.APPLICATION_ID,
        )
    }

    private fun hmacSha256Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
