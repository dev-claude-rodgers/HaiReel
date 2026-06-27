package com.rodgers.routist.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.viewmodel.*
import com.rodgers.routist.util.SignatureStorage
import com.rodgers.routist.viewmodel.*
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.viewmodel.*
import kotlinx.coroutines.launch

internal fun DailyReportFragment.showPrivacyPolicyDialog() {
    if (!isAdded) return
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density

    val text = """
プライバシーポリシー
最終更新：2026年6月

1. 収集する情報
本アプリ（HaiReel）は、以下の情報をお客様の端末内にのみ保存します。
・入力された配達先住所・メモ
・日報（稼働日・件数・走行距離）
・アプリ設定（氏名・単価・事業者情報）

2. 情報の利用目的
収集した情報は、ルート管理・日報作成・Excel出力のためにのみ使用します。

3. 外部サービスの利用
住所の地図表示・ルート案内のため、Google Maps API を使用しています。
入力した住所はGoogle のサーバーに送信される場合があります。
Google のプライバシーポリシーは https://policies.google.com/privacy をご参照ください。

4. 第三者への提供
お客様の情報を開発者または第三者に送信・販売・共有することはありません。
すべてのデータはお客様の端末内にのみ保存されます。

5. データの削除
アプリをアンインストールすることで、端末に保存されたすべてのデータが削除されます。

6. お問い合わせ
本ポリシーに関するご質問は、アプリ内の「アプリについて」よりご連絡ください。
    """.trimIndent()

    val tv = android.widget.TextView(ctx).apply {
        this.text = text
        textSize = 14f
        setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
        setLineSpacing(0f, 1.4f)
    }
    val scroll = android.widget.ScrollView(ctx).apply {
        setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurface))
        addView(tv)
    }

    MaterialAlertDialogBuilder(ctx)
        .setTitle("プライバシーポリシー")
        .setView(scroll)
        .setPositiveButton("閉じる", null)
        .show()
}

internal fun DailyReportFragment.showAppSettingsDialog() {
    if (!isAdded) return
    val ctx  = requireContext()
    val settingsGroupId = reportViewModel.assignmentId.value
    val groupLabel = deliveryViewModel.currentGroup()?.name?.let { "「$it」の設定" } ?: "アプリ設定"
    val dp   = ctx.resources.displayMetrics.density
    val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

    val scroll = ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
    }
    scroll.addView(root)

    val colorOnSurface = com.google.android.material.color.MaterialColors.getColor(
        ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    val colorOnSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(
        ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
    val colorOutline = com.google.android.material.color.MaterialColors.getColor(
        ctx, com.google.android.material.R.attr.colorOutline, Color.GRAY)

    fun section(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 14f; setTextColor(colorOnSurface)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (18 * dp).toInt(); it.bottomMargin = (6 * dp).toInt() }
    }
    fun field(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 13f; setTextColor(colorOnSurfaceVariant)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    }

    root.addView(section("── 表示設定"))
    val darkGroup = android.widget.RadioGroup(ctx)
    val rbDarkSystem = android.widget.RadioButton(ctx).apply { text = "システム設定に従う"; id = View.generateViewId() }
    val rbDarkLight  = android.widget.RadioButton(ctx).apply { text = "ライトモード"; id = View.generateViewId() }
    val rbDarkDark   = android.widget.RadioButton(ctx).apply { text = "ダークモード"; id = View.generateViewId() }
    darkGroup.addView(rbDarkSystem); darkGroup.addView(rbDarkLight); darkGroup.addView(rbDarkDark)
    when (AppSettings.getDarkMode(ctx)) {
        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO  -> rbDarkLight.isChecked = true
        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> rbDarkDark.isChecked = true
        else -> rbDarkSystem.isChecked = true
    }
    root.addView(darkGroup)

    root.addView(section("── 雇用形態"))
    val empGroup      = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.HORIZONTAL }
    val rbContractor  = android.widget.RadioButton(ctx).apply { text = "業務委託"; id = View.generateViewId() }
    val rbEmployee    = android.widget.RadioButton(ctx).apply { text = "正社員・パート"; id = View.generateViewId() }
    empGroup.addView(rbContractor); empGroup.addView(rbEmployee)
    if (AppSettings.getEmploymentType(ctx, settingsGroupId) == "employee") rbEmployee.isChecked = true
    else rbContractor.isChecked = true
    root.addView(empGroup)

    root.addView(section("── 事業者情報"))
    root.addView(field("事業者名"))
    val etCompany = EditText(ctx).apply {
        hint = "例: 〇〇運送株式会社"; inputType = InputType.TYPE_CLASS_TEXT
        setText(AppSettings.getCompanyName(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etCompany)

    root.addView(field("車番（ナンバー）"))
    val etVehicle = EditText(ctx).apply {
        hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
        setText(AppSettings.getVehicleNumber(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etVehicle)

    root.addView(section("── 点呼設定"))
    root.addView(field("ドライバー名"))
    val etDriver = EditText(ctx).apply {
        hint = "氏名"; inputType = InputType.TYPE_CLASS_TEXT
        setText(AppSettings.getDriverName(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etDriver)

    root.addView(field("確認者（運行管理者）名"))
    val etChecker = EditText(ctx).apply {
        hint = "乗務員本人（自己点呼時）"
        inputType = InputType.TYPE_CLASS_TEXT
        setText(AppSettings.getCheckerName(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etChecker)

    root.addView(section("── セキュリティ設定"))
    val swAppLock = run {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (4 * dp).toInt() }
        }
        row.addView(TextView(ctx).apply {
            text = "アプリロック（指紋・顔・PIN）"; textSize = 15f
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        val sw = androidx.appcompat.widget.SwitchCompat(ctx).apply {
            isChecked = AppSettings.isAppLockEnabled(ctx)
        }
        row.addView(sw); root.addView(row); sw
    }

    root.addView(field("ロックまでの時間（分）"))
    val etLockTimeout = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "30"
        setText(AppSettings.getLockTimeoutMinutes(ctx).toString())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etLockTimeout)
    root.addView(TextView(ctx).apply {
        text = "バックグラウンド移行または無操作でこの時間が経過するとロックされます"
        textSize = 12f; setTextColor(colorOutline)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })

    root.addView(field("バックアップパスワード（空欄で暗号化なし）"))
    val etBackupPw = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = "パスワードを設定"
        setText(AppSettings.getBackupPassword(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etBackupPw)

    MaterialAlertDialogBuilder(ctx)
        .setTitle(groupLabel)
        .setView(scroll)
        .setPositiveButton("保存") { _, _ ->
            AppSettings.setEmploymentType(ctx, settingsGroupId, if (rbEmployee.isChecked) "employee" else "contractor")
            AppSettings.setCompanyName(ctx, etCompany.text.toString().trim())
            AppSettings.setVehicleNumber(ctx, etVehicle.text.toString().trim())
            AppSettings.setDriverName(ctx, etDriver.text.toString().trim())
            AppSettings.setCheckerName(ctx, etChecker.text.toString().trim())
            val darkMode = when {
                rbDarkDark.isChecked  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                rbDarkLight.isChecked -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                else                  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppSettings.setDarkMode(ctx, darkMode)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(darkMode)
            AppSettings.setAppLockEnabled(ctx, swAppLock.isChecked)
            AppSettings.setLockTimeoutMinutes(ctx, etLockTimeout.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 30)
            AppSettings.setBackupPassword(ctx, etBackupPw.text.toString())
            Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.showSignatureDialog(type: String, label: String) {
    if (!isAdded) return
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
    }
    val sigView = SignatureView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, (160 * dp).toInt())
    }
    root.addView(sigView)
    root.addView(TextView(ctx).apply {
        text = "↑ここに署名してください"; textSize = 12f; gravity = Gravity.CENTER
        setTextColor(Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (4 * dp).toInt() }
    })
    if (SignatureStorage.exists(ctx, type)) {
        root.addView(android.widget.Button(ctx).apply {
            text = "保存済み署名を削除"; isAllCaps = false; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (8 * dp).toInt() }
            setOnClickListener {
                SignatureStorage.clear(ctx, type)
                Toast.makeText(ctx, "${label}の署名を削除しました", Toast.LENGTH_SHORT).show()
            }
        })
    }
    MaterialAlertDialogBuilder(ctx)
        .setTitle("${label}の署名")
        .setView(root)
        .setPositiveButton("保存") { _, _ ->
            if (sigView.isEmpty()) Toast.makeText(ctx, "署名を入力してから保存してください", Toast.LENGTH_SHORT).show()
            else {
                SignatureStorage.save(ctx, type, sigView.getBitmap())
                Toast.makeText(ctx, "${label}の署名を保存しました", Toast.LENGTH_SHORT).show()
            }
        }
        .setNeutralButton("クリア") { _, _ -> sigView.clear() }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DailyReportFragment.showAssignmentSummarySheet() {
    if (!isAdded) return
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val ym    = reportViewModel.yearMonth.value
    val (y, m) = ym.split("-").map { it.toInt() }
    val groups = deliveryViewModel.groups.value

    lifecycleScope.launch {
        val allRecords = reportViewModel.allRecordsForMonth(ym)
        if (!isAdded) return@launch

        val byAssignment = allRecords
            .groupBy { it.assignmentId }
            .filter { (assignmentId, _) ->
                assignmentId.isBlank() || groups.any { it.id == assignmentId }
            }

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            setPadding(0, 0, 0, (24 * dp).toInt())
        }

        root.addView(TextView(ctx).apply {
            text = "${y}年${m}月 案件別サマリー"
            textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
        })
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        fun addDivider(indent: Boolean) = root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.marginStart = if (indent) (20 * dp).toInt() else 0 }
        })

        fun stat(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 14f; setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        fun addRow(label: String, colorHex: String?,
                   days: Int, deliv: Int, income: Int, fuel: Int,
                   isTotal: Boolean = false) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
            }
            val labelRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            if (colorHex != null) {
                labelRow.addView(android.view.View(ctx).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        try { setColor(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) {}
                    }
                    layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt())
                        .also { it.marginEnd = (10 * dp).toInt(); it.gravity = android.view.Gravity.CENTER_VERTICAL }
                })
            }
            labelRow.addView(TextView(ctx).apply {
                text = label
                textSize = if (isTotal) 15f else 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(onSurface)
            })
            row.addView(labelRow)
            val statsRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (6 * dp).toInt() }
            }
            statsRow.addView(stat("${days}日稼働"))
            statsRow.addView(stat("配達 ${deliv}件"))
            if (income > 0) statsRow.addView(stat("%,d円".format(income)))
            if (fuel   > 0) statsRow.addView(stat("燃料 %,d円".format(fuel)))
            row.addView(statsRow)
            root.addView(row)
            addDivider(indent = !isTotal)
        }

        if (byAssignment.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "この月の記録はありません"; textSize = 15f; setTextColor(onSurfaceVariant)
                gravity = android.view.Gravity.CENTER
                setPadding(0, (40 * dp).toInt(), 0, (40 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        } else {
            var totalDays = 0; var totalDeliv = 0; var totalIncome = 0; var totalFuel = 0

            byAssignment.forEach { (assignmentId, records) ->
                val group  = groups.find { it.id == assignmentId }
                val label  = group?.name ?: "未分類"
                val days   = records.size
                val deliv  = records.sumOf { it.deliveryCount }
                val income = records.sumOf { it.income }
                val fuel   = records.sumOf { it.fuelCost }
                totalDays   += days; totalDeliv  += deliv
                totalIncome += income; totalFuel += fuel
                addRow(label, group?.colorHex, days, deliv, income, fuel)
            }

            if (byAssignment.size > 1) {
                addRow("合計", null, totalDays, totalDeliv, totalIncome, totalFuel, isTotal = true)
            }
        }

        val sv = android.widget.ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(sv)
        sheet.show()
    }
}

internal fun DailyReportFragment.showFareCalculationDialog() {
    val ctx = requireContext()
    val dp = ctx.resources.displayMetrics.density

    // AppSettings から読み込む（自動計算ボタンと同じ設定を参照）
    fuelPricePerL     = com.rodgers.routist.util.AppSettings.getFuelPricePerLiter(ctx)
    fuelEfficiencyKmL = com.rodgers.routist.util.AppSettings.getFuelEfficiencyKmPerL(ctx)
    val fuelPrefs = ctx.getSharedPreferences("fuel_settings", android.content.Context.MODE_PRIVATE)
    vehicleTypeName   = fuelPrefs.getString("vehicle_type",   vehicleTypeName) ?: vehicleTypeName
    fuelTypeName      = fuelPrefs.getString("fuel_type_name", fuelTypeName)    ?: fuelTypeName

    val scroll = android.widget.ScrollView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
    }
    scroll.addView(root)

    fun label(text: String) = android.widget.TextView(ctx).apply {
        this.text = text; textSize = 12f
        setTextColor(android.graphics.Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    }

    root.addView(label("車種"))
    val vehicleNames = DailyReportFragment.VEHICLE_PRESETS.keys.toList()
    val vehicleSpinner = android.widget.Spinner(ctx).apply {
        adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        setSelection(vehicleNames.indexOf(vehicleTypeName).coerceAtLeast(0))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(vehicleSpinner)

    root.addView(label("燃料種別"))
    val fuelTypeNames = DailyReportFragment.FUEL_PRICES.keys.toList()
    val fuelTypeRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    val fuelTypeBtns = fuelTypeNames.map { name ->
        android.widget.Button(ctx).apply {
            text = name; isAllCaps = false; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = (4 * dp).toInt() }
        }
    }
    fuelTypeBtns.forEach { fuelTypeRow.addView(it) }
    root.addView(fuelTypeRow)

    val fuelRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    val fuelPriceInput = android.widget.EditText(ctx).apply {
        hint = "168"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setText(fuelPricePerL.toString())
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
    }
    fuelRow.addView(fuelPriceInput)
    fuelRow.addView(android.widget.TextView(ctx).apply { text = "円/L  "; textSize = 12f })
    val fuelEffInput = android.widget.EditText(ctx).apply {
        hint = "12.0"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText("%.1f".format(fuelEfficiencyKmL))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
    }
    fuelRow.addView(fuelEffInput)
    fuelRow.addView(android.widget.TextView(ctx).apply { text = "km/L  "; textSize = 12f })
    val fuelSaveBtn = android.widget.Button(ctx).apply {
        text = "保存"; isAllCaps = false; textSize = 11f
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    fuelRow.addView(fuelSaveBtn)
    root.addView(fuelRow)

    fun updateFuelTypeBtns(selected: String) {
        fuelTypeBtns.forEachIndexed { i, btn ->
            val on = fuelTypeNames[i] == selected
            btn.setTextColor(if (on) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#0288D1"))
            btn.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (on) android.graphics.Color.parseColor("#0288D1") else android.graphics.Color.parseColor("#E3F2FD"))
                cornerRadius = 6 * dp
            }
        }
    }
    updateFuelTypeBtns(fuelTypeName)

    fuelTypeBtns.forEachIndexed { i, btn ->
        btn.setOnClickListener {
            fuelTypeName = fuelTypeNames[i]
            val price = DailyReportFragment.FUEL_PRICES[fuelTypeName] ?: fuelPricePerL
            fuelPriceInput.setText(price.toString())
            updateFuelTypeBtns(fuelTypeName)
            fuelPrefs.edit().putString("fuel_type_name", fuelTypeName).putInt("fuel_price_yen", price).apply()
            fuelPricePerL = price
        }
    }

    var spinnerReady = false
    vehicleSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
            if (!spinnerReady) { spinnerReady = true; return }
            vehicleTypeName = vehicleNames[pos]
            val preset = DailyReportFragment.VEHICLE_PRESETS[vehicleTypeName] ?: return
            fuelTypeName = preset.first
            val eff = preset.second
            val price = DailyReportFragment.FUEL_PRICES[fuelTypeName] ?: fuelPricePerL
            fuelEffInput.setText("%.1f".format(eff))
            fuelPriceInput.setText(price.toString())
            updateFuelTypeBtns(fuelTypeName)
            fuelEfficiencyKmL = eff; fuelPricePerL = price
            com.rodgers.routist.util.AppSettings.setFuelPricePerLiter(ctx, price)
            com.rodgers.routist.util.AppSettings.setFuelEfficiencyKmPerL(ctx, eff)
            fuelPrefs.edit().putString("vehicle_type", vehicleTypeName).putString("fuel_type_name", fuelTypeName).apply()
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }

    fuelSaveBtn.setOnClickListener {
        val p = fuelPriceInput.text.toString().toIntOrNull()
        val e = fuelEffInput.text.toString().toFloatOrNull()
        when {
            p == null || p <= 0 -> fuelPriceInput.error = "ガソリン単価を入力してください（1円以上）"
            e == null || e <= 0f -> fuelEffInput.error = "燃費を入力してください（0より大きい値）"
            else -> {
                fuelPricePerL = p; fuelEfficiencyKmL = e
                com.rodgers.routist.util.AppSettings.setFuelPricePerLiter(ctx, p)
                com.rodgers.routist.util.AppSettings.setFuelEfficiencyKmPerL(ctx, e)
                fuelPrefs.edit().putString("vehicle_type", vehicleTypeName).putString("fuel_type_name", fuelTypeName).apply()
                Toast.makeText(ctx, "保存しました（${vehicleTypeName} / ${fuelTypeName} / ¥${p}/L / ${e}km/L）", Toast.LENGTH_SHORT).show()
            }
        }
    }

    MaterialAlertDialogBuilder(ctx)
        .setTitle("燃料費設定")
        .setView(scroll)
        .setNegativeButton("閉じる", null)
        .show()
}
