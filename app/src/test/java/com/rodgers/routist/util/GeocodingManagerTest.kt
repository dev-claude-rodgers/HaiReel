package com.rodgers.routist.util

import com.rodgers.routist.db.GeocodingCacheDao
import com.rodgers.routist.db.GeocodingCacheEntity
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeocodingManagerTest {

    @MockK
    private lateinit var mockClient: GeocodingApi

    @MockK
    private lateinit var mockCache: GeocodingCacheDao

    private lateinit var manager: GeocodingManager

    private val recentTs = System.currentTimeMillis()
    private val expiredTs = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        manager = GeocodingManager(mockClient, mockCache)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── geocode: キャッシュヒット ─────────────────────────────

    @Test
    fun `キャッシュが有効な場合はAPIを呼ばずに返す`() = runTest {
        val cached = GeocodingCacheEntity("東京都新宿区", 35.68, 139.70, cachedAt = recentTs)
        coEvery { mockCache.get("東京都新宿区") } returns cached

        val result = manager.geocode("東京都新宿区")

        assertNotNull(result)
        assertEquals(35.68, result!!.lat, 0.001)
        assertEquals(139.70, result.lng, 0.001)
        coVerify(exactly = 0) { mockClient.geocode(any()) }
    }

    @Test
    fun `キャッシュが期限切れの場合はAPIを呼ぶ`() = runTest {
        val expired = GeocodingCacheEntity("東京都新宿区", 35.68, 139.70, cachedAt = expiredTs)
        coEvery { mockCache.get("東京都新宿区") } returns expired
        coEvery { mockClient.geocode("東京都新宿区") } returns
            GeocodingClient.GeoResult(35.69, 139.71, "東京都新宿区1-1-1")
        coJustRun { mockCache.put(any()) }

        manager.geocode("東京都新宿区")

        coVerify(exactly = 1) { mockClient.geocode("東京都新宿区") }
    }

    // ── geocode: キャッシュミス ───────────────────────────────

    @Test
    fun `キャッシュがない場合はAPIを呼びキャッシュに保存する`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode("東京都新宿区") } returns
            GeocodingClient.GeoResult(35.69, 139.71, "東京都新宿区1-1-1")
        coJustRun { mockCache.put(any()) }

        val result = manager.geocode("東京都新宿区")

        assertNotNull(result)
        assertEquals(35.69, result!!.lat, 0.001)
        coVerify(exactly = 1) { mockCache.put(match { it.address == "東京都新宿区" }) }
    }

    @Test
    fun `APIがnullを返した場合はnullを返しキャッシュに保存しない`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode(any()) } returns null

        val result = manager.geocode("存在しない住所")

        assertNull(result)
        coVerify(exactly = 0) { mockCache.put(any()) }
    }

    // ── geocodeWithFallback ────────────────────────────────────

    @Test
    fun `geocodeが成功すればPlaces検索はしない`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode(any()) } returns
            GeocodingClient.GeoResult(35.69, 139.71, "東京都新宿区1-1")
        coJustRun { mockCache.put(any()) }

        manager.geocodeWithFallback("東京都新宿区", "新宿店")

        coVerify(exactly = 0) { mockClient.searchPlaces(any()) }
    }

    @Test
    fun `geocodeがnullのときnameでPlaces検索する`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode(any()) } returns null
        coEvery { mockClient.searchPlaces("新宿店") } returns listOf(
            GeocodingClient.PlaceInfo("新宿店", "東京都新宿区1", 35.69, 139.71)
        )

        val result = manager.geocodeWithFallback("東京都新宿区", "新宿店")

        assertNotNull(result)
        assertEquals(35.69, result!!.lat, 0.001)
    }

    @Test
    fun `geocodeもPlacesもnullならnullを返す`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode(any()) } returns null
        coEvery { mockClient.searchPlaces(any()) } returns emptyList()

        val result = manager.geocodeWithFallback("存在しない住所", "存在しない店")

        assertNull(result)
    }

    @Test
    fun `nameがnullならPlaces検索しない`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode(any()) } returns null

        val result = manager.geocodeWithFallback("存在しない住所", null)

        assertNull(result)
        coVerify(exactly = 0) { mockClient.searchPlaces(any()) }
    }

    // ── formattedAddress キャッシュ保存・返却 ────────────────

    @Test
    fun `APIが返したformattedAddressがキャッシュに保存される`() = runTest {
        coEvery { mockCache.get(any()) } returns null
        coEvery { mockClient.geocode("東京都新宿区西新宿") } returns
            GeocodingClient.GeoResult(35.69, 139.71, "東京都新宿区西新宿2丁目8−1")
        coJustRun { mockCache.put(any()) }

        manager.geocode("東京都新宿区西新宿")

        coVerify {
            mockCache.put(match {
                it.address == "東京都新宿区西新宿" &&
                it.formattedAddress == "東京都新宿区西新宿2丁目8−1"
            })
        }
    }

    @Test
    fun `キャッシュヒット時はformattedAddressが返される`() = runTest {
        val cached = GeocodingCacheEntity(
            address          = "東京都新宿区",
            lat              = 35.68,
            lng              = 139.70,
            formattedAddress = "東京都新宿区歌舞伎町1丁目",
            cachedAt         = recentTs
        )
        coEvery { mockCache.get("東京都新宿区") } returns cached

        val result = manager.geocode("東京都新宿区")

        assertNotNull(result)
        assertEquals("東京都新宿区歌舞伎町1丁目", result!!.formattedAddress)
    }

    @Test
    fun `キャッシュのformattedAddressが空の場合は入力住所を返す`() = runTest {
        val cached = GeocodingCacheEntity(
            address          = "東京都新宿区",
            lat              = 35.68,
            lng              = 139.70,
            formattedAddress = "",  // 旧バージョンのキャッシュ
            cachedAt         = recentTs
        )
        coEvery { mockCache.get("東京都新宿区") } returns cached

        val result = manager.geocode("東京都新宿区")

        assertNotNull(result)
        assertEquals("東京都新宿区", result!!.formattedAddress)  // 入力住所にフォールバック
    }

    // ── evictExpiredCache ─────────────────────────────────────

    @Test
    fun `evictExpiredCacheでDAOのevictExpiredを呼ぶ`() = runTest {
        coJustRun { mockCache.evictExpired(any()) }

        manager.evictExpiredCache()

        coVerify(exactly = 1) { mockCache.evictExpired(any()) }
    }
}
