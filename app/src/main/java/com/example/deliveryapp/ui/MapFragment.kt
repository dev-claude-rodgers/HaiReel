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
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
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
    private val markers = mutableMapOf<String, Marker>()          // 施設ピン用（配達ピンはClusterManagerが管理）
    internal val facilityMarkers = mutableListOf<Marker>()
    private var clusterManager: ClusterManager<DeliveryClusterItem>? = null

    /** ClusterItem ラッパー。分散済み座標を保持する */
    inner class DeliveryClusterItem(
        val delivery: Delivery,
        val markerColor: Int,
        val groupName: String,
        private val pos: LatLng
    ) : ClusterItem {
        override fun getPosition() = pos
        override fun getTitle() = "[${groupName}] ${delivery.order}. ${delivery.displayTitle}"
        override fun getSnippet() = delivery.id
        override fun getZIndex() = 0f
    }

    /** 既存の MarkerIconFactory カスタムアイコンをクラスター解除時に適用するRenderer */
    inner class DeliveryClusterRenderer(
        ctx: android.content.Context,
        map: GoogleMap,
        cm: ClusterManager<DeliveryClusterItem>
    ) : DefaultClusterRenderer<DeliveryClusterItem>(ctx, map, cm) {

        override fun onBeforeClusterItemRendered(item: DeliveryClusterItem, opts: MarkerOptions) {
            opts.icon(MarkerIconFactory.createWithColor(
                item.delivery.order, item.markerColor, item.delivery.isCompleted
            ))
        }

        // 3件以上でクラスター化（2件以下は個別ピンを見せる）
        override fun shouldRenderAsCluster(cluster: Cluster<DeliveryClusterItem>) = cluster.size >= 3
    }
    private val routeLines = mutableListOf<com.google.android.gms.maps.model.Polyline>()
    internal var showRouteLines = true
    internal var lastKnownLocation: android.location.Location? = null
    private var pendingPinLocation: LatLng? = null
    private var tileOverlay: TileOverlay? = null
    internal var rainRadarVisible = false

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

        // APIキー未設定時にダイアログで案内
        val ctx = requireContext()
        if (!com.rodgers.routist.util.AppSettings.hasUserApiKey(ctx)) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("🗺 Google APIキーが必要です")
                .setMessage(
                    "地図・住所変換には Google APIキーの設定が必要です。\n\n" +
                    "【設定手順】\n" +
                    "1. Google Cloud Console で APIキーを取得\n" +
                    "2. 設定タブ → Google APIキー設定 から登録\n\n" +
                    "APIキーは個人利用の範囲では\n通常無料枠内に収まります。"
                )
                .setPositiveButton("設定タブへ") { _, _ ->
                    (activity as? com.rodgers.routist.MainActivity)?.navigateToSettings()
                }
                .setNegativeButton("後で", null)
                .show()
        }

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
        // 気象警報チェック（エリアヒントから都道府県を判定）
        checkDisasterAlert()
    }

    private fun checkDisasterAlert() {
        if (!isAdded) return
        val ctx = requireContext()
        val areaHint = viewModel.areaHint.value
        if (areaHint.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val prefCode = com.rodgers.routist.util.DisasterAlertManager
                .getPrefCodeFromGeocodedAddress(areaHint) ?: return@launch
            val alert = com.rodgers.routist.util.DisasterAlertManager
                .fetchAlerts(prefCode) ?: return@launch
            val level = com.rodgers.routist.util.DisasterAlertManager
                .getAlertLevel(alert)

            if (level == com.rodgers.routist.util.DisasterAlertManager.AlertLevel.NONE) return@launch
            if (!isAdded) return@launch

            val icon  = if (level == com.rodgers.routist.util.DisasterAlertManager.AlertLevel.WARNING) "🔴" else "🟡"
            val title = if (level == com.rodgers.routist.util.DisasterAlertManager.AlertLevel.WARNING) "警報発令中" else "注意報発令中"
            val types = alert.warningTypes.take(3).joinToString("・")
            val msg   = if (alert.headline.isNotBlank()) alert.headline else types

            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "$icon $title: $msg", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setBackgroundTint(
                    if (level == com.rodgers.routist.util.DisasterAlertManager.AlertLevel.WARNING)
                        android.graphics.Color.parseColor("#D32F2F")
                    else android.graphics.Color.parseColor("#F57F17")
                )
                .setTextColor(android.graphics.Color.WHITE)
                .show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        // ── ClusterManager 初期化 ─────────────────────────────────────
        val cm = ClusterManager<DeliveryClusterItem>(requireContext(), map)
        cm.renderer = DeliveryClusterRenderer(requireContext(), map, cm)
        // クラスタータップ → 内包ピンに合わせてズームイン
        cm.setOnClusterClickListener { cluster ->
            val b = LatLngBounds.Builder().apply { cluster.items.forEach { include(it.position) } }.build()
            try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 120)) } catch (_: Exception) {}
            true
        }
        // 個別ピンタップ → 既存の詳細シートを表示
        cm.setOnClusterItemClickListener { item ->
            val delivery = item.delivery
            val nearby = viewModel.allDeliveries.value.values.flatten()
                .filter { it.hasLocation &&
                    Math.abs(it.lat - delivery.lat) < 0.0002 &&
                    Math.abs(it.lng - delivery.lng) < 0.0002 }
                .sortedBy { it.order }
            if (nearby.size > 1) showBuildingDeliveries(nearby, delivery.address)
            else showDeliveryOptions(delivery)
            true
        }
        clusterManager = cm
        // カメラ停止時に再クラスタリング
        map.setOnCameraIdleListener(cm)

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
            // 施設マーカーは ClusterManager の前に処理
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
            // 配達マーカー・クラスターは ClusterManager にデリゲート
            clusterManager?.onMarkerClick(marker) ?: false
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
        if (_binding == null) return  // onDestroyView後の呼び出しを安全にスキップ
        val map = googleMap ?: return
        val cm  = clusterManager ?: return
        map.clear()
        cm.clearItems()
        markers.clear()
        facilityMarkers.clear()
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
        val clusterItems = mutableListOf<DeliveryClusterItem>()

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
                clusterItems.add(DeliveryClusterItem(delivery, markerColor, group.name, spreadPos))
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

        // ClusterManager にピンを渡して描画
        cm.addItems(clusterItems)
        cm.cluster()

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

    internal fun toggleRainRadar() {
        if (rainRadarVisible) {
            tileOverlay?.remove()
            tileOverlay = null
            rainRadarVisible = false
        } else {
            loadRainRadar()
        }
    }

    private fun loadRainRadar() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = com.rodgers.routist.util.RainRadarManager.fetchLatest()
            if (info == null) {
                if (isAdded) Toast.makeText(requireContext(), "雨雲データを取得できませんでした", Toast.LENGTH_SHORT).show()
                return@launch
            }
            tileOverlay = googleMap?.addTileOverlay(
                TileOverlayOptions().tileProvider(createRainTileProvider(info)).transparency(0.0f)
            )
            rainRadarVisible = true
            googleMap?.animateCamera(CameraUpdateFactory.zoomTo(6f))
            if (isAdded) Toast.makeText(requireContext(),
                "🌧 雨雲レーダーON：広域表示で雨域を確認できます",
                Toast.LENGTH_LONG).show()
        }
    }

    private val emptyTileBytes: ByteArray by lazy {
        val bmp = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        out.toByteArray()
    }
    private fun emptyTile() = com.google.android.gms.maps.model.Tile(1, 1, emptyTileBytes)

    private fun createRainTileProvider(
        info: com.rodgers.routist.util.RainRadarManager.RadarInfo
    ): com.google.android.gms.maps.model.TileProvider {
        val maxZoom = 5
        return object : com.google.android.gms.maps.model.TileProvider {
            override fun getTile(x: Int, y: Int, zoom: Int): com.google.android.gms.maps.model.Tile {
                return try {
                    val diff = maxOf(0, zoom - maxZoom)
                    val tz = minOf(zoom, maxZoom)
                    val tx = x shr diff
                    val ty = y shr diff
                    val conn = (java.net.URL("${info.host}${info.path}/256/$tz/$tx/$ty/6/1_1.png")
                        .openConnection() as java.net.HttpURLConnection).also {
                            it.connectTimeout = 5000; it.readTimeout = 5000
                        }
                    if (conn.responseCode != 200) return emptyTile()
                    val rawBytes = conn.inputStream.use { it.readBytes() }
                    if (rawBytes.size < 4 || rawBytes[0] != 0x89.toByte() || rawBytes[1] != 0x50.toByte()) {
                        return emptyTile()
                    }
                    if (diff == 0) {
                        return com.google.android.gms.maps.model.Tile(256, 256, rawBytes)
                    }
                    val src = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        ?: return emptyTile()
                    val size = 256 shr diff
                    if (size <= 0) return emptyTile()
                    val ox = (x and ((1 shl diff) - 1)) * size
                    val oy = (y and ((1 shl diff) - 1)) * size
                    val cropped = android.graphics.Bitmap.createBitmap(src, ox, oy, size, size)
                    src.recycle()
                    val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 256, 256, true)
                    if (scaled !== cropped) cropped.recycle()
                    val bytes = java.io.ByteArrayOutputStream().also {
                        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }.toByteArray()
                    scaled.recycle()
                    com.google.android.gms.maps.model.Tile(256, 256, bytes)
                } catch (_: Exception) {
                    emptyTile()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapHandler.removeCallbacks(mapRefreshRunner)
        googleMap?.setOnMyLocationChangeListener(null)  // 非推奨リスナーのメモリリーク解除
        _binding = null
    }

}
