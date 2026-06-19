package com.rodgers.routist.util

import org.junit.Assert.*
import org.junit.Test

class GeocodingClientTest {

    // ── extractPrefCity ───────────────────────────────────────

    @Test
    fun `extractPrefCityで都道府県を抽出できる`() {
        // 市区町村の regex は "東京都新宿区" に一致するが、都道府県 "東京都" の接頭辞なので除外される
        val result = GeocodingClient.extractPrefCity("東京都新宿区西新宿1-1-1")
        assertTrue("東京都が含まれる", result.contains("東京都"))
    }

    @Test
    fun `extractPrefCityで大阪府を抽出できる`() {
        val result = GeocodingClient.extractPrefCity("大阪府大阪市北区梅田1-1")
        assertTrue("大阪府が含まれる", result.contains("大阪府"))
    }

    @Test
    fun `extractPrefCityで北海道を抽出できる`() {
        val result = GeocodingClient.extractPrefCity("北海道札幌市中央区大通西1-1")
        assertTrue("北海道が含まれる", result.contains("北海道"))
    }

    @Test
    fun `extractPrefCityで番地のみの住所は空リスト`() {
        val result = GeocodingClient.extractPrefCity("1-2-3")
        assertTrue("キーワードなし", result.isEmpty())
    }

    @Test
    fun `extractPrefCityで空文字は空リスト`() {
        val result = GeocodingClient.extractPrefCity("")
        assertTrue("空リスト", result.isEmpty())
    }

    @Test
    fun `extractPrefCityで町名後の地区名を抽出できる`() {
        val result = GeocodingClient.extractPrefCity("茨城県つくば市研究学園1-1")
        assertTrue("茨城県が含まれる", result.contains("茨城県"))
        assertTrue("研究学園が含まれる", result.contains("研究学園"))
    }

    // ── resultMatchesInput ────────────────────────────────────

    @Test
    fun `resultMatchesInputで同じ都市は一致する`() {
        assertTrue(
            GeocodingClient.resultMatchesInput(
                "東京都新宿区西新宿1-1",
                "日本、東京都新宿区西新宿1丁目1"
            )
        )
    }

    @Test
    fun `resultMatchesInputで異なる都道府県は不一致`() {
        assertFalse(
            GeocodingClient.resultMatchesInput(
                "東京都新宿区西新宿1-1",
                "大阪府大阪市北区梅田1-1"
            )
        )
    }

    @Test
    fun `resultMatchesInputでキーワードなし住所はtrueを返す`() {
        assertTrue(
            GeocodingClient.resultMatchesInput(
                "1-2-3",
                "東京都新宿区西新宿1丁目"
            )
        )
    }

}
