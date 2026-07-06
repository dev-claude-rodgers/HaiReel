package com.rodgers.haireel.util

import com.rodgers.haireel.db.GeocodingCacheDao
import com.rodgers.haireel.db.GeocodingCacheEntity
import com.rodgers.haireel.model.Delivery
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

    // ── batchGeocode ──────────────────────────────────────────

    @Test
    fun `batchGeocode_isRequestDeniedがtrueのとき即座にdeliveries件数を返す`() = runTest {
        every { mockClient.isRequestDenied } returns true

        val deliveries = listOf(
            Delivery(id = "d1", order = 0, address = "東京都新宿区1-1-1"),
            Delivery(id = "d2", order = 1, address = "東京都渋谷区2-2-2")
        )
        val failed = manager.batchGeocode(
            deliveries = deliveries,
            areaHint = "",
            isInArea = { true },
            extractLocalPart = { it },
            isGroupActive = { true },
            onProgress = { _, _ -> },
            onResult = {}
        )

        assertEquals(2, failed)
        coVerify(exactly = 0) { mockClient.geocode(any()) }
    }

    @Test
    fun `batchGeocode_空リストのとき失敗数0を返す`() = runTest {
        every { mockClient.isRequestDenied } returns false
        every { mockClient.hasBias() } returns true
        every { mockClient.biasLat } returns 35.68
        every { mockClient.biasLng } returns 139.70
        every { mockClient.setBias(any(), any()) } just Runs

        val failed = manager.batchGeocode(
            deliveries = emptyList(),
            areaHint = "",
            isInArea = { true },
            extractLocalPart = { it },
            isGroupActive = { true },
            onProgress = { _, _ -> },
            onResult = {}
        )

        assertEquals(0, failed)
    }

    @Test
    fun `batchGeocode_isGroupActiveがfalseのとき最初の件で中断する`() = runTest {
        every { mockClient.isRequestDenied } returns false
        every { mockClient.hasBias() } returns true
        every { mockClient.biasLat } returns 35.68
        every { mockClient.biasLng } returns 139.70
        every { mockClient.setBias(any(), any()) } just Runs

        var onResultCalled = false
        val failed = manager.batchGeocode(
            deliveries = listOf(
                Delivery(id = "d1", order = 0, address = "東京都新宿区1-1-1")
            ),
            areaHint = "",
            isInArea = { true },
            extractLocalPart = { it },
            isGroupActive = { false },
            onProgress = { _, _ -> },
            onResult = { onResultCalled = true }
        )

        assertEquals(0, failed)
        assertFalse(onResultCalled)
        coVerify(exactly = 0) { mockClient.geocode(any()) }
    }

    @Test
    fun `batchGeocode_isGeocoded済みの配達はgeocode APIをスキップする`() = runTest {
        every { mockClient.isRequestDenied } returns false
        every { mockClient.hasBias() } returns true
        every { mockClient.biasLat } returns 35.68
        every { mockClient.biasLng } returns 139.70
        every { mockClient.setBias(any(), any()) } just Runs

        val progressList = mutableListOf<Pair<Int, Int>>()
        var onResultCalled = false
        val failed = manager.batchGeocode(
            deliveries = listOf(
                Delivery(id = "d1", order = 0, address = "東京都新宿区1-1-1", isGeocoded = true, lat = 35.68, lng = 139.70)
            ),
            areaHint = "",
            isInArea = { true },
            extractLocalPart = { it },
            isGroupActive = { true },
            onProgress = { c, t -> progressList.add(c to t) },
            onResult = { onResultCalled = true }
        )

        assertEquals(0, failed)
        assertEquals(1, progressList.size)
        assertFalse(onResultCalled)
        coVerify(exactly = 0) { mockClient.geocode(any()) }
    }
}
