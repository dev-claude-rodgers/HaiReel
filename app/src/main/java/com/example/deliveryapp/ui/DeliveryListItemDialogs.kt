package com.rodgers.routist.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.routist.R
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.Room
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.PatternStorage
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.util.TimeSlotColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
internal fun DeliveryListFragment.showItemOptions(delivery: Delivery, showNavComplete: Boolean = true) {
        if (adapter.isSelectMode) return
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
            text = "${delivery.order}. ${delivery.displayTitle}"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
        })
        if (!delivery.name.isNullOrBlank()) {
            headerCol.addView(TextView(ctx).apply {
                text = "📍 ${delivery.address}"
                textSize = 13f; setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt() }
            })
        }
        val statusParts = mutableListOf<String>()
        statusParts.add(if (delivery.isCompleted) "✅ 完了済み" else "⬜ 未完了")
        if (!delivery.timeSlot.isNullOrBlank()) statusParts.add("🕐 ${delivery.timeSlot}")
        if ((delivery.packageCount ?: 0) > 0) statusParts.add("📦 ${delivery.packageCount}個")
        headerCol.addView(TextView(ctx).apply {
            text = statusParts.joinToString("  ")
            textSize = 13f
            setTextColor(if (delivery.isCompleted)
                android.graphics.Color.parseColor("#2E7D32") else onSurfaceVariant)
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

        // ── 行ヘルパー ───────────────────────────────────────────
        val rippleRes = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(rippleRes)
                setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
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

        // ── アクション ───────────────────────────────────────────
        if (showNavComplete) {
            row("🧭", "ナビ開始", "Google マップでルート案内を起動する") { openNavigation(delivery) }
            val toggleEmoji = if (delivery.isCompleted) "↩️" else "✅"
            val toggleTitle = if (delivery.isCompleted) "未完了に戻す" else "完了にする"
            val toggleSub   = if (delivery.isCompleted) "未配達として再度リストに戻す" else "この配達先を完了済みにする"
            row(toggleEmoji, toggleTitle, toggleSub) { viewModel.toggleCompleted(delivery.id) }
            divider()
        }
        row("✏️", "名前・住所を編集", "配達先の名前や住所を変更する") { showEditDialog(delivery) }
        row("🕐", "時間帯・個数を設定",
            if (!delivery.timeSlot.isNullOrBlank()) "現在: ${delivery.timeSlot}${if ((delivery.packageCount ?: 0) > 0) " · ${delivery.packageCount}個" else ""}"
            else "配達時間帯・荷物個数を登録する") { showTimeSlotPackageDialog(delivery) }

        val noteTitle = if (delivery.note.isNullOrBlank()) "メモを追加" else "メモを編集"
        val noteSub   = if (delivery.note.isNullOrBlank()) "受け取り方法・備考などを記録する"
                        else delivery.note!!.take(60).let { if (delivery.note!!.length > 60) "$it…" else it }
        row("📝", noteTitle, noteSub) { showNoteDialog(delivery) }

        val photoCount = delivery.allPhotoUris.size
        val photoTitle = if (photoCount == 0) "写真を追加" else "写真を見る（${photoCount}枚）"
        val photoSub   = if (photoCount == 0) "配達証明・不在票などを撮影して保存する"
                         else "撮影済みの写真を確認・追加する"
        row("📷", photoTitle, photoSub) {
            if (photoCount == 0) showPhotoAddOptions(delivery.id)
            else showPhotosViewer(delivery, 0)
        }

        val roomCount = delivery.roomList.size
        val roomTitle = if (roomCount == 0) "部屋を追加" else "部屋リスト（${roomCount}室）"
        val roomSub   = if (roomCount == 0) "マンションの部屋ごとに訪問状況を管理する"
                        else {
                            val visited = delivery.roomList.count { !it.status.isNullOrBlank() }
                            val apo     = delivery.roomList.count { it.status == "アポ獲得" }
                            "訪問${visited}室${if (apo > 0) " · アポ${apo}件" else ""}"
                        }
        row("🏠", roomTitle, roomSub) { showRoomListDialog(delivery.id) }
        divider()

        row("🗑", "この訪問先を削除", "削除後は元に戻せません",
            ContextCompat.getColor(ctx, R.color.colorActionRed)) {
            AlertDialog.Builder(ctx)
                .setMessage("「${delivery.displayTitle}」を削除しますか？\n削除後は元に戻せません。")
                .setPositiveButton("削除") { _, _ -> viewModel.deleteDelivery(delivery.id) }
                .setNegativeButton("キャンセル", null).show()
        }

        root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = ScrollView(ctx).apply { addView(root) }
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


internal fun DeliveryListFragment.showNoteView(delivery: Delivery) {
        if (delivery.note.isNullOrBlank()) { showNoteDialog(delivery); return }
        AlertDialog.Builder(requireContext())
            .setTitle("メモ")
            .setMessage(delivery.note)
            .setPositiveButton("編集") { _, _ -> showNoteDialog(delivery) }
            .setNegativeButton("閉じる", null)
            .show()
    }

internal fun DeliveryListFragment.showPhotosViewer(delivery: Delivery, startIndex: Int = 0) {
        val photos = delivery.allPhotoUris
        if (photos.isEmpty()) { showPhotoAddOptions(delivery.id); return }
        var currentIndex = startIndex.coerceIn(0, photos.size - 1)
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 0) }
        val imgView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (280 * dp).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true
        }
        val tvCounter = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER; textSize = 13f; setPadding(0, 8, 0, 4)
        }
        val navRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val btnPrev = Button(ctx).apply { text = "◀ 前"; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val btnNext = Button(ctx).apply { text = "次 ▶"; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        navRow.addView(btnPrev); navRow.addView(btnNext)
        root.addView(imgView); root.addView(tvCounter); root.addView(navRow)

        fun reload() {
            tvCounter.text = "${currentIndex + 1} / ${photos.size}"
            btnPrev.isEnabled = currentIndex > 0; btnNext.isEnabled = currentIndex < photos.size - 1
            val path = photos[currentIndex]
            lifecycleScope.launch(Dispatchers.IO) {
                val bmp = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                withContext(Dispatchers.Main) { imgView.setImageBitmap(bmp) }
            }
        }
        btnPrev.setOnClickListener { if (currentIndex > 0) { currentIndex--; reload() } }
        btnNext.setOnClickListener { if (currentIndex < photos.size - 1) { currentIndex++; reload() } }
        reload()

        AlertDialog.Builder(ctx)
            .setTitle("写真 (${photos.size}枚)")
            .setView(root)
            .setPositiveButton("追加") { _, _ -> showPhotoAddOptions(delivery.id) }
            .setNeutralButton("この写真を削除") { _, _ ->
                File(photos[currentIndex]).delete()
                viewModel.removePhoto(delivery.id, currentIndex)
                val updated = viewModel.deliveries.value?.find { it.id == delivery.id }
                if (updated != null && updated.allPhotoUris.isNotEmpty()) {
                    showPhotosViewer(updated, currentIndex.coerceAtMost(updated.allPhotoUris.size - 1))
                }
            }
            .setNegativeButton("閉じる", null)
            .show()
    }

internal fun DeliveryListFragment.showPhotoAddOptions(deliveryId: String) {
        pendingPhotoDeliveryId = deliveryId
        AlertDialog.Builder(requireContext())
            .setTitle("写真を追加")
            .setItems(arrayOf("カメラで撮影", "ギャラリーから選択")) { _, which ->
                when (which) {
                    0 -> if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                            launchCamera(deliveryId)
                         else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    1 -> galleryLauncher.launch("image/*")
                }
            }.show()
    }

internal fun DeliveryListFragment.showNoteDialog(delivery: Delivery) {
        val input = EditText(requireContext()).apply {
            hint = "メモを入力"
            setText(delivery.note ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            setPadding(48, 16, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("メモの編集")
            .setView(input)
            .setPositiveButton("保存") { _, _ -> viewModel.editNote(delivery.id, input.text.toString().trim()) }
            .setNegativeButton("キャンセル", null)
            .also { if (!delivery.note.isNullOrBlank()) it.setNeutralButton("削除") { _, _ -> viewModel.editNote(delivery.id, "") } }
            .show()
    }


internal fun DeliveryListFragment.launchCamera(deliveryId: String) {
        val photoFile = File(requireContext().filesDir,
            "camera_photos/delivery_photo_${deliveryId}_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        pendingPhotoFilePath = photoFile.absolutePath
        pendingPhotoDeliveryId = deliveryId
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
        cameraLauncher.launch(uri)
    }

internal fun DeliveryListFragment.showEditDialog(delivery: Delivery) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 24, 64, 8)
        }
        val nameLabel = TextView(ctx).apply { text = "名前" }
        val nameInput = EditText(ctx).apply {
            hint = "例: ファミリーマート渋谷店"
            setText(delivery.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val addrLabel = TextView(ctx).apply {
            text = "住所"
            setPadding(0, 20, 0, 0)
        }
        val addrInput = EditText(ctx).apply {
            hint = "例: 東京都渋谷区〇〇1-2-3"
            setText(delivery.address)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(nameLabel)
        layout.addView(nameInput)
        layout.addView(addrLabel)
        layout.addView(addrInput)

        AlertDialog.Builder(ctx)
            .setTitle("名前・住所を編集")
            .setView(layout)
            .setPositiveButton("修正して再検索") { _, _ ->
                val newName = nameInput.text.toString().trim()
                val newAddress = addrInput.text.toString().trim()
                if (newAddress.isNotBlank()) viewModel.editDelivery(delivery.id, newName, newAddress)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

internal fun DeliveryListFragment.showTimeSlotPackageDialog(delivery: Delivery) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        // テンプレートクイック選択
        val templates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)
        val timeInput = EditText(ctx).apply {
            hint = "例: 14:00-16:00"
            setText(delivery.timeSlot ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val templateRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val scrollView = HorizontalScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        templates.forEach { tmpl ->
            val btn = Button(ctx).apply {
                text = tmpl.name
                textSize = 12f
                setPadding((10 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
                try { setTextColor(android.graphics.Color.parseColor(tmpl.colorHex)) } catch (_: Exception) {}
                setOnClickListener { timeInput.setText(tmpl.name) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * dp).toInt()
                ).apply { marginEnd = (6 * dp).toInt() }
            }
            chipRow.addView(btn)
        }
        scrollView.addView(chipRow)
        templateRow.addView(scrollView)

        val editBtn = Button(ctx).apply {
            text = "編集"
            textSize = 12f
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            setOnClickListener { showTemplateEditDialog() }
        }
        templateRow.addView(editBtn)

        val timeLabel = TextView(ctx).apply {
            text = "時間帯指定"
            setPadding(0, (8 * dp).toInt(), 0, (2 * dp).toInt())
        }
        val pkgLabel = TextView(ctx).apply {
            text = "個数"
            setPadding(0, (16 * dp).toInt(), 0, (2 * dp).toInt())
        }
        val pkgInput = EditText(ctx).apply {
            hint = "0"
            setText(if (delivery.packageCount > 0) delivery.packageCount.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(templateRow)
        layout.addView(timeLabel)
        layout.addView(timeInput)
        layout.addView(pkgLabel)
        layout.addView(pkgInput)

        AlertDialog.Builder(ctx)
            .setTitle("時間帯・個数の設定")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val slot = timeInput.text.toString().trim().ifBlank { null }
                val pkg  = pkgInput.text.toString().toIntOrNull() ?: 0
                viewModel.updateTimeSlotAndPackage(delivery.id, slot, pkg)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

internal fun DeliveryListFragment.showBatchTimeSlotDialog(ids: Set<String>) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        val templates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplates(ctx)
        val timeInput = EditText(ctx).apply {
            hint = "例: 14:00-16:00　（空白でクリア）"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val scrollView = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        templates.forEach { tmpl ->
            val btn = Button(ctx).apply {
                text = tmpl
                textSize = 12f
                setPadding((10 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
                setOnClickListener { timeInput.setText(tmpl) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * dp).toInt()
                ).apply { marginEnd = (6 * dp).toInt() }
            }
            chipRow.addView(btn)
        }
        scrollView.addView(chipRow)

        val hintText = TextView(ctx).apply {
            text = "時間帯を選択または入力"
            textSize = 12f
            setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, 0, 0, (4 * dp).toInt())
        }

        layout.addView(hintText)
        layout.addView(scrollView)
        layout.addView(timeInput)

        AlertDialog.Builder(ctx)
            .setTitle("${ids.size}件の時間帯を一括設定")
            .setView(layout)
            .setPositiveButton("適用") { _, _ ->
                val slot = timeInput.text.toString().trim().ifBlank { null }
                viewModel.batchUpdateTimeSlot(ids, slot)
                exitSelectMode()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }


internal fun DeliveryListFragment.openNavigation(delivery: Delivery) {
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


    // ────────────────────────────────────────────────────────────

