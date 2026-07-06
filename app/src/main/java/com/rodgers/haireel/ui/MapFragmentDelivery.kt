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


    internal fun MapFragment.showBuildingDeliveries(deliveries: List<Delivery>, address: String) {
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
            val displayName = delivery.displayTitle
            col.addView(TextView(ctx).apply {
                text = displayName; textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(if (delivery.isCompleted)
                    onSurfaceVariant else onSurfaceColor)
            })
            val subParts = mutableListOf<String>()
            if (!delivery.timeSlot.isNullOrBlank()) subParts.add("🕐 ${delivery.timeSlot}")
            if (delivery.packageCount > 0) subParts.add("📦 ${delivery.packageCount}個")
            if (!delivery.note.isNullOrBlank()) subParts.add("📝 ${delivery.note.take(20)}")
            if (subParts.isNotEmpty()) col.addView(TextView(ctx).apply {
                text = subParts.joinToString("  ")
                textSize = 13f
                setTextColor(onSurfaceVariant)
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
            text = "最初の未完了${"配達先"}にナビを起動する"; textSize = 14f
            setTextColor(onSurfaceVariant)
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


    internal fun MapFragment.showDeliveryOptions(delivery: Delivery) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val slotTemplates    = com.rodgers.haireel.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)

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
        val titleText = delivery.displayTitle
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
                (parentFragment as? DeliveryListFragment)?.switchToListView()
            }
        }
        root.addView(editBtn)

        sheet.setContentView(root)
        sheet.setOnShowListener {
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            sheet.behavior.skipCollapsed = false
            sheet.behavior.isDraggable = true
            sheet.behavior.peekHeight = (360 * dp).toInt()
        }
        sheet.show()
    }


    internal fun MapFragment.showNoteDialogForDelivery(delivery: Delivery) {
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



    internal fun MapFragment.openNavigation(delivery: Delivery) {
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

