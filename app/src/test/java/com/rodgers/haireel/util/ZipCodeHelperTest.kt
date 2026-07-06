package com.rodgers.haireel.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ZipCodeHelperTest {

    // ── ZipResult.address ────────────────────────────────────────

    @Test
    fun `addressはpref+city+townを結合する`() {
        val r = ZipCodeHelper.ZipResult(pref = "東京都", city = "新宿区", town = "西新宿")
        assertEquals("東京都新宿区西新宿", r.address)
    }

    @Test
    fun `townが空でもaddressはpref+cityになる`() {
        val r = ZipCodeHelper.ZipResult(pref = "大阪府", city = "大阪市北区", town = "")
        assertEquals("大阪府大阪市北区", r.address)
    }

    @Test
    fun `全フィールドが空のaddressは空文字`() {
        val r = ZipCodeHelper.ZipResult(pref = "", city = "", town = "")
        assertEquals("", r.address)
    }

    // ── バリデーション（HTTP不要なケース: 無効入力はnullを返す）───────────

    @Test
    fun `6桁ではnullを返す`() = runBlocking {
        val result = ZipCodeHelper.lookup("123456")
        assertNull(result)
    }

    @Test
    fun `8桁ではnullを返す`() = runBlocking {
        val result = ZipCodeHelper.lookup("12345678")
        assertNull(result)
    }

    @Test
    fun `空文字ではnullを返す`() = runBlocking {
        val result = ZipCodeHelper.lookup("")
        assertNull(result)
    }

    @Test
    fun `数字以外を含む7文字ではnullを返す`() = runBlocking {
        val result = ZipCodeHelper.lookup("abc1234")
        assertNull(result)
    }

    @Test
    fun `ハイフンを含んでも7桁数字以外ならnullを返す`() = runBlocking {
        // "123-456" → digits = "123456" → 6桁なのでnull
        val result = ZipCodeHelper.lookup("123-456")
        assertNull(result)
    }
}
