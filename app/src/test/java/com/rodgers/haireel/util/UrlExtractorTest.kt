package com.rodgers.haireel.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UrlExtractorTest {

    // ── Google Maps @lat,lng パターン ─────────────────────────

    @Test
    fun `Googleマップの座標付きURLから緯度経度を取得できる`() = runTest {
        val url = "https://www.google.com/maps/place/東京駅/@35.6812362,139.7671248,17z"
        val result = UrlExtractor.extract(url)
        assertNotNull(result)
        assertEquals(35.6812362, result!!.lat!!, 0.001)
        assertEquals(139.7671248, result.lng!!, 0.001)
    }

    @Test
    fun `Googleマップのplace名を取得できる`() = runTest {
        val url = "https://www.google.com/maps/place/%E6%9D%B1%E4%BA%AC%E9%A7%85/@35.6812,139.7671,17z"
        val result = UrlExtractor.extract(url)
        assertNotNull(result)
        assertNotNull(result!!.name)
    }

    // ── Google Maps ?q= パターン ─────────────────────────────

    @Test
    fun `qパラメータに座標が含まれる場合は座標を取得できる`() = runTest {
        val url = "https://maps.google.com/?q=35.6812,139.7671"
        val result = UrlExtractor.extract(url)
        assertNotNull(result)
        assertEquals(35.6812, result!!.lat!!, 0.001)
        assertEquals(139.7671, result.lng!!, 0.001)
    }

    @Test
    fun `qパラメータにスポット名がある場合はqueryに設定される`() = runTest {
        val url = "https://maps.google.com/?q=東京タワー"
        val result = UrlExtractor.extract(url)
        assertNotNull(result)
        assertEquals("東京タワー", result!!.query)
    }

    // ── Google Maps /place/name/ パターン ────────────────────

    @Test
    fun `placeパスからスポット名を取得できる`() = runTest {
        val url = "https://www.google.com/maps/place/Shibuya+Station/"
        val result = UrlExtractor.extract(url)
        assertNotNull(result)
        assertTrue(result!!.name.isNotBlank())
    }

    // ── maps.google.com ──────────────────────────────────────

    @Test
    fun `mapsサブドメインのURLも処理できる`() = runTest {
        val url = "https://maps.google.com/maps/place/Tokyo+Tower/@35.6586,139.7454,17z"
        val result = UrlExtractor.extract(url)
        assertNotNull(result)
    }

    // ── 非Googleマップ URL ───────────────────────────────────

    @Test
    fun `GoogleマップでないURLはnullを返すかタイムアウトで例外を吸収する`() = runTest {
        // 存在しないURLはネットワークエラーで null が返る
        val result = try {
            UrlExtractor.extract("https://example-nonexistent-domain-12345.com/")
        } catch (e: Exception) {
            null
        }
        // ネットワーク不可環境では null
        assertNull(result)
    }

    @Test
    fun `空文字はnullを返す`() = runTest {
        val result = UrlExtractor.extract("")
        assertNull(result)
    }

    // ── ExtractedPlace データクラス ───────────────────────────

    @Test
    fun `ExtractedPlaceのデフォルト座標はnull`() {
        val place = UrlExtractor.ExtractedPlace(name = "テスト", query = "クエリ")
        assertNull(place.lat)
        assertNull(place.lng)
    }

    @Test
    fun `ExtractedPlaceに座標を設定できる`() {
        val place = UrlExtractor.ExtractedPlace(name = "テスト", query = "クエリ", lat = 35.0, lng = 139.0)
        assertEquals(35.0, place.lat!!, 0.001)
        assertEquals(139.0, place.lng!!, 0.001)
    }
}
