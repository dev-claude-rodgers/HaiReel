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
internal fun DeliveryListFragment.showTemplateEditDialog() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val AppSettings = com.rodgers.haireel.util.AppSettings
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
                templates.add(com.rodgers.haireel.util.AppSettings.TimeSlotTemplate(newName, selectedNewHex))
                AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                rvAdapter.notifyItemInserted(templates.size - 1)
                addInput.setText("")
                Toast.makeText(ctx, "「$newName」を追加しました", Toast.LENGTH_SHORT).show()
            }
        }
    }




