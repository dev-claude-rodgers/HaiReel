package com.rodgers.haireel.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.model.FuelRecord
import com.rodgers.haireel.model.Vehicle
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.viewmodel.FuelViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

private val vehicleColors = listOf(
    Color.parseColor("#1565C0"),
    Color.parseColor("#2E7D32"),
    Color.parseColor("#C62828"),
    Color.parseColor("#7B1FA2"),
    Color.parseColor("#E65100"),
    Color.parseColor("#00838F"),
)

internal fun DailyReportFragment.showFuelRecordSheet(fuelViewModel: FuelViewModel) {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density

    val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
    val primaryColor     = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)

    val sheet = BottomSheetDialog(ctx)
    val root  = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceColor)
    }

    root.addMenuHeader("給油記録", dp, onSurface, onSurfaceVariant, outlineVariant) { sheet.dismiss() }

    // ── 状態
    var selectedVehicleId = -1L
    var latestRecords  = emptyList<FuelRecord>()
    var latestVehicles = emptyList<Vehicle>()

    // ── 車両フィルターチップ行
    val chipRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
    }
    val chipScroll = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addView(chipRow)
    }
    root.addView(chipScroll)

    val listContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(listContainer)

    // ── チップ生成ヘルパー
    fun makeChip(label: String, selected: Boolean, color: Int = primaryColor, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER; isClickable = true
            setTextColor(if (selected) Color.WHITE else color)
            background = GradientDrawable().apply {
                setColor(if (selected) color else Color.TRANSPARENT)
                setStroke((1.5f * dp).toInt(), color)
                cornerRadius = 20 * dp
            }
            setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.marginEnd = (8 * dp).toInt() }
            setOnClickListener { onClick() }
        }

    // rebuildAll は後で定義（前方参照のためvar）
    var rebuildAll: () -> Unit = {}

    // ── チップ再描画
    fun buildChips() {
        chipRow.removeAllViews()
        if (latestVehicles.isNotEmpty()) {
            chipRow.addView(makeChip("全車両", selectedVehicleId == -1L) {
                selectedVehicleId = -1L; rebuildAll()
            })
            latestVehicles.forEachIndexed { idx, v ->
                chipRow.addView(makeChip(v.name, selectedVehicleId == v.id,
                    vehicleColors[idx % vehicleColors.size]) {
                    selectedVehicleId = v.id; rebuildAll()
                })
            }
        }
        chipRow.addView(makeChip("🚗 車両管理", false, onSurfaceVariant) {
            showVehicleManagementDialog(fuelViewModel)
        })
    }

    // ── 給油記録リスト描画
    fun renderList(records: List<FuelRecord>) {
        listContainer.removeAllViews()

        if (records.isEmpty() && latestVehicles.isEmpty() && latestRecords.isEmpty()) {
            listContainer.addView(TextView(ctx).apply {
                text = "給油記録がありません\n右下の＋ボタンで追加してください"
                textSize = 14f; setTextColor(onSurfaceVariant)
                gravity = Gravity.CENTER
                setPadding(0, (32 * dp).toInt(), 0, (32 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return
        }

        // ── グラフ（月別給油金額）
        if (latestRecords.isNotEmpty()) {
            val year = LocalDate.now().year
            val datasets = buildFuelChartDatasets(latestRecords, latestVehicles, selectedVehicleId, year)
            if (datasets.any { it.values.any { v -> v > 0f } }) {
                listContainer.addView(TextView(ctx).apply {
                    text = "${year}年 月別給油金額"; textSize = 12f; setTextColor(onSurfaceVariant)
                    setPadding((16 * dp).toInt(), (12 * dp).toInt(), 0, 0)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                listContainer.addView(BarChartView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (170 * dp).toInt())
                        .also { it.setMargins((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt()) }
                    setData(datasets)
                })
                if (datasets.size > 1) {
                    val legendRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    datasets.forEach { ds ->
                        legendRow.addView(android.view.View(ctx).apply {
                            background = GradientDrawable().apply { setColor(ds.color); cornerRadius = 3 * dp }
                            layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt())
                                .also { it.gravity = Gravity.CENTER_VERTICAL }
                        })
                        legendRow.addView(TextView(ctx).apply {
                            text = " ${ds.label}"; textSize = 11f; setTextColor(onSurfaceVariant)
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                .also { it.marginEnd = (12 * dp).toInt() }
                        })
                    }
                    listContainer.addView(legendRow)
                }
            }
        }

        if (records.isEmpty()) {
            listContainer.addView(TextView(ctx).apply {
                text = "この車両の給油記録がありません"
                textSize = 14f; setTextColor(onSurfaceVariant)
                gravity = Gravity.CENTER
                setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return
        }

        val entries = fuelViewModel.entriesFrom(records)

        // 燃費サマリー
        val validEcos = entries.mapNotNull { it.fuelEconomy }
        if (validEcos.isNotEmpty()) {
            val avg   = validEcos.average()
            val best  = validEcos.max()
            val worst = validEcos.min()
            val summaryRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            fun summaryChip(label: String, value: String) = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply { text = label; textSize = 10f; setTextColor(onSurfaceVariant); gravity = Gravity.CENTER })
                addView(TextView(ctx).apply { text = value; textSize = 14f; setTextColor(onSurface); gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD })
            }
            summaryRow.addView(summaryChip("平均燃費", "%.1f km/L".format(avg)))
            summaryRow.addView(summaryChip("最良", "%.1f km/L".format(best)))
            summaryRow.addView(summaryChip("最低", "%.1f km/L".format(worst)))
            listContainer.addView(summaryRow)

            val autoSetRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            autoSetRow.addView(Button(ctx).apply {
                text = "↑ この燃費を設定に反映（平均 %.1f km/L）".format(avg)
                isAllCaps = false; textSize = 11f
                setTextColor(primaryColor); background = null
                setOnClickListener {
                    val newEco = avg.toFloat()
                    AppSettings.setFuelEfficiencyKmPerL(ctx, newEco)
                    fuelEfficiencyKmL = newEco
                    Toast.makeText(ctx, "燃費を %.1f km/L に設定しました".format(newEco), Toast.LENGTH_SHORT).show()
                }
            })
            listContainer.addView(autoSetRow)
        }

        // 仕切り線
        fun divider() = listContainer.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                .also { it.marginStart = (16 * dp).toInt() }
            setBackgroundColor(outlineVariant)
        })

        entries.reversed().forEach { entry ->
            val r = entry.record
            divider()
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                val ripple = android.util.TypedValue().also {
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }.resourceId
                setBackgroundResource(ripple); isClickable = true
            }

            val line1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            // 全車両モード時: 車両名バッジを表示
            if (selectedVehicleId == -1L && r.vehicleId != 0L) {
                val v = latestVehicles.find { it.id == r.vehicleId }
                if (v != null) {
                    val vIdx = latestVehicles.indexOf(v)
                    line1.addView(TextView(ctx).apply {
                        text = v.name; textSize = 10f; setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            setColor(vehicleColors[vIdx % vehicleColors.size]); cornerRadius = 4 * dp
                        }
                        setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            .also { it.gravity = Gravity.CENTER_VERTICAL; it.marginEnd = (8 * dp).toInt() }
                    })
                }
            }
            line1.addView(TextView(ctx).apply {
                text = r.date; textSize = 15f; setTextColor(onSurface); typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            line1.addView(TextView(ctx).apply {
                text = "%,d円".format(r.totalCost); textSize = 15f; setTextColor(onSurface); typeface = Typeface.DEFAULT_BOLD
            })
            row.addView(line1)

            val ecoText  = entry.fuelEconomy?.let { " → 燃費 %.1f km/L".format(it) } ?: ""
            val distText = entry.distanceKm?.let { "走行 ${it}km" } ?: ""
            val detail = buildString {
                append("%.1fL × %,d円/L".format(r.liters, r.pricePerLiter))
                if (r.odometer > 0) append("  ODO: %,dkm".format(r.odometer))
                if (distText.isNotEmpty()) append("  $distText")
                if (ecoText.isNotEmpty()) append(ecoText)
            }
            row.addView(TextView(ctx).apply {
                text = detail; textSize = 12f; setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt() }
            })
            if (r.note.isNotBlank()) {
                row.addView(TextView(ctx).apply { text = "📝 ${r.note}"; textSize = 12f; setTextColor(onSurfaceVariant) })
            }

            row.setOnClickListener { showFuelInputDialog(fuelViewModel, r, r.vehicleId) { rebuildAll() } }
            row.setOnLongClickListener {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("削除")
                    .setMessage("${r.date} の給油記録を削除しますか？")
                    .setPositiveButton("削除") { _, _ -> fuelViewModel.delete(r) }
                    .setNegativeButton("キャンセル", null)
                    .show()
                true
            }
            listContainer.addView(row)
        }
        divider()
    }

    // ── rebuildAll の実体を定義
    rebuildAll = {
        buildChips()
        val filtered = if (selectedVehicleId == -1L) latestRecords
                       else latestRecords.filter { it.vehicleId == selectedVehicleId }
        renderList(filtered)
    }

    // 追加ボタン
    val addBtn = com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(ctx).apply {
        text = "給油を記録"; setIconResource(android.R.drawable.ic_input_add)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .also { it.gravity = Gravity.END; it.setMargins((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt()) }
        setOnClickListener {
            showFuelInputDialog(fuelViewModel, null, selectedVehicleId) { rebuildAll() }
        }
    }
    root.addView(addBtn)
    root.addView(android.view.View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (16 * dp).toInt())
    })

    // records と vehicles の変化を監視して再描画
    lifecycleScope.launch {
        combine(fuelViewModel.records, fuelViewModel.vehicles) { r, v -> r to v }
            .collect { (r, v) ->
                latestRecords  = r
                latestVehicles = v
                rebuildAll()
            }
    }

    val sv = ScrollView(ctx).apply {
        addView(root)
        isFocusableInTouchMode = true
        descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
    }
    sheet.setContentView(sv)
    sheet.setOnShowListener {
        val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.behavior.isDraggable = false
    }
    sheet.show()
}

private fun buildFuelChartDatasets(
    allRecords: List<FuelRecord>,
    vehicles: List<Vehicle>,
    selectedVehicleId: Long,
    year: Int
): List<BarChartView.DataSet> {
    return if (selectedVehicleId == -1L) {
        val result = mutableListOf<BarChartView.DataSet>()
        vehicles.forEachIndexed { idx, vehicle ->
            val monthly = (1..12).map { m ->
                allRecords.filter { it.vehicleId == vehicle.id && it.date.startsWith("%04d-%02d".format(year, m)) }
                    .sumOf { it.totalCost }.toFloat()
            }
            if (monthly.any { it > 0f })
                result += BarChartView.DataSet(vehicle.name, monthly, vehicleColors[idx % vehicleColors.size])
        }
        val unassigned = allRecords.filter { it.vehicleId == 0L }
        if (unassigned.isNotEmpty()) {
            val monthly = (1..12).map { m ->
                unassigned.filter { it.date.startsWith("%04d-%02d".format(year, m)) }
                    .sumOf { it.totalCost }.toFloat()
            }
            if (monthly.any { it > 0f })
                result += BarChartView.DataSet("未設定", monthly, Color.parseColor("#9E9E9E"))
        }
        result
    } else {
        val vIdx  = vehicles.indexOfFirst { it.id == selectedVehicleId }
        val color = if (vIdx >= 0) vehicleColors[vIdx % vehicleColors.size] else Color.parseColor("#1565C0")
        val name  = vehicles.find { it.id == selectedVehicleId }?.name ?: "車両"
        val monthly = (1..12).map { m ->
            allRecords.filter { it.vehicleId == selectedVehicleId && it.date.startsWith("%04d-%02d".format(year, m)) }
                .sumOf { it.totalCost }.toFloat()
        }
        listOf(BarChartView.DataSet(name, monthly, color))
    }
}

private fun DailyReportFragment.showVehicleManagementDialog(fuelViewModel: FuelViewModel) {
    val ctx      = requireContext()
    val dp       = ctx.resources.displayMetrics.density
    val onSurface = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val vehicles  = fuelViewModel.vehicles.value

    val scroll = ScrollView(ctx)
    val root   = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
    }
    scroll.addView(root)

    if (vehicles.isEmpty()) {
        root.addView(TextView(ctx).apply {
            text = "車両が登録されていません"; textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, (24 * dp).toInt(), 0, (24 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    } else {
        vehicles.forEachIndexed { idx, vehicle ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * dp).toInt())
            }
            row.addView(android.view.View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(vehicleColors[idx % vehicleColors.size])
                }
                layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt())
                    .also { it.gravity = Gravity.CENTER_VERTICAL; it.marginEnd = (10 * dp).toInt() }
            })
            row.addView(TextView(ctx).apply {
                text = vehicle.name; textSize = 15f; setTextColor(onSurface)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(ctx).apply {
                text = "編集"; textSize = 13f
                setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorPrimary))
                setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                setOnClickListener { showVehicleEditDialog(fuelViewModel, vehicle) }
            })
            row.addView(TextView(ctx).apply {
                text = "削除"; textSize = 13f; setTextColor(Color.parseColor("#C62828"))
                setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                setOnClickListener {
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle("車両を削除")
                        .setMessage("「${vehicle.name}」を削除しますか？\n関連する給油記録の車両設定は「未設定」になります。")
                        .setPositiveButton("削除") { _, _ -> fuelViewModel.deleteVehicle(vehicle) }
                        .setNegativeButton("キャンセル", null)
                        .show()
                }
            })
            root.addView(row)
        }
    }

    val builder = MaterialAlertDialogBuilder(ctx)
        .setTitle("車両管理")
        .setView(scroll)
        .setNegativeButton("閉じる", null)
    if (vehicles.size < 3) {
        builder.setPositiveButton("+ 車両を追加") { _, _ -> showVehicleEditDialog(fuelViewModel, null) }
    } else {
        builder.setMessage("車両は3台まで登録できます")
    }
    builder.show()
}

private fun DailyReportFragment.showVehicleEditDialog(fuelViewModel: FuelViewModel, existing: Vehicle?) {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density

    val nameInput = EditText(ctx).apply {
        setText(existing?.name ?: "")
        hint = "例: 軽トラA"
        inputType = InputType.TYPE_CLASS_TEXT
        setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
    }

    MaterialAlertDialogBuilder(ctx)
        .setTitle(if (existing == null) "車両を追加" else "車両を編集")
        .setView(nameInput)
        .setPositiveButton("保存") { _, _ ->
            val name = nameInput.text.toString().trim()
            if (name.isNotBlank()) {
                fuelViewModel.upsertVehicle((existing ?: Vehicle(name = name)).copy(name = name))
                Toast.makeText(ctx, if (existing == null) "「${name}」を追加しました" else "「${name}」を更新しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "車両名を入力してください", Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

private fun DailyReportFragment.showFuelInputDialog(
    fuelViewModel: FuelViewModel,
    existing: FuelRecord?,
    defaultVehicleId: Long = -1L,
    onSaved: () -> Unit
) {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density

    var selectedDate = existing?.date ?: LocalDate.now().toString()

    val scroll = ScrollView(ctx)
    val root   = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
    }
    scroll.addView(root)

    fun label(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 12f
        setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .also { it.topMargin = (10 * dp).toInt() }
    }
    fun numInput(hint: String, init: String = "") = EditText(ctx).apply {
        this.hint = hint; setText(init)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    // ── 車両選択
    val vehicles = fuelViewModel.vehicles.value
    var selectedVehicleId: Long = when {
        existing != null      -> existing.vehicleId
        defaultVehicleId >= 0 -> defaultVehicleId
        vehicles.size == 1    -> vehicles[0].id
        else                  -> 0L
    }

    if (vehicles.isNotEmpty()) {
        val vehicleOptions = listOf(Pair(0L, "未設定")) + vehicles.map { Pair(it.id, it.name) }
        root.addView(label("車両"))
        root.addView(Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item,
                vehicleOptions.map { it.second })
            val idx = vehicleOptions.indexOfFirst { it.first == selectedVehicleId }.coerceAtLeast(0)
            setSelection(idx)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    selectedVehicleId = vehicleOptions[pos].first
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        })
    }

    // 日付ボタン
    val btnDate = Button(ctx).apply {
        isAllCaps = false; textSize = 15f; text = "📅  $selectedDate"; background = null
        setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorPrimary))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setOnClickListener {
            val parts = selectedDate.split("-").map { it.toInt() }
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                text = "📅  $selectedDate"
            }, parts[0], parts[1] - 1, parts[2]).show()
        }
    }
    root.addView(label("給油日"))
    root.addView(btnDate)

    val litersIn    = numInput("例: 30.5", existing?.liters?.let { "%.1f".format(it) } ?: "")
    val priceIn     = numInput("例: 175",  existing?.pricePerLiter?.toString() ?: "")
    val totalCostIn = numInput("例: 5337", existing?.totalCost?.toString() ?: "")
    val odometerIn  = numInput("例: 45230", existing?.odometer?.takeIf { it > 0 }?.toString() ?: "")
    val noteIn      = EditText(ctx).apply {
        hint = "メモ（任意）"; setText(existing?.note ?: "")
        inputType = InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    val autoCalc = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            val l = litersIn.text.toString().toFloatOrNull() ?: return
            val p = priceIn.text.toString().toIntOrNull() ?: return
            val calc = (l * p).toInt()
            if (totalCostIn.text.toString().toIntOrNull() != calc) totalCostIn.setText(calc.toString())
        }
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
    }
    litersIn.addTextChangedListener(autoCalc)
    priceIn.addTextChangedListener(autoCalc)

    root.addView(label("給油量（L）"));    root.addView(litersIn)
    root.addView(label("単価（円/L）"));   root.addView(priceIn)
    root.addView(label("合計金額（円）")); root.addView(totalCostIn)
    root.addView(label("オドメーター（km）（任意）")); root.addView(odometerIn)
    root.addView(label("メモ（任意）"));   root.addView(noteIn)

    MaterialAlertDialogBuilder(ctx)
        .setTitle(if (existing == null) "給油を記録" else "給油記録を編集")
        .setView(scroll)
        .setPositiveButton("保存", null)
        .setNegativeButton("キャンセル", null)
        .show()
        .apply {
            getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val liters = litersIn.text.toString().toFloatOrNull()
                val price  = priceIn.text.toString().toIntOrNull()
                val total  = totalCostIn.text.toString().toIntOrNull()
                if (liters == null || liters <= 0f) {
                    Toast.makeText(ctx, "給油量を入力してください", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                if (price == null || price <= 0) {
                    Toast.makeText(ctx, "単価を入力してください", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                fuelViewModel.upsert(FuelRecord(
                    id            = existing?.id ?: 0L,
                    date          = selectedDate,
                    liters        = liters,
                    pricePerLiter = price,
                    totalCost     = total ?: (liters * price).toInt(),
                    odometer      = odometerIn.text.toString().toIntOrNull() ?: 0,
                    note          = noteIn.text.toString().trim(),
                    vehicleId     = selectedVehicleId
                ))
                Toast.makeText(ctx, "給油記録を保存しました", Toast.LENGTH_SHORT).show()
                onSaved()
                dismiss()
            }
        }
}
