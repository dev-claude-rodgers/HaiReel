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
import com.rodgers.routist.viewmodel.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    internal var _binding: FragmentMapBinding? = null
    internal val binding get() = _binding!!
    internal var googleMap: GoogleMap? = null
    private val markers = mutableMapOf<String, Marker>()
    internal val facilityMarkers = mutableListOf<Marker>()
    private val routeLines = mutableListOf<com.google.android.gms.maps.model.Polyline>()
    internal var showRouteLines = true
    internal var lastKnownLocation: android.location.Location? = null
    private var pendingPinLocation: LatLng? = null

    internal data class NearbyPlace(val name: String, val address: String, val lat: Double, val lng: Double)

    internal val viewModel: DeliveryViewModel by activityViewModels()

    // switchGroup は visibleGroupIds・currentGroupId・allDeliveries を連続して更新するため
    // 各オブザーバが順番に発火し、途中の中間状態で地図が再描画されてしまう。
    // Handler.post を使い、同一フレーム内の複数発火をまとめて1回だけ描画する。
    private val mapHandler = Handler(Looper.getMainLooper())
    private val mapRefreshRunner = Runnable {
        updateAllMarkers(viewModel.allDeliveries.value)
    }

    private fun scheduleMapRefresh() {
        mapHandler.removeCallbacks(mapRefreshRunner)
        mapHandler.post(mapRefreshRunner)
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> if (perms.values.any { it }) enableMyLocation() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        com.google.android.gms.maps.MapsInitializer.initialize(
            requireContext(),
            com.google.android.gms.maps.MapsInitializer.Renderer.LATEST
        ) { }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            ?: return
        mapFragment.getMapAsync(this)

        binding.buttonMenu.setOnClickListener { showMapMenu() }
        binding.buttonZoomIn.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        binding.buttonZoomOut.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        binding.buttonMyLocation.setOnClickListener {
            lastKnownLocation?.let { loc ->
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude), 15f))
            } ?: run {
                checkLocationPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleMapRefresh()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        checkLocationPermission()

        // 地図の空白を長押しでピン追加
        map.setOnMapLongClickListener { latLng ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("ピンを追加")
                .setMessage("この場所をルートに追加します。")
                .setPositiveButton("追加") { _, _ ->
                    viewModel.addPinFromLocation(latLng.latitude, latLng.longitude)
                    // addPinFromLocation は同期的に allDeliveries を更新するため
                    // コルーチン経由を待たずに直接描画してピンを確実に表示する
                    pendingPinLocation = LatLng(latLng.latitude, latLng.longitude)
                    updateAllMarkers(viewModel.allDeliveries.value)
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        map.setOnMarkerClickListener { marker ->
            // 施設マーカー
            if (marker in facilityMarkers) {
                val name = marker.title ?: ""
                val addr = marker.snippet ?: ""
                val lat = marker.position.latitude
                val lng = marker.position.longitude
                val ctx = requireContext()
                androidx.appcompat.app.AlertDialog.Builder(ctx)
                    .setTitle(name)
                    .setMessage(addr)
                    .setPositiveButton("🧭 ナビ開始") { _, _ ->
                        val uri = android.net.Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            .apply { setPackage("com.google.android.apps.maps") }
                        if (intent.resolveActivity(ctx.packageManager) != null) startActivity(intent)
                    }
                    .setNegativeButton("閉じる", null).show()
                return@setOnMarkerClickListener true
            }
            // 配達マーカー
            val clicked = markers.entries.find { it.value == marker }
                ?.let { entry -> viewModel.allDeliveries.value.values.flatten().find { it.id == entry.key } }
            if (clicked != null) {
                val nearby = viewModel.allDeliveries.value.values.flatten()
                    .filter { it.hasLocation &&
                        Math.abs(it.lat - clicked.lat) < 0.0002 &&
                        Math.abs(it.lng - clicked.lng) < 0.0002 }
                    .sortedBy { it.order }
                if (nearby.size > 1) showBuildingDeliveries(nearby, clicked.address)
                else showDeliveryOptions(clicked)
            }
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allDeliveries.collectLatest { scheduleMapRefresh() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groups.collectLatest {
                updateGroupsButtonLabel()
                scheduleMapRefresh()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // リスト切替時は visibleGroupIds を必ずリセットしてから再描画を予約する
            viewModel.currentGroupId.collectLatest {
                viewModel.setVisibleGroups(null)
                scheduleMapRefresh()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mapFilter.collectLatest { scheduleMapRefresh() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pinAddedFromMap.collect { pin ->
                pendingPinLocation = LatLng(pin.lat, pin.lng)
                scheduleMapRefresh()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.visibleGroupIds.collectLatest {
                updateGroupsButtonLabel()
                scheduleMapRefresh()
            }
        }

        // ジオコーディング完了時に地図を更新してカメラをピンに合わせる
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.geocodingProgress.collectLatest { progress ->
                if (progress != null && !progress.isRunning && progress.total > 0) {
                    scheduleMapRefresh()
                }
            }
        }
    }

    internal fun updateAllMarkers(allMap: Map<String, List<Delivery>>) {
        val map = googleMap ?: return
        map.clear()
        markers.clear()
        routeLines.forEach { it.remove() }
        routeLines.clear()

        val groups = viewModel.groups.value
        val filter = viewModel.mapFilter.value
        val currentGroupId = viewModel.currentGroupId.value
        // visibleGroups に currentGroupId が含まれていない場合はリスト切替後の古い値なので null 扱い
        val rawVisible = viewModel.visibleGroupIds.value
        val visibleGroups = if (rawVisible != null && currentGroupId !in rawVisible) null else rawVisible
        val bounds = LatLngBounds.Builder()
        var hasAny = false

        val slotTemplates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(requireContext())

        groups.forEach outer@{ group ->
            val shouldShow = if (visibleGroups != null) group.id in visibleGroups
                             else group.id == currentGroupId
            if (!shouldShow) return@outer
            val list = allMap[group.id] ?: return@outer
            val geocoded = list.filter { it.hasLocation }
            val color = try { android.graphics.Color.parseColor(group.colorHex) } catch (_: Exception) { android.graphics.Color.parseColor("#F44336") }

            val visible = if (filter != null) geocoded.filter { it.id in filter } else geocoded

            // 同一座標のピンを扇状に分散（0.0001° ≈ 11m のグリッドで同一判定）
            val locationGroups = visible.groupBy {
                "${Math.round(it.lat / 0.0001)}_${Math.round(it.lng / 0.0001)}"
            }

            visible.forEach { delivery ->
                if (delivery.isCompleted) return@forEach
                val key = "${Math.round(delivery.lat / 0.0001)}_${Math.round(delivery.lng / 0.0001)}"
                val group2 = locationGroups[key] ?: listOf(delivery)
                val idx = group2.indexOf(delivery)
                val spreadPos = if (group2.size > 1) {
                    val angle = 2 * Math.PI * idx / group2.size - Math.PI / 2
                    val r = 0.00012
                    LatLng(delivery.lat + r * Math.cos(angle), delivery.lng + r * Math.sin(angle))
                } else {
                    LatLng(delivery.lat, delivery.lng)
                }
                val markerColor = TimeSlotColor.colorFor(delivery.timeSlot, slotTemplates) ?: color
                val icon = MarkerIconFactory.createWithColor(delivery.order, markerColor, delivery.isCompleted)
                val label = if (!delivery.name.isNullOrBlank()) "${delivery.name} - ${delivery.address}" else delivery.address
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(spreadPos)
                        .title("[${group.name}] ${delivery.order}. $label")
                        .icon(icon)
                )
                marker?.let { markers[delivery.id] = it }
                bounds.include(spreadPos)
                hasAny = true
            }

            // フィルターなし・現在のグループのみルート線を表示（未完了のみ、2件以上のとき）
            val incomplete = geocoded.filter { !it.isCompleted }
            if (filter == null && showRouteLines && incomplete.size >= 2) {
                val points = incomplete.map { LatLng(it.lat, it.lng) }
                map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(color)
                        .width(6f)
                        .pattern(listOf(Dash(20f), Gap(10f)))
                )?.let { routeLines.add(it) }
            }
        }

        val pinLoc = pendingPinLocation
        if (pinLoc != null) {
            pendingPinLocation = null
            try { map.animateCamera(CameraUpdateFactory.newLatLngZoom(pinLoc, 16f)) }
            catch (_: Exception) {}
        } else if (hasAny) {
            try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)) }
            catch (_: Exception) {}
        }

    }

    private fun updateGroupsButtonLabel() {}


    private fun checkLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) enableMyLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        @Suppress("MissingPermission", "DEPRECATION")
        googleMap?.let { map ->
            map.isMyLocationEnabled = true
            map.setOnMyLocationChangeListener { location ->
                lastKnownLocation = location
                viewModel.setLocationBias(location.latitude, location.longitude)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapHandler.removeCallbacks(mapRefreshRunner)
        _binding = null
    }

}
