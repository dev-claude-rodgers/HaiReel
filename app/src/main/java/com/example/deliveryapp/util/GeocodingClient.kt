package com.rodgers.routist.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

object GeocodingClient {

    private const val GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"
    private var apiKey: String = ""
    private var areaHint: String = ""
    var biasLat: Double = 0.0
        private set
    var biasLng: Double = 0.0
        private set

    fun configure(apiKey: String) { this.apiKey = apiKey }
    fun setAreaHint(hint: String) { areaHint = hint }
    fun setBias(lat: Double, lng: Double) { biasLat = lat; biasLng = lng }
    fun hasBias(): Boolean = biasLat != 0.0 && biasLng != 0.0

    data class GeoResult(
        val lat: Double,
        val lng: Double,
        val formattedAddress: String = ""
    )

    data class RouteResult(
        val distanceMeters: Int,
        val durationSeconds: Int
    )

    data class PlaceInfo(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double
    )

    data class LegInfo(val distanceMeters: Int, val durationSeconds: Int)

    data class RouteWithLegs(
        val legs: List<LegInfo>,
        val encodedPolyline: String
    )

    suspend fun getRoute(
        fromLat: Double, fromLng: Double,
        toLat: Double,   toLng: Double
    ): RouteResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=$fromLat,$fromLng" +
                "&destination=$toLat,$toLng" +
                "&mode=driving&language=ja&key=$apiKey"
            val json = fetch(url) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val routesArr = json.getJSONArray("routes")
            if (routesArr.length() == 0) return@withContext null
            val legsArr0 = routesArr.getJSONObject(0).getJSONArray("legs")
            if (legsArr0.length() == 0) return@withContext null
            val leg = legsArr0.getJSONObject(0)
            RouteResult(
                distanceMeters = leg.getJSONObject("distance").getInt("value"),
                durationSeconds = leg.getJSONObject("duration").getInt("value")
            )
        } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e); null }
    }

    /** 店名または住所キーワードで場所を検索し候補を最大5件返す */
    suspend fun searchPlaces(query: String): List<PlaceInfo> = withContext(Dispatchers.IO) {
        try {
            val q = if (areaHint.isNotBlank()) "$areaHint $query" else query
            val encoded = URLEncoder.encode(q, "UTF-8")
            val sb = StringBuilder("https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encoded&language=ja&region=jp&key=$apiKey")
            if (biasLat != 0.0 && biasLng != 0.0) {
                sb.append("&location=$biasLat,$biasLng&radius=50000")
            }
            val json = fetch(sb.toString()) ?: return@withContext emptyList()
            if (json.getString("status") != "OK") return@withContext emptyList()
            val results = json.getJSONArray("results")
            (0 until minOf(5, results.length())).map { i ->
                val r   = results.getJSONObject(i)
                val loc = r.getJSONObject("geometry").getJSONObject("location")
                val addr = r.optString("formatted_address", "")
                    .replace(Regex("^日本[、,]\\s*"), "")
                    .replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
                    .trim()
                PlaceInfo(r.getString("name"), addr, loc.getDouble("lat"), loc.getDouble("lng"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 出発地 → 経由地（複数）→ 目的地 のルートと各区間距離を取得する */
    suspend fun getRouteWithWaypoints(
        fromLat: Double, fromLng: Double,
        toLat: Double,   toLng: Double,
        waypoints: List<Pair<Double, Double>> = emptyList()
    ): RouteWithLegs? = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder("https://maps.googleapis.com/maps/api/directions/json")
            sb.append("?origin=$fromLat,$fromLng")
            sb.append("&destination=$toLat,$toLng")
            if (waypoints.isNotEmpty()) {
                sb.append("&waypoints=")
                sb.append(waypoints.joinToString("|") { "${it.first},${it.second}" })
            }
            sb.append("&mode=driving&language=ja&key=$apiKey")
            val json = fetch(sb.toString()) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return@withContext null
            val route = routes.getJSONObject(0)
            val legsArr = route.getJSONArray("legs")
            val legs = (0 until legsArr.length()).map { i ->
                val leg = legsArr.getJSONObject(i)
                LegInfo(
                    leg.getJSONObject("distance").getInt("value"),
                    leg.getJSONObject("duration").getInt("value")
                )
            }
            RouteWithLegs(legs, route.getJSONObject("overview_polyline").getString("points"))
        } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e); null }
    }

    /** Google エンコードポリライン文字列を (lat, lng) のリストに復号する */
    fun decodePolylinePoints(encoded: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var i = 0; var lat = 0; var lng = 0
        while (i < encoded.length) {
            var b: Int; var shift = 0; var r = 0
            do { b = encoded[i++].code - 63; r = r or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (r and 1 != 0) (r shr 1).inv() else r shr 1
            shift = 0; r = 0
            do { b = encoded[i++].code - 63; r = r or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lng += if (r and 1 != 0) (r shr 1).inv() else r shr 1
            result.add(Pair(lat / 1e5, lng / 1e5))
        }
        return result
    }

    suspend fun geocode(address: String): GeoResult? = withContext(Dispatchers.IO) {
        try {
            // areaHintは常に先頭に付けて県外ヒットを防ぐ
            val query = if (areaHint.isNotBlank()) "$areaHint $address" else address
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("$GEOCODE_URL?address=$encoded&language=ja&region=jp&key=$apiKey")
            if (biasLat != 0.0 && biasLng != 0.0) {
                urlBuilder.append("&bounds=${biasLat - 0.2},${biasLng - 0.2}|${biasLat + 0.2},${biasLng + 0.2}")
            }
            val json = fetch(urlBuilder.toString()) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val results = json.getJSONArray("results")
            if (results.length() == 0) return@withContext null
            val result = results.getJSONObject(0)
            val loc = result.getJSONObject("geometry").getJSONObject("location")
            val raw = result.optString("formatted_address", "")
            // "日本、〒150-0001 " のような先頭の国名・郵便番号を除去
            val formatted = raw
                .replace(Regex("^日本[、,]\\s*"), "")
                .replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
                .trim()
            GeoResult(lat = loc.getDouble("lat"), lng = loc.getDouble("lng"), formattedAddress = formatted)
        } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e); null }
    }

    /** areaHint を付けずにジオコーディング（エリア修正用） */
    suspend fun geocodeExact(address: String): GeoResult? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(address, "UTF-8")
            val urlBuilder = StringBuilder("$GEOCODE_URL?address=$encoded&language=ja&region=jp&key=$apiKey")
            if (biasLat != 0.0 && biasLng != 0.0) {
                urlBuilder.append("&bounds=${biasLat - 0.2},${biasLng - 0.2}|${biasLat + 0.2},${biasLng + 0.2}")
            }
            val json = fetch(urlBuilder.toString()) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val resultsEx = json.getJSONArray("results")
            if (resultsEx.length() == 0) return@withContext null
            val result = resultsEx.getJSONObject(0)
            val loc = result.getJSONObject("geometry").getJSONObject("location")
            val raw = result.optString("formatted_address", "")
            val formatted = raw
                .replace(Regex("^日本[、,]\\s*"), "")
                .replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
                .trim()
            GeoResult(lat = loc.getDouble("lat"), lng = loc.getDouble("lng"), formattedAddress = formatted)
        } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e); null }
    }

    // 逆ジオコーディング: 座標→住所
    suspend fun reverseGeocode(lat: Double, lng: Double): GeoResult? = withContext(Dispatchers.IO) {
        try {
            val url = "$GEOCODE_URL?latlng=$lat,$lng&language=ja&key=$apiKey"
            val json = fetch(url) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val resultsRev = json.getJSONArray("results")
            if (resultsRev.length() == 0) return@withContext null
            val result = resultsRev.getJSONObject(0)
            val formatted = result.optString("formatted_address", "")
            GeoResult(lat = lat, lng = lng, formattedAddress = formatted)
        } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e); null }
    }

    /** 入力住所から都道府県・市区町村・地区名を抽出する（ひらがな・カタカナ・漢字すべて対応） */
    fun extractPrefCity(address: String): List<String> {
        val jpChar = "[\\u3040-\\u30FF\\u3400-\\u9FFF]"
        val result = mutableListOf<String>()
        Regex("${jpChar}{1,4}[都道府県]").find(address)?.value?.let { result.add(it) }
        val cityMatch = Regex("${jpChar}{1,8}[市区町村]").find(address)
        cityMatch?.value?.let { city ->
            if (result.none { city.startsWith(it) }) result.add(city)
            // 市区町村直後の地区・大字名も抽出（例："研究学園"、"東新井"）
            val afterCity = address.substringAfter(city)
            Regex("^(${jpChar}{2,8})(?=[\\d０-９丁番号\\s]|$)").find(afterCity)
                ?.groupValues?.get(1)?.let { area -> if (area.isNotBlank()) result.add(area) }
        }
        return result
    }

    /** 入力住所に含まれる都道府県・市区町村がジオコード結果に含まれるか検証する */
    fun resultMatchesInput(inputAddress: String, geocodedAddress: String): Boolean {
        val keywords = extractPrefCity(inputAddress)
        if (keywords.isEmpty()) return true
        return keywords.any { kw -> geocodedAddress.contains(kw) }
    }

    /** 入力住所に対して最大5件の候補を返す（areaHintなし、バイアスなし） */
    suspend fun geocodeCandidates(address: String): List<GeoResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(address, "UTF-8")
            val url = "$GEOCODE_URL?address=$encoded&language=ja&region=jp&key=$apiKey"
            val json = fetch(url) ?: return@withContext emptyList()
            if (json.getString("status") != "OK") return@withContext emptyList()
            val results = json.getJSONArray("results")
            (0 until minOf(5, results.length())).map { i ->
                val r = results.getJSONObject(i)
                val loc = r.getJSONObject("geometry").getJSONObject("location")
                val raw = r.optString("formatted_address", "")
                val formatted = raw
                    .replace(Regex("^日本[、,]\\s*"), "")
                    .replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
                    .trim()
                GeoResult(lat = loc.getDouble("lat"), lng = loc.getDouble("lng"), formattedAddress = formatted)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun fetch(url: String): JSONObject? {
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        return try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun looksLikeAddress(text: String): Boolean =
        text.contains(Regex("[都道府県市区町村]")) && text.contains(Regex("[0-9０-９丁目番地号]"))
}
