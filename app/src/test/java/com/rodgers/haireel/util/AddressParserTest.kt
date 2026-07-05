package com.rodgers.haireel.util

import org.junit.Assert.*
import org.junit.Test

class AddressParserTest {

    // ── 基本パース ─────────────────────────────────────────────

    @Test
    fun `住所のみの1行をパースできる`() {
        val result = AddressParser.parse("東京都新宿区西新宿1-1-1")
        assertEquals(1, result.size)
        assertEquals("東京都新宿区西新宿１－１－１", result[0].address)  // 半角→全角変換後
        assertEquals("", result[0].name)
    }

    @Test
    fun `タブ区切りで店名と住所に分割できる`() {
        val result = AddressParser.parse("新宿店\t東京都新宿区西新宿1-1-1")
        assertEquals(1, result.size)
        assertEquals("新宿店", result[0].name)
        assertEquals("東京都新宿区西新宿１－１－１", result[0].address)  // 半角→全角変換後
    }

    @Test
    fun `複数行をパースできる`() {
        val text = "東京都新宿区1\n大阪府大阪市2\n茨城県つくば市3"
        val result = AddressParser.parse(text)
        assertEquals(3, result.size)
        assertEquals("東京都新宿区１", result[0].address)  // 半角→全角変換後
        assertEquals("大阪府大阪市２", result[1].address)
        assertEquals("茨城県つくば市３", result[2].address)
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
        assertEquals("東京都新宿区西新宿１－１", result[0].address)  // 半角→全角変換後
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

    // ── toFullWidth ───────────────────────────────────────────

    @Test
    fun `toFullWidthで半角数字を全角に変換する`() {
        assertEquals("１２３", AddressParser.toFullWidth("123"))
    }

    @Test
    fun `toFullWidthで半角英字を全角に変換する`() {
        assertEquals("ａｂｃＡＢＣ", AddressParser.toFullWidth("abcABC"))
    }

    @Test
    fun `toFullWidthでハイフンを全角ハイフンに変換する`() {
        assertEquals("ａ１－Ｂ", AddressParser.toFullWidth("a1-B"))
    }

    @Test
    fun `toFullWidthで全角文字は変換されない`() {
        assertEquals("東京都", AddressParser.toFullWidth("東京都"))
    }

    // ── extractAreaKey ────────────────────────────────────────

    @Test
    fun `extractAreaKeyで丁目まで抽出される`() {
        assertEquals("東京都新宿区西新宿1丁目", AddressParser.extractAreaKey("東京都新宿区西新宿1丁目1-1"))
    }

    @Test
    fun `extractAreaKeyで区レベルまで抽出される`() {
        assertEquals("大阪府大阪市北区", AddressParser.extractAreaKey("大阪府大阪市北区梅田1-1"))
    }

    @Test
    fun `extractAreaKeyで町レベルまで抽出される`() {
        // [区町村] regex で 町 がマッチする（区 と 町 の両方が含まれる場合は最後の一致）
        assertEquals("神奈川県川崎市中原区小杉町", AddressParser.extractAreaKey("神奈川県川崎市中原区小杉町1-1-1"))
    }

    @Test
    fun `extractAreaKeyで空文字は空文字を返す`() {
        assertEquals("", AddressParser.extractAreaKey(""))
    }
}
