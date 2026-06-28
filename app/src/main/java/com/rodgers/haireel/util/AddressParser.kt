package com.rodgers.haireel.util

object AddressParser {

    data class Entry(val name: String, val address: String)

    // テキストを改行で分割し、店名\t住所 または 住所のみ の形式をパース
    fun parse(text: String): List<Entry> {
        return text.removePrefix("﻿")  // UTF-8 BOM除去
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }
            .filter { it.address.isNotBlank() }
    }

    private fun parseLine(line: String): Entry? {
        val cleaned = line
            .replace(Regex("^\\d+[.)、。]\\s*"), "")
            .replace(Regex("^[①-⑳]\\s*"), "")
            .trim()
        if (cleaned.isBlank()) return null

        return if (cleaned.contains('\t')) {
            val parts = cleaned.split('\t', limit = 2)
            Entry(
                toFullWidth(parts[0].trim()).take(20),
                toFullWidth(parts[1].trim()).take(50)
            )
        } else {
            Entry("", toFullWidth(cleaned).take(50))
        }
    }

    /**
     * 半角文字を全角に統一する。
     * 住所・名前のインポート時に表記を揃えるために使用。
     *   0-9       → ０-９
     *   A-Z       → Ａ-Ｚ
     *   a-z       → ａ-ｚ
     *   - (ハイフン) → －（全角ハイフン）
     *   ｰ (半角長音) → ー（全角長音）
     */
    /**
     * 住所からエリアキー（丁目レベル）を抽出する。
     * グループ表示用。Google Maps API 不要。
     */
    fun extractAreaKey(address: String): String {
        // 丁目で区切る（最も粒度が細かく実用的）
        val chomeIdx = address.indexOf("丁目")
        if (chomeIdx >= 0) return address.substring(0, chomeIdx + 2)

        // 番地の直前の数字・記号を除いた部分まで
        val banchiIdx = address.indexOf("番地")
        if (banchiIdx >= 0) {
            var i = banchiIdx - 1
            while (i >= 0 && (address[i] in '０'..'９' || address[i] in '0'..'9' ||
                   address[i] == '－' || address[i] == '-' || address[i] == '−')) i--
            if (i >= 0) return address.substring(0, i + 1)
        }

        // 区・町・村レベルで区切る
        val lastWard = Regex("[区町村]").findAll(address).lastOrNull()
        if (lastWard != null) return address.substring(0, lastWard.range.last + 1)

        return address.take(10)
    }

    fun toFullWidth(text: String): String = text.map { c ->
        when (c) {
            in '0'..'9' -> ('０'.code + (c - '0')).toChar()
            in 'A'..'Z' -> ('Ａ'.code + (c - 'A')).toChar()
            in 'a'..'z' -> ('ａ'.code + (c - 'a')).toChar()
            '-'         -> '－'
            'ｰ'        -> 'ー'
            else -> c
        }
    }.joinToString("")
}
