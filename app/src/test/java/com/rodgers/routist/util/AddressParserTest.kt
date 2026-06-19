package com.rodgers.routist.util

import org.junit.Assert.*
import org.junit.Test

class AddressParserTest {

    // ── 基本パース ─────────────────────────────────────────────

    @Test
    fun `住所のみの1行をパースできる`() {
        val result = AddressParser.parse("東京都新宿区西新宿1-1-1")
        assertEquals(1, result.size)
        assertEquals("東京都新宿区西新宿1-1-1", result[0].address)
        assertEquals("", result[0].name)
    }

    @Test
    fun `タブ区切りで店名と住所に分割できる`() {
        val result = AddressParser.parse("新宿店\t東京都新宿区西新宿1-1-1")
        assertEquals(1, result.size)
        assertEquals("新宿店", result[0].name)
        assertEquals("東京都新宿区西新宿1-1-1", result[0].address)
    }

    @Test
    fun `複数行をパースできる`() {
        val text = "東京都新宿区1\n大阪府大阪市2\n茨城県つくば市3"
        val result = AddressParser.parse(text)
        assertEquals(3, result.size)
        assertEquals("東京都新宿区1", result[0].address)
        assertEquals("大阪府大阪市2", result[1].address)
        assertEquals("茨城県つくば市3", result[2].address)
    }

    @Test
    fun `空文字は空リストを返す`() {
        assertTrue(AddressParser.parse("").isEmpty())
    }

    @Test
    fun `空白行はスキップされる`() {
        val text = "東京都新宿区1\n\n\n大阪府大阪市2"
        val result = AddressParser.parse(text)
        assertEquals(2, result.size)
    }

    // ── 番号プレフィックス除去 ─────────────────────────────────

    @Test
    fun `先頭の数字ピリオドが除去される`() {
        val result = AddressParser.parse("1. 東京都新宿区西新宿")
        assertEquals("東京都新宿区西新宿", result[0].address)
    }

    @Test
    fun `先頭の数字カッコが除去される`() {
        val result = AddressParser.parse("2) 大阪府大阪市北区")
        assertEquals("大阪府大阪市北区", result[0].address)
    }

    @Test
    fun `先頭の丸数字が除去される`() {
        val result = AddressParser.parse("①東京都新宿区")
        assertEquals("東京都新宿区", result[0].address)
    }

    @Test
    fun `先頭の数字読点が除去される`() {
        val result = AddressParser.parse("3、東京都渋谷区")
        assertEquals("東京都渋谷区", result[0].address)
    }

    // ── UTF-8 BOM 除去 ────────────────────────────────────────

    @Test
    fun `UTF8 BOMが除去される`() {
        val text = "﻿東京都新宿区西新宿1-1"
        val result = AddressParser.parse(text)
        assertEquals(1, result.size)
        assertEquals("東京都新宿区西新宿1-1", result[0].address)
    }

    // ── 長さ制限 ──────────────────────────────────────────────

    @Test
    fun `住所は50文字で切り詰められる`() {
        val long = "東京都新宿区西新宿".repeat(10)
        val result = AddressParser.parse(long)
        assertTrue(result[0].address.length <= 50)
    }

    @Test
    fun `店名は20文字で切り詰められる`() {
        val longName = "あ".repeat(30)
        val result = AddressParser.parse("$longName\t東京都新宿区")
        assertTrue(result[0].name.length <= 20)
    }

    // ── 前後スペース除去 ──────────────────────────────────────

    @Test
    fun `行頭末尾のスペースが除去される`() {
        val result = AddressParser.parse("  東京都新宿区  ")
        assertEquals("東京都新宿区", result[0].address)
    }

    @Test
    fun `タブ分割後の前後スペースも除去される`() {
        val result = AddressParser.parse(" 新宿店 \t 東京都新宿区 ")
        assertEquals("新宿店", result[0].name)
        assertEquals("東京都新宿区", result[0].address)
    }
}
