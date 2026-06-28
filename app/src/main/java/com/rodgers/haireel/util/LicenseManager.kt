package com.rodgers.haireel.util

import android.content.Context
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ライセンスキー管理
 *
 * キー形式: RJ-YYYY-XXXXXXXX-SIG
 *   YYYY     = 有効期限年（例: 2027）
 *   XXXXXXXX = ランダム8文字（英数字大文字）
 *   SIG      = HMAC-SHA256 の先頭8文字（不正防止）
 *
 * 例: RJ-2027-A3BK9F2C-E4D1A8F2
 *
 * キー生成はサーバーまたはツールで行う。
 * 検証はオフラインで完結する。
 */
object LicenseManager {

    // 秘密鍵（外部に漏らさないこと）
    // ※ APK に埋め込まれるため 100% 安全ではないが、カジュアルな不正利用を防ぐには十分
    private const val SECRET = "RouteJin2026-SecretKey-軽貨物"

    /** キーを検証してアプリに保存する。成功したら true を返す */
    fun activate(ctx: Context, rawKey: String): Boolean {
        val key = rawKey.trim().uppercase()
        val expiry = validateKey(key) ?: return false
        AppSettings.setLicenseKey(ctx, key)
        AppSettings.setLicenseExpiry(ctx, expiry)
        return true
    }

    /**
     * キーを検証して有効期限（エポックミリ秒）を返す。
     * 無効なら null を返す。
     */
    fun validateKey(key: String): Long? {
        val parts = key.split("-")
        if (parts.size != 4) return null
        if (parts[0] != "RJ") return null

        val yearStr = parts[1]
        val random  = parts[2]
        val sig     = parts[3]

        val year = yearStr.toIntOrNull() ?: return null
        if (year < 2026 || year > 2100) return null
        if (random.length != 8) return null

        // 署名検証
        val payload = "RJ-$yearStr-$random"
        val expected = hmacSha256(payload, SECRET).take(8).uppercase()
        if (sig != expected) return null

        // 有効期限: その年の12月31日 23:59:59
        val cal = java.util.Calendar.getInstance().apply {
            set(year, 11, 31, 23, 59, 59)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** キーを生成する（販売者ツール用）*/
    fun generateKey(year: Int): String {
        val random = (1..8).map {
            ('A'..'Z').toList() + ('0'..'9').toList()
        }.flatMap { it }.let { chars ->
            (1..8).map { chars.random() }.joinToString("")
        }
        val payload = "RJ-$year-$random"
        val sig = hmacSha256(payload, SECRET).take(8).uppercase()
        return "$payload-$sig"
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
