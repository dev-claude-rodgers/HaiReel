package com.rodgers.routist.util

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
