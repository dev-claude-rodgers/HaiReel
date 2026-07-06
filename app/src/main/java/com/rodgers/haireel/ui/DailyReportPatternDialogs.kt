package com.rodgers.haireel.ui

import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.R
import com.rodgers.haireel.model.ColumnType
import com.rodgers.haireel.model.ExcelColumn
import com.rodgers.haireel.model.ReportPattern
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.PatternStorage
import com.rodgers.haireel.util.themeColor

internal fun DailyReportFragment.showPatternListDialog() {
    if (!isAdded) return
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val maxH = (ctx.resources.displayMetrics.heightPixels * 0.75).toInt()
    val scroll   = ScrollView(ctx).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, maxH
        )
    }
    val listRoot = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
    }
    scroll.addView(listRoot)

    fun rebuildList() {
        listRoot.removeAllViews()
        val activeId = currentPattern().id
        val patterns = PatternStorage.getAll(ctx)
        val activeBg       = ContextCompat.getColor(ctx, R.color.colorReportPrimaryBg)
        val inactiveBg     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val activeBorder   = ContextCompat.getColor(ctx, R.color.colorReportPrimary)
        val inactiveBorder = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val primaryColor   = ContextCompat.getColor(ctx, R.color.colorReportPrimary)
        val onSurfaceColor = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.colorReportSecondaryText)
        val greenColor     = ContextCompat.getColor(ctx, R.color.colorActionGreen)
        val redColor       = ContextCompat.getColor(ctx, R.color.colorActionRed)

        val addBtn = android.widget.Button(ctx).apply {
            text = "+ 新しいパターンを追加"; isAllCaps = false; textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8 * dp).toInt() }
        }
        listRoot.addView(addBtn)
        addBtn.setOnClickListener {
            showPatternEditDialog(null) { rebuildList() }
        }

        if (patterns.isEmpty()) {
            listRoot.addView(TextView(ctx).apply {
                text = "パターンがありません。\n上のボタンから追加してください。"
                textSize = 13f
                setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (16 * dp).toInt() }
            })
            return
        }

        patterns.forEach { pattern ->
            val isActive = (pattern.id == activeId)

            val selectAction: () -> Unit = {
                PatternStorage.setActiveId(ctx, pattern.id)
                val gid = reportViewModel.assignmentId.value
                if (gid.isNotBlank()) deliveryViewModel.linkPatternToGroup(gid, pattern.id)
                reportViewModel.setClosingDay(pattern.closingDay)
                rebuildList()
                Toast.makeText(ctx, "「${pattern.title}」を選択しました", Toast.LENGTH_SHORT).show()
            }

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (isActive) activeBg else inactiveBg)
                    setStroke((1 * dp).toInt(), if (isActive) activeBorder else inactiveBorder)
                    cornerRadius = 6 * dp
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (8 * dp).toInt() }
                if (!isActive) setOnClickListener { selectAction() }
            }

            val titleRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }
            val checkMark = if (isActive) "✅  " else "      "
            titleRow.addView(TextView(ctx).apply {
                text = "$checkMark${pattern.title}"; textSize = 14f
                setTextColor(if (isActive) primaryColor else onSurfaceColor)
                if (isActive) typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            card.addView(titleRow)

            if (pattern.clientName.isNotBlank()) {
                card.addView(TextView(ctx).apply {
                    text = "取引先: ${pattern.clientName}"; textSize = 12f
                    setTextColor(secondaryColor)
                })
            }
            if (pattern.driverName.isNotBlank()) {
                card.addView(TextView(ctx).apply {
                    text = "担当者: ${pattern.driverName}"; textSize = 12f
                    setTextColor(secondaryColor)
                })
            }

            val btnRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (6 * dp).toInt() }
            }
            fun rowBtn(label: String, color: Int, action: () -> Unit) =
                android.widget.Button(ctx).apply {
                    text = label; isAllCaps = false; textSize = 12f
                    setTextColor(color); background = null
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                        .also { it.marginStart = (8 * dp).toInt() }
                    setOnClickListener { action() }
                }

            if (!isActive) {
                btnRow.addView(rowBtn("✅ 選択", greenColor) { selectAction() })
            }
            btnRow.addView(rowBtn("編集", primaryColor) {
                showPatternEditDialog(pattern) { rebuildList() }
            })
            if (patterns.size > 1) {
                btnRow.addView(rowBtn("削除", redColor) {
                    MaterialAlertDialogBuilder(ctx)
                        .setMessage("「${pattern.title}」を削除しますか？")
                        .setPositiveButton("削除") { _, _ ->
                            PatternStorage.delete(ctx, pattern.id)
                            rebuildList()
                        }
                        .setNegativeButton("キャンセル", null).show()
                })
            }
            card.addView(btnRow)
            listRoot.addView(card)
        }
    }

    rebuildList()

    MaterialAlertDialogBuilder(ctx)
        .setTitle("帳票設定")
        .setView(scroll)
        .setNegativeButton("閉じる", null)
        .show()
}

internal fun DailyReportFragment.showPatternEditDialog(pattern: ReportPattern?, onSaved: () -> Unit = {}) {
    if (!isAdded) return
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val maxH = (ctx.resources.displayMetrics.heightPixels * 0.75).toInt()
    val scroll = ScrollView(ctx).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, maxH
        )
    }
    val root   = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
    }
    scroll.addView(root)

    val base = pattern ?: ReportPattern.default(PatternStorage.nextId(ctx))

    val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val colorSurfaceVariant   = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
    val colorOutlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

    fun label(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 13f; setTextColor(colorOnSurfaceVariant)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    }
    fun divider() = android.view.View(ctx).apply {
        setBackgroundColor(colorOutlineVariant)
        layoutParams = LinearLayout.LayoutParams(MATCH, (1 * dp).toInt())
            .also { it.topMargin = (16 * dp).toInt(); it.bottomMargin = (12 * dp).toInt() }
    }
    fun field(value: String, hint: String = "") = EditText(ctx).apply {
        setText(value); this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    root.addView(label("帳票タイトル"))
    val titleIn   = field(base.title, "稼働報告書")
    root.addView(titleIn)

    root.addView(label("担当者名"))
    val driverIn  = field(base.driverName.ifBlank { AppSettings.getDriverName(ctx) }, "例: 〇〇 〇〇")
    root.addView(driverIn)

    root.addView(label("取引先名"))
    val clientIn  = field(base.clientName, "例: 委託元の会社名")
    root.addView(clientIn)

    root.addView(label("締め日"))
    val closingRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    var isMonthEnd = base.closingDay >= 31  // 月末ボタンが明示的に選択されているか
    val closingIn = EditText(ctx).apply {
        val display = if (base.closingDay >= 31) "" else base.closingDay.toString()
        setText(display); inputType = InputType.TYPE_CLASS_NUMBER
        hint = "例: 25"
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    val monthEndBtn = com.google.android.material.button.MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "月末"
        isAllCaps = false; textSize = 13f
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
            .also { it.marginStart = (8 * dp).toInt() }
    }
    val toggleColor = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
    fun updateMonthEndStyle(active: Boolean) {
        monthEndBtn.setTextColor(if (active) android.graphics.Color.WHITE else toggleColor)
        monthEndBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (active) toggleColor else android.graphics.Color.TRANSPARENT
        )
    }
    updateMonthEndStyle(isMonthEnd)
    closingIn.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
            if (!s.isNullOrEmpty()) { isMonthEnd = false; updateMonthEndStyle(false) }
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
    monthEndBtn.setOnClickListener {
        isMonthEnd = true
        closingIn.setText("")
        closingIn.clearFocus()
        updateMonthEndStyle(true)
    }
    closingRow.addView(closingIn)
    closingRow.addView(monthEndBtn)
    root.addView(closingRow)

    root.addView(divider())
    root.addView(TextView(ctx).apply {
        text = "Excel 出力列"
        textSize = 13f; setTextColor(colorOnSurfaceVariant)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })
    root.addView(TextView(ctx).apply {
        text = "種別を選ぶと列名が自動入力されます（A4横目安: 5〜6列）"
        textSize = 11f; setTextColor(colorOnSurfaceVariant)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8 * dp).toInt() }
    })

    val allowedColTypes = listOf(
        ColumnType.START_TIME,
        ColumnType.END_TIME,
        ColumnType.WORKING_HOURS,
        ColumnType.DELIVERY_COUNT,
        ColumnType.PACKAGE_COUNT,
        ColumnType.AREA,
        ColumnType.METER_START,
        ColumnType.METER_END,
        ColumnType.DISTANCE,
        ColumnType.FUEL_COST,
        ColumnType.INCOME,
        ColumnType.ALC_CHECK
    )
    val colTypeLabels = allowedColTypes.map { it.defaultLabel }.toTypedArray()

    val editingCols = base.excelColumns.map { ExcelColumn(it.type, it.label) }.toMutableList()

    val colListRoot = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(colListRoot)

    fun rebuildColList() {
        colListRoot.removeAllViews()
        editingCols.forEachIndexed { idx, col ->
            // labelEditを先に生成してスピナーリスナーから参照できるようにする
            val labelEdit = EditText(ctx).apply {
                setText(col.label)
                hint = "列名（省略時は種別名を使用）"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
                        if (idx < editingCols.size)
                            editingCols[idx] = editingCols[idx].copy(label = s?.toString() ?: "")
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }

            val spinner = android.widget.Spinner(ctx).apply {
                adapter = android.widget.ArrayAdapter(
                    ctx, android.R.layout.simple_spinner_item, colTypeLabels
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(allowedColTypes.indexOf(col.type).coerceAtLeast(0))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                var isInitialized = false
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                        if (!isInitialized) { isInitialized = true; return }
                        if (idx < editingCols.size) {
                            val newType = allowedColTypes[pos]
                            editingCols[idx] = editingCols[idx].copy(type = newType, label = newType.defaultLabel)
                            labelEdit.setText(newType.defaultLabel)
                        }
                    }
                    override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                }
            }

            val delBtn = com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = "✕"
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.colorActionRed))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                setOnClickListener { editingCols.removeAt(idx); rebuildColList() }
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }
            topRow.addView(spinner)
            topRow.addView(delBtn)

            val bottomRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (4 * dp).toInt() }
            }
            bottomRow.addView(TextView(ctx).apply {
                text = "列名:"; textSize = 12f; setTextColor(colorOnSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                    .also { it.marginEnd = (6 * dp).toInt() }
            })
            bottomRow.addView(labelEdit)

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(colorSurfaceVariant); cornerRadius = 8 * dp
                }
                setPadding((10 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (6 * dp).toInt() }
            }
            card.addView(topRow)
            card.addView(bottomRow)
            colListRoot.addView(card)
        }
    }

    rebuildColList()

    val addColBtn = android.widget.Button(ctx).apply {
        text = "+ 列を追加"
        isAllCaps = false
        textSize = 13f
        setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary))
        background = null
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        setOnClickListener {
            val used = editingCols.map { it.type }.toSet()
            val next = allowedColTypes.firstOrNull { it !in used } ?: ColumnType.DELIVERY_COUNT
            editingCols.add(ExcelColumn(next, next.defaultLabel))
            rebuildColList()
        }
    }
    root.addView(addColBtn)

    root.addView(divider())
    root.addView(label("報酬タイプ"))
    val payLabels = listOf("個建て（個数×単価）", "車建て（日当制）", "時間制（時間×単価）", "なし（集計しない）")
    val payGroup  = android.widget.RadioGroup(ctx)
    var selectedPayType = base.paymentType
    payLabels.forEachIndexed { i, lbl ->
        payGroup.addView(android.widget.RadioButton(ctx).apply {
            text = lbl; id = android.view.View.generateViewId()
            isChecked = (base.paymentType == i)
            setOnCheckedChangeListener { _, checked -> if (checked) selectedPayType = i }
        })
    }
    root.addView(payGroup)

    root.addView(label("単価 / 日当 / 時間単価（円）"))
    val unitPriceIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
        if (base.unitPrice > 0) setText(base.unitPrice.toString())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(unitPriceIn)
    root.addView(TextView(ctx).apply {
        text = "個建て（個数×単価）/ 車建て（日当制）/ 時間制（時間×単価）"
        textSize = 11f; setTextColor(colorOnSurfaceVariant)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })

    val isNew = (pattern == null)
    val dlgPattern = MaterialAlertDialogBuilder(ctx)
        .setTitle(if (isNew) "パターンを追加" else "パターンを編集")
        .setView(scroll)
        .setPositiveButton("保存", null)
        .setNegativeButton("キャンセル", null)
        .show()
    dlgPattern.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val titleStr = titleIn.text.toString().trim()
            val closingText = closingIn.text.toString().trim()
            val cd = when {
                isMonthEnd -> 31
                closingText.isEmpty() -> {
                    closingIn.error = "締め日を入力するか「月末」を選んでください"
                    return@setOnClickListener
                }
                else -> {
                    val n = closingText.toIntOrNull()
                    if (n == null || n !in 1..30) {
                        closingIn.error = "締め日は1〜30の数字、または「月末」を選んでください"
                        return@setOnClickListener
                    }
                    n
                }
            }
            val updated = base.copy(
                title         = titleStr.ifBlank { "稼働報告書" },
                driverName    = driverIn.text.toString().trim(),
                clientName    = clientIn.text.toString().trim(),
                closingDay    = cd,
                excelColumns  = editingCols.map { it.copy(label = it.label.ifBlank { it.type.defaultLabel }) },
                paymentType   = selectedPayType,
                unitPrice     = unitPriceIn.text.toString().toIntOrNull() ?: 0
            )
            try {
                PatternStorage.save(ctx, updated)
            } catch (e: Exception) {
                Toast.makeText(ctx, "保存に失敗しました: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isNew) PatternStorage.setActiveId(ctx, updated.id)
            if (PatternStorage.getActiveId(ctx) == updated.id) {
                reportViewModel.setClosingDay(updated.closingDay)
            }
            val msg = when {
                titleStr.isBlank() -> "帳票タイトルが未入力のため「稼働報告書」を使用します"
                isNew -> "パターンを追加しました"
                else  -> "保存しました"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            onSaved()
            dlgPattern.dismiss()
        }
}
