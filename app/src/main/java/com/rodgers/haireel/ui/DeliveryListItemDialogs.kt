package com.rodgers.haireel.ui

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
import com.rodgers.haireel.R
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.Room
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.PatternStorage
import com.rodgers.haireel.util.hasPermission
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.util.TimeSlotColor
import com.rodgers.haireel.viewmodel.DeliveryViewModel
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
        if (delivery.packageCount > 0) statusParts.add("📦 ${delivery.packageCount}個")
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

        // ── アクション ───────────────────────────────────────────
        if (showNavComplete) {
            row("🧭", "ナビ開始", "Google マップでルート案内を起動する") { openNavigation(delivery) }
            val toggleEmoji = if (delivery.isCompleted) "↩️" else "✅"
            val toggleTitle = if (delivery.isCompleted) "未完了に戻す" else "完了にする"
            val t = "配達先"
            val toggleSub   = if (delivery.isCompleted) "未${"配達"}として再度リストに戻す" else "この${t}を完了済みにする"
            row(toggleEmoji, toggleTitle, toggleSub) { viewModel.toggleCompleted(delivery.id) }
            divider()
        }
        row("✏️", "名前・住所を編集", "${"配達先"}の名前や住所を変更する") { showEditDialog(delivery) }
        row("🕐", "時間帯・個数を設定",
            if (!delivery.timeSlot.isNullOrBlank()) "現在: ${delivery.timeSlot}${if (delivery.packageCount > 0) " · ${delivery.packageCount}個" else ""}"
            else "配達時間帯・荷物個数を登録する") { showTimeSlotPackageDialog(delivery) }

        val noteTitle = if (delivery.note.isNullOrBlank()) "メモを追加" else "メモを編集"
        val noteSub   = if (delivery.note.isNullOrBlank()) "受け取り方法・備考などを記録する"
                        else delivery.note.take(60).let { if (delivery.note.length > 60) "$it…" else it }
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
                val deleteIdx = currentIndex.coerceIn(0, photos.size - 1)
                File(photos[deleteIdx]).delete()
                viewModel.removePhoto(delivery.id, deleteIdx)
                val updated = viewModel.deliveries.value.find { it.id == delivery.id }
                if (updated != null && updated.allPhotoUris.isNotEmpty()) {
                    val nextIdx = (deleteIdx - 1).coerceAtLeast(0)
                        .coerceAtMost(updated.allPhotoUris.size - 1)
                    showPhotosViewer(updated, nextIdx)
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
                    0 -> if (requireContext().hasPermission(Manifest.permission.CAMERA))
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
        val ctx  = requireContext()
        val dp   = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val colorSurfaceVariant   = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        val scroll = ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }
        scroll.addView(layout)

        fun label(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 13f; setTextColor(colorOnSurfaceVariant)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (2 * dp).toInt() }
        }

        val nameInput = EditText(ctx).apply {
            hint = "例: 〇〇店"
            setText(delivery.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val kanaInput = EditText(ctx).apply {
            hint = "任意・音声案内で使用"
            setText(delivery.nameKana ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 13f
            setTextColor(colorOnSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        // 候補リスト（名前入力に連動）
        val candidateBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(colorSurfaceVariant)
                setStroke((1 * dp).toInt(), colorOnSurfaceVariant.let {
                    android.graphics.Color.argb(60,
                        android.graphics.Color.red(it),
                        android.graphics.Color.green(it),
                        android.graphics.Color.blue(it))
                })
                cornerRadius = 6 * dp
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (4 * dp).toInt() }
        }

        val addrInput = EditText(ctx).apply {
            hint = "例: 〇〇県〇〇市１ー１ー１"
            // 店名として取り込まれた場合（name == address）は住所欄を空欄にする
            val displayAddr = if (!delivery.name.isNullOrBlank() && delivery.address == delivery.name) ""
                              else delivery.address
            setText(displayAddr)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        var watcher: android.text.TextWatcher? = null
        fun fillFromCandidate(name: String, address: String) {
            val w = watcher ?: return
            nameInput.removeTextChangedListener(w)
            nameInput.setText(name)
            nameInput.setSelection(name.length)
            nameInput.addTextChangedListener(w)
            addrInput.setText(address)
            candidateBox.visibility = View.GONE
        }

        fun rebuildCandidates(query: String) {
            val found = viewModel.searchDeliveriesByName(query, excludeId = delivery.id)
            if (found.isEmpty()) { candidateBox.visibility = View.GONE; return }
            candidateBox.removeAllViews()
            candidateBox.addView(TextView(ctx).apply {
                text = "候補（タップで選択）"
                textSize = 11f; setTextColor(colorOnSurfaceVariant)
                setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
            })
            found.forEach { cand ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(ripple)
                    setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                }
                row.addView(TextView(ctx).apply {
                    text = cand.name; textSize = 14f; setTextColor(colorOnSurface)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
                row.addView(TextView(ctx).apply {
                    text = cand.displayAddress; textSize = 12f
                    setTextColor(colorOnSurfaceVariant); maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                        .also { it.topMargin = (2 * dp).toInt() }
                })
                row.setOnClickListener {
                    fillFromCandidate(cand.name ?: "", cand.displayAddress)
                }
                candidateBox.addView(row)
                candidateBox.addView(View(ctx).apply {
                    setBackgroundColor(android.graphics.Color.argb(40,
                        android.graphics.Color.red(colorOnSurfaceVariant),
                        android.graphics.Color.green(colorOnSurfaceVariant),
                        android.graphics.Color.blue(colorOnSurfaceVariant)))
                    layoutParams = LinearLayout.LayoutParams(MATCH, (1 * dp).toInt())
                        .also { it.marginStart = (10 * dp).toInt() }
                })
            }
            candidateBox.visibility = View.VISIBLE
        }

        watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                rebuildCandidates(s?.toString()?.trim() ?: "")
            }
        }
        nameInput.addTextChangedListener(watcher)

        layout.addView(label("名前・店名"))
        layout.addView(nameInput)
        layout.addView(candidateBox)
        layout.addView(label("ふりがな（音声案内用・任意）"))
        layout.addView(kanaInput)
        layout.addView(label("住所"))
        layout.addView(addrInput)

        // ── 住所バリデーション バナー ───────────────────────────────
        val warnBanner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding((10*dp).toInt(), (8*dp).toInt(), (10*dp).toInt(), (8*dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FFF8E1"))
                cornerRadius = 6*dp
                setStroke((1*dp).toInt(), android.graphics.Color.parseColor("#FFB300"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (6*dp).toInt() }
        }
        val tvWarn = TextView(ctx).apply {
            textSize = 12f; setTextColor(android.graphics.Color.parseColor("#E65100"))
        }
        warnBanner.addView(tvWarn)

        // 「電話番号を削除」「郵便番号→住所に変換」ボタン行
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (4*dp).toInt() }
        }
        val btnClean = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (8*dp).toInt() }
            visibility = View.GONE
        }
        val btnZip = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        btnRow.addView(btnClean); btnRow.addView(btnZip)
        warnBanner.addView(btnRow)
        layout.addView(warnBanner)

        // リアルタイムバリデーション
        fun validateAddress(text: String) {
            if (text.isBlank()) { warnBanner.visibility = View.GONE; return }
            val result = com.rodgers.haireel.util.AddressValidator.validate(text)
            if (!result.hasIssue) { warnBanner.visibility = View.GONE; return }

            val msgs = mutableListOf<String>()
            if (com.rodgers.haireel.util.AddressValidator.Issue.POSTAL_CODE in result.issues)
                msgs.add("🟡 郵便番号が含まれています")
            if (com.rodgers.haireel.util.AddressValidator.Issue.PHONE_NUMBER in result.issues)
                msgs.add("🟡 電話番号が含まれています")
            if (com.rodgers.haireel.util.AddressValidator.Issue.NO_ADDRESS_KEYWORD in result.issues)
                msgs.add("🔴 住所（市・区・町など）が見つかりません")
            tvWarn.text = msgs.joinToString("\n")
            warnBanner.visibility = View.VISIBLE

            // 「不要な情報を削除」ボタン
            val hasRemovable = com.rodgers.haireel.util.AddressValidator.Issue.POSTAL_CODE in result.issues ||
                               com.rodgers.haireel.util.AddressValidator.Issue.PHONE_NUMBER in result.issues
            if (hasRemovable) {
                btnClean.text = "🗑 電話番号・郵便番号を削除"
                btnClean.visibility = View.VISIBLE
                btnClean.setOnClickListener {
                    addrInput.setText(result.cleaned)
                    addrInput.setSelection(result.cleaned.length)
                }
            } else {
                btnClean.visibility = View.GONE
            }

            // 郵便番号→住所変換ボタン
            if (result.extractedPostalCode != null) {
                btnZip.text = "📮 郵便番号で住所を補完"
                btnZip.visibility = View.VISIBLE
                btnZip.setOnClickListener {
                    btnZip.isEnabled = false
                    btnZip.text = "変換中..."
                    lifecycleScope.launch {
                        val zip = com.rodgers.haireel.util.AddressValidator.toHyphenFormat(
                            result.extractedPostalCode
                        )
                        val addr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.rodgers.haireel.util.ZipCodeHelper.lookup(zip)
                        }
                        if (addr != null) {
                            val cleaned = result.cleaned.ifBlank { "" }
                            addrInput.setText(
                                if (cleaned.isNotBlank()) "${addr.address} $cleaned"
                                else addr.address
                            )
                            addrInput.setSelection(addrInput.text.length)
                        } else {
                            Toast.makeText(ctx, "郵便番号から住所が見つかりませんでした", Toast.LENGTH_SHORT).show()
                        }
                        btnZip.isEnabled = true
                        btnZip.text = "📮 郵便番号で住所を補完"
                    }
                }
            } else {
                btnZip.visibility = View.GONE
            }
        }

        addrInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                validateAddress(s?.toString() ?: "")
            }
        })
        // 初期値を検証
        validateAddress(addrInput.text.toString())

        val dlg = AlertDialog.Builder(ctx)
            .setTitle("名前・住所を編集")
            .setView(scroll)
            .setPositiveButton("修正して再検索", null)
            .setNeutralButton("そのまま保存", null)
            .setNegativeButton("キャンセル", null)
            .show()

        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val newName    = nameInput.text.toString().trim()
            val newKana    = kanaInput.text.toString().trim().ifBlank { null }
            val newAddress = addrInput.text.toString().trim()
            if (newAddress.isBlank() && newName.isBlank()) return@setOnClickListener
            viewModel.updateNameAndAddressOnly(delivery.id, newName, newAddress, newKana)
            dlg.dismiss()
        }

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName    = nameInput.text.toString().trim()
            val newKana    = kanaInput.text.toString().trim().ifBlank { null }
            val newAddress = addrInput.text.toString().trim()
            // 住所も店名も両方空なら何もしない
            if (newAddress.isBlank() && newName.isBlank()) return@setOnClickListener
            // ふりがなだけ即保存（住所は再ジオコーディングが必要）
            if (newKana != delivery.nameKana) {
                viewModel.updateNameKana(delivery.id, newKana)
            }
            dlg.dismiss()

            lifecycleScope.launch {
                // 住所があればジオコーディング候補を取得、なければ空
                val geoCandidates = if (newAddress.isNotBlank()) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.rodgers.haireel.util.GeocodingClient.geocodeCandidates(newAddress)
                    }
                } else emptyList()
                // 店名があればPlaces候補を取得
                val placeCandidates = if (newName.isNotBlank()) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.rodgers.haireel.util.GeocodingClient.searchPlaces(newName)
                    }
                } else emptyList()

                // 住所・lat・lng の三つ組リスト（表示ラベル / 保存住所 / 座標）
                val items = mutableListOf<Triple<String, Double, Double>>()
                // 店名候補を先頭に（住所より関連度が高い）
                placeCandidates.forEach { p ->
                    items.add(Triple(p.address, p.lat, p.lng))
                }
                // 住所候補を追加（座標が重複するものは除外）
                geoCandidates.forEach { r ->
                    val dup = items.any {
                        Math.abs(it.second - r.lat) < 0.001 && Math.abs(it.third - r.lng) < 0.001
                    }
                    if (!dup) items.add(Triple(r.formattedAddress, r.lat, r.lng))
                }

                if (items.isEmpty()) {
                    if (newAddress.isBlank()) {
                        android.widget.Toast.makeText(ctx,
                            "「$newName」の場所が見つかりませんでした。住所も入力してみてください。",
                            android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        // 候補なし → 通常のジオコーディングにフォールバック
                        viewModel.editDelivery(delivery.id, newName, newAddress)
                    }
                    return@launch
                }

                val labels = items.map { it.first }.toTypedArray()
                var selectedIdx = 0
                AlertDialog.Builder(ctx)
                    .setTitle(if (newName.isNotBlank()) "「$newName」の場所を選んでください" else "場所を選んでください")
                    .setSingleChoiceItems(labels, 0) { _, which -> selectedIdx = which }
                    .setPositiveButton("この場所を使う") { _, _ ->
                        val sel = items[selectedIdx]
                        viewModel.applyCandidate(delivery.id, newName, sel.first, sel.second, sel.third)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }
    }

internal fun DeliveryListFragment.showTimeSlotPackageDialog(delivery: Delivery) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        // テンプレートクイック選択
        val templates = com.rodgers.haireel.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)
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

        val templates = com.rodgers.haireel.util.AppSettings.getTimeSlotTemplates(ctx)
        val timeInput = EditText(ctx).apply {
            hint = "例: 14:00-16:00"
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

