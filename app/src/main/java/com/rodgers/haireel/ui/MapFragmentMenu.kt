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
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.GeocodingClient
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.util.MarkerIconFactory
import com.rodgers.haireel.util.TimeSlotColor
import com.rodgers.haireel.viewmodel.DeliveryViewModel
import com.rodgers.haireel.viewmodel.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


    internal fun MapFragment.showMapMenu() {
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
                setTextColor(onSurfaceVariant)
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

        // ── ルート操作
        row("🗺", "ルート最適化", "出発地から最短順に並び替える") {
            val geocodedCount = viewModel.deliveries.value.count { it.hasLocation }
            if (geocodedCount < 2) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("ルート最適化できません")
                    .setMessage("地図に配置済みの住所が2件未満です。\n住所を入力すると自動で地図に配置されます。配達リストで「⏳ 検索中」が消えてから実行してください。")
                    .setPositiveButton("OK", null).show()
                return@row
            }
            val hasTimeWindows = viewModel.deliveries.value.any {
                !it.openTime.isNullOrBlank() || !it.closeTime.isNullOrBlank()
            }
            val nowCal = java.util.Calendar.getInstance()
            val nowMinutes = nowCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                             nowCal.get(java.util.Calendar.MINUTE)
            val threshold = AppSettings.getUrgencyThresholdMinutes(ctx)
            val timeWindowNote = if (hasTimeWindows)
                "\n\n営業時間が設定されている場所は閉店${threshold}分前を優先します。" else ""

            // 出発地入力欄（前回の住所を引き継ぐ）
            val dp = ctx.resources.displayMetrics.density
            val etDeparture = EditText(ctx).apply {
                hint = "例: 東京都渋谷区〇〇1-2-3（空欄で現在地）"
                inputType = InputType.TYPE_CLASS_TEXT
                setText(AppSettings.getDepartureAddress(ctx))
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            }
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
                addView(TextView(ctx).apply {
                    text = "出発地・帰着地の住所"
                    textSize = 13f
                    setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
                addView(etDeparture)
                addView(TextView(ctx).apply {
                    text = "※ 空欄の場合は現在地から最適化します"
                    textSize = 11f
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                    setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
            }

            MaterialAlertDialogBuilder(ctx)
                .setTitle("ルート最適化")
                .setMessage("地図に配置済みの${geocodedCount}件を最短経路で並び替えます。$timeWindowNote")
                .setView(container)
                .setPositiveButton("最適化する") { _, _ ->
                    val inputAddress = etDeparture.text.toString().trim()
                    lifecycleScope.launch {
                        val depLat: Double
                        val depLng: Double
                        if (inputAddress.isNotBlank()) {
                            Toast.makeText(ctx, "住所を検索中...", Toast.LENGTH_SHORT).show()
                            val geo = withContext(Dispatchers.IO) {
                                GeocodingClient.geocodeExact(inputAddress)
                            }
                            if (geo == null) {
                                Toast.makeText(ctx, "住所が見つかりませんでした", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            depLat = geo.lat; depLng = geo.lng
                            AppSettings.setDepartureAddress(ctx, inputAddress)
                            AppSettings.setDepartureLatLng(ctx, depLat, depLng)
                        } else {
                            val loc = lastKnownLocation
                            if (loc == null) {
                                Toast.makeText(ctx, "現在地が取得できません。住所を入力してください。", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            depLat = loc.latitude; depLng = loc.longitude
                        }

                        val result = viewModel.optimizeRoute(depLat, depLng, nowMinutes, threshold)

                        // 合計距離を計算: 出発地→1件目 + 地点間合計 + 最終地点→出発地
                        val ordered = result.ordered
                        val totalKm = if (ordered.isNotEmpty()) {
                            var km = 0.0
                            val first = ordered.first()
                            val last  = ordered.last()
                            if (first.hasLocation)
                                km += com.rodgers.haireel.util.RouteOptimizer.haversine(depLat, depLng, first.lat, first.lng)
                            for (i in 0 until ordered.size - 1) {
                                val a = ordered[i]; val b = ordered[i + 1]
                                if (a.hasLocation && b.hasLocation)
                                    km += com.rodgers.haireel.util.RouteOptimizer.haversine(a.lat, a.lng, b.lat, b.lng)
                            }
                            if (last.hasLocation)
                                km += com.rodgers.haireel.util.RouteOptimizer.haversine(last.lat, last.lng, depLat, depLng)
                            km
                        } else 0.0

                        val totalStr = "${"%.1f".format(totalKm)}km"
                        if (result.skipped.isNotEmpty()) {
                            val names = result.skipped.joinToString("\n") { "・${it.displayTitle}" }
                            MaterialAlertDialogBuilder(ctx)
                                .setTitle("⚠ 閉店済みのためスキップ")
                                .setMessage("以下の配達先はすでに閉店しているため、リスト末尾に移動しました。\n\n$names\n\n合計距離（往復）: $totalStr")
                                .setPositiveButton("OK", null).show()
                        } else {
                            Toast.makeText(ctx, "最適化完了　合計距離: $totalStr", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("キャンセル", null).show()
        }
        val routeEmoji = if (showRouteLines) "🔵" else "⚫"
        val routeSub   = if (showRouteLines) "経路線 ON → タップで非表示" else "経路線 OFF → タップで表示"
        row(routeEmoji, "経路線の表示切替", routeSub) {
            showRouteLines = !showRouteLines
            updateAllMarkers(viewModel.allDeliveries.value)
        }
        val radarEmoji = if (rainRadarVisible) "🌧" else "⛅"
        val radarSub   = if (rainRadarVisible) "雨雲レーダー ON → タップで非表示" else "雨雲レーダー OFF → タップで表示"
        row(radarEmoji, "雨雲レーダー", radarSub) { toggleRainRadar() }
        row("👁", "他のルートも表示", "複数ルートを地図に重ねて表示する") { showGroupVisibilityDialog() }
        divider()
        // ── 周辺情報
        row("🔍", "近くの施設を探す", "コンビニ・パーキング・道の駅など") { showNearbyFacilitiesDialog(sheet) }
        if (facilityMarkers.isNotEmpty()) {
            row("✕", "施設マーカーを消す", "${facilityMarkers.size}件の施設ピンを削除") {
                facilityMarkers.forEach { it.remove() }
                facilityMarkers.clear()
                savedFacilityPlaces.clear()
            }
        }
        divider()
        // ── 緊急
        row("🆘", "SOS送信", "緊急連絡先にSMSを送信する",
            ContextCompat.getColor(ctx, R.color.colorSosDanger)) { showSosDialog() }
        row("📞", "SOS連絡先を設定", "緊急時の連絡先電話番号を登録する") { showSosContactDialog() }
        divider()
        // ── 削除
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


    internal fun MapFragment.showTimeSlotLegend() {
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

        val slotTemplates = com.rodgers.haireel.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)
        slotTemplates.forEach { tmpl ->
            val color = try { android.graphics.Color.parseColor(tmpl.colorHex) } catch (_: Exception) { android.graphics.Color.GRAY }
            legendRow(tmpl.name, color)
        }

        layout.addView(android.view.View(ctx).apply {
            setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant))
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


    internal fun MapFragment.showGroupVisibilityDialog() {
        val groups = viewModel.groups.value
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

