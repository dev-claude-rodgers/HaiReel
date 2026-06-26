package com.rodgers.routist.util

import android.content.Context
import org.json.JSONObject

/**
 * TTS読み替え辞書
 * 「はなまさ」→「ハナマサ」のように登録しておくと、
 * 読み上げ前に自動で置換してTTSの誤読を防ぐ。
 */
object TtsDictionary {

    private const val PREFS_KEY = "tts_dictionary_json"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("haireel_prefs", Context.MODE_PRIVATE)

    /** 辞書を取得: Map<表記, 読み> */
    fun getAll(ctx: Context): Map<String, String> {
        val json = prefs(ctx).getString(PREFS_KEY, "{}") ?: "{}"
        val obj  = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        return buildMap {
            obj.keys().forEach { key -> put(key, obj.getString(key)) }
        }
    }

    /** エントリを追加・更新 */
    fun put(ctx: Context, surface: String, reading: String) {
        val obj = runCatching { JSONObject(prefs(ctx).getString(PREFS_KEY, "{}") ?: "{}") }
                      .getOrElse { JSONObject() }
        obj.put(surface.trim(), reading.trim())
        prefs(ctx).edit().putString(PREFS_KEY, obj.toString()).apply()
    }

    /** エントリを削除 */
    fun remove(ctx: Context, surface: String) {
        val obj = runCatching { JSONObject(prefs(ctx).getString(PREFS_KEY, "{}") ?: "{}") }
                      .getOrElse { JSONObject() }
        obj.remove(surface.trim())
        prefs(ctx).edit().putString(PREFS_KEY, obj.toString()).apply()
    }

    /**
     * テキストに辞書を適用して返す。
     * 長い表記を先に処理して短い表記による部分置換を防ぐ。
     */
    fun apply(ctx: Context, text: String): String {
        val dic = getAll(ctx)
        if (dic.isEmpty()) return text
        var result = text
        dic.entries
            .sortedByDescending { it.key.length }  // 長い表記を優先
            .forEach { (surface, reading) ->
                result = result.replace(surface, reading)
            }
        return result
    }

    /** ひらがな → カタカナ変換（TTS読み上げの安定化に使用） */
    fun hiraganaToKatakana(text: String): String =
        text.map { c ->
            if (c in 'ぁ'..'ん') (c.code + 0x60).toChar() else c
        }.joinToString("")
}
