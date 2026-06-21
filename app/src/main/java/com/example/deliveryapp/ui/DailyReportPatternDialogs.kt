package com.rodgers.routist.ui

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
import com.rodgers.routist.R
import com.rodgers.routist.model.ReportPattern
import com.rodgers.routist.util.PatternStorage
import com.rodgers.routist.util.themeColor

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
                textSize = 13f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
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

    fun label(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 12f; setTextColor(Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (8 * dp).toInt(); it.bottomMargin = (2 * dp).toInt() }
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
    val driverIn  = field(base.driverName, "山田 太郎")
    root.addView(driverIn)

    root.addView(label("取引先名"))
    val clientIn  = field(base.clientName, "◯◯運輸")
    root.addView(clientIn)

    root.addView(label("締め日（1〜31）"))
    val closingIn = EditText(ctx).apply {
        setText(base.closingDay.toString()); inputType = InputType.TYPE_CLASS_NUMBER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(closingIn)

    root.addView(label("配達件数 列ラベル"))
    val delivLblIn = field(base.deliveryLabel, "配達件数")
    root.addView(delivLblIn)

    root.addView(label("個数 列ラベル"))
    val pkgLblIn   = field(base.packageLabel, "個数")
    root.addView(pkgLblIn)

    root.addView(TextView(ctx).apply {
        text = "Excel に出力する列"
        textSize = 13f; setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurface))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (12 * dp).toInt() }
    })

    fun chk(label: String, checked: Boolean) = CheckBox(ctx).apply {
        text = label; isChecked = checked
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val chkStartEnd = chk("開始・終了時刻", base.showStartEndTime)
    val chkWorkHrs  = chk("稼働時間",      base.showWorkingHours)
    val chkDelivery = chk("配達件数",  base.showDelivery)
    val chkPackage  = chk("個数",      base.showPackage)
    val chkDistance = chk("走行距離",         base.showDistance)
    val chkFuel     = chk("燃料費",           base.showFuel)
    val chkMeter    = chk("開始/終了メーター", base.showMeter)
    val chkIncome   = chk("収入",             base.showIncome)
    val chkArea     = chk("エリア",           base.showArea)
    val chkRemarks  = chk("備考",             base.showRemarks)
    val chkTotal    = chk("合計行を表示する", base.showTotal)
    listOf(chkStartEnd, chkWorkHrs, chkDelivery, chkPackage, chkIncome, chkDistance, chkMeter, chkFuel, chkArea, chkRemarks, chkTotal).forEach { root.addView(it) }

    root.addView(TextView(ctx).apply {
        text = "報酬タイプ"; textSize = 13f
        setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurface))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (14 * dp).toInt(); it.bottomMargin = (2 * dp).toInt() }
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    })
    val payLabels = listOf("個建て(個数×単価)", "車建て(日当制)", "時間制(時間×単価)", "なし(集計しない)")
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
        text = "個建て(個数×単価) / 車建て(日当制) / 時間制(時間×単価)"
        textSize = 11f; setTextColor(Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })

    val isNew = (pattern == null)
    MaterialAlertDialogBuilder(ctx)
        .setTitle(if (isNew) "パターンを追加" else "パターンを編集")
        .setView(scroll)
        .setPositiveButton("保存") { _, _ ->
            val titleStr = titleIn.text.toString().trim()
            val updated = base.copy(
                title         = titleStr.ifBlank { "稼働報告書" },
                driverName    = driverIn.text.toString().trim(),
                clientName    = clientIn.text.toString().trim(),
                closingDay    = closingIn.text.toString().toIntOrNull()?.coerceIn(1, 31) ?: 25,
                deliveryLabel = delivLblIn.text.toString().trim().ifBlank { "配達件数" },
                packageLabel  = pkgLblIn.text.toString().trim().ifBlank { "個数" },
                showStartEndTime = chkStartEnd.isChecked,
                showWorkingHours = chkWorkHrs.isChecked,
                showDelivery  = chkDelivery.isChecked,
                showPackage   = chkPackage.isChecked,
                showIncome    = chkIncome.isChecked,
                showDistance  = chkDistance.isChecked,
                showMeter     = chkMeter.isChecked,
                showFuel      = chkFuel.isChecked,
                showArea      = chkArea.isChecked,
                showTotal     = chkTotal.isChecked,
                showRemarks   = chkRemarks.isChecked,
                paymentType   = selectedPayType,
                unitPrice     = unitPriceIn.text.toString().toIntOrNull() ?: 0
            )
            PatternStorage.save(ctx, updated)
            if (isNew) PatternStorage.setActiveId(ctx, updated.id)
            if (PatternStorage.getActiveId(ctx) == updated.id) {
                reportViewModel.setClosingDay(updated.closingDay)
            }
            val msg = when {
                titleStr.isBlank() -> "パターン名が未入力のため「稼働報告書」を使用します"
                isNew -> "パターンを追加しました"
                else  -> "保存しました"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            onSaved()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}
