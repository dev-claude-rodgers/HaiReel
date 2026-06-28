package com.rodgers.haireel.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object RainRadarManager {
    private const val META_URL = "https://api.rainviewer.com/public/weather-maps.json"

    data class RadarInfo(val host: String, val path: String)

    suspend fun fetchLatest(): RadarInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(URL(META_URL).readText())
            val host = json.optString("host", "https://tilecache.rainviewer.com")
            val past = json.getJSONObject("radar").getJSONArray("past")
            if (past.length() == 0) return@withContext null
            val latest = past.getJSONObject(past.length() - 1)
            RadarInfo(host, latest.getString("path"))
        } catch (_: Exception) { null }
    }
}
