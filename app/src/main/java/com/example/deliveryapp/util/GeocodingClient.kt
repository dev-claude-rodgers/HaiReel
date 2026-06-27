package com.rodgers.routist.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

object GeocodingClient : GeocodingApi {

    private const val GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"
    private var apiKey: String = ""
    private var areaHint: String = ""
    override var biasLat: Double = 0.0
        private set
    override var biasLng: Double = 0.0
        private set

    override var isRequestDenied: Boolean = false
        private set

    override fun configure(apiKey: String) { this.apiKey = apiKey; isRequestDenied = false }
    override fun setAreaHint(hint: String) { areaHint = hint }
    override fun setBias(lat: Double, lng: Double) { biasLat = lat; biasLng = lng }
    override fun hasBias(): Boolean = biasLat != 0.0 && biasLng != 0.0

    data class GeoResult(
        val lat: Double,
        val lng: Double,
        val formattedAddress: String = ""
    )

    data class PlaceInfo(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double
    )

    /** 店名または住所キーワードで場所を検索し候補を最大5件返す */
    override suspend fun searchPlaces(query: String): List<PlaceInfo> = withContext(Dispatchers.IO) {
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

    override suspend fun geocode(address: String): GeoResult? = withContext(Dispatchers.IO) {
        try {
            // areaHintは常に先頭に付けて県外ヒットを防ぐ
            val query = if (areaHint.isNotBlank()) "$areaHint $address" else address
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("$GEOCODE_URL?address=$encoded&language=ja&region=jp&key=$apiKey")
            if (biasLat != 0.0 && biasLng != 0.0) {
                urlBuilder.append("&bounds=${biasLat - 0.2},${biasLng - 0.2}|${biasLat + 0.2},${biasLng + 0.2}")
            }
            val json = fetch(urlBuilder.toString()) ?: return@withContext null
            val status = json.getString("status")
            if (status == "REQUEST_DENIED") { isRequestDenied = true; return@withContext null }
            if (status != "OK") return@withContext null
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
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e); null
        }
    }

    /** areaHint を付けずにジオコーディング（エリア修正用） */
    override suspend fun geocodeExact(address: String): GeoResult? = withContext(Dispatchers.IO) {
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
    override suspend fun reverseGeocode(lat: Double, lng: Double): GeoResult? = withContext(Dispatchers.IO) {
        try {
            val url = "$GEOCODE_URL?latlng=$lat,$lng&language=ja&key=$apiKey"
            val json = fetch(url) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val resultsRev = json.getJSONArray("results")
            if (resultsRev.length() == 0) return@withContext null
            val result = resultsRev.getJSONObject(0)
            val formatted = result.optString("formatted_address", "")
                .replace(Regex("^日本[、,]\\s*"), "")
                .replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
                .trim()
            GeoResult(lat = lat, lng = lng, formattedAddress = formatted)
        } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e); null }
    }

    /** 入力住所から都道府県・市区町村・地区名を抽出する（ひらがな・カタカナ・漢字すべて対応） */
    override fun extractPrefCity(address: String): List<String> {
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
    override fun resultMatchesInput(inputAddress: String, geocodedAddress: String): Boolean {
        val keywords = extractPrefCity(inputAddress)
        if (keywords.isEmpty()) return true
        return keywords.any { kw -> geocodedAddress.contains(kw) }
    }

    /** 入力住所に対して最大5件の候補を返す（areaHintなし、バイアスなし） */
    override suspend fun geocodeCandidates(address: String): List<GeoResult> = withContext(Dispatchers.IO) {
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

    /** 座標から半径30m以内の施設名を返す（住所→店名の推測） */
    override suspend fun searchNearbyName(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=$lat,$lng&radius=30&language=ja&key=$apiKey"
            val json = fetch(url) ?: return@withContext null
            if (json.getString("status") != "OK") return@withContext null
            val results = json.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            results.getJSONObject(0).optString("name").ifBlank { null }
        } catch (_: Exception) { null }
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

}
