package com.rodgers.routist.util

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseManager {

    private const val PREFS = "routist_license"
    private const val KEY_PRO = "is_pro"
    // キーの検証に使う秘密値（リリース時は ProGuard で難読化すること）
    private const val SECRET = "R0UT1ST-PR0-SECRET-2026"

    fun isPro(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PRO, false)

    fun getStoredKey(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("license_key", "") ?: ""

    /**
     * ライセンスキーを検証して Pro を有効化する。
     * 成功したら true を返す。
     *
     * キー形式: PPPP-PPPP-CCCC-CCCC
     *   P = 8文字のランダムペイロード（大文字英数字・O/0/I/1 を除く）
     *   C = HMAC-SHA256(SECRET, P) の先頭4バイトを16進数8文字にしたもの
     */
    fun activate(ctx: Context, rawKey: String): Boolean {
        val key = rawKey.replace("-", "").uppercase().trim()
        if (key.length != 16) return false
        if (!isValidKey(key)) return false
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PRO, true)
            .putString("license_key", formatKey(key))
            .apply()
        return true
    }

    private fun isValidKey(key: String): Boolean {
        return try {
            val payload  = key.substring(0, 8)
            val checksum = key.substring(8)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val digest   = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            val expected = digest.take(4).joinToString("") { "%02X".format(it) }
            checksum == expected
        } catch (_: Exception) {
            false
        }
    }

    private fun formatKey(key: String): String =
        "${key.substring(0,4)}-${key.substring(4,8)}-${key.substring(8,12)}-${key.substring(12,16)}"

    fun showUpgradeDialog(ctx: Context) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Pro版の機能です ✨")
            .setMessage("この機能は Pro版 が必要です。\n\n・複数案件（ルート）\n・Excel出力\n・バックアップ・復元\n・帳票パターン複数\n\n設定タブ → 「Pro版を有効化」からライセンスキーを入力してください。")
            .setPositiveButton("閉じる", null)
            .show()
    }

    /**
     * 開発者用: ライセンスキーを生成する（端末で実行する必要はなく、スクリプトでも可）
     *
     * 使い方（Kotlin REPL や main 関数で実行）:
     *   repeat(10) { println(LicenseManager.generateKey()) }
     */
    fun generateKey(): String {
        val chars   = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val payload = (1..8).map { chars.random() }.joinToString("")
        val mac     = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest   = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val checksum = digest.take(4).joinToString("") { "%02X".format(it) }
        return formatKey(payload + checksum)
    }
}
