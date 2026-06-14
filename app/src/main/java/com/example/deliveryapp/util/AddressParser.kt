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
            Entry(parts[0].trim(), parts[1].trim())
        } else {
            Entry("", cleaned)
        }
    }
}
