package com.rodgers.routist.util

import com.rodgers.routist.db.GeocodingCacheDao
import com.rodgers.routist.db.GeocodingCacheEntity
import com.rodgers.routist.model.Delivery
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocodingManager @Inject constructor(
    private val client: GeocodingApi,
    private val cache: GeocodingCacheDao
) {
    private val cacheTtlMs = 30L * 24 * 60 * 60 * 1000

    data class GeocodedResult(
        val deliveryId: String,
        val lat: Double,
        val lng: Double,
        val officialAddress: String,
        val suggestedName: String? = null
    )

    // ── 単一住所のジオコーディング（キャッシュ付き） ──────────────

    suspend fun geocode(address: String): GeocodingClient.GeoResult? {
        val threshold = System.currentTimeMillis() - cacheTtlMs
        cache.get(address)?.takeIf { it.cachedAt >= threshold }?.let { cached ->
            return GeocodingClient.GeoResult(cached.lat, cached.lng, address)
        }
        val result = client.geocode(address) ?: return null
        cache.put(GeocodingCacheEntity(address = address, lat = result.lat, lng = result.lng))
        return result
    }

    suspend fun geocodeWithFallback(address: String, name: String?): GeocodingClient.GeoResult? {
        return geocode(address) ?: name?.takeIf { it.isNotBlank() }?.let { n ->
            client.searchPlaces(n).firstOrNull()?.let { place ->
                GeocodingClient.GeoResult(place.lat, place.lng, place.address)
            }
        }
    }

    // ── バッチジオコーディング（DeliveryViewModel の startGeocoding から移動） ──

    /**
     * @param deliveries    処理対象リスト（開始時点のスナップショット）
     * @param areaHint      配達地域キーワード（カンマ区切り）
     * @param isInArea      住所がエリア内かどうかの判定
     * @param extractLocalPart 都道府県・市区町村を除いたローカル部分を返す
     * @param isGroupActive グループが今もアクティブか確認（false なら即中断）
     * @param onProgress    進捗通知 (current, total)
     * @param onResult      1件ジオコーディング成功ごとに呼ばれる
     * @return 失敗件数
     */
    suspend fun batchGeocode(
        deliveries: List<Delivery>,
        areaHint: String,
        isInArea: (String) -> Boolean,
        extractLocalPart: (String) -> String,
        isGroupActive: () -> Boolean,
        onProgress: (current: Int, total: Int) -> Unit,
        onResult: suspend (GeocodedResult) -> Unit
    ): Int {
        var failedCount = 0

        // APIキーが使用不可なら全件スキップ（REQUEST_DENIEDの場合、各コールを待つと数分かかる）
        if (client.isRequestDenied) return deliveries.size

        // 位置バイアス未設定ならエリアキーワードをジオコードして補完
        if (areaHint.isNotBlank() && !client.hasBias()) {
            val keyword = areaHint.split(Regex("[,，、]")).first().trim()
            client.geocodeExact(keyword)?.let { client.setBias(it.lat, it.lng) }
        }

        // エリアバイアス設定のジオコードで REQUEST_DENIED が検出された場合もスキップ
        if (client.isRequestDenied) return deliveries.size

        // セントロイド（重心）を動的に更新してバイアスに反映
        val centroidLats = deliveries.filter { it.isGeocoded && it.lat != 0.0 }
            .map { it.lat }.toMutableList()
        val centroidLngs = deliveries.filter { it.isGeocoded && it.lng != 0.0 }
            .map { it.lng }.toMutableList()
        if (client.hasBias()) {
            repeat(3) {
                centroidLats.add(client.biasLat)
                centroidLngs.add(client.biasLng)
            }
        }
        fun updateBias() {
            if (centroidLats.isNotEmpty()) {
                client.setBias(centroidLats.average(), centroidLngs.average())
            }
        }
        updateBias()

        // 第1パス後に更新された状態を追跡（第2パスで参照するため）
        val firstPassResults = mutableMapOf<String, GeocodedResult>()

        // 第1パス: 未ジオコーディング件を処理
        deliveries.forEachIndexed { index, delivery ->
            if (!isGroupActive()) return failedCount
            onProgress(index + 1, deliveries.size)

            if (delivery.isGeocoded) return@forEachIndexed
            if (client.isRequestDenied) {
                failedCount += deliveries.size - index
                return failedCount
            }

            // 店名として取り込まれた場合（address == name かつ住所の特徴なし）は
            // Places API を優先してから標準ジオコーディングにフォールバック
            var result = if (isStoreName(delivery.address, delivery.name)) {
                val searchQuery = delivery.name ?: delivery.address
                client.searchPlaces(searchQuery).firstOrNull()?.let {
                    GeocodingClient.GeoResult(it.lat, it.lng, it.address)
                } ?: geocodeWithFallback(delivery.address, null)
            } else {
                geocodeWithFallback(delivery.address, delivery.name ?: delivery.address)
            }

            // 都道府県・市区町村が明示されているのに別地域がヒットした場合、候補から正解を探す
            if (result != null && !client.resultMatchesInput(delivery.address, result.formattedAddress)) {
                val better = client.geocodeCandidates(delivery.address)
                    .firstOrNull { client.resultMatchesInput(delivery.address, it.formattedAddress) }
                if (better != null) {
                    result = better
                } else {
                    for (kw in client.extractPrefCity(delivery.address)) {
                        val targeted = client.geocodeExact("$kw ${delivery.address}")
                        if (targeted != null && client.resultMatchesInput(delivery.address, targeted.formattedAddress)) {
                            result = targeted; break
                        }
                        delay(100)
                    }
                }
            }

            // セントロイドから 150km 超ならより近い Places 結果で置き換える
            val cur = result
            if (cur != null && centroidLats.size >= 2) {
                val cLat = centroidLats.average()
                val cLng = centroidLngs.average()
                val distKm = Math.sqrt((cur.lat - cLat).let { it * it } + (cur.lng - cLng).let { it * it }) * 111.0
                if (distKm > 150.0) {
                    val nearby = client.searchPlaces(delivery.address).firstOrNull()?.let {
                        GeocodingClient.GeoResult(it.lat, it.lng, it.address)
                    }
                    if (nearby != null) {
                        val nDistKm = Math.sqrt((nearby.lat - cLat).let { it * it } + (nearby.lng - cLng).let { it * it }) * 111.0
                        if (nDistKm < distKm) result = nearby
                    }
                }
            }

            // エリア外ヒット時は配達地域キーワード＋ローカル部分で再試行
            if (result != null && areaHint.isNotBlank() && !isInArea(result.formattedAddress)) {
                val localPart = extractLocalPart(delivery.address)
                for (kw in areaHint.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }) {
                    val fixed = client.geocodeExact("$kw $localPart")
                    if (fixed != null && isInArea(fixed.formattedAddress)) { result = fixed; break }
                    delay(100)
                }
                if (!isInArea(result?.formattedAddress ?: "")) {
                    val fixedPlace = client.searchPlaces(extractLocalPart(delivery.address))
                        .firstOrNull { isInArea(it.address) }
                        ?.let { GeocodingClient.GeoResult(it.lat, it.lng, it.address) }
                    if (fixedPlace != null) result = fixedPlace
                }
            }

            if (result != null) {
                centroidLats.add(result.lat)
                centroidLngs.add(result.lng)
                updateBias()
                // 名前がない住所の場合、座標から施設名を推測する
                val suggestedName = if (delivery.name.isNullOrBlank()) {
                    client.searchNearbyName(result.lat, result.lng)
                } else null
                val geocodedResult = GeocodedResult(
                    deliveryId = delivery.id,
                    lat = result.lat,
                    lng = result.lng,
                    officialAddress = result.formattedAddress.ifBlank { delivery.address },
                    suggestedName = suggestedName
                )
                firstPassResults[delivery.id] = geocodedResult
                onResult(geocodedResult)
            } else {
                failedCount++
            }
            delay(200)
        }

        // 第2パス: ジオコーディング済みでエリア外の住所を修正（第1パス結果も含む）
        if (areaHint.isNotBlank() && isGroupActive()) {
            val effectiveDeliveries = deliveries.map { d ->
                firstPassResults[d.id]?.let { r ->
                    d.copy(address = r.officialAddress, lat = r.lat, lng = r.lng, isGeocoded = true)
                } ?: d
            }
            effectiveDeliveries.filter { it.isGeocoded && !isInArea(it.address) }.forEach { delivery ->
                if (!isGroupActive()) return failedCount
                val localPart = extractLocalPart(delivery.address)
                for (kw in areaHint.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }) {
                    val fixed = client.geocodeExact("$kw $localPart")
                    if (fixed != null && isInArea(fixed.formattedAddress)) {
                        onResult(GeocodedResult(delivery.id, fixed.lat, fixed.lng, fixed.formattedAddress))
                        break
                    }
                    delay(100)
                }
            }
        }

        return failedCount
    }

    suspend fun evictExpiredCache() {
        cache.evictExpired(System.currentTimeMillis() - cacheTtlMs)
    }

    // address == name かつ住所らしい特徴がない → 店名として扱う
    private fun isStoreName(address: String, name: String?): Boolean {
        if (name.isNullOrBlank() || address != name) return false
        val hasAddress = address.contains(Regex("[都道府県]")) ||
                         address.contains(Regex("[0-9０-９][丁目番地号]")) ||
                         address.contains(Regex("\\d+[-ー−]\\d+"))
        return !hasAddress
    }
}
