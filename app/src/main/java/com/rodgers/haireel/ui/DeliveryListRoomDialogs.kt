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
internal fun DeliveryListFragment.showRoomListDialog(deliveryId: String) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val delivery = viewModel.deliveries.value.find { it.id == deliveryId } ?: return

        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
        }
        scroll.addView(container)

        var dialog: AlertDialog? = null

        // 自動生成ボタン（常に表示）
        container.addView(android.widget.Button(ctx).apply {
            text = "部屋番号を自動生成（階数・部屋数を入力）"
            isAllCaps = false
            setTextColor(android.graphics.Color.parseColor("#0D47A1"))
            setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10 * dp).toInt() }
            setOnClickListener {
                dialog?.dismiss()
                showRoomGenerationDialog(deliveryId, delivery)
            }
        })

        if (delivery.roomList.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "部屋はまだ登録されていません"
                textSize = 13f
                setTextColor(android.graphics.Color.GRAY)
                setPadding(0, (16 * dp).toInt(), 0, (12 * dp).toInt())
            })
        }

        // 階ごとにグループ化（末尾2桁=部屋番号、それ以前=階数）
        val floorGroups = delivery.roomList
            .groupBy { room ->
                val d = room.number.filter { it.isDigit() }
                if (d.length >= 3) d.dropLast(2).toIntOrNull() else null
            }
            .entries.sortedWith(compareBy { it.key ?: Int.MAX_VALUE })
        val showHeaders = floorGroups.size > 1

        fun addRoomCard(room: Room) {
            val hasNote = !room.note.isNullOrBlank()

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 6 * dp
                    setColor(0x08000000)
                    setStroke(1, 0x20000000)
                }
                setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (6 * dp).toInt() }
            }

            // 部屋番号
            card.addView(TextView(ctx).apply {
                text = room.number
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (10 * dp).toInt() }
            })

            // メモ表示（入力済み：薄い青背景＋ネイビー文字　未入力：プレースホルダー）
            card.addView(TextView(ctx).apply {
                text = if (hasNote) room.note else "メモを入力..."
                textSize = 13f
                if (hasNote) {
                    setTextColor(android.graphics.Color.parseColor("#1A237E"))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#E8EAF6"))
                        cornerRadius = 4 * dp
                    }
                    setPadding((6 * dp).toInt(), (3 * dp).toInt(), (6 * dp).toInt(), (3 * dp).toInt())
                } else {
                    setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    background = null
                    setPadding(0, 0, 0, 0)
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // 編集アイコン
            card.addView(TextView(ctx).apply {
                text = "✏️"
                textSize = 14f
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            })

            // 削除ボタン
            card.addView(TextView(ctx).apply {
                text = "✕"
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#B71C1C"))
                gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#FFEBEE"))
                    cornerRadius = 4 * dp
                }
                setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = (4 * dp).toInt() }
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setMessage("「${room.number}」を削除します。この操作は取り消せません。")
                        .setPositiveButton("削除") { _, _ ->
                            viewModel.deleteRoom(deliveryId, room.id)
                            dialog?.dismiss()
                            showRoomListDialog(deliveryId)
                        }
                        .setNegativeButton("キャンセル", null).show()
                }
            })

            // カードをタップ → メモ編集ダイアログ
            card.setOnClickListener {
                val input = EditText(ctx).apply {
                    setText(room.note)
                    hint = "不在・担当者名・折り返し希望など"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    maxLines = 4
                    setPadding((48 * dp).toInt(), (16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt())
                }
                AlertDialog.Builder(ctx)
                    .setTitle(room.number)
                    .setView(input)
                    .setPositiveButton("保存") { _, _ ->
                        viewModel.updateRoom(deliveryId, room.id, input.text.toString().trim(), room.isCompleted)
                        Toast.makeText(ctx, "メモを保存しました", Toast.LENGTH_SHORT).show()
                        dialog?.dismiss()
                        showRoomListDialog(deliveryId)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }

            // 長押し → メモクリア
            card.setOnLongClickListener {
                AlertDialog.Builder(ctx)
                    .setTitle(room.number)
                    .setMessage("メモをクリアしますか？")
                    .setPositiveButton("クリア") { _, _ ->
                        viewModel.updateRoom(deliveryId, room.id, "", room.isCompleted)
                        dialog?.dismiss()
                        showRoomListDialog(deliveryId)
                    }
                    .setNegativeButton("キャンセル", null).show()
                true
            }

            container.addView(card)
        }

        floorGroups.forEach { (floor, floorRooms) ->
            if (showHeaders) {
                container.addView(TextView(ctx).apply {
                    text = if (floor != null) "${floor}階" else "その他"
                    textSize = 12f
                    setTextColor(android.graphics.Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#607D8B"))
                        cornerRadius = 4 * dp
                    }
                    setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (10 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
                })
            }
            floorRooms.forEach { room -> addRoomCard(room) }
        }

        // 部屋追加ボタン
        container.addView(android.widget.Button(ctx).apply {
            text = "＋ 部屋を追加"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * dp).toInt() }
            setOnClickListener {
                val input = EditText(ctx).apply {
                    hint = "例：101号室　202　B1-305"
                    inputType = InputType.TYPE_CLASS_TEXT
                    setPadding((48 * dp).toInt(), (16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt())
                }
                val addDlg = AlertDialog.Builder(ctx)
                    .setTitle("部屋を追加")
                    .setView(input)
                    .setPositiveButton("追加", null)
                    .setNegativeButton("キャンセル", null)
                    .show()
                addDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val num = input.text.toString().trim()
                    if (num.isBlank()) {
                        input.error = "部屋番号を入力してください"
                    } else {
                        viewModel.addRoom(deliveryId, num)
                        addDlg.dismiss()
                        dialog?.dismiss()
                        showRoomListDialog(deliveryId)
                    }
                }
            }
        })

        val total = delivery.roomList.size
        val statLine = if (total > 0) "  ${total}室" else ""

        dialog = AlertDialog.Builder(ctx)
            .setTitle("${delivery.order}. ${delivery.displayTitle}$statLine")
            .setView(scroll)
            .setNegativeButton("閉じる", null)
            .show()
    }

internal fun DeliveryListFragment.showRoomGenerationDialog(deliveryId: String, delivery: Delivery) {
        if (delivery.roomList.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setMessage("すでに${delivery.roomList.size}室が登録されています。\n上書きして自動生成しますか？")
                .setPositiveButton("上書き") { _, _ -> openRoomGenerationForm(deliveryId) }
                .setNegativeButton("キャンセル", null)
                .show()
        } else {
            openRoomGenerationForm(deliveryId)
        }
    }

internal fun DeliveryListFragment.openRoomGenerationForm(deliveryId: String) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(root)

        fun sectionLabel(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 13f
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                it.topMargin = (10 * dp).toInt(); it.bottomMargin = (3 * dp).toInt()
            }
        }
        fun blueBorder() = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#F0F4FF"))
            setStroke((2 * dp).toInt(), android.graphics.Color.parseColor("#3F51B5"))
            cornerRadius = 6 * dp
        }
        fun numInput(default: String, large: Boolean = false) = EditText(ctx).apply {
            setText(default)
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = if (large) 24f else 16f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        fun radioBtn(label: String) = android.widget.RadioButton(ctx).apply {
            text = label; textSize = 13f; id = android.view.View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginEnd = (6 * dp).toInt() }
        }

        // === 形式選択 ===
        root.addView(sectionLabel("部屋番号の形式"))
        val rgFormat = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val rb101    = radioBtn("101形式　　（101号室, 102号室, 201号室…）")
        val rbSeq    = radioBtn("1号室形式　（1号室, 2号室, 3号室…）")
        val rbPrefix = radioBtn("記号形式　　（B101, B102, A201…）")
        rgFormat.addView(rb101); rgFormat.addView(rbSeq); rgFormat.addView(rbPrefix)
        rb101.isChecked = true
        root.addView(rgFormat)

        // === 記号入力 ===
        val prefixSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        prefixSection.addView(sectionLabel("記号を入力（例: A, B, 東, 西）"))
        val prefixInput = EditText(ctx).apply {
            setText("A")
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        prefixSection.addView(prefixInput)
        // 開始番号と部屋数の横並び入力
        prefixSection.addView(sectionLabel("開始番号　　　　　　　部屋数"))
        val pfxRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val pfxStartInput = EditText(ctx).apply {
            setText("101")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val pfxCountInput = EditText(ctx).apply {
            setText("5")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also { it.marginStart = (8 * dp).toInt() }
        }
        pfxRow.addView(pfxStartInput)
        pfxRow.addView(pfxCountInput)
        prefixSection.addView(pfxRow)
        root.addView(prefixSection)

        // === 階数入力 ===
        val floorSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        floorSection.addView(sectionLabel("何階建てですか？"))
        val floorInput = numInput("3", large = true)
        floorSection.addView(floorInput)
        root.addView(floorSection)

        // === 部屋数モード ===
        val roomModeSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        roomModeSection.addView(sectionLabel("各階の部屋数"))
        val rgRoomMode = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val rbCommon   = radioBtn("全階共通")
        val rbPerFloor = radioBtn("階ごとに指定")
        rgRoomMode.addView(rbCommon); rgRoomMode.addView(rbPerFloor)
        rbCommon.isChecked = true
        roomModeSection.addView(rgRoomMode)
        root.addView(roomModeSection)

        // === 共通部屋数入力 ===
        val commonSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val commonInput = numInput("4", large = true)
        commonSection.addView(commonInput)
        root.addView(commonSection)

        // === 階ごと部屋数入力 ===
        val perFloorSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(perFloorSection)
        val perFloorInputs = mutableListOf<EditText>()

        // === 通し番号用: 総部屋数 ===
        val totalSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        totalSection.addView(sectionLabel("総部屋数"))
        val totalInput = numInput("10", large = true)
        totalSection.addView(totalInput)
        root.addView(totalSection)

        // === プレビュー ===
        val tvPreview = TextView(ctx).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#E65100"))
            setPadding(0, (12 * dp).toInt(), 0, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(tvPreview)

        // --- ロジック ---
        fun buildRoomNumbers(): List<String>? {
            if (rbSeq.isChecked) {
                val total = totalInput.text.toString().toIntOrNull()?.coerceIn(1, 999) ?: return null
                return (1..total).map { "${it}号室" }
            }
            if (rbPrefix.isChecked) {
                val prefix = prefixInput.text.toString().trim()
                val start  = pfxStartInput.text.toString().toIntOrNull()?.coerceIn(1, 9999) ?: return null
                val count  = pfxCountInput.text.toString().toIntOrNull()?.coerceIn(1, 999)  ?: return null
                return (0 until count).map { "${prefix}${start + it}" }
            }
            val f = floorInput.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: return null
            return if (rbPerFloor.isChecked) {
                if (perFloorInputs.size < f) return null
                (1..f).flatMap { floor ->
                    val u = perFloorInputs[floor - 1].text.toString().toIntOrNull()?.coerceIn(1, 99) ?: return null
                    (1..u).map { unit -> "${floor}${unit.toString().padStart(2, '0')}号室" }
                }
            } else {
                val u = commonInput.text.toString().toIntOrNull()?.coerceIn(1, 99) ?: return null
                (1..f).flatMap { floor ->
                    (1..u).map { unit -> "${floor}${unit.toString().padStart(2, '0')}号室" }
                }
            }
        }

        fun updatePreview() {
            val rooms = buildRoomNumbers() ?: run { tvPreview.text = ""; return }
            tvPreview.text = rooms.take(6).joinToString("  ") + "…\n計 ${rooms.size} 部屋を生成します"
        }

        fun buildPerFloorRows() {
            perFloorSection.removeAllViews()
            perFloorInputs.clear()
            val f = floorInput.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: return
            for (floor in 1..f) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (4 * dp).toInt() }
                }
                row.addView(TextView(ctx).apply {
                    text = "${floor}階："
                    textSize = 15f
                    setTextColor(android.graphics.Color.parseColor("#1A237E"))
                    minWidth = (52 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                })
                val unitInput = EditText(ctx).apply {
                    setText("4")
                    inputType = InputType.TYPE_CLASS_NUMBER
                    textSize = 18f
                    setTextColor(android.graphics.Color.parseColor("#1A237E"))
                    background = blueBorder()
                    setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                }
                row.addView(unitInput)
                row.addView(TextView(ctx).apply {
                    text = "部屋"; textSize = 14f
                    setPadding((8 * dp).toInt(), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                })
                perFloorSection.addView(row)
                perFloorInputs.add(unitInput)
                unitInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) { updatePreview() }
                })
            }
        }

        fun updateVisibility() {
            val isSeq = rbSeq.isChecked
            val isPfx = rbPrefix.isChecked
            val isPer = rbPerFloor.isChecked
            prefixSection.visibility   = if (isPfx) android.view.View.VISIBLE else android.view.View.GONE
            floorSection.visibility    = if (isSeq || isPfx) android.view.View.GONE else android.view.View.VISIBLE
            roomModeSection.visibility = if (isSeq || isPfx) android.view.View.GONE else android.view.View.VISIBLE
            commonSection.visibility   = if (isSeq || isPfx || isPer) android.view.View.GONE else android.view.View.VISIBLE
            totalSection.visibility    = if (isSeq) android.view.View.VISIBLE else android.view.View.GONE
            if (!isSeq && !isPfx && isPer) {
                perFloorSection.visibility = android.view.View.VISIBLE
                buildPerFloorRows()
            } else {
                perFloorSection.visibility = android.view.View.GONE
            }
            updatePreview()
        }

        val basicWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updatePreview() }
        }
        floorInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (rbPerFloor.isChecked) buildPerFloorRows()
                updatePreview()
            }
        })
        commonInput.addTextChangedListener(basicWatcher)
        totalInput.addTextChangedListener(basicWatcher)
        prefixInput.addTextChangedListener(basicWatcher)
        pfxStartInput.addTextChangedListener(basicWatcher)
        pfxCountInput.addTextChangedListener(basicWatcher)
        rgFormat.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        rgRoomMode.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updatePreview()

        AlertDialog.Builder(ctx)
            .setTitle("部屋番号を自動生成")
            .setView(scroll)
            .setPositiveButton("生成する") { _, _ ->
                val rooms = buildRoomNumbers() ?: return@setPositiveButton
                if (rooms.isEmpty()) return@setPositiveButton
                viewModel.generateRooms(deliveryId, rooms)
                android.widget.Toast.makeText(ctx, "${rooms.size}室を生成しました", android.widget.Toast.LENGTH_SHORT).show()
                showRoomListDialog(deliveryId)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

internal fun DeliveryListFragment.showRoomNoteEditDialog(deliveryId: String, room: Room, onSaved: () -> Unit) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val input = EditText(ctx).apply {
            hint = "例：不在・興味あり・次回訪問"
            setText(room.note)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            setPadding((48 * dp).toInt(), (16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt())
        }
        AlertDialog.Builder(ctx)
            .setTitle("${room.number}　メモを編集")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                viewModel.updateRoom(deliveryId, room.id, input.text.toString().trim(), room.isCompleted)
                Toast.makeText(ctx, "メモを保存しました", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

