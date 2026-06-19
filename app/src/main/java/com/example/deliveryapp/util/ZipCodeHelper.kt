package com.rodgers.routist.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object ZipCodeHelper {

    data class ZipResult(
        val pref: String,
        val city: String,
        val town: String
    ) {
        val address: String get() = "$pref$city$town"
    }

    suspend fun lookup(zipcode: String): ZipResult? = withContext(Dispatchers.IO) {
        try {
            val digits = zipcode.replace("-", "").replace("ー", "").trim()
            if (digits.length != 7 || !digits.all { it.isDigit() }) return@withContext null
            val url = "https://zipcloud.ibsnet.co.jp/api/search?zipcode=$digits"
            val json = JSONObject(URL(url).readText())
            if (json.getInt("status") != 200) return@withContext null
            val results = json.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            val r = results.getJSONObject(0)
            ZipResult(
                pref = r.optString("address1", ""),
                city = r.optString("address2", ""),
                town = r.optString("address3", "")
            )
        } catch (_: Exception) { null }
    }
}
