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
internal fun DeliveryListFragment.showTemplateEditDialog() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val AppSettings = com.rodgers.routist.util.AppSettings
        val templates = AppSettings.getTimeSlotTemplatesWithColor(ctx).toMutableList()
        var selectedNewHex = TimeSlotColor.PALETTE.first()

        fun makeDotDrawable(hexColor: String) = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(try { android.graphics.Color.parseColor(hexColor) } catch (_: Exception) { android.graphics.Color.GRAY })
        }

        // 2行×6列のカラーパレット
        fun paletteGrid(currentHex: String, onPick: (String) -> Unit): LinearLayout {
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }
            val palette = TimeSlotColor.PALETTE
            val dotViews = mutableListOf<android.view.View>()
            for (r in 0..1) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0..5) {
                    val idx = r * 6 + c
                    val hex = palette[idx]
                    val sz = (28 * dp).toInt()
                    val dot = android.view.View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(sz, sz)
                            .also { it.marginEnd = (6 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
                        background = makeDotDrawable(hex).also {
                            if (hex.equals(currentHex, ignoreCase = true))
                                it.setStroke((3 * dp).toInt(), android.graphics.Color.BLACK)
                        }
                        setOnClickListener {
                            dotViews.forEachIndexed { j, v ->
                                (v.background as? android.graphics.drawable.GradientDrawable)
                                    ?.setStroke(if (j == idx) (3 * dp).toInt() else 0, android.graphics.Color.BLACK)
                                v.invalidate()
                            }
                            onPick(hex)
                        }
                    }
                    dotViews.add(dot)
                    row.addView(dot)
                }
                container.addView(row)
            }
            return container
        }

        // テンプレート一覧 RecyclerView
        lateinit var rvAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        rvAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = templates.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val handle = android.widget.ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_drag_handle)
                    alpha = 0.35f
                    layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                        .also { it.marginEnd = (10 * dp).toInt() }
                }
                val dot = android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                        .also { it.marginEnd = (10 * dp).toInt() }
                    background = makeDotDrawable("#888888")
                }
                val label = TextView(ctx).apply {
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val del = android.widget.ImageButton(ctx).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    background = null
                    layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                }
                row.addView(handle); row.addView(dot); row.addView(label); row.addView(del)
                return object : RecyclerView.ViewHolder(row) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tmpl = templates[position]
                val row = holder.itemView as LinearLayout
                val dot   = row.getChildAt(1)
                val label = row.getChildAt(2) as TextView
                val del   = row.getChildAt(3) as android.widget.ImageButton

                (dot.background as? android.graphics.drawable.GradientDrawable)
                    ?.setColor(try { android.graphics.Color.parseColor(tmpl.colorHex) } catch (_: Exception) { android.graphics.Color.GRAY })
                dot.invalidate()
                label.text = tmpl.name
                try { label.setTextColor(android.graphics.Color.parseColor(tmpl.colorHex)) } catch (_: Exception) {}

                dot.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos < 0) return@setOnClickListener
                    val cur = templates[pos]
                    var colorDlg: AlertDialog? = null
                    val inner = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                    }
                    inner.addView(paletteGrid(cur.colorHex) { newHex ->
                        templates[pos] = cur.copy(colorHex = newHex)
                        AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                        rvAdapter.notifyItemChanged(pos)
                        colorDlg?.dismiss()
                    })
                    colorDlg = AlertDialog.Builder(ctx)
                        .setTitle("「${cur.name}」の色")
                        .setView(inner)
                        .setNegativeButton("キャンセル", null)
                        .show()
                }

                del.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos < 0) return@setOnClickListener
                    templates.removeAt(pos)
                    AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                    rvAdapter.notifyItemRemoved(pos)
                }
            }
        }

        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = rvAdapter
            isNestedScrollingEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: androidx.recyclerview.widget.RecyclerView, vh: RecyclerView.ViewHolder) =
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition; val to = target.adapterPosition
                templates.add(to, templates.removeAt(from))
                rvAdapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, d: Int) {}
            override fun isLongPressDragEnabled() = true
            override fun clearView(rv: androidx.recyclerview.widget.RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
            }
        }).attachToRecyclerView(rv)

        // 新規追加エリア
        val previewDot = android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                .also { it.marginEnd = (8 * dp).toInt() }
            background = makeDotDrawable(selectedNewHex)
        }
        val addInput = EditText(ctx).apply {
            hint = "テンプレート名"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(previewDot); addView(addInput)
        }

        val colorPickerWrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            addView(paletteGrid(selectedNewHex) { hex ->
                selectedNewHex = hex
                (previewDot.background as? android.graphics.drawable.GradientDrawable)
                    ?.setColor(try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { android.graphics.Color.GRAY })
                previewDot.invalidate()
            })
        }

        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(rv); addView(addRow); addView(colorPickerWrapper)
        }

        val tmplDlg = AlertDialog.Builder(ctx)
            .setTitle("テンプレートを編集")
            .setView(outer)
            .setPositiveButton("追加", null)
            .setNegativeButton("閉じる", null)
            .show()
        tmplDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = addInput.text.toString().trim()
            if (newName.isBlank()) {
                addInput.error = "テンプレート名を入力してください"
            } else {
                templates.add(com.rodgers.routist.util.AppSettings.TimeSlotTemplate(newName, selectedNewHex))
                AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                rvAdapter.notifyItemInserted(templates.size - 1)
                addInput.setText("")
                Toast.makeText(ctx, "「$newName」を追加しました", Toast.LENGTH_SHORT).show()
            }
        }
    }


internal fun DeliveryListFragment.showOutOfAreaCandidatesDialog(items: List<DeliveryViewModel.OutOfAreaItem>) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val primaryColor     = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)

        val selectedResults = mutableMapOf<String, com.rodgers.routist.util.GeocodingClient.GeoResult>()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(ctx).apply {
            text = "🔧 エリア外住所の修正（${items.size}件）"
            textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD
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

        // 各エリア外アイテム
        val bodyLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt())
        }

        items.forEachIndexed { index, item ->
            val delivery = item.delivery

            if (index > 0) bodyLayout.addView(android.view.View(ctx).apply {
                setBackgroundColor(outlineVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                    .also { it.setMargins(0, (12 * dp).toInt(), 0, (12 * dp).toInt()) }
            })

            // 名前
            bodyLayout.addView(TextView(ctx).apply {
                text = if (!delivery.name.isNullOrBlank()) delivery.name else "（名前なし）"
                textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(onSurfaceColor)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (8 * dp).toInt() }
            })
            // 現在住所
            bodyLayout.addView(TextView(ctx).apply {
                text = "現在: ${delivery.address}"; textSize = 13f
                setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (8 * dp).toInt() }
            })

            if (item.candidates.isEmpty()) {
                bodyLayout.addView(TextView(ctx).apply {
                    text = "候補が見つかりませんでした"; textSize = 14f
                    setTextColor(onSurfaceVariant)
                })
            } else {
                val radioGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
                item.candidates.forEachIndexed { idx, candidate ->
                    val rb = android.widget.RadioButton(ctx)
                    rb.id = View.generateViewId()
                    rb.text = candidate.formattedAddress; rb.textSize = 14f
                    rb.setTextColor(onSurfaceColor)
                    rb.isChecked = idx == 0
                    radioGroup.addView(rb)
                    if (idx == 0) selectedResults[delivery.id] = candidate
                    rb.setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedResults[delivery.id] = candidate
                    }
                }
                bodyLayout.addView(radioGroup)
            }
        }

        val scroll = ScrollView(ctx).apply {
            addView(bodyLayout)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(scroll)

        // 保存ボタン
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })
        val saveBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "保存"; textSize = 16f
            setBackgroundColor(primaryColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * dp).toInt())
                .also { it.setMargins((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt()) }
            setOnClickListener {
                selectedResults.forEach { (deliveryId, result) ->
                    viewModel.applyOutOfAreaFix(deliveryId, result)
                }
                Toast.makeText(ctx, "${selectedResults.size}件を修正しました", Toast.LENGTH_SHORT).show()
                sheet.dismiss()
            }
        }
        root.addView(saveBtn)

        sheet.setContentView(root)
        sheet.show()
    }

internal fun DeliveryListFragment.showAreaSettingDialog() {
        val ctx = requireContext()
        val edit = EditText(ctx).apply {
            setText(viewModel.areaHint.value ?: "")
            hint = "例：横浜市, 静岡市（漢字で入力）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(64, 24, 64, 24)
        }
        AlertDialog.Builder(ctx)
            .setTitle("配達地域")
            .setMessage("都道府県・市区町村を設定すると、住所の検索精度が上がります。複数のエリアはカンマ区切りで入力できます。")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val area = edit.text.toString().trim()
                viewModel.setAreaHint(area)
                if (area.isNotBlank()) showAddressCheckResult(area)
            }
            .setNeutralButton("クリア") { _, _ ->
                viewModel.setAreaHint("")
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

internal fun DeliveryListFragment.areaMatches(address: String, area: String): Boolean {
        val areas = area.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
        return areas.any { single ->
            if (address.contains(single)) return@any true
            // 都道府県プレフィックスを除いた市区町村以降で再確認
            val cityPart = single.replace(Regex("^.+[都道府県]"), "")
            cityPart.length >= 2 && address.contains(cityPart)
        }
    }

internal fun DeliveryListFragment.showAddressCheckResult(area: String) {
        val deliveries = viewModel.deliveries.value ?: return
        if (deliveries.isEmpty()) return
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val notGeocoded = deliveries.filter { !it.isGeocoded }
        val mismatched  = deliveries.filter { it.isGeocoded && !areaMatches(it.address, area) }

        if (mismatched.isEmpty() && notGeocoded.isEmpty()) {
            Toast.makeText(ctx, "✅ 全 ${deliveries.size} 件が「$area」内の住所です", Toast.LENGTH_SHORT).show()
            return
        }

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val warnColor        = ContextCompat.getColor(ctx, R.color.colorActionRed)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー
        root.addView(TextView(ctx).apply {
            text = "住所精査結果"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
        })
        root.addView(View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        fun sectionLabel(text: String, color: Int) = root.addView(TextView(ctx).apply {
            this.text = text; textSize = 15f; setTextColor(color)
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt())
        })

        fun itemRow(delivery: Delivery) = root.addView(TextView(ctx).apply {
            val label = buildString {
                append("${delivery.order}. ")
                if (!delivery.name.isNullOrBlank()) append("${delivery.name}  ")
                append(delivery.address)
            }
            text = label; textSize = 13f; setTextColor(onSurfaceVariant); maxLines = 2
            setPadding((36 * dp).toInt(), (4 * dp).toInt(), (20 * dp).toInt(), (4 * dp).toInt())
        })

        // エリア外の可能性がある件
        if (mismatched.isNotEmpty()) {
            sectionLabel("⚠️  ${mismatched.size}件がエリア外の可能性があります", warnColor)
            mismatched.forEach { itemRow(it) }
        }

        // 検索未完了の件
        if (notGeocoded.isNotEmpty()) {
            if (mismatched.isNotEmpty()) root.addView(View(ctx).apply {
                setBackgroundColor(outlineVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                    .also { it.setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) }
            })
            sectionLabel("⏳  ${notGeocoded.size}件は住所検索中です（精査待ち）", onSurfaceVariant)
            notGeocoded.forEach { itemRow(it) }
        }

        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (32 * dp).toInt())
        })

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        sheet.setContentView(ScrollView(ctx).apply { addView(root) })
        sheet.show()
    }

