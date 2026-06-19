package com.rodgers.routist.ui

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.model.TenkoRecord
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.LocationTrackingService
import com.rodgers.routist.util.themeColor
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

internal fun TenkoFragment.showBeforeDialog(date: String, existing: TenkoRecord?) {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val cGreen   = Color.parseColor("#4CAF50"); val cBgGreen = Color.parseColor("#E8F5E9")
    val cRed     = Color.parseColor("#F44336"); val cBgRed   = Color.parseColor("#FFEBEE")
    val cBlue    = Color.parseColor("#1E88E5"); val cBgBlue  = Color.parseColor("#E3F2FD")
    val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

    val scroll = ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20*dp).toInt(),(8*dp).toInt(),(20*dp).toInt(),(16*dp).toInt())
    }
    scroll.addView(root)

    fun sectionLabel(icon: String, text: String) = TextView(ctx).apply {
        this.text = "$icon  $text"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorOnSurface)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (16*dp).toInt(); it.bottomMargin = (6*dp).toInt() }
    }

    fun styledBtn(label: String) = Button(ctx).apply { text = label; isAllCaps = false; textSize = 13f }

    fun applyStyle(btn: Button, active: Boolean, ac: Int, bg: Int) {
        btn.setTextColor(if (active) Color.WHITE else ac)
        btn.background = GradientDrawable().apply { setColor(if (active) ac else bg); cornerRadius = 8*dp }
    }

    fun hRow(vararg btns: Button) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btns.forEach { addView(it) }
    }

    // ── 確認方法（3+2グリッド）
    root.addView(sectionLabel("📋", "確認方法"))
    val methods = listOf("対面", "電話", "IT点呼", "自己点呼", "その他")
    var selMethod: String = existing?.beforeMethod?.takeIf { methods.contains(it) } ?: "対面"
    val mBtns = methods.map { m ->
        styledBtn(m).also { btn ->
            btn.layoutParams = LinearLayout.LayoutParams(0, (38*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        }
    }
    fun refreshMethod() = mBtns.forEachIndexed { i, btn -> applyStyle(btn, methods[i] == selMethod, cBlue, cBgBlue) }
    mBtns.forEachIndexed { i, btn -> btn.setOnClickListener { selMethod = methods[i]; refreshMethod() } }
    refreshMethod()
    root.addView(hRow(*mBtns.take(3).toTypedArray()))
    root.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (4*dp).toInt() }
        mBtns.drop(3).forEach { addView(it) }
        // 残り2件の幅を揃えるためスペーサー
        addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
    })

    // ── 時刻（タップで TimePickerDialog）
    root.addView(sectionLabel("⏰", "時刻"))
    var selTime: String = existing?.beforeTime ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()).orEmpty()
    val timeBtn = Button(ctx).apply {
        text = "🕐  $selTime"; isAllCaps = false; textSize = 16f; setTextColor(cBlue)
        background = GradientDrawable().apply { setColor(cBgBlue); cornerRadius = 8*dp }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46*dp).toInt())
    }
    timeBtn.setOnClickListener {
        val parts = selTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(ctx, { _, hour, min -> selTime = "%02d:%02d".format(hour, min); timeBtn.text = "🕐  $selTime" }, h, m, true).show()
    }
    root.addView(timeBtn)

    // ── 体調
    root.addView(sectionLabel("💪", "体調"))
    var healthOk = existing?.beforeHealth != false
    val btnHOk = styledBtn("✓ 良好"); val btnHNg = styledBtn("✗ 不良")
    fun refreshHealth() { applyStyle(btnHOk, healthOk, cGreen, cBgGreen); applyStyle(btnHNg, !healthOk, cRed, cBgRed) }
    btnHOk.setOnClickListener { healthOk = true;  refreshHealth() }
    btnHNg.setOnClickListener { healthOk = false; refreshHealth() }
    for (btn in listOf(btnHOk, btnHNg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshHealth(); root.addView(hRow(btnHOk, btnHNg))

    // ── 疲労
    root.addView(sectionLabel("😴", "疲労"))
    var fatigueYes = existing?.beforeFatigue == true
    val btnFNo = styledBtn("なし"); val btnFYes = styledBtn("あり")
    fun refreshFatigue() { applyStyle(btnFNo, !fatigueYes, cGreen, cBgGreen); applyStyle(btnFYes, fatigueYes, cRed, cBgRed) }
    btnFNo.setOnClickListener  { fatigueYes = false; refreshFatigue() }
    btnFYes.setOnClickListener { fatigueYes = true;  refreshFatigue() }
    for (btn in listOf(btnFNo, btnFYes)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshFatigue(); root.addView(hRow(btnFNo, btnFYes))

    // ── アルコール数値
    root.addView(sectionLabel("🍺", "アルコール数値"))
    val alcRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    val etAlcohol = EditText(ctx).apply {
        setText(existing?.beforeAlcohol?.let { "%.2f".format(it) } ?: "0.00")
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#0D47A1"))
        background = GradientDrawable().apply {
            setColor(cBgBlue); cornerRadius = 8*dp
            setStroke((2*dp).toInt(), cBlue)
        }
        setPadding((16*dp).toInt(),(12*dp).toInt(),(16*dp).toInt(),(12*dp).toInt())
        layoutParams = LinearLayout.LayoutParams(0, (56*dp).toInt(), 1f)
    }
    alcRow.addView(etAlcohol)
    alcRow.addView(TextView(ctx).apply { text = " mg/L"; textSize = 16f; setTextColor(colorOnSurfaceVariant) })
    root.addView(alcRow)

    // ── 車両点検
    root.addView(sectionLabel("🚗", "車両点検"))
    var inspOk = existing?.beforeInspection != false
    val btnIOk = styledBtn("✓ 異常なし"); val btnINg = styledBtn("⚠ 要確認")
    fun refreshInsp() { applyStyle(btnIOk, inspOk, cGreen, cBgGreen); applyStyle(btnINg, !inspOk, cRed, cBgRed) }
    btnIOk.setOnClickListener { inspOk = true;  refreshInsp() }
    btnINg.setOnClickListener { inspOk = false; refreshInsp() }
    for (btn in listOf(btnIOk, btnINg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshInsp(); root.addView(hRow(btnIOk, btnINg))

    // ── 指示事項
    root.addView(sectionLabel("📝", "指示事項"))
    val etInstruction = EditText(ctx).apply {
        hint = "なし"; setText(existing?.beforeInstruction ?: "")
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 5
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(etInstruction)

    // ── 確認者
    root.addView(sectionLabel("👤", "確認者"))
    val etChecker = EditText(ctx).apply {
        hint = "氏名"; setText(existing?.beforeChecker ?: AppSettings.getCheckerName(ctx))
        inputType = InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(etChecker)

    // ── 使用車両（2台以上登録時のみ選択表示）
    val registeredVehicles = AppSettings.getVehicles(ctx).filter { it.isNotBlank() }
    var selVehicleNumber = existing?.vehicleNumber
        ?: registeredVehicles.firstOrNull() ?: ""
    if (registeredVehicles.size >= 2) {
        root.addView(sectionLabel("🚗", "使用車両"))
        val vBtns = registeredVehicles.map { v ->
            styledBtn(v).also { btn ->
                btn.layoutParams = LinearLayout.LayoutParams(0, (38 * dp).toInt(), 1f)
                    .also { it.marginEnd = (4 * dp).toInt() }
            }
        }
        fun refreshVehicle() = vBtns.forEachIndexed { i, btn ->
            applyStyle(btn, registeredVehicles[i] == selVehicleNumber, cBlue, cBgBlue)
        }
        vBtns.forEachIndexed { i, btn ->
            btn.setOnClickListener { selVehicleNumber = registeredVehicles[i]; refreshVehicle() }
        }
        refreshVehicle()
        root.addView(hRow(*vBtns.toTypedArray()))
    }

    val dlgBefore = MaterialAlertDialogBuilder(ctx)
        .setTitle("乗務前点呼  $date")
        .setView(scroll)
        .setPositiveButton("保存", null)
        .setNegativeButton("キャンセル", null).show()

    dlgBefore.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val alc = (etAlcohol.text.toString().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 9.99)
        if (alc > 0.15) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("⚠️ アルコール値が基準超過")
                .setMessage("アルコール値が基準を超えています（%.2f mg/L）。\nこのまま保存しますか？".format(alc))
                .setPositiveButton("保存する") { _, _ ->
                    viewModel.saveBefore(date, existing, selMethod, selTime,
                        healthOk, fatigueYes, alc, inspOk,
                        etInstruction.text.toString().trim(), etChecker.text.toString().trim(),
                        selVehicleNumber)
                    if (date == LocalDate.now().toString() && AppSettings.isLocationTrackingEnabled(ctx)) LocationTrackingService.start(ctx)
                    dlgBefore.dismiss()
                }
                .setNegativeButton("修正する", null).show()
        } else {
            viewModel.saveBefore(date, existing, selMethod, selTime,
                healthOk, fatigueYes, alc, inspOk,
                etInstruction.text.toString().trim(), etChecker.text.toString().trim(),
                selVehicleNumber)
            if (date == LocalDate.now().toString()) LocationTrackingService.start(ctx)
            dlgBefore.dismiss()
        }
    }
}

internal fun TenkoFragment.showAfterDialog(date: String, existing: TenkoRecord?) {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val cGreen   = Color.parseColor("#4CAF50"); val cBgGreen = Color.parseColor("#E8F5E9")
    val cRed     = Color.parseColor("#F44336"); val cBgRed   = Color.parseColor("#FFEBEE")
    val cBlue    = Color.parseColor("#1E88E5"); val cBgBlue  = Color.parseColor("#E3F2FD")
    val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

    val scroll = ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20*dp).toInt(),(8*dp).toInt(),(20*dp).toInt(),(16*dp).toInt())
    }
    scroll.addView(root)

    fun sectionLabel(icon: String, text: String) = TextView(ctx).apply {
        this.text = "$icon  $text"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorOnSurface)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (16*dp).toInt(); it.bottomMargin = (6*dp).toInt() }
    }

    fun styledBtn(label: String) = Button(ctx).apply { text = label; isAllCaps = false; textSize = 13f }

    fun applyStyle(btn: Button, active: Boolean, ac: Int, bg: Int) {
        btn.setTextColor(if (active) Color.WHITE else ac)
        btn.background = GradientDrawable().apply { setColor(if (active) ac else bg); cornerRadius = 8*dp }
    }

    fun hRow(vararg btns: Button) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btns.forEach { addView(it) }
    }

    // ── 確認方法（3+2グリッド）
    root.addView(sectionLabel("📋", "確認方法"))
    val methods = listOf("対面", "電話", "IT点呼", "自己点呼", "その他")
    var selMethod: String = existing?.afterMethod?.takeIf { methods.contains(it) } ?: "対面"
    val mBtns = methods.map { m ->
        styledBtn(m).also { btn ->
            btn.layoutParams = LinearLayout.LayoutParams(0, (38*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        }
    }
    fun refreshMethod() = mBtns.forEachIndexed { i, btn -> applyStyle(btn, methods[i] == selMethod, cBlue, cBgBlue) }
    mBtns.forEachIndexed { i, btn -> btn.setOnClickListener { selMethod = methods[i]; refreshMethod() } }
    refreshMethod()
    root.addView(hRow(*mBtns.take(3).toTypedArray()))
    root.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (4*dp).toInt() }
        mBtns.drop(3).forEach { addView(it) }
        addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
    })

    // ── 時刻
    root.addView(sectionLabel("⏰", "時刻"))
    var selTime: String = existing?.afterTime ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()).orEmpty()
    val timeBtn = Button(ctx).apply {
        text = "🕐  $selTime"; isAllCaps = false; textSize = 16f; setTextColor(cBlue)
        background = GradientDrawable().apply { setColor(cBgBlue); cornerRadius = 8*dp }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46*dp).toInt())
    }
    timeBtn.setOnClickListener {
        val parts = selTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(ctx, { _, hour, min -> selTime = "%02d:%02d".format(hour, min); timeBtn.text = "🕐  $selTime" }, h, m, true).show()
    }
    root.addView(timeBtn)

    // ── 体調
    root.addView(sectionLabel("💪", "体調"))
    var healthOk = existing?.afterHealth != false
    val btnHOk = styledBtn("✓ 良好"); val btnHNg = styledBtn("✗ 不良")
    fun refreshHealth() { applyStyle(btnHOk, healthOk, cGreen, cBgGreen); applyStyle(btnHNg, !healthOk, cRed, cBgRed) }
    btnHOk.setOnClickListener { healthOk = true;  refreshHealth() }
    btnHNg.setOnClickListener { healthOk = false; refreshHealth() }
    for (btn in listOf(btnHOk, btnHNg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshHealth(); root.addView(hRow(btnHOk, btnHNg))

    // ── 疲労
    root.addView(sectionLabel("😴", "疲労"))
    var fatigueYes = existing?.afterFatigue == true
    val btnFNo = styledBtn("なし"); val btnFYes = styledBtn("あり")
    fun refreshFatigue() { applyStyle(btnFNo, !fatigueYes, cGreen, cBgGreen); applyStyle(btnFYes, fatigueYes, cRed, cBgRed) }
    btnFNo.setOnClickListener  { fatigueYes = false; refreshFatigue() }
    btnFYes.setOnClickListener { fatigueYes = true;  refreshFatigue() }
    for (btn in listOf(btnFNo, btnFYes)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshFatigue(); root.addView(hRow(btnFNo, btnFYes))

    // ── アルコール数値
    root.addView(sectionLabel("🍺", "アルコール数値"))
    val alcRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    val etAlcohol = EditText(ctx).apply {
        setText(existing?.afterAlcohol?.let { "%.2f".format(it) } ?: "0.00")
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#0D47A1"))
        background = GradientDrawable().apply {
            setColor(cBgBlue); cornerRadius = 8*dp
            setStroke((2*dp).toInt(), cBlue)
        }
        setPadding((16*dp).toInt(),(12*dp).toInt(),(16*dp).toInt(),(12*dp).toInt())
        layoutParams = LinearLayout.LayoutParams(0, (56*dp).toInt(), 1f)
    }
    alcRow.addView(etAlcohol)
    alcRow.addView(TextView(ctx).apply { text = " mg/L"; textSize = 16f; setTextColor(colorOnSurfaceVariant) })
    root.addView(alcRow)

    // ── 事故
    root.addView(sectionLabel("🚨", "事故"))
    var accidentYes = existing?.afterAccident == true
    val btnANo = styledBtn("なし"); val btnAYes = styledBtn("あり")
    fun refreshAccident() { applyStyle(btnANo, !accidentYes, cGreen, cBgGreen); applyStyle(btnAYes, accidentYes, cRed, cBgRed) }
    btnANo.setOnClickListener  { accidentYes = false; refreshAccident() }
    btnAYes.setOnClickListener { accidentYes = true;  refreshAccident() }
    for (btn in listOf(btnANo, btnAYes)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshAccident(); root.addView(hRow(btnANo, btnAYes))

    // ── 車両状態
    root.addView(sectionLabel("🚗", "車両状態"))
    var vehicleOk = existing?.afterVehicle != false
    val btnVOk = styledBtn("✓ 異常なし"); val btnVNg = styledBtn("⚠ 要確認")
    fun refreshVehicle() { applyStyle(btnVOk, vehicleOk, cGreen, cBgGreen); applyStyle(btnVNg, !vehicleOk, cRed, cBgRed) }
    btnVOk.setOnClickListener { vehicleOk = true;  refreshVehicle() }
    btnVNg.setOnClickListener { vehicleOk = false; refreshVehicle() }
    for (btn in listOf(btnVOk, btnVNg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
    refreshVehicle(); root.addView(hRow(btnVOk, btnVNg))

    // ── 指示事項
    root.addView(sectionLabel("📝", "指示事項"))
    val etInstruction = EditText(ctx).apply {
        hint = "なし"; setText(existing?.afterInstruction ?: "")
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 5
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(etInstruction)

    // ── 確認者
    root.addView(sectionLabel("👤", "確認者"))
    val etChecker = EditText(ctx).apply {
        hint = "氏名"; setText(existing?.afterChecker ?: AppSettings.getCheckerName(ctx))
        inputType = InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(etChecker)

    // ── 特記事項
    root.addView(sectionLabel("🗒", "特記事項"))
    val etNote = EditText(ctx).apply {
        hint = "なし"; setText(existing?.note ?: "")
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 5
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(etNote)

    val dlgAfter = MaterialAlertDialogBuilder(ctx)
        .setTitle("乗務後点呼  $date")
        .setView(scroll)
        .setPositiveButton("保存", null)
        .setNegativeButton("キャンセル", null).show()

    dlgAfter.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val alc = (etAlcohol.text.toString().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 9.99)
        if (alc > 0.15) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("⚠️ アルコール値が基準超過")
                .setMessage("アルコール値が基準を超えています（%.2f mg/L）。\nこのまま保存しますか？".format(alc))
                .setPositiveButton("保存する") { _, _ ->
                    viewModel.saveAfter(date, existing, selMethod, selTime,
                        healthOk, fatigueYes, alc, accidentYes, vehicleOk,
                        etInstruction.text.toString().trim(), etChecker.text.toString().trim(),
                        etNote.text.toString().trim())
                    if (date == LocalDate.now().toString()) LocationTrackingService.stop(ctx)
                    dlgAfter.dismiss()
                }
                .setNegativeButton("修正する", null).show()
        } else {
            viewModel.saveAfter(date, existing, selMethod, selTime,
                healthOk, fatigueYes, alc, accidentYes, vehicleOk,
                etInstruction.text.toString().trim(), etChecker.text.toString().trim(),
                etNote.text.toString().trim())
            if (date == LocalDate.now().toString()) LocationTrackingService.stop(ctx)
            dlgAfter.dismiss()
        }
    }
}
