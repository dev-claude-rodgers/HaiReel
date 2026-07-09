package com.rodgers.haireel.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.R
import com.rodgers.haireel.model.ColumnType
import com.rodgers.haireel.model.ReportPattern
import com.rodgers.haireel.model.WorkRecord
import com.rodgers.haireel.util.themeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

fun showDailyReportEditDialog(
    ctx: Context,
    record: WorkRecord,
    pattern: ReportPattern,
    fuelPricePerL: Int,
    fuelEfficiencyKmL: Float,
    fragmentManager: FragmentManager,
    isAdded: () -> Boolean,
    scope: CoroutineScope,
    assignmentId: () -> String,
    calcIncomeFn: (ReportPattern, Int, Int, Int) -> Int,
    onSave: suspend (WorkRecord) -> Unit
) {
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    var selectedDate  = record.date
    var startH        = record.startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 9
    var startM        = record.startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    var endH          = record.endTime.split(":").getOrNull(0)?.toIntOrNull() ?: 18
    var endM          = record.endTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    var endDateOffset = record.endDateOffset
    var isNoWork      = record.noWork

    val alcValues  = listOf("", "○", "×")
    var alcIdx     = alcValues.indexOf(record.alcCheck).coerceAtLeast(0)

    val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

    val scroll = ScrollView(ctx)
    val root   = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
    }
    scroll.addView(root)

    fun label(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 13f; setTextColor(colorOnSurfaceVariant)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    }

    // 稼働あり / 休み トグル
    val workBtnId = View.generateViewId()
    val restBtnId = View.generateViewId()
    val noWorkToggle = com.google.android.material.button.MaterialButtonToggleGroup(ctx).apply {
        isSingleSelection = true
        isSelectionRequired = true
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (12 * dp).toInt() }
    }
    val btnWork = com.google.android.material.button.MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        id = workBtnId; text = "稼働あり"; isAllCaps = false; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    val btnRest = com.google.android.material.button.MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        id = restBtnId; text = "休日"; isAllCaps = false; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    noWorkToggle.addView(btnWork)
    noWorkToggle.addView(btnRest)
    noWorkToggle.check(if (isNoWork) restBtnId else workBtnId)
    noWorkToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked) isNoWork = (checkedId == restBtnId)
    }
    root.addView(noWorkToggle)

    // 日付
    val btnDate = android.widget.Button(ctx).apply {
        isAllCaps = false; textSize = 15f
        setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary)); background = null
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    fun refreshDateBtn() { btnDate.text = "📅  $selectedDate" }
    refreshDateBtn()
    root.addView(label("日付")); root.addView(btnDate)
    btnDate.setOnClickListener {
        val parts = selectedDate.split("-").mapNotNull { it.toIntOrNull() }
        val c = Calendar.getInstance().apply {
            if (parts.size >= 3) set(parts[0], parts[1] - 1, parts[2])
        }
        DatePickerDialog(ctx, { _, y, m, d ->
            selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
            refreshDateBtn()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    val hasExistingTime = record.startTime.isNotBlank() || record.endTime.isNotBlank()
    var showTime = hasExistingTime || pattern.excelColumns.any { it.type == ColumnType.START_TIME || it.type == ColumnType.END_TIME }

    val offsetBtnList       = mutableListOf<android.widget.Button>()
    val offsetSelectedColor = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
    val offsetUnselectColor = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val offsetBorderColor   = ctx.themeColor(com.google.android.material.R.attr.colorOutline)
    val offsetTextUnselect  = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    fun applyOffsetStyle() {
        offsetBtnList.forEachIndexed { j, b ->
            val sel = (j == endDateOffset)
            (b.background as? android.graphics.drawable.GradientDrawable)?.apply {
                setColor(if (sel) offsetSelectedColor else offsetUnselectColor)
                setStroke((2f * dp).toInt(), if (sel) offsetSelectedColor else offsetBorderColor)
            }
            b.setTextColor(if (sel) Color.WHITE else offsetTextUnselect)
            b.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    val timeLbl   = label("開始時刻 ／ 終了時刻")
    val timeRow   = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val offsetLbl = label("終了日")
    val offsetRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    fun applyTimeVisibility() {
        val v = if (showTime) View.VISIBLE else View.GONE
        timeLbl.visibility = v; timeRow.visibility = v
        offsetLbl.visibility = v; offsetRow.visibility = v
    }

    val btnStart = android.widget.Button(ctx).apply { isAllCaps = false; textSize = 16f; background = null }
    val btnEnd   = android.widget.Button(ctx).apply { isAllCaps = false; textSize = 16f; background = null }
    fun refreshTimeBtns() {
        btnStart.text = "%02d:%02d".format(startH, startM)
        btnEnd.text   = "%02d:%02d".format(endH, endM)
    }
    refreshTimeBtns()
    btnStart.setOnClickListener {
        if (!isAdded()) return@setOnClickListener
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(startH).setMinute(startM).build()
        picker.show(fragmentManager, "start")
        picker.addOnPositiveButtonClickListener {
            startH = picker.hour; startM = picker.minute; refreshTimeBtns()
        }
    }
    btnEnd.setOnClickListener {
        if (!isAdded()) return@setOnClickListener
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(endH).setMinute(endM).build()
        picker.show(fragmentManager, "end")
        picker.addOnPositiveButtonClickListener {
            endH = picker.hour; endM = picker.minute
            if (endDateOffset == 0 && (endH * 60 + endM) <= (startH * 60 + startM)) {
                endDateOffset = 1; applyOffsetStyle()
            }
            refreshTimeBtns()
        }
    }
    listOf(btnStart, TextView(ctx).apply { text = " 〜 "; textSize = 16f }, btnEnd)
        .forEach { timeRow.addView(it) }

    listOf("当日", "翌日", "+2日").forEachIndexed { i, lbl ->
        val btn = android.widget.Button(ctx).apply {
            text = lbl; isAllCaps = false; textSize = 14f
            setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(offsetUnselectColor)
                setStroke((1.5f * dp).toInt(), offsetBorderColor)
                cornerRadius = 6 * dp
            }
            setTextColor(offsetTextUnselect)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also {
                if (i > 0) it.marginStart = (4 * dp).toInt()
            }
            setOnClickListener { endDateOffset = i; applyOffsetStyle() }
        }
        offsetBtnList.add(btn)
        offsetRow.addView(btn)
    }
    applyOffsetStyle()

    root.addView(timeLbl); root.addView(timeRow)
    root.addView(offsetLbl); root.addView(offsetRow)
    applyTimeVisibility()

    // 配達件数 / 個数
    root.addView(label("配達件数 ／ 個数"))
    val cntRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val delivCntIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"; setText(record.deliveryCount.toString())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    val pkgCntIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"; setText(record.packageCount.toString())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    cntRow.addView(delivCntIn)
    cntRow.addView(TextView(ctx).apply { text = " 件  "; textSize = 13f })
    cntRow.addView(pkgCntIn)
    cntRow.addView(TextView(ctx).apply { text = " 個"; textSize = 13f })
    root.addView(cntRow)

    // エリア
    root.addView(label("エリア"))
    val areaIn = EditText(ctx).apply {
        hint = "例: 〇〇区・市内"; setText(record.area)
        inputType = InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(areaIn)

    // アルコールチェック
    root.addView(label("アルコールチェック"))
    val alcSelectedBg      = listOf("#9E9E9E", "#388E3C", "#D32F2F")
    val alcUnselectedColor = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val alcBorderColor     = ctx.themeColor(com.google.android.material.R.attr.colorOutline)
    val alcUnselectedText  = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val alcLabels = listOf("未実施", "○ 正常", "× 異常")
    val alcRow    = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val alcBtnList = mutableListOf<android.widget.Button>()
    fun applyAlcStyle() {
        alcBtnList.forEachIndexed { j, b ->
            val selected = (j == alcIdx)
            (b.background as android.graphics.drawable.GradientDrawable).apply {
                setColor(if (selected) Color.parseColor(alcSelectedBg[j]) else alcUnselectedColor)
                setStroke((2f * dp).toInt(), if (selected) Color.parseColor(alcSelectedBg[j]) else alcBorderColor)
            }
            b.setTextColor(if (selected) Color.WHITE else alcUnselectedText)
            b.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
    alcLabels.forEachIndexed { i, lbl ->
        val btn = android.widget.Button(ctx).apply {
            text = lbl; isAllCaps = false; textSize = 14f
            setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(alcUnselectedColor)
                setStroke((2f * dp).toInt(), alcBorderColor)
                cornerRadius = 6 * dp
            }
            setTextColor(alcUnselectedText)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also {
                if (i > 0) it.marginStart = (4 * dp).toInt()
            }
            setOnClickListener { alcIdx = i; applyAlcStyle() }
        }
        alcBtnList.add(btn)
        alcRow.addView(btn)
    }
    applyAlcStyle()
    root.addView(alcRow)

    // メーター
    root.addView(label("開始メーター ／ 終了メーター（km）"))
    val meterRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val startMeterIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
        if (record.startMeter > 0) setText(record.startMeter.toString())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    val endMeterIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
        if (record.endMeter > 0) setText(record.endMeter.toString())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    meterRow.addView(startMeterIn)
    meterRow.addView(TextView(ctx).apply { text = "km"; textSize = 13f; setTextColor(Color.GRAY) })
    meterRow.addView(TextView(ctx).apply { text = "  〜  "; textSize = 14f })
    meterRow.addView(endMeterIn)
    meterRow.addView(TextView(ctx).apply { text = "km"; textSize = 13f; setTextColor(Color.GRAY) })
    root.addView(meterRow)

    // 走行距離
    root.addView(label("走行距離（km）"))
    var distManuallyEdited = false
    val distIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        hint = "0.0"
        setText(when {
            record.distanceKm > 0f -> "%.0f".format(record.distanceKm)
            else -> ""
        })
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(distIn)
    var updatingDistFromMeter = false
    distIn.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { if (!updatingDistFromMeter) distManuallyEdited = true }
    })
    val meterWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (distManuallyEdited) return
            val sm = startMeterIn.text.toString().toIntOrNull()
            val em = endMeterIn.text.toString().toIntOrNull()
            if (sm != null && em != null && em > sm) {
                updatingDistFromMeter = true
                distIn.setText((em - sm).toString())
                updatingDistFromMeter = false
            }
        }
    }
    startMeterIn.addTextChangedListener(meterWatcher)
    endMeterIn.addTextChangedListener(meterWatcher)

    // 収入
    root.addView(label("収入（円）"))
    val incomeRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val incomeIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
        setText(if (record.income > 0) record.income.toString() else "")
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    val incomeCalcBtn = android.widget.Button(ctx).apply {
        text = "💴 自動計算"; isAllCaps = false; textSize = 12f
        setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary)); background = null
        setOnClickListener {
            val dc   = delivCntIn.text.toString().toIntOrNull() ?: 0
            val pc   = pkgCntIn.text.toString().toIntOrNull() ?: 0
            val wm   = ((endH * 60 + endM + endDateOffset * 24 * 60) - (startH * 60 + startM)).coerceAtLeast(0)
            val calc = calcIncomeFn(pattern, dc, wm, pc)
            if (calc > 0) {
                incomeIn.setText(calc.toString())
            } else {
                val msg = when (pattern.paymentType) {
                    3    -> "帳票設定の「報酬タイプ」を\n・個建て(個数×単価)\n・車建て(日当制)\n・時間制(時間×単価)\nのいずれかに設定してください"
                    0    -> if (pc == 0) "個数を入力してください" else "帳票設定の「単価」を設定してください"
                    1    -> "帳票設定の「単価（日当）」を設定してください"
                    2    -> if (wm == 0) "開始・終了時刻を入力してください" else "帳票設定の「単価」を設定してください"
                    else -> "帳票設定の「単価」を設定してください"
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("自動計算できません")
                    .setMessage(msg)
                    .setPositiveButton("閉じる", null)
                    .show()
            }
        }
    }
    incomeRow.addView(incomeIn); incomeRow.addView(incomeCalcBtn)
    root.addView(incomeRow)

    // 燃料費
    root.addView(label("燃料費（円）"))
    val fuelRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val fuelIn = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
        if (record.fuelCost > 0) setText(record.fuelCost.toString())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    val fuelCalcBtn = android.widget.Button(ctx).apply {
        text = "⛽ 自動計算"; isAllCaps = false; textSize = 12f
        setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary)); background = null
        setOnClickListener {
            val dist = distIn.text.toString().toFloatOrNull() ?: 0f
            if (dist > 0f && fuelEfficiencyKmL > 0f && fuelPricePerL > 0) {
                val cost = (dist / fuelEfficiencyKmL * fuelPricePerL).toInt()
                fuelIn.setText(cost.toString())
            } else {
                val msg = when {
                    fuelPricePerL <= 0 && fuelEfficiencyKmL <= 0f -> "設定でガソリン単価と燃費(km/L)を入力してください"
                    fuelPricePerL <= 0                            -> "設定でガソリン単価(円/L)を入力してください"
                    fuelEfficiencyKmL <= 0f                       -> "設定で燃費(km/L)を入力してください"
                    else                                          -> "走行距離を先に入力してください"
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("自動計算できません")
                    .setMessage(msg)
                    .setPositiveButton("閉じる", null)
                    .show()
            }
        }
    }
    fuelRow.addView(fuelIn); fuelRow.addView(fuelCalcBtn)
    root.addView(fuelRow)

    // 備考
    root.addView(label("備考"))
    val remarksIn = EditText(ctx).apply {
        hint = "特記事項など"; setText(record.remarks)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        maxLines = 3; layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(remarksIn)

    val dlg = MaterialAlertDialogBuilder(ctx)
        .setTitle(if (record.id == 0L) "日報を記録（$selectedDate）" else "日報を編集（${record.date}）")
        .setView(scroll)
        .setPositiveButton("保存", null)
        .setNegativeButton("キャンセル", null)
        .show()
    dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
        val sm = startMeterIn.text.toString().toIntOrNull() ?: 0
        val em = endMeterIn.text.toString().toIntOrNull()   ?: 0
        if (em > 0 && sm == 0) {
            startMeterIn.error = "開始メーターも入力してください"; return@setOnClickListener
        }
        if (sm > 0 && em > 0 && sm >= em) {
            Toast.makeText(ctx, "終了メーターは開始メーターより大きい値にしてください", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val dist       = distIn.text.toString().toFloatOrNull()
            ?: if (em > sm && sm > 0) (em - sm).toFloat() else record.distanceKm
        val delivCount = delivCntIn.text.toString().toIntOrNull() ?: 0
        val workMins   = ((endH * 60 + endM + endDateOffset * 24 * 60) - (startH * 60 + startM)).coerceAtLeast(0)
        val updated = record.copy(
            date          = selectedDate,
            startTime     = if (isNoWork) "" else "%02d:%02d".format(startH, startM),
            endTime       = if (isNoWork) "" else "%02d:%02d".format(endH, endM),
            endDateOffset = if (isNoWork) 0 else endDateOffset,
            startMeter    = if (isNoWork) 0 else sm,
            endMeter      = if (isNoWork) 0 else em,
            distanceKm    = if (isNoWork) 0f else dist,
            deliveryCount = if (isNoWork) 0 else delivCount,
            packageCount  = if (isNoWork) 0 else (pkgCntIn.text.toString().toIntOrNull() ?: 0),
            area          = if (isNoWork) "" else areaIn.text.toString().trim(),
            alcCheck      = if (isNoWork) "" else alcValues[alcIdx],
            remarks       = remarksIn.text.toString().trim(),
            income        = if (isNoWork) 0 else (incomeIn.text.toString().toIntOrNull() ?: 0),
            fuelCost      = if (isNoWork) 0 else (fuelIn.text.toString().toIntOrNull() ?: 0),
            assignmentId  = assignmentId(),
            noWork        = isNoWork
        )
        scope.launch {
            withContext(NonCancellable) { onSave(updated) }
            if (!isAdded()) return@launch
            Toast.makeText(ctx, "保存しました（$selectedDate）", Toast.LENGTH_SHORT).show()
            try { dlg.dismiss() } catch (_: Exception) {}
        }
    }
}
