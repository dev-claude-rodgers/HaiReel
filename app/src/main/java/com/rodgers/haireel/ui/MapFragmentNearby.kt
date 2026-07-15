package com.rodgers.haireel.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.rodgers.haireel.R
import com.rodgers.haireel.databinding.FragmentMapBinding
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.util.GeocodingClient
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.util.MarkerIconFactory
import com.rodgers.haireel.util.TimeSlotColor
import com.rodgers.haireel.viewmodel.DeliveryViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


    internal fun MapFragment.showNearbyFacilitiesDialog(parentSheet: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val ctx = requireContext()
        val depLat = com.rodgers.haireel.util.AppSettings.getDepartureLat(ctx)
        val depLng = com.rodgers.haireel.util.AppSettings.getDepartureLng(ctx)
        val hasDep = depLat != 0.0 || depLng != 0.0
        val depAddress = com.rodgers.haireel.util.AppSettings.getDepartureAddress(ctx)

        val arrLat = com.rodgers.haireel.util.AppSettings.getArrivalLat(ctx)
        val arrLng = com.rodgers.haireel.util.AppSettings.getArrivalLng(ctx)
        val hasArr = arrLat != 0.0 || arrLng != 0.0
        val arrAddress = com.rodgers.haireel.util.AppSettings.getArrivalAddress(ctx)

        if (hasDep || hasArr) {
            // 出発地または帰着地が設定済み → 基点を選ばせる
            val choiceList = mutableListOf("📍 現在地から探す")
            if (hasDep) choiceList.add("🏠 出発地から探す（$depAddress）")
            if (hasArr) choiceList.add("🏁 帰着地から探す（$arrAddress）")
            val choices = choiceList.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("どこから探しますか？")
                .setItems(choices) { _, which ->
                    when {
                        which == 0 -> {
                            val loc = lastKnownLocation
                            if (loc == null) {
                                Toast.makeText(ctx, "現在地が取得できません。位置情報を許可してください。", Toast.LENGTH_LONG).show()
                            } else {
                                showFacilityTypeDialog(parentSheet, loc.latitude, loc.longitude)
                            }
                        }
                        which == 1 && hasDep -> showFacilityTypeDialog(parentSheet, depLat, depLng)
                        else                 -> showFacilityTypeDialog(parentSheet, arrLat, arrLng)
                    }
                }
                .setNegativeButton("キャンセル", null).show()
        } else {
            val loc = lastKnownLocation
            if (loc == null) {
                Toast.makeText(ctx, "現在地が取得できません。位置情報を許可してください。", Toast.LENGTH_LONG).show()
                return
            }
            showFacilityTypeDialog(parentSheet, loc.latitude, loc.longitude)
        }
    }

    private fun MapFragment.showFacilityTypeDialog(
        parentSheet: com.google.android.material.bottomsheet.BottomSheetDialog,
        lat: Double,
        lng: Double
    ) {
        val ctx = requireContext()
        val options = arrayOf(
            "🚻 公衆トイレ",
            "🏪 コンビニ",
            "🅿️ コインパーキング",
            "🍽️ 飲食店",
            "⛽ ガソリンスタンド",
            "🏦 ATM・銀行",
            "💊 薬局・ドラッグストア",
            "🛒 スーパー"
        )
        val types    = arrayOf("", "convenience_store", "parking", "restaurant", "gas_station", "atm", "pharmacy", "supermarket")
        val keywords = arrayOf("公衆トイレ", "", "", "", "", "", "", "")

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("近くの施設を探す")
            .setItems(options) { _, which ->
                parentSheet.dismiss()
                searchNearbyFacilities(lat, lng, types[which], keywords[which], options[which])
            }
            .setNegativeButton("キャンセル", null).show()
    }


    internal fun MapFragment.searchNearbyFacilities(lat: Double, lng: Double, type: String, keyword: String, label: String) {
        val ctx = requireContext()
        Toast.makeText(ctx, "${label}を検索中…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val places = fetchNearbyPlaces(lat, lng, type, keyword)
            if (places == null) {
                // 通信エラー（電波弱い・タイムアウト）
                if (isAdded) Toast.makeText(ctx, "通信エラーです。電波の良い場所で再試行してください。", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (places.isEmpty()) {
                Toast.makeText(ctx, "近くに${label}が見つかりません。別の場所を試してください。", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val map = googleMap ?: return@launch
            facilityMarkers.forEach { it.remove() }
            facilityMarkers.clear()
            savedFacilityPlaces.clear()

            places.forEach { place: MapFragment.NearbyPlace ->
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(place.lat, place.lng))
                        .title(place.name)
                        .snippet(place.address)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                ) ?: return@forEach
                marker.tag = place
                facilityMarkers.add(marker)
                savedFacilityPlaces.add(place)
            }
            Toast.makeText(ctx, "${label} ${places.size}件を地図に表示しました", Toast.LENGTH_SHORT).show()
        }
    }

    // null = 通信エラー / emptyList = 取得成功だが件数ゼロ
    private suspend fun MapFragment.fetchNearbyPlaces(lat: Double, lng: Double, type: String, keyword: String): List<MapFragment.NearbyPlace>? =
        withContext(Dispatchers.IO) {
            try {
                val appCtx = context ?: return@withContext null
                val userKey = com.rodgers.haireel.util.AppSettings.getUserApiKey(appCtx)
                val apiKey = if (userKey.isNotBlank()) userKey
                             else com.rodgers.haireel.BuildConfig.MAPS_API_KEY
                val sb = StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
                sb.append("?location=$lat,$lng&radius=1500&language=ja&key=$apiKey")
                if (type.isNotEmpty()) sb.append("&type=$type")
                if (keyword.isNotEmpty()) sb.append("&keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")

                val conn = (java.net.URL(sb.toString()).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 8000
                }
                if (conn.responseCode != 200) return@withContext null
                val json = org.json.JSONObject(conn.inputStream.use { it.reader().readText() })
                val results = json.optJSONArray("results") ?: return@withContext emptyList()
                (0 until minOf(results.length(), 20)).mapNotNull { i ->
                    val r = results.optJSONObject(i) ?: return@mapNotNull null
                    val loc = r.optJSONObject("geometry")?.optJSONObject("location") ?: return@mapNotNull null
                    MapFragment.NearbyPlace(
                        name    = r.optString("name"),
                        address = r.optString("vicinity"),
                        lat     = loc.getDouble("lat"),
                        lng     = loc.getDouble("lng")
                    )
                }
            } catch (_: java.net.UnknownHostException) { null }
              catch (_: java.net.SocketTimeoutException) { null }
              catch (_: Exception) { null }
        }

