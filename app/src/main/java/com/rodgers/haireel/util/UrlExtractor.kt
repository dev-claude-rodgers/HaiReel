package com.rodgers.haireel.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLDecoder

object UrlExtractor {

    data class ExtractedPlace(
        val name: String,
        val query: String,      // ジオコーディングに使うクエリ
        val lat: Double? = null,
        val lng: Double? = null
    )

    suspend fun extract(urlString: String): ExtractedPlace? = withContext(Dispatchers.IO) {
        try {
            val url = urlString.trim()

            // Google Maps URL から座標・スポット名を抽出
            val googleResult = parseGoogleMapsUrl(url)
            if (googleResult != null) return@withContext googleResult

            // 一般URLをfetchしてスポット名・住所を抽出
            fetchAndExtract(url)
        } catch (e: Exception) {
            null
        }
    }

    // Google Maps URL パターン
    // https://www.google.com/maps/place/スポット名/@lat,lng,zoom
    // https://maps.google.com/?q=lat,lng
    // https://maps.google.com/?q=スポット名
    private fun parseGoogleMapsUrl(url: String): ExtractedPlace? {
        if (!url.contains("google.com/maps") && !url.contains("maps.google.com") && !url.contains("goo.gl/maps")) return null

        // @lat,lng,zoom パターン
        val atPattern = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
        val atMatch = atPattern.find(url)
        if (atMatch != null) {
            val lat = atMatch.groupValues[1].toDoubleOrNull()
            val lng = atMatch.groupValues[2].toDoubleOrNull()
            // スポット名を取得
            val name = extractGooglePlaceName(url) ?: "Google Mapsスポット"
            if (lat != null && lng != null) {
                return ExtractedPlace(name = name, query = name, lat = lat, lng = lng)
            }
        }

        // ?q= パターン
        val qPattern = Regex("""[?&]q=([^&]+)""")
        val qMatch = qPattern.find(url)
        if (qMatch != null) {
            val q = URLDecoder.decode(qMatch.groupValues[1], "UTF-8")
            // 座標形式か確認
            val coordPattern = Regex("""(-?\d+\.\d+),(-?\d+\.\d+)""")
            val coordMatch = coordPattern.find(q)
            if (coordMatch != null) {
                val lat = coordMatch.groupValues[1].toDoubleOrNull()
                val lng = coordMatch.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    return ExtractedPlace(name = "Google Mapsピン", query = q, lat = lat, lng = lng)
                }
            }
            return ExtractedPlace(name = q, query = q)
        }

        // /place/スポット名/ パターン
        val placePattern = Regex("""/place/([^/@]+)""")
        val placeMatch = placePattern.find(url)
        if (placeMatch != null) {
            val name = URLDecoder.decode(placeMatch.groupValues[1].replace("+", " "), "UTF-8")
            return ExtractedPlace(name = name, query = name)
        }

        return null
    }

    private fun extractGooglePlaceName(url: String): String? {
        val placePattern = Regex("""/place/([^/@]+)""")
        val match = placePattern.find(url)
        return match?.let { URLDecoder.decode(it.groupValues[1].replace("+", " "), "UTF-8") }
    }

    // 一般URLをfetchしてスポット名・住所を抽出
    private fun fetchAndExtract(urlString: String): ExtractedPlace? {
        val connection = URL(urlString).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14)")
        connection.connectTimeout = 5000
        connection.readTimeout = 8000
        val html = connection.getInputStream().bufferedReader().readText()

        // ページタイトルを取得
        val title = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim() ?: ""

        // JSON-LD から住所を抽出 (Schema.org)
        val jsonLdAddress = extractJsonLdAddress(html)
        if (jsonLdAddress != null) {
            return ExtractedPlace(name = title.ifBlank { jsonLdAddress }, query = jsonLdAddress)
        }

        // meta geo.position
        val geoPattern = Regex("""meta[^>]+name=["\']geo\.position["\'][^>]+content=["\']([^;]+);([^"\']+)""", RegexOption.IGNORE_CASE)
        val geoMatch = geoPattern.find(html)
        if (geoMatch != null) {
            val lat = geoMatch.groupValues[1].trim().toDoubleOrNull()
            val lng = geoMatch.groupValues[2].trim().toDoubleOrNull()
            if (lat != null && lng != null) {
                return ExtractedPlace(name = title.ifBlank { "スポット" }, query = title, lat = lat, lng = lng)
            }
        }

        // 日本の住所パターンを本文から検索（都道府県から始まる住所）
        val addressPattern = Regex("""((?:北海道|[東西南北]?[都道府県])[^\s、。「」【】]{5,30}(?:\d+[-－]\d+[-－]?\d*))""")
        val addressMatch = addressPattern.find(html)
        if (addressMatch != null) {
            val address = addressMatch.groupValues[1]
            return ExtractedPlace(name = title.ifBlank { address }, query = address)
        }

        // タイトルだけをクエリとして使用
        if (title.isNotBlank()) {
            return ExtractedPlace(name = title, query = title)
        }

        return null
    }

    private fun extractJsonLdAddress(html: String): String? {
        val scriptPattern = Regex("""<script[^>]+type=["\']application/ld\+json["\'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        for (match in scriptPattern.findAll(html)) {
            val json = match.groupValues[1]
            // addressの抽出（簡易パース）
            val streetPattern = Regex(""""streetAddress"\s*:\s*"([^"]+)"""")
            val localityPattern = Regex(""""addressLocality"\s*:\s*"([^"]+)"""")
            val regionPattern = Regex(""""addressRegion"\s*:\s*"([^"]+)"""")
            val street = streetPattern.find(json)?.groupValues?.get(1)
            val locality = localityPattern.find(json)?.groupValues?.get(1)
            val region = regionPattern.find(json)?.groupValues?.get(1)
            if (street != null || locality != null) {
                return listOfNotNull(region, locality, street).joinToString("")
            }
        }
        return null
    }
}
