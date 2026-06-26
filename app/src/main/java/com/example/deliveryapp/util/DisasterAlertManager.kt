package com.rodgers.routist.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * 気象庁API を使った現在地周辺の災害・警報通知
 *
 * 気象庁防災情報API（無料・認証不要）:
 *   https://www.jma.go.jp/bosai/warning/data/warning/{都道府県コード}.json
 *
 * 都道府県コード例:
 *   東京都: 130000 / 大阪府: 270000 / 愛知県: 230000
 *   茨城県: 080000 / 千葉県: 120000 / 埼玉県: 110000
 *   群馬県: 100000 / 神奈川: 140000
 */
object DisasterAlertManager {

    private const val TAG = "DisasterAlertManager"
    private const val JMA_BASE = "https://www.jma.go.jp/bosai/warning/data/warning"

    // 都道府県コードマップ（主要都道府県）
    private val PREF_CODES = mapOf(
        "北海道" to "016000", "青森" to "020000", "岩手" to "030000",
        "宮城" to "040000", "秋田" to "050000", "山形" to "060000",
        "福島" to "070000", "茨城" to "080000", "栃木" to "090000",
        "群馬" to "100000", "埼玉" to "110000", "千葉" to "120000",
        "東京" to "130000", "神奈川" to "140000", "新潟" to "150000",
        "富山" to "160000", "石川" to "170000", "福井" to "180000",
        "山梨" to "190000", "長野" to "200000", "岐阜" to "210000",
        "静岡" to "220000", "愛知" to "230000", "三重" to "240000",
        "滋賀" to "250000", "京都" to "260000", "大阪" to "270000",
        "兵庫" to "280000", "奈良" to "290000", "和歌山" to "300000",
        "鳥取" to "310000", "島根" to "320000", "岡山" to "330000",
        "広島" to "340000", "山口" to "350000", "徳島" to "360000",
        "香川" to "370000", "愛媛" to "380000", "高知" to "390000",
        "福岡" to "400000", "佐賀" to "410000", "長崎" to "420000",
        "熊本" to "430000", "大分" to "440000", "宮崎" to "450000",
        "鹿児島" to "460100", "沖縄" to "470000"
    )

    data class AlertInfo(
        val prefName: String,
        val headline: String,          // 見出し文
        val hasWarning: Boolean,       // 警報あり
        val hasAdvisory: Boolean,      // 注意報あり
        val warningTypes: List<String> // 警報・注意報の種類
    )

    // ─── 都道府県コードから警報情報を取得 ──────────────
    suspend fun fetchAlerts(prefCode: String): AlertInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "$JMA_BASE/$prefCode.json"
            val json = JSONObject(URL(url).readText())

            val headline = json.optString("headlineText", "")

            // 警報・注意報の種類を収集
            val warningTypes = mutableListOf<String>()
            val areaTypes = json.optJSONArray("areaTypes")
            var hasWarning  = false
            var hasAdvisory = false

            if (areaTypes != null) {
                for (i in 0 until areaTypes.length()) {
                    val areas = areaTypes.getJSONObject(i).optJSONArray("areas") ?: continue
                    for (j in 0 until areas.length()) {
                        val warnings = areas.getJSONObject(j).optJSONArray("warnings") ?: continue
                        for (k in 0 until warnings.length()) {
                            val w = warnings.getJSONObject(k)
                            val status = w.optString("status", "")
                            val text   = w.optString("text", "")
                            if (status == "発表" || status == "継続") {
                                if (text.contains("警報")) {
                                    hasWarning = true
                                    if (text !in warningTypes) warningTypes.add(text)
                                } else if (text.contains("注意報")) {
                                    hasAdvisory = true
                                    if (text !in warningTypes) warningTypes.add(text)
                                }
                            }
                        }
                    }
                }
            }

            AlertInfo(
                prefName     = prefCode,
                headline     = headline,
                hasWarning   = hasWarning,
                hasAdvisory  = hasAdvisory,
                warningTypes = warningTypes.distinct()
            )
        } catch (e: Exception) {
            Log.e(TAG, "気象庁API 取得エラー: ${e.message}")
            null
        }
    }

    // ─── 住所文字列から都道府県コードを取得 ────────────
    fun getPrefCode(address: String): String? {
        return PREF_CODES.entries.firstOrNull { address.contains(it.key) }?.value
    }

    // ─── 現在地の都道府県コードを逆ジオコーディングから取得 ─
    fun getPrefCodeFromGeocodedAddress(geocodedAddress: String): String? {
        return getPrefCode(geocodedAddress)
    }

    // ─── 警報レベルを返す ────────────────────────────
    fun getAlertLevel(alert: AlertInfo): AlertLevel {
        return when {
            alert.hasWarning  -> AlertLevel.WARNING   // 警報（赤）
            alert.hasAdvisory -> AlertLevel.ADVISORY  // 注意報（黄）
            else              -> AlertLevel.NONE      // なし
        }
    }

    enum class AlertLevel { NONE, ADVISORY, WARNING }
}
