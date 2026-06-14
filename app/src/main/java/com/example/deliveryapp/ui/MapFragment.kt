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

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private val markers = mutableMapOf<String, Marker>()
    private val facilityMarkers = mutableListOf<Marker>()
    private var routeLine: Polyline? = null
    private var showRouteLines = true
    private var lastKnownLocation: android.location.Location? = null

    private data class NearbyPlace(val name: String, val address: String, val lat: Double, val lng: Double)

    private val viewModel: DeliveryViewModel by activityViewModels()

    // switchGroup は visibleGroupIds・currentGroupId・allDeliveries を連続して更新するため
    // 各オブザーバが順番に発火し、途中の中間状態で地図が再描画されてしまう。
    // Handler.post を使い、同一フレーム内の複数発火をまとめて1回だけ描画する。
    private val mapHandler = Handler(Looper.getMainLooper())
    private val mapRefreshRunner = Runnable {
        viewModel.allDeliveries.value?.let { updateAllMarkers(it) }
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

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
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
                ?.let { entry -> viewModel.allDeliveries.value?.values?.flatten()?.find { it.id == entry.key } }
            if (clicked != null) {
                val nearby = (viewModel.allDeliveries.value?.values?.flatten() ?: emptyList())
                    .filter { it.hasLocation &&
                        Math.abs(it.lat - clicked.lat) < 0.0002 &&
                        Math.abs(it.lng - clicked.lng) < 0.0002 }
                    .sortedBy { it.order }
                if (nearby.size > 1) showBuildingDeliveries(nearby, clicked.address)
                else showDeliveryOptions(clicked)
            }
            true
        }

        viewModel.allDeliveries.observe(viewLifecycleOwner) {
            scheduleMapRefresh()
        }

        viewModel.groups.observe(viewLifecycleOwner) {
            updateGroupsButtonLabel()
            scheduleMapRefresh()
        }

        viewModel.currentGroupId.observe(viewLifecycleOwner) {
            // リスト切替時は visibleGroupIds を必ずリセットしてから再描画を予約する
            viewModel.setVisibleGroups(null)
            scheduleMapRefresh()
        }

        viewModel.mapFilter.observe(viewLifecycleOwner) {
            scheduleMapRefresh()
        }

        viewModel.pinAddedFromMap.observe(viewLifecycleOwner) {
            scheduleMapRefresh()
        }

        viewModel.visibleGroupIds.observe(viewLifecycleOwner) {
            updateGroupsButtonLabel()
            scheduleMapRefresh()
        }

        // ジオコーディング完了時に地図を更新してカメラをピンに合わせる
        viewModel.geocodingProgress.observe(viewLifecycleOwner) { progress ->
            if (!progress.isRunning && progress.total > 0) {
                scheduleMapRefresh()
            }
        }
    }

    private fun updateAllMarkers(allMap: Map<String, List<Delivery>>) {
        val map = googleMap ?: return
        map.clear()
        markers.clear()
        routeLine = null

        val groups = viewModel.groups.value ?: return
        val filter = viewModel.mapFilter.value
        val currentGroupId = viewModel.currentGroupId.value
        // visibleGroups に currentGroupId が含まれていない場合はリスト切替後の古い値なので null 扱い
        val rawVisible = viewModel.visibleGroupIds.value
        val visibleGroups = if (rawVisible != null && currentGroupId !in rawVisible) null else rawVisible
        val bounds = LatLngBounds.Builder()
        var hasAny = false
        val slotTemplates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(requireContext())

        groups.forEach { group ->
            val shouldShow = if (visibleGroups != null) group.id in visibleGroups
                             else group.id == currentGroupId
            if (!shouldShow) return@forEach
            val list = allMap[group.id] ?: return@forEach
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

            // フィルターなし・現在のグループのみルート線を表示
            if (filter == null && showRouteLines && group.id == viewModel.currentGroupId.value && geocoded.size >= 2) {
                val points = geocoded.map { LatLng(it.lat, it.lng) }
                routeLine = map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(color)
                        .width(6f)
                        .pattern(listOf(Dash(20f), Gap(10f)))
                )
            }
        }

        if (hasAny) {
            try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)) }
            catch (_: Exception) {}
        }

    }

    private fun updateGroupsButtonLabel() {}

    private fun showMapMenu() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー（タイトル + × ボタン）
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(ctx).apply {
            text = "地図メニュー"; textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        val rippleRes = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(rippleRes)
                setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = emoji; textSize = 28f; gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = title; textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(color)
            })
            if (sub.isNotBlank()) col.addView(TextView(ctx).apply {
                text = sub; textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#555555"))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
            })
            row.addView(col)
            row.setOnClickListener { sheet.dismiss(); action() }
            root.addView(row)
        }

        fun divider() = root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
            setBackgroundColor(outlineVariant)
        })

        row("🗺", "ルート最適化", "現在地から最短順に並び替える") {
            val loc = lastKnownLocation
            MaterialAlertDialogBuilder(ctx)
                .setTitle("ルート最適化")
                .setMessage("ルートを現在地から最短経路で再配置します。")
                .setPositiveButton("最適化する") { _, _ ->
                    viewModel.optimizeRoute(loc?.latitude ?: 35.6895, loc?.longitude ?: 139.6917)
                }
                .setNegativeButton("キャンセル", null).show()
        }
        val routeEmoji = if (showRouteLines) "🔵" else "⚫"
        val routeSub   = if (showRouteLines) "現在 ON → タップで非表示" else "現在 OFF → タップで表示"
        row(routeEmoji, "経路線の表示切替", routeSub) {
            showRouteLines = !showRouteLines
            viewModel.allDeliveries.value?.let { updateAllMarkers(it) }
        }
        row("👁", "他のルートも表示", "複数ルートを地図に重ねて表示する") { showGroupVisibilityDialog() }
        divider()
        row("🔍", "近くの施設を探す", "コンビニ・パーキング・道の駅など") { showNearbyFacilitiesDialog(sheet) }
        if (facilityMarkers.isNotEmpty()) {
            row("✕", "施設マーカーを消す", "${facilityMarkers.size}件の施設ピンを地図から削除") {
                facilityMarkers.forEach { it.remove() }
                facilityMarkers.clear()
            }
        }
        divider()
        row("📞", "SOS連絡先を設定", "緊急時の連絡先電話番号を登録する") { showSosContactDialog() }
        row("🆘", "SOS送信", "緊急連絡先にSMSを送信する",
            ContextCompat.getColor(ctx, R.color.colorSosDanger)) { showSosDialog() }
        divider()
        row("🗑", "ピンをすべて削除", "現在のルートの全ピンを削除する",
            ContextCompat.getColor(ctx, R.color.colorActionRed)) {
            val group = viewModel.currentGroup() ?: return@row
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setMessage("「${group.name}」のピンを全件削除しますか？")
                .setPositiveButton("削除") { _, _ -> viewModel.clearCurrentGroup() }
                .setNegativeButton("キャンセル", null).show()
        }

        root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = android.widget.ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(scrollView)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = false
        }
        sheet.show()
    }

    private fun showTimeSlotLegend() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
        }

        layout.addView(android.widget.TextView(ctx).apply {
            text = "時間帯とマーカーの色"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (16 * dp).toInt())
        })

        fun legendRow(label: String, color: Int) {
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (10 * dp).toInt() }
            }
            val circle = android.view.View(ctx).apply {
                val size = (20 * dp).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
                    .also { it.marginEnd = (14 * dp).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
            }
            val tv = android.widget.TextView(ctx).apply {
                text = label
                textSize = 15f
            }
            row.addView(circle)
            row.addView(tv)
            layout.addView(row)
        }

        val slotTemplates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)
        slotTemplates.forEach { tmpl ->
            val color = try { android.graphics.Color.parseColor(tmpl.colorHex) } catch (_: Exception) { android.graphics.Color.GRAY }
            legendRow(tmpl.name, color)
        }

        layout.addView(android.view.View(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#DDDDDD"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also { it.topMargin = (4 * dp).toInt(); it.bottomMargin = (10 * dp).toInt() }
        })
        legendRow("時間帯なし → ルートの色", android.graphics.Color.parseColor("#888888"))
        legendRow("完了済み", android.graphics.Color.parseColor("#9E9E9E"))

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setView(layout)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showNearbyFacilitiesDialog(parentSheet: com.google.android.material.bottomsheet.BottomSheetDialog) {
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

    private fun searchNearbyFacilities(lat: Double, lng: Double, type: String, keyword: String, label: String) {
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

            places.forEach { place ->
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

    private suspend fun fetchNearbyPlaces(lat: Double, lng: Double, type: String, keyword: String): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = com.rodgers.routist.BuildConfig.MAPS_API_KEY
                val sb = StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
                sb.append("?location=$lat,$lng&radius=1500&language=ja&key=$apiKey")
                if (type.isNotEmpty()) sb.append("&type=$type")
                if (keyword.isNotEmpty()) sb.append("&keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")

                val json = org.json.JSONObject(java.net.URL(sb.toString()).readText())
                val results = json.optJSONArray("results") ?: return@withContext emptyList()
                (0 until minOf(results.length(), 20)).mapNotNull { i ->
                    val r = results.optJSONObject(i) ?: return@mapNotNull null
                    val loc = r.optJSONObject("geometry")?.optJSONObject("location") ?: return@mapNotNull null
                    NearbyPlace(
                        name    = r.optString("name"),
                        address = r.optString("vicinity"),
                        lat     = loc.getDouble("lat"),
                        lng     = loc.getDouble("lng")
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    private fun showSosContactDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("sos_settings", android.content.Context.MODE_PRIVATE)
        val current = prefs.getString("sos_phone", "") ?: ""
        val input = android.widget.EditText(ctx).apply {
            hint = "090-1234-5678"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(current)
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("📞 SOS連絡先を設定")
            .setMessage("SOS送信時にSMSを送る電話番号を登録してください。")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val phone = input.text.toString().trim()
                prefs.edit().putString("sos_phone", phone).apply()
                Toast.makeText(ctx, "保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showSosDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("sos_settings", android.content.Context.MODE_PRIVATE)
        val phone = prefs.getString("sos_phone", "") ?: ""
        if (phone.isBlank()) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("SOS連絡先が未設定")
                .setMessage("メニューの「📞 SOS連絡先を設定」から連絡先を登録してください。")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val types = arrayOf("🚗 交通事故", "🔧 車両トラブル", "❓ その他")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("🆘 SOS送信 - トラブルの内容を選択")
            .setItems(types) { _, which ->
                val typeStr = types[which].substringAfter(" ")
                val loc = lastKnownLocation
                val locStr = if (loc != null)
                    "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                else "位置情報なし"
                val msg = "【SOS】${typeStr}が発生しました。\n現在地: $locStr"
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                    putExtra("sms_body", msg)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(ctx, "SMSアプリを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showGroupVisibilityDialog() {
        val groups = viewModel.groups.value ?: return
        val currentGroupId = viewModel.currentGroupId.value
        val otherGroups = groups.filter { it.id != currentGroupId }
        if (otherGroups.isEmpty()) return

        val currentVisible = viewModel.visibleGroupIds.value
        val checked = BooleanArray(otherGroups.size) { i ->
            currentVisible != null && otherGroups[i].id in currentVisible
        }
        val names = otherGroups.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("他のルートも地図に表示する")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("適用") { _, _ ->
                val selectedOthers = otherGroups.filterIndexed { i, _ -> checked[i] }.map { it.id }
                if (selectedOthers.isEmpty()) {
                    viewModel.setVisibleGroups(null)
                } else {
                    viewModel.setVisibleGroups((listOfNotNull(currentGroupId) + selectedOthers).toSet())
                }
            }
            .setNeutralButton("現在のルートのみ") { _, _ -> viewModel.setVisibleGroups(null) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showBuildingDeliveries(deliveries: List<Delivery>, address: String) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ── ヘッダー ─────────────────────────────────────────────
        val done  = deliveries.count { it.isCompleted }
        val total = deliveries.size
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }
        val headerCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerCol.addView(TextView(ctx).apply {
            text = "🏢 同一建物の配達"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
        })
        headerCol.addView(TextView(ctx).apply {
            text = "📍 $address"
            textSize = 13f; setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (2 * dp).toInt() }
        })
        val progressColor = if (done == total) android.graphics.Color.parseColor("#2E7D32") else onSurfaceVariant
        headerCol.addView(TextView(ctx).apply {
            text = "完了 $done / $total 件"
            textSize = 13f; setTextColor(progressColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (4 * dp).toInt() }
        })
        headerRow.addView(headerCol)
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        // ── 配達リスト ───────────────────────────────────────────
        val rippleRes = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        deliveries.forEach { delivery ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(rippleRes)
                setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            }

            // 番号バッジ
            row.addView(TextView(ctx).apply {
                text = "${delivery.order}"
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (delivery.isCompleted)
                        android.graphics.Color.parseColor("#9E9E9E")
                    else android.graphics.Color.parseColor("#1565C0"))
                }
                layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
            })

            // 名前・状態
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            val displayName = if (!delivery.name.isNullOrBlank()) delivery.name!! else delivery.address
            col.addView(TextView(ctx).apply {
                text = displayName; textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(if (delivery.isCompleted)
                    android.graphics.Color.parseColor("#9E9E9E") else onSurfaceColor)
            })
            val subParts = mutableListOf<String>()
            if (!delivery.timeSlot.isNullOrBlank()) subParts.add("🕐 ${delivery.timeSlot}")
            if ((delivery.packageCount ?: 0) > 0) subParts.add("📦 ${delivery.packageCount}個")
            if (!delivery.note.isNullOrBlank()) subParts.add("📝 ${delivery.note!!.take(20)}")
            if (subParts.isNotEmpty()) col.addView(TextView(ctx).apply {
                text = subParts.joinToString("  ")
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#555555"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt() }
            })
            row.addView(col)

            // 完了バッジ
            row.addView(TextView(ctx).apply {
                text = if (delivery.isCompleted) "完了" else "未完了"
                textSize = 12f
                setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12 * dp
                    setColor(if (delivery.isCompleted)
                        android.graphics.Color.parseColor("#4CAF50")
                    else android.graphics.Color.parseColor("#FF9800"))
                }
            })

            row.setOnClickListener {
                sheet.dismiss()
                showDeliveryOptions(delivery)
            }
            root.addView(row)

            // 区切り線
            root.addView(android.view.View(ctx).apply {
                setBackgroundColor(outlineVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                    .also { it.setMargins((74 * dp).toInt(), 0, 0, 0) }
            })
        }

        // ── 全件ナビ（まとめて開始） ─────────────────────────────
        val navRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(rippleRes)
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
        }
        navRow.addView(TextView(ctx).apply {
            text = "🧭"; textSize = 28f; gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val navCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = (14 * dp).toInt() }
        }
        navCol.addView(TextView(ctx).apply {
            text = "この建物へナビ開始"; textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurfaceColor)
        })
        navCol.addView(TextView(ctx).apply {
            text = "最初の未完了配達先にナビを起動する"; textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#555555"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
        })
        navRow.addView(navCol)
        navRow.setOnClickListener {
            sheet.dismiss()
            val target = deliveries.firstOrNull { !it.isCompleted } ?: deliveries.first()
            openNavigation(target)
        }
        root.addView(navRow)

        root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = android.widget.ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(scrollView)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = false
        }
        sheet.show()
    }

    private fun showDeliveryOptions(delivery: Delivery) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val slotTemplates    = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        fun hLine() = root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt()) }
        })

        // ── ヘッダー ─────────────────────────────────────────────
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }
        val infoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // 番号 + タイトル
        val titleText = if (!delivery.name.isNullOrBlank()) delivery.name!! else delivery.address
        infoCol.addView(TextView(ctx).apply {
            text = "${delivery.order}.  $titleText"
            textSize = 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
        })
        // 名前がある場合は住所を副行に
        if (!delivery.name.isNullOrBlank()) {
            infoCol.addView(TextView(ctx).apply {
                text = "📍 ${delivery.address}"
                textSize = 13f
                setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt() }
            })
        }

        // バッジ行（時間帯・個数・完了状態）
        val badgeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (6 * dp).toInt() }
        }
        fun badge(text: String, textColor: Int, bgColor: Int) {
            badgeRow.addView(TextView(ctx).apply {
                this.text = text; textSize = 12f
                setTextColor(textColor)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(bgColor); cornerRadius = (12 * dp) }
                setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.marginEnd = (6 * dp).toInt() }
            })
        }

        if (!delivery.timeSlot.isNullOrBlank()) {
            val slotColor = TimeSlotColor.colorFor(delivery.timeSlot, slotTemplates)
                ?: android.graphics.Color.parseColor("#1565C0")
            val bgAlpha = (slotColor and 0xFFFFFF) or 0x22000000
            badge("🕐 ${delivery.timeSlot}", slotColor, bgAlpha)
        }
        if (delivery.packageCount > 0) {
            badge("📦 ${delivery.packageCount}個",
                android.graphics.Color.parseColor("#E65100"),
                android.graphics.Color.parseColor("#22E65100"))
        }
        if (delivery.isCompleted) {
            badge("✅ 完了済み",
                android.graphics.Color.parseColor("#2E7D32"),
                android.graphics.Color.parseColor("#222E7D32"))
        }
        if (badgeRow.childCount > 0) infoCol.addView(badgeRow)

        headerRow.addView(infoCol)
        // ✕ 閉じるボタン
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)

        // ── メモ ─────────────────────────────────────────────────
        if (!delivery.note.isNullOrBlank()) {
            hLine()
            root.addView(TextView(ctx).apply {
                text = "📝  ${delivery.note}"
                textSize = 14f
                setTextColor(onSurfaceColor)
                setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())
            })
        }

        // ── 写真サムネイル ────────────────────────────────────────
        val photos = delivery.allPhotoUris
        if (photos.isNotEmpty()) {
            hLine()
            val hScroll = android.widget.HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            }
            val photoRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            photos.forEach { uriStr ->
                val thumb = android.widget.ImageView(ctx).apply {
                    val sz = (80 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(sz, sz)
                        .also { it.marginEnd = (8 * dp).toInt() }
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = (6 * dp); setColor(outlineVariant) }
                    clipToOutline = true
                    try { setImageURI(android.net.Uri.parse(uriStr)) } catch (_: Exception) {}
                    setOnClickListener {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                .setDataAndType(android.net.Uri.parse(uriStr), "image/*")
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
                photoRow.addView(thumb)
            }
            hScroll.addView(photoRow)
            root.addView(hScroll)
        }

        // ── ボタン ────────────────────────────────────────────────
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.topMargin = (8 * dp).toInt() }
        })
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        // ナビボタン（アウトライン・固定幅）
        val naviBtn = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "🧭 ナビ"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (52 * dp).toInt())
                .also { it.marginEnd = (10 * dp).toInt() }
            setOnClickListener { sheet.dismiss(); openNavigation(delivery) }
        }

        // 完了ボタン（塗りつぶし・伸びる）
        val doneLabel = if (delivery.isCompleted) "↩️  未完了に戻す" else "✅  完了にする"
        val doneBgColor = if (delivery.isCompleted)
            android.graphics.Color.parseColor("#757575")
        else android.graphics.Color.parseColor("#2E7D32")
        val doneBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = doneLabel
            setBackgroundColor(doneBgColor)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, (52 * dp).toInt(), 1f)
            setOnClickListener {
                viewModel.toggleCompleted(delivery.id)
                sheet.dismiss()
            }
        }

        btnRow.addView(naviBtn)
        btnRow.addView(doneBtn)
        root.addView(btnRow)

        // 詳細を編集ボタン
        val editBtn = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "✏️  詳細を編集\n（時間帯・メモ・写真など）"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also {
                    it.setMargins((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
                }
            setOnClickListener {
                sheet.dismiss()
                viewModel.requestEditDelivery(delivery.id)
                requireActivity().findViewById<androidx.viewpager2.widget.ViewPager2>(
                    com.rodgers.routist.R.id.viewPager)?.currentItem = 0
            }
        }
        root.addView(editBtn)

        sheet.setContentView(root)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            sheet.behavior.skipCollapsed = false
            sheet.behavior.isDraggable = true
            sheet.behavior.peekHeight = (360 * dp).toInt()
        }
        sheet.show()
    }

    private fun showNoteDialogForDelivery(delivery: Delivery) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            setText(delivery.note ?: "")
            hint = "備考・時間帯・受け取り方法など"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; maxLines = 6
            setPadding(48, 24, 48, 8)
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("📝 メモ")
            .setView(input)
            .setPositiveButton("保存") { _, _ -> viewModel.editNote(delivery.id, input.text.toString().trim()) }
            .setNeutralButton(if (delivery.note.isNullOrBlank()) null else "削除") { _, _ ->
                viewModel.editNote(delivery.id, "")
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }


    private fun openNavigation(delivery: Delivery) {
        val uri = if (delivery.hasLocation) {
            Uri.parse("google.navigation:q=${delivery.lat},${delivery.lng}&mode=d")
        } else {
            Uri.parse("google.navigation:q=${Uri.encode(delivery.address)}&mode=d")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(delivery.address)}")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun simpleTextWatcher(action: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) { action() }
    }

    private fun checkLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) enableMyLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun enableMyLocation() {
        googleMap?.isMyLocationEnabled = true
        // GPS座標をViewModelに渡してジオコーディングのバイアスに使用
        googleMap?.setOnMyLocationChangeListener { location ->
            lastKnownLocation = location
            viewModel.setLocationBias(location.latitude, location.longitude)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapHandler.removeCallbacks(mapRefreshRunner)
        _binding = null
    }

}
