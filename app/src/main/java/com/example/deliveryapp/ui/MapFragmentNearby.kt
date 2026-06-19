package com.rodgers.routist.ui

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
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentMapBinding
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.util.GeocodingClient
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.util.MarkerIconFactory
import com.rodgers.routist.util.TimeSlotColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
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
        val loc = lastKnownLocation
        if (loc == null) {
            Toast.makeText(ctx, "現在地が取得できません。位置情報を許可してください。", Toast.LENGTH_LONG).show()
            return
        }

        val options = arrayOf(
            "🏪 コンビニ",
            "🅿️ コインパーキング",
            "⛽ ガソリンスタンド",
            "🍽️ 飲食店",
            "🛣️ 道の駅"
        )
        val types = arrayOf("convenience_store", "parking", "gas_station", "restaurant", "")
        val keywords = arrayOf("", "", "", "", "道の駅")

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("近くの施設を探す")
            .setItems(options) { _, which ->
                parentSheet.dismiss()
                val type = types[which]
                val keyword = keywords[which]
                val label = options[which]
                searchNearbyFacilities(loc.latitude, loc.longitude, type, keyword, label)
            }
            .setNegativeButton("キャンセル", null).show()
    }


    internal fun MapFragment.searchNearbyFacilities(lat: Double, lng: Double, type: String, keyword: String, label: String) {
        val ctx = requireContext()
        Toast.makeText(ctx, "${label}を検索中…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val places = fetchNearbyPlaces(lat, lng, type, keyword)
            if (places.isEmpty()) {
                Toast.makeText(ctx, "近くに${label}が見つかりません。別の場所を試してください。", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val map = googleMap ?: return@launch
            facilityMarkers.forEach { it.remove() }
            facilityMarkers.clear()

            places.forEach { place: MapFragment.NearbyPlace ->
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(place.lat, place.lng))
                        .title(place.name)
                        .snippet(place.address)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                ) ?: return@forEach
                facilityMarkers.add(marker)
            }
            Toast.makeText(ctx, "${label} ${places.size}件を地図に表示しました", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun MapFragment.fetchNearbyPlaces(lat: Double, lng: Double, type: String, keyword: String): List<MapFragment.NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val appCtx = context ?: return@withContext emptyList()
                val userKey = com.rodgers.routist.util.AppSettings.getUserApiKey(appCtx)
                val apiKey = if (userKey.isNotBlank()) userKey
                             else com.rodgers.routist.BuildConfig.MAPS_API_KEY
                val sb = StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
                sb.append("?location=$lat,$lng&radius=1500&language=ja&key=$apiKey")
                if (type.isNotEmpty()) sb.append("&type=$type")
                if (keyword.isNotEmpty()) sb.append("&keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")

                val json = org.json.JSONObject(java.net.URL(sb.toString()).readText())
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
            } catch (_: Exception) { emptyList() }
        }

