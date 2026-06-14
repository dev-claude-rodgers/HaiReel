package com.rodgers.routist.util

import org.junit.Assert.*
import org.junit.Test

class AddressParserTest {

    @Test
    fun `空文字列は空リストを返す`() {
        val result = AddressParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `空白のみは空リストを返す`() {
        val result = AddressParser.parse("   \n  \n")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `住所のみの1行をパースする`() {
        val result = AddressParser.parse("東京都新宿区西新宿1-1-1")
        assertEquals(1, result.size)
        assertEquals("", result[0].name)
        assertEquals("東京都新宿区西新宿1-1-1", result[0].address)
    }

    @Test
    fun `タブ区切りで店名と住所をパースする`() {
        val result = AddressParser.parse("コンビニA\t東京都渋谷区道玄坂2-1-1")
        assertEquals(1, result.size)
        assertEquals("コンビニA", result[0].name)
        assertEquals("東京都渋谷区道玄坂2-1-1", result[0].address)
    }

    @Test
    fun `複数行をパースする`() {
        val input = """
            東京都千代田区1-1
            大阪府大阪市北区2-2
            愛知県名古屋市中区3-3
        """.trimIndent()
        val result = AddressParser.parse(input)
        assertEquals(3, result.size)
    }

    @Test
    fun `番号プレフィックスが除去される`() {
        val result = AddressParser.parse("1. 東京都新宿区西新宿1-1-1")
        assertEquals("東京都新宿区西新宿1-1-1", result[0].address)
    }

    @Test
    fun `丸数字プレフィックスが除去される`() {
        val result = AddressParser.parse("① 東京都新宿区西新宿1-1-1")
        assertEquals("東京都新宿区西新宿1-1-1", result[0].address)
    }

    @Test
    fun `UTF8 BOMが除去される`() {
        val bom = "﻿"
        val result = AddressParser.parse("${bom}東京都新宿区西新宿1-1-1")
        assertEquals(1, result.size)
        assertEquals("東京都新宿区西新宿1-1-1", result[0].address)
    }

    @Test
    fun `途中の空行は無視される`() {
        val input = "東京都千代田区1-1\n\n大阪府大阪市北区2-2"
        val result = AddressParser.parse(input)
        assertEquals(2, result.size)
    }
}
