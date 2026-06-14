package com.rodgers.routist.ui

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rodgers.routist.util.themeColor
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentDailyReportBinding
import com.rodgers.routist.excel.ExcelGenerator
import com.rodgers.routist.model.ReportPattern
import com.rodgers.routist.model.WorkRecord
import com.rodgers.routist.util.BackupManager
import com.rodgers.routist.util.GeocodingClient
import com.rodgers.routist.util.LicenseManager
import com.rodgers.routist.util.PatternStorage
import com.rodgers.routist.util.SignatureStorage
import com.rodgers.routist.viewmodel.DeliveryViewModel
import com.rodgers.routist.viewmodel.ReportViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyReportFragment : Fragment() {

    data class DayEntry(val date: String, val record: WorkRecord?)

    private var _binding: FragmentDailyReportBinding? = null
    private val binding get() = _binding!!

    private val deliveryViewModel: DeliveryViewModel by activityViewModels()
    private val reportViewModel: ReportViewModel by viewModels()
    private val tenkoViewModel: com.rodgers.routist.viewmodel.TenkoViewModel by activityViewModels()
    private lateinit var adapter: DayEntryAdapter

    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    private var fuelPricePerL:     Int    = 168
    private var fuelEfficiencyKmL: Float  = 12.0f
    private var vehicleTypeName:   String = "軽自動車"
    private var fuelTypeName:      String = "レギュラー"

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val ctx = context ?: return@registerForActivityResult
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    BackupManager.restoreBackup(ctx, uri)
                    if (isAdded) Toast.makeText(ctx, "バックアップから復元しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    if (isAdded) Toast.makeText(ctx, "復元エラー: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DayEntryAdapter(
            onTap    = { entry -> openEditForDate(entry.date) },
            onDelete = { record -> confirmDelete(record) },
            onShare  = { record -> shareRecord(record) }
        )

        binding.recyclerReport.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = this@DailyReportFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        binding.btnPrevMonth.setOnClickListener { reportViewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { reportViewModel.nextMonth() }
        binding.btnMenu.setOnClickListener     { showReportMenu() }

        // アクティブパターンの締め日をViewModelに反映
        val initPattern = PatternStorage.ensureDefault(requireContext())
        reportViewModel.setClosingDay(initPattern.closingDay)

        // 選択中の案件IDを日報ViewModelに連動させ、案件名バーと帳票パターンを更新する
        deliveryViewModel.currentGroupId.observe(viewLifecycleOwner) { groupId ->
            reportViewModel.setAssignmentId(groupId ?: "")
            val group = deliveryViewModel.currentGroup()
            // 案件名バー
            if (group != null && group.name.isNotBlank()) {
                binding.tvAssignment.visibility = View.VISIBLE
                binding.tvAssignment.text = "📦 ${group.name}"
                try {
                    val color = android.graphics.Color.parseColor(group.colorHex)
                    binding.tvAssignment.setBackgroundColor(
                        android.graphics.Color.argb(60,
                            android.graphics.Color.red(color),
                            android.graphics.Color.green(color),
                            android.graphics.Color.blue(color))
                    )
                } catch (_: Exception) {
                    binding.tvAssignment.setBackgroundColor(android.graphics.Color.parseColor("#222222"))
                }
            } else {
                binding.tvAssignment.visibility = View.GONE
            }
            // 案件に紐づいた帳票パターンを自動適用
            val linkedPatternId = group?.patternId ?: -1
            if (linkedPatternId != -1) {
                val pattern = PatternStorage.get(requireContext(), linkedPatternId)
                if (pattern != null) {
                    PatternStorage.setActiveId(requireContext(), linkedPatternId)
                    reportViewModel.setClosingDay(pattern.closingDay)
                }
            }
        }

        // 月ラベル
        lifecycleScope.launch {
            reportViewModel.yearMonth.collect { ym ->
                val (y, m) = ym.split("-").map { it.toInt() }
                binding.tvMonth.text = "${y}年${m}月"
            }
        }

        // 日報リスト更新
        lifecycleScope.launch {
            combine(reportViewModel.records, reportViewModel.yearMonth) { records, ym ->
                Pair(records, ym)
            }.collect { (records, ym) ->
                val days = generateDayEntries(records, ym, 0)
                adapter.submitList(days)
                updateSummary(records)
                binding.tvEmptyReport.visibility  = View.GONE
                binding.recyclerReport.visibility = View.VISIBLE
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val nowYm    = todayStr.substring(0, 7)
                if (ym == nowYm) {
                    val todayIdx = days.indexOfFirst { it.date == todayStr }
                    if (todayIdx >= 0) binding.recyclerReport.scrollToPosition(todayIdx)
                } else {
                    binding.recyclerReport.scrollToPosition(0)
                }
            }
        }


    }

    private fun generateDayEntries(
        records: List<WorkRecord>, yearMonth: String, @Suppress("UNUSED_PARAMETER") closingDay: Int
    ): List<DayEntry> {
        val (y, m) = yearMonth.split("-").map { it.toInt() }
        val startDate = LocalDate.of(y, m, 1)
        val endDate   = java.time.YearMonth.of(y, m).atEndOfMonth()
        val recordMap = records.associateBy { it.date }
        return generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .map { date ->
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                DayEntry(dateStr, recordMap[dateStr])
            }.toList()
    }

    private fun updateSummary(records: List<WorkRecord>) {
        if (!isAdded) return
        binding.tvSummaryDays.text       = "${records.size}日稼働"
        binding.tvSummaryDeliveries.text = "配達 ${records.sumOf { it.deliveryCount }}件"
        binding.tvSummaryDistance.text   = "走行 ${"%.1f".format(records.sumOf { it.distanceKm.toDouble() })}km"
        val ctx = requireContext()
        val gid = reportViewModel.assignmentId.value
        val unitPrice   = com.rodgers.routist.util.AppSettings.getUnitPrice(ctx, gid)
        val paymentType = com.rodgers.routist.util.AppSettings.getPaymentType(ctx, gid)
        val incomeEnabled = paymentType != 2 && !(paymentType == 1 && unitPrice == 0)
        val totalIncome = records.sumOf { it.income }
        if (incomeEnabled && totalIncome > 0) {
            binding.tvSummaryIncome.visibility = View.VISIBLE
            binding.tvSummaryIncome.text = "報酬 ${"%,d".format(totalIncome)}円"
        } else {
            binding.tvSummaryIncome.visibility = View.GONE
        }
        val totalFuel = records.sumOf { it.fuelCost }
        if (totalFuel > 0) {
            binding.tvSummaryFuel.visibility = View.VISIBLE
            binding.tvSummaryFuel.text = "燃料 ${"%,d".format(totalFuel)}円"
        } else {
            binding.tvSummaryFuel.visibility = View.GONE
        }
    }

    // ─────── 今日ボタン ───────
    private fun openTodayDialog() {
        reportViewModel.jumpToToday()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date())
        openEditForDate(today)
    }

    // ─────── 任意の日付を編集 ───────
    private fun openEditForDate(date: String) {
        lifecycleScope.launch {
            val existing = reportViewModel.recordForDate(date)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date())
            val base = if (existing != null) existing
            else if (date == today) {
                val deliveries = deliveryViewModel.deliveries.value ?: emptyList()
                WorkRecord(
                    date          = date,
                    deliveryCount = deliveries.count { it.isCompleted },
                    packageCount  = deliveries.filter { it.isCompleted }.sumOf { it.packageCount }
                )
            } else WorkRecord(date = date)
            if (!isAdded) return@launch
            showEditDialog(base)
        }
    }

    // ─────── 編集ダイアログ ───────
    private fun showEditDialog(record: WorkRecord) {
        if (!isAdded) return
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        var selectedDate  = record.date
        var startH        = record.startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 9
        var startM        = record.startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        var endH          = record.endTime.split(":").getOrNull(0)?.toIntOrNull() ?: 18
        var endM          = record.endTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        var endDateOffset = record.endDateOffset

        val alcOptions = listOf("未実施", "○ 正常", "× 異常")
        val alcValues  = listOf("",       "○",      "×")
        var alcIdx     = alcValues.indexOf(record.alcCheck).coerceAtLeast(0)

        val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
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

        // ── 日付
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

        val isContractor = com.rodgers.routist.util.AppSettings.getEmploymentType(ctx, reportViewModel.assignmentId.value) == "contractor"
        // 業務委託の場合: 既存データに時刻があればデフォルトで展開
        val hasExistingTime = record.startTime.isNotBlank() || record.endTime.isNotBlank()
        var showTime = !isContractor || hasExistingTime

        // 日またぎボタンを先に宣言して applyOffsetStyle を定義可能にする
        val offsetBtnList    = mutableListOf<android.widget.Button>()
        val offsetSelectedColor = ContextCompat.getColor(ctx, R.color.colorReportPrimary)
        val offsetUnselectColor = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val offsetTextUnselect  = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        fun applyOffsetStyle() {
            offsetBtnList.forEachIndexed { j, b ->
                val sel = (j == endDateOffset)
                (b.background as? android.graphics.drawable.GradientDrawable)
                    ?.setColor(if (sel) offsetSelectedColor else offsetUnselectColor)
                b.setTextColor(if (sel) android.graphics.Color.WHITE else offsetTextUnselect)
            }
        }

        val timeLbl = label("開始時刻 ／ 終了時刻")
        val timeRow = LinearLayout(ctx).apply {
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

        // 業務委託のみ「時刻を入力する」チェックボックスを表示
        if (isContractor) {
            val cbTime = CheckBox(ctx).apply {
                text = "時刻を入力する"
                isChecked = showTime
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (10 * dp).toInt() }
                setOnCheckedChangeListener { _, checked ->
                    showTime = checked; applyTimeVisibility()
                }
            }
            root.addView(cbTime)
        }

        val btnStart = android.widget.Button(ctx).apply {
            isAllCaps = false; textSize = 16f; background = null
        }
        val btnEnd = android.widget.Button(ctx).apply {
            isAllCaps = false; textSize = 16f; background = null
        }
        fun refreshTimeBtns() {
            btnStart.text = "%02d:%02d".format(startH, startM)
            btnEnd.text   = "%02d:%02d".format(endH, endM)
        }
        refreshTimeBtns()
        btnStart.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(startH).setMinute(startM).build()
            picker.show(childFragmentManager, "start")
            picker.addOnPositiveButtonClickListener {
                startH = picker.hour; startM = picker.minute; refreshTimeBtns()
            }
        }
        btnEnd.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(endH).setMinute(endM).build()
            picker.show(childFragmentManager, "end")
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

        // ── 終了日（当日 / 翌日 / +2日）
        listOf("当日", "翌日", "+2日").forEachIndexed { i, lbl ->
            val btn = android.widget.Button(ctx).apply {
                text = lbl; isAllCaps = false; textSize = 14f
                setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(offsetUnselectColor); cornerRadius = 6 * dp
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

        // ── メーター
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

        // ── 配達件数 / 個数
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

        // ── エリア
        root.addView(label("エリア"))
        val areaIn = EditText(ctx).apply {
            hint = "例: 北部・市内"; setText(record.area)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(areaIn)

        // ── アルコールチェック
        root.addView(label("アルコールチェック"))
        // 選択時の背景色・文字色（未実施=グレー / ○正常=グリーン / ×異常=レッド）
        val alcSelectedBg      = listOf("#9E9E9E", "#388E3C", "#D32F2F")
        val alcUnselectedColor = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val alcUnselectedText  = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val alcLabels = listOf("未実施", "○ 正常", "× 異常")

        val alcRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val alcBtnList = mutableListOf<android.widget.Button>()

        fun applyAlcStyle() {
            alcBtnList.forEachIndexed { j, b ->
                val selected = (j == alcIdx)
                (b.background as android.graphics.drawable.GradientDrawable)
                    .setColor(if (selected) Color.parseColor(alcSelectedBg[j]) else alcUnselectedColor)
                b.setTextColor(if (selected) android.graphics.Color.WHITE else alcUnselectedText)
            }
        }

        alcLabels.forEachIndexed { i, lbl ->
            val btn = android.widget.Button(ctx).apply {
                text = lbl; isAllCaps = false; textSize = 14f
                setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(alcUnselectedColor); cornerRadius = 6 * dp
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

        // ── 走行距離（メーター差から自動計算）
        root.addView(label("走行距離（km）"))
        var distManuallyEdited = false
        val distIn = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "0.0"; setText(if (record.distanceKm > 0f) record.distanceKm.toString() else "")
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(distIn)
        // setText による初期化の後にリスナーを登録する
        distIn.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { distManuallyEdited = true }
        })
        distManuallyEdited = false
        val meterWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (distManuallyEdited) return
                val sm = startMeterIn.text.toString().toIntOrNull()
                val em = endMeterIn.text.toString().toIntOrNull()
                if (sm != null && em != null && em > sm) distIn.setText((em - sm).toString())
            }
        }
        startMeterIn.addTextChangedListener(meterWatcher)
        endMeterIn.addTextChangedListener(meterWatcher)

        // ── 備考
        root.addView(label("備考"))
        val remarksIn = EditText(ctx).apply {
            hint = "特記事項など"; setText(record.remarks)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3; layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(remarksIn)

        // ── 報酬
        val paymentType     = com.rodgers.routist.util.AppSettings.getPaymentType(ctx, reportViewModel.assignmentId.value)
        val unitPrice       = com.rodgers.routist.util.AppSettings.getUnitPrice(ctx, reportViewModel.assignmentId.value)
        val trackIncome     = paymentType != 2
        val invoiceReg      = com.rodgers.routist.util.AppSettings.isInvoiceRegistered(ctx)
        val invoiceBadge    = if (invoiceReg) "✅ インボイス登録済み" else "⚠️ インボイス未登録"
        val incomeLabelView = label(buildString {
            append(if (paymentType == 1 && unitPrice > 0) "報酬（件数単価: ${unitPrice}円）" else "報酬（円）")
            append("  $invoiceBadge")
        })
        val incomeIn = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
            val initIncome = when {
                record.income > 0 -> record.income.toString()
                paymentType == 1 && unitPrice > 0 -> (unitPrice * record.deliveryCount).let { if (it > 0) it.toString() else "" }
                else -> ""
            }
            setText(initIncome)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        if (trackIncome) {
            root.addView(incomeLabelView)
            root.addView(incomeIn)
        }
        if (paymentType == 1 && unitPrice > 0) {
            var incomeManuallyEdited = false
            var updatingIncome = false
            incomeIn.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!updatingIncome) incomeManuallyEdited = true
                }
            })
            delivCntIn.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!incomeManuallyEdited) {
                        val cnt = delivCntIn.text.toString().toIntOrNull() ?: 0
                        updatingIncome = true
                        incomeIn.setText((unitPrice * cnt).toString())
                        updatingIncome = false
                    }
                }
            })
        }

        // ── 燃料費
        val fuelPrice = com.rodgers.routist.util.AppSettings.getFuelPricePerLiter(ctx)
        val fuelEff   = com.rodgers.routist.util.AppSettings.getFuelEfficiencyKmPerL(ctx)
        root.addView(label("燃料費（円）  ※ ${fuelPrice}円/L・${fuelEff}km/L で自動計算可"))
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
                if (dist > 0f && fuelEff > 0f && fuelPrice > 0) {
                    val cost = (dist / fuelEff * fuelPrice).toInt()
                    fuelIn.setText(cost.toString())
                } else {
                    val msg = when {
                        fuelPrice <= 0 && fuelEff <= 0f -> "設定でガソリン単価と燃費（km/L）を入力してください"
                        fuelPrice <= 0                  -> "設定でガソリン単価（円/L）を入力してください"
                        fuelEff   <= 0f                 -> "設定で燃費（km/L）を入力してください"
                        else                            -> "走行距離を先に入力してください"
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        fuelRow.addView(fuelIn); fuelRow.addView(fuelCalcBtn)
        root.addView(fuelRow)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (record.id == 0L) "日報を記録（$selectedDate）" else "日報を修正（${record.date}）")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                val sm   = startMeterIn.text.toString().toIntOrNull() ?: 0
                val em   = endMeterIn.text.toString().toIntOrNull()   ?: 0
                val dist = distIn.text.toString().toFloatOrNull()
                    ?: if (em > sm) (em - sm).toFloat() else record.distanceKm
                reportViewModel.save(record.copy(
                    date          = selectedDate,
                    startTime     = "%02d:%02d".format(startH, startM),
                    endTime       = "%02d:%02d".format(endH, endM),
                    endDateOffset = endDateOffset,
                    startMeter    = sm, endMeter = em, distanceKm = dist,
                    deliveryCount = delivCntIn.text.toString().toIntOrNull() ?: 0,
                    packageCount  = pkgCntIn.text.toString().toIntOrNull()   ?: 0,
                    area          = areaIn.text.toString().trim(),
                    alcCheck      = alcValues[alcIdx],
                    remarks       = remarksIn.text.toString().trim(),
                    income        = incomeIn.text.toString().toIntOrNull() ?: 0,
                    fuelCost      = fuelIn.text.toString().toIntOrNull() ?: 0,
                    assignmentId  = reportViewModel.assignmentId.value
                ))
                Toast.makeText(ctx, "保存しました（$selectedDate）", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ─────── 削除 ───────
    private fun confirmDelete(record: WorkRecord) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("${record.date} の日報を削除します。この操作は取り消せません。")
            .setPositiveButton("削除") { _, _ -> reportViewModel.delete(record) }
            .setNegativeButton("キャンセル", null).show()
    }

    // ─────── 共有 ───────
    private fun shareRecord(record: WorkRecord) {
        val text = buildString {
            appendLine("【日報】${record.date}")
            if (record.startTime.isNotBlank() || record.endTime.isNotBlank())
                appendLine("勤務: ${record.startTime}〜${record.endTime}" +
                    (if (record.endDateOffset > 0) "(+${record.endDateOffset}日)" else "") +
                    "  ${record.workingHoursText}")
            if (record.distanceKm > 0f) appendLine("走行: ${"%.1f".format(record.distanceKm)}km")
            if (record.startMeter > 0 || record.endMeter > 0)
                appendLine("メーター: ${record.startMeter}→${record.endMeter}km")
            appendLine("配達: ${record.deliveryCount}件" + if (record.packageCount > 0) "  ${record.packageCount}個" else "")
            if (record.area.isNotBlank()) appendLine("エリア: ${record.area}")
            if (record.alcCheck.isNotBlank()) appendLine("ALC: ${record.alcCheck}")
            if (record.remarks.isNotBlank()) appendLine("備考: ${record.remarks}")
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "日報 ${record.date}")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "日報を共有"))
    }

    // ─────── BottomSheet メニュー ───────
    private fun showReportMenu() {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val sheet = BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val redColor         = ContextCompat.getColor(ctx, R.color.colorActionRed)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(ctx).apply {
            text = "日報メニュー"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)
        root.addView(View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(ripple)
                setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = emoji; textSize = 28f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color)
            })
            if (sub.isNotBlank()) col.addView(TextView(ctx).apply {
                text = sub; textSize = 14f; setTextColor(onSurfaceVariant)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
            })
            row.addView(col)
            row.setOnClickListener { sheet.dismiss(); action() }
            root.addView(row)
        }

        fun divider() = root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
            setBackgroundColor(outlineVariant)
        })

        // ── 今日の記録
        row("📅", "今日の日報を記録", "今日の行を開いて入力する") { openTodayDialog() }
        divider()
        // ── 案件横断サマリー
        row("📈", "案件別サマリー", "今月の案件ごとの稼働・収入を一覧表示") { showAssignmentSummarySheet() }
        divider()
        // ── 設定・署名
        row("📄", "帳票設定", "取引先・締め日・Excel出力列") { showPatternListDialog() }
        row("🖊️", "作業者署名", "Excelに印刷する作業者の署名") { showSignatureDialog(SignatureStorage.TYPE_DRIVER, "作業者") }
        row("🤝", "取引先署名", "Excelに印刷する取引先の署名") { showSignatureDialog(SignatureStorage.TYPE_CLIENT, "取引先") }
        row("⛽", "燃料費設定", "燃料単価・走行距離から燃料コストを管理する") { showFareCalculationDialog() }
        divider()
        // ── 出力・データ管理
        row("📊", "Excelを出力", "集計期間の稼働報告書を保存・共有") { exportExcel() }
        row("💾", "バックアップ", "データをzipファイルで保存") { backupData() }
        row("📂", "バックアップから復元", "以前のデータを読み込む") {
            restoreLauncher.launch(arrayOf("application/zip", "*/*"))
        }

        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = android.widget.ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(scrollView)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = false
        }
        sheet.show()
    }

    // ─────── プライバシーポリシーダイアログ ───────
    private fun showPrivacyPolicyDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val text = """
プライバシーポリシー
最終更新：2026年6月

1. 収集する情報
本アプリ（Routist）は、以下の情報をお客様の端末内にのみ保存します。
・入力された配達先住所・メモ
・日報（稼働日・件数・報酬・走行距離）
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
            setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            setLineSpacing(0f, 1.4f)
        }
        val scroll = android.widget.ScrollView(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
            addView(tv)
        }

        android.app.AlertDialog.Builder(ctx)
            .setTitle("プライバシーポリシー")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    // ─────── アプリ設定ダイアログ ───────
    private fun showAppSettingsDialog() {
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

        // ── 表示設定
        root.addView(section("── 表示設定"))
        val darkGroup = android.widget.RadioGroup(ctx)
        val rbDarkSystem = android.widget.RadioButton(ctx).apply { text = "システム設定に従う"; id = View.generateViewId() }
        val rbDarkLight  = android.widget.RadioButton(ctx).apply { text = "ライトモード"; id = View.generateViewId() }
        val rbDarkDark   = android.widget.RadioButton(ctx).apply { text = "ダークモード"; id = View.generateViewId() }
        darkGroup.addView(rbDarkSystem); darkGroup.addView(rbDarkLight); darkGroup.addView(rbDarkDark)
        when (com.rodgers.routist.util.AppSettings.getDarkMode(ctx)) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO  -> rbDarkLight.isChecked = true
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> rbDarkDark.isChecked = true
            else -> rbDarkSystem.isChecked = true
        }
        root.addView(darkGroup)

        // ── 雇用形態
        root.addView(section("── 雇用形態"))
        val empGroup      = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.HORIZONTAL }
        val rbContractor  = android.widget.RadioButton(ctx).apply { text = "業務委託"; id = View.generateViewId() }
        val rbEmployee    = android.widget.RadioButton(ctx).apply { text = "正社員・パート"; id = View.generateViewId() }
        empGroup.addView(rbContractor); empGroup.addView(rbEmployee)
        if (com.rodgers.routist.util.AppSettings.getEmploymentType(ctx, settingsGroupId) == "employee") rbEmployee.isChecked = true
        else rbContractor.isChecked = true
        root.addView(empGroup)

        // ── 報酬設定
        root.addView(section("── 報酬設定"))

        root.addView(field("報酬タイプ"))
        val payGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
        val rbDaily  = android.widget.RadioButton(ctx).apply { text = "日当制";       id = View.generateViewId() }
        val rbUnit   = android.widget.RadioButton(ctx).apply { text = "件数単価制";   id = View.generateViewId() }
        val rbNone   = android.widget.RadioButton(ctx).apply { text = "なし";         id = View.generateViewId() }
        payGroup.addView(rbDaily); payGroup.addView(rbUnit); payGroup.addView(rbNone)
        when (com.rodgers.routist.util.AppSettings.getPaymentType(ctx, settingsGroupId)) {
            1    -> rbUnit.isChecked = true
            2    -> rbNone.isChecked = true
            else -> rbDaily.isChecked = true
        }
        root.addView(payGroup)

        val isInvoicedNow = com.rodgers.routist.util.AppSettings.isInvoiceRegistered(ctx)
        val tvUnitLabel = field(if (isInvoicedNow) "件数単価（税抜）" else "件数単価（円）")
        root.addView(tvUnitLabel)
        val etUnit = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
            val v = com.rodgers.routist.util.AppSettings.getUnitPrice(ctx, settingsGroupId)
            if (v > 0) setText(v.toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etUnit)
        val tvUnitTax = TextView(ctx).apply {
            textSize = 12f; setTextColor(colorOutline)
            visibility = if (isInvoicedNow) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (2 * dp).toInt() }
        }
        root.addView(tvUnitTax)
        // ── 事業者情報
        root.addView(section("── 事業者情報"))

        root.addView(field("事業者名"))
        val etCompany = EditText(ctx).apply {
            hint = "例: 〇〇運送株式会社"; inputType = InputType.TYPE_CLASS_TEXT
            setText(com.rodgers.routist.util.AppSettings.getCompanyName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etCompany)

        root.addView(field("車番（ナンバー）"))
        val etVehicle = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(com.rodgers.routist.util.AppSettings.getVehicleNumber(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle)

        // ── 燃料費設定
        root.addView(section("── 燃料費設定"))
        root.addView(field("ガソリン単価（円/L）"))
        val etFuelPrice = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "170"
            val v = com.rodgers.routist.util.AppSettings.getFuelPricePerLiter(ctx)
            setText(v.toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etFuelPrice)
        root.addView(field("燃費（km/L）"))
        val etFuelEff = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "15.0"
            val v = com.rodgers.routist.util.AppSettings.getFuelEfficiencyKmPerL(ctx)
            setText(v.toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etFuelEff)

        // ── インボイス設定
        root.addView(section("── インボイス設定"))
        val swInvoice = run {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (4 * dp).toInt() }
            }
            row.addView(TextView(ctx).apply {
                text = "インボイス（適格請求書）登録済み"; textSize = 15f
                setTextColor(colorOnSurface)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            val sw = androidx.appcompat.widget.SwitchCompat(ctx).apply {
                isChecked = com.rodgers.routist.util.AppSettings.isInvoiceRegistered(ctx)
            }
            row.addView(sw); root.addView(row); sw
        }

        fun updateUnitLabel() {
            val price = etUnit.text.toString().toIntOrNull() ?: 0
            if (swInvoice.isChecked) {
                tvUnitLabel.text = "件数単価（税抜）"
                tvUnitTax.visibility = View.VISIBLE
                tvUnitTax.text = "消費税10%込: ${(price * 1.1).toInt()}円 / 件"
            } else {
                tvUnitLabel.text = "件数単価（円）"
                tvUnitTax.visibility = View.GONE
            }
        }
        swInvoice.setOnCheckedChangeListener { _, _ -> updateUnitLabel() }
        etUnit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateUnitLabel()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        updateUnitLabel()

        // ── 点呼設定
        root.addView(section("── 点呼設定"))

        root.addView(field("ドライバー名"))
        val etDriver = EditText(ctx).apply {
            hint = "氏名"; inputType = InputType.TYPE_CLASS_TEXT
            setText(com.rodgers.routist.util.AppSettings.getDriverName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etDriver)

        root.addView(field("確認者（運行管理者）名"))
        val etChecker = EditText(ctx).apply {
            hint = "自己点呼の場合は自分の名前"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(com.rodgers.routist.util.AppSettings.getCheckerName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etChecker)

        // ── セキュリティ設定
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
                isChecked = com.rodgers.routist.util.AppSettings.isAppLockEnabled(ctx)
            }
            row.addView(sw); root.addView(row); sw
        }

        root.addView(field("ロックまでの時間（分）"))
        val etLockTimeout = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "30"
            setText(com.rodgers.routist.util.AppSettings.getLockTimeoutMinutes(ctx).toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etLockTimeout)
        root.addView(TextView(ctx).apply {
            text = "バックグラウンドや操作なしでこの時間が経過するとロックされます"
            textSize = 12f
            setTextColor(colorOutline)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        })

        root.addView(field("バックアップパスワード（空欄で暗号化なし）"))
        val etBackupPw = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "パスワードを設定"
            setText(com.rodgers.routist.util.AppSettings.getBackupPassword(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etBackupPw)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(groupLabel)
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                com.rodgers.routist.util.AppSettings.setEmploymentType(ctx, settingsGroupId, if (rbEmployee.isChecked) "employee" else "contractor")
                com.rodgers.routist.util.AppSettings.setPaymentType(ctx, settingsGroupId, if (rbUnit.isChecked) 1 else if (rbNone.isChecked) 2 else 0)
                com.rodgers.routist.util.AppSettings.setUnitPrice(ctx, settingsGroupId, etUnit.text.toString().toIntOrNull() ?: 0)
                com.rodgers.routist.util.AppSettings.setFuelPricePerLiter(ctx, etFuelPrice.text.toString().toIntOrNull() ?: 170)
                com.rodgers.routist.util.AppSettings.setFuelEfficiencyKmPerL(ctx, etFuelEff.text.toString().toFloatOrNull() ?: 15f)
                com.rodgers.routist.util.AppSettings.setInvoiceRegistered(ctx, swInvoice.isChecked)
                com.rodgers.routist.util.AppSettings.setCompanyName(ctx, etCompany.text.toString().trim())
                com.rodgers.routist.util.AppSettings.setVehicleNumber(ctx, etVehicle.text.toString().trim())
                com.rodgers.routist.util.AppSettings.setDriverName(ctx, etDriver.text.toString().trim())
                com.rodgers.routist.util.AppSettings.setCheckerName(ctx, etChecker.text.toString().trim())
                val darkMode = when {
                    rbDarkDark.isChecked  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    rbDarkLight.isChecked -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    else                  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                com.rodgers.routist.util.AppSettings.setDarkMode(ctx, darkMode)
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(darkMode)
                val lockTimeout = etLockTimeout.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 30
                com.rodgers.routist.util.AppSettings.setAppLockEnabled(ctx, swAppLock.isChecked)
                com.rodgers.routist.util.AppSettings.setLockTimeoutMinutes(ctx, lockTimeout)
                com.rodgers.routist.util.AppSettings.setBackupPassword(ctx, etBackupPw.text.toString())
                Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ─────── パターン一覧ダイアログ ───────
    private fun showPatternListDialog() {
        if (!isAdded) return
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val scroll   = ScrollView(ctx)
        val listRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
        }
        scroll.addView(listRoot)

        var dialog: androidx.appcompat.app.AlertDialog? = null

        fun rebuildList() {
            listRoot.removeAllViews()
            val activeId = PatternStorage.getActiveId(ctx)
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

            // 追加ボタン
            val addBtn = android.widget.Button(ctx).apply {
                text = "+ 新しいパターンを追加"; isAllCaps = false; textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (8 * dp).toInt() }
            }
            listRoot.addView(addBtn)
            addBtn.setOnClickListener {
                val patternCount = PatternStorage.getAll(ctx).size
                if (patternCount >= 1 && !LicenseManager.isPro(ctx)) {
                    LicenseManager.showUpgradeDialog(ctx)
                } else {
                    showPatternEditDialog(null) { rebuildList() }
                }
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

                // カード背景
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                    setBackgroundColor(if (isActive) activeBg else inactiveBg)
                    val borderDp = (1 * dp).toInt()
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (isActive) activeBg else inactiveBg)
                        setStroke(borderDp, if (isActive) activeBorder else inactiveBorder)
                        cornerRadius = 6 * dp
                    }
                    background = bg
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                        .also { it.bottomMargin = (8 * dp).toInt() }
                }

                // タイトル行
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

                // 詳細
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

                // ボタン行
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
                    btnRow.addView(rowBtn("選択", greenColor) {
                        PatternStorage.setActiveId(ctx, pattern.id)
                        reportViewModel.setClosingDay(pattern.closingDay)
                        rebuildList()
                        Toast.makeText(ctx, "「${pattern.title}」を選択しました", Toast.LENGTH_SHORT).show()
                    })
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

        dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("帳票設定")
            .setView(scroll)
            .setNegativeButton("閉じる", null)
            .show()
    }

    // ─────── パターン編集ダイアログ ───────
    private fun showPatternEditDialog(pattern: ReportPattern?, onSaved: () -> Unit = {}) {
        if (!isAdded) return
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val scroll = ScrollView(ctx)
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
        val chkTime     = chk("時刻（開始・終了・稼働時間）", base.showTime)
        val chkDelivery = chk("配達件数",  base.showDelivery)
        val chkPackage  = chk("個数",      base.showPackage)
        val chkDistance = chk("走行距離",  base.showDistance)
        val chkArea     = chk("エリア",    base.showArea)
        val chkRemarks  = chk("備考",      base.showRemarks)
        listOf(chkTime, chkDelivery, chkPackage, chkDistance, chkArea, chkRemarks).forEach { root.addView(it) }

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
                    showTime      = chkTime.isChecked,
                    showDelivery  = chkDelivery.isChecked,
                    showPackage   = chkPackage.isChecked,
                    showDistance  = chkDistance.isChecked,
                    showArea      = chkArea.isChecked,
                    showRemarks   = chkRemarks.isChecked
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

    // ─────── 署名ダイアログ ───────
    private fun showSignatureDialog(type: String, label: String) {
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
                else { SignatureStorage.save(ctx, type, sigView.getBitmap())
                    Toast.makeText(ctx, "${label}の署名を保存しました", Toast.LENGTH_SHORT).show() }
            }
            .setNeutralButton("クリア") { _, _ -> sigView.clear() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ─────── 案件別サマリー ───────
    private fun showAssignmentSummarySheet() {
        if (!isAdded) return
        val ctx   = requireContext()
        if (!LicenseManager.isPro(ctx)) { LicenseManager.showUpgradeDialog(ctx); return }
        val dp    = ctx.resources.displayMetrics.density
        val ym    = reportViewModel.yearMonth.value
        val (y, m) = ym.split("-").map { it.toInt() }
        val groups = deliveryViewModel.groups.value ?: emptyList()

        lifecycleScope.launch {
            val allRecords = reportViewModel.allRecordsForMonth(ym)
            if (!isAdded) return@launch

            // assignmentId でグループ化
            val byAssignment = allRecords.groupBy { it.assignmentId }

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

            // ヘッダー
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

            var totalDays = 0; var totalDeliv = 0; var totalIncome = 0; var totalFuel = 0

            fun addRow(label: String, colorHex: String?, records: List<com.rodgers.routist.model.WorkRecord>, isTotal: Boolean = false) {
                val days   = records.size
                val deliv  = records.sumOf { it.deliveryCount }
                val income = records.sumOf { it.income }
                val fuel   = records.sumOf { it.fuelCost }
                if (!isTotal) { totalDays += days; totalDeliv += deliv; totalIncome += income; totalFuel += fuel }

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
                    if (isTotal) setBackgroundColor(android.graphics.Color.argb(20, 255, 255, 255))
                }
                // ラベル行
                val labelRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                // カラードット
                if (colorHex != null) {
                    labelRow.addView(android.view.View(ctx).apply {
                        try { setBackgroundColor(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) {}
                        layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt())
                            .also { it.marginEnd = (10 * dp).toInt(); it.gravity = android.view.Gravity.CENTER_VERTICAL }
                        // 円形にする
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            try { setColor(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) {}
                        }
                    })
                }
                labelRow.addView(TextView(ctx).apply {
                    text = label
                    textSize = if (isTotal) 15f else 16f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(onSurface)
                })
                row.addView(labelRow)

                // 数値行
                val statsRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .also { it.topMargin = (6 * dp).toInt() }
                }
                fun stat(value: String) = TextView(ctx).apply {
                    text = value; textSize = 14f; setTextColor(onSurfaceVariant)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                statsRow.addView(stat("${days}日稼働"))
                statsRow.addView(stat("配達 ${deliv}件"))
                if (income > 0) statsRow.addView(stat("%,d円".format(income)))
                if (fuel   > 0) statsRow.addView(stat("燃料 %,d円".format(fuel)))
                row.addView(statsRow)
                root.addView(row)
                root.addView(android.view.View(ctx).apply {
                    setBackgroundColor(outlineVariant)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                        .also { it.setMargins(if (isTotal) 0 else (20 * dp).toInt(), 0, 0, 0) }
                })
            }

            if (byAssignment.isEmpty()) {
                root.addView(TextView(ctx).apply {
                    text = "この月の記録はありません"; textSize = 15f; setTextColor(onSurfaceVariant)
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, (40 * dp).toInt(), 0, (40 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            } else {
                // 案件ごとの行
                byAssignment.forEach { (assignmentId, records) ->
                    val group = groups.find { it.id == assignmentId }
                    val label = group?.name ?: if (assignmentId.isBlank()) "未分類" else "削除済み案件"
                    addRow(label, group?.colorHex, records)
                }
                // 合計行（案件が複数のときのみ）
                if (byAssignment.size > 1) {
                    addRow("合計", null,
                        listOf<com.rodgers.routist.model.WorkRecord>().also {
                            // ダミー：合計値は addRow 内で totalXxx を使わず直接計算済み
                        }, isTotal = false
                    )
                    // 合計を上書き表示
                    root.removeViewAt(root.childCount - 1) // 直前のdivider削除
                    root.removeViewAt(root.childCount - 1) // 合計行削除（再描画）
                    val fakeRecords = List(totalDays) {
                        com.rodgers.routist.model.WorkRecord(
                            date = "", deliveryCount = if (it == 0) totalDeliv else 0,
                            income = if (it == 0) totalIncome else 0,
                            fuelCost = if (it == 0) totalFuel else 0
                        )
                    }
                    // totalDays, totalDeliv, totalIncome, totalFuel を直接使って合計行を描画
                    val totalRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
                        setBackgroundColor(android.graphics.Color.argb(15, 255, 255, 255))
                    }
                    totalRow.addView(TextView(ctx).apply {
                        text = "合計"; textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface)
                    })
                    val statsRow2 = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            .also { it.topMargin = (6 * dp).toInt() }
                    }
                    fun stat2(v: String) = TextView(ctx).apply {
                        text = v; textSize = 14f; setTextColor(onSurfaceVariant)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    statsRow2.addView(stat2("${totalDays}日稼働"))
                    statsRow2.addView(stat2("配達 ${totalDeliv}件"))
                    if (totalIncome > 0) statsRow2.addView(stat2("%,d円".format(totalIncome)))
                    if (totalFuel   > 0) statsRow2.addView(stat2("燃料 %,d円".format(totalFuel)))
                    totalRow.addView(statsRow2)
                    root.addView(totalRow)
                }
            }

            val sv = android.widget.ScrollView(ctx).apply { addView(root) }
            sheet.setContentView(sv)
            sheet.show()
        }
    }

    // ─────── Excel出力 ───────
    private fun exportExcel() {
        if (!isAdded) return
        val ctx = requireContext()
        if (!LicenseManager.isPro(ctx)) { LicenseManager.showUpgradeDialog(ctx); return }
        val ym  = reportViewModel.yearMonth.value
        exportNippo(ctx, ym)
    }

    private fun exportNippo(ctx: android.content.Context, ym: String) {
        val pattern = PatternStorage.ensureDefault(ctx)
        val assignmentName = deliveryViewModel.currentGroup()?.name ?: ""
        lifecycleScope.launch {
            try {
                val (startDate, endDate) = ReportViewModel.computePeriod(ym, pattern.closingDay)
                val records = reportViewModel.recordsForPeriod(startDate, endDate)
                if (records.isEmpty()) {
                    Toast.makeText(ctx, "この期間の記録がまだありません", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val driverSig = SignatureStorage.fileFor(ctx, SignatureStorage.TYPE_DRIVER).takeIf { it.exists() }
                val clientSig = SignatureStorage.fileFor(ctx, SignatureStorage.TYPE_CLIENT).takeIf { it.exists() }
                val file = ExcelGenerator(ctx).generate(records, ym, pattern, driverSig, clientSig, assignmentName)
                shareExcel(ctx, file)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Excel出力エラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportTenko(ctx: android.content.Context, ym: String) {
        lifecycleScope.launch {
            try {
                val records = tenkoViewModel.recordsForMonth(ym)
                val file = com.rodgers.routist.excel.TenkoExcelGenerator(ctx).generate(records, ym)
                shareExcel(ctx, file)
            } catch (e: Exception) {
                Toast.makeText(ctx, "点呼簿出力エラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareExcel(ctx: android.content.Context, file: java.io.File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Excelを共有"))
    }

    // ─────── バックアップ ───────
    private fun backupData() {
        if (!isAdded) return
        val ctx = requireContext()
        if (!LicenseManager.isPro(ctx)) { LicenseManager.showUpgradeDialog(ctx); return }
        lifecycleScope.launch {
            try {
                val file = BackupManager.createBackup(ctx)
                val uri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "バックアップを共有"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "バックアップエラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── 燃料費設定ダイアログ ──
    private fun showFareCalculationDialog() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val fuelPrefs = ctx.getSharedPreferences("fuel_settings", android.content.Context.MODE_PRIVATE)
        fuelPricePerL     = fuelPrefs.getInt("fuel_price_yen",    fuelPricePerL)
        fuelEfficiencyKmL = fuelPrefs.getFloat("fuel_eff_kml",    fuelEfficiencyKmL)
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
        val vehicleNames = VEHICLE_PRESETS.keys.toList()
        val vehicleSpinner = android.widget.Spinner(ctx).apply {
            adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicleNames)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(vehicleNames.indexOf(vehicleTypeName).coerceAtLeast(0))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(vehicleSpinner)

        root.addView(label("燃料種別"))
        val fuelTypeNames = FUEL_PRICES.keys.toList()
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
                val price = FUEL_PRICES[fuelTypeName] ?: fuelPricePerL
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
                val preset = VEHICLE_PRESETS[vehicleTypeName] ?: return
                fuelTypeName = preset.first
                val eff = preset.second
                val price = FUEL_PRICES[fuelTypeName] ?: fuelPricePerL
                fuelEffInput.setText("%.1f".format(eff))
                fuelPriceInput.setText(price.toString())
                updateFuelTypeBtns(fuelTypeName)
                fuelEfficiencyKmL = eff; fuelPricePerL = price
                fuelPrefs.edit().putString("vehicle_type", vehicleTypeName).putString("fuel_type_name", fuelTypeName)
                    .putFloat("fuel_eff_kml", eff).putInt("fuel_price_yen", price).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        fuelSaveBtn.setOnClickListener {
            val p = fuelPriceInput.text.toString().toIntOrNull() ?: return@setOnClickListener
            val e = fuelEffInput.text.toString().toFloatOrNull() ?: return@setOnClickListener
            if (p > 0 && e > 0f) {
                fuelPricePerL = p; fuelEfficiencyKmL = e
                fuelPrefs.edit().putInt("fuel_price_yen", p).putFloat("fuel_eff_kml", e)
                    .putString("vehicle_type", vehicleTypeName).putString("fuel_type_name", fuelTypeName).apply()
                Toast.makeText(ctx, "保存しました（${vehicleTypeName}・${fuelTypeName} ¥${p}/L・${e}km/L）", Toast.LENGTH_SHORT).show()
            }
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("燃料費設定")
            .setView(scroll)
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun estimateFuelCost(distM: Int): Int =
        if (fuelEfficiencyKmL > 0f && fuelPricePerL > 0)
            (distM / 1000.0 * fuelPricePerL / fuelEfficiencyKmL).toInt()
        else 0

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val phi1 = Math.toRadians(lat1); val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1); val dLam = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dPhi / 2).let { it * it } +
                Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLam / 2).let { it * it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    companion object {
        var initialDialogShownThisSession = false
        val FUEL_PRICES = linkedMapOf(
            "レギュラー" to 168,
            "ハイオク"   to 180,
            "軽油"       to 148
        )
        val VEHICLE_PRESETS = linkedMapOf(
            "軽自動車"       to Pair("レギュラー", 20.0f),
            "コンパクトカー" to Pair("レギュラー", 18.0f),
            "普通車"         to Pair("レギュラー", 14.0f),
            "ミニバン"       to Pair("レギュラー", 11.0f),
            "SUV"            to Pair("ハイオク",   11.5f),
            "軽トラック"     to Pair("レギュラー", 17.0f),
            "2tトラック"     to Pair("軽油",        8.0f),
            "4tトラック"     to Pair("軽油",        6.0f),
            "大型トラック"   to Pair("軽油",        4.0f)
        )
    }
}

// ─────────────────────────────────────────────────────────
// 月全日付アダプター
// ─────────────────────────────────────────────────────────

class DayEntryAdapter(
    private val onTap:    (DailyReportFragment.DayEntry) -> Unit,
    private val onDelete: (WorkRecord) -> Unit,
    private val onShare:  (WorkRecord) -> Unit
) : RecyclerView.Adapter<DayEntryAdapter.VH>() {

    private val items = mutableListOf<DailyReportFragment.DayEntry>()

    fun submitList(list: List<DailyReportFragment.DayEntry>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].date == list[n].date
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp  = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return VH(root, dp)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val root: LinearLayout, private val dp: Float) : RecyclerView.ViewHolder(root) {

        fun bind(entry: DailyReportFragment.DayEntry) {
            root.removeAllViews()
            val ctx = root.context
            val primaryColor   = ContextCompat.getColor(ctx, R.color.colorReportPrimary)
            val secondaryColor = ContextCompat.getColor(ctx, R.color.colorReportSecondaryText)
            val accentColor    = ContextCompat.getColor(ctx, R.color.colorAccentOrange)
            val satColor       = ContextCompat.getColor(ctx, R.color.colorSaturdayText)
            val sunColor       = ContextCompat.getColor(ctx, R.color.colorSundayText)
            val weekdayColor   = ContextCompat.getColor(ctx, R.color.colorWeekdayText)
            val surfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
            val onSurfaceColor = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
            val todayBgColor   = ContextCompat.getColor(ctx, R.color.colorTodayBg)
            val dayBgColor     = ContextCompat.getColor(ctx, R.color.colorDayBg)
            val greenColor     = ContextCompat.getColor(ctx, R.color.colorActionGreen)
            val redColor       = ContextCompat.getColor(ctx, R.color.colorActionRed)

            val dateLocal = LocalDate.parse(entry.date)
            val dayOfWeek = dateLocal.dayOfWeek
            val weekdayJP = when (dayOfWeek) {
                DayOfWeek.MONDAY    -> "月"
                DayOfWeek.TUESDAY   -> "火"
                DayOfWeek.WEDNESDAY -> "水"
                DayOfWeek.THURSDAY  -> "木"
                DayOfWeek.FRIDAY    -> "金"
                DayOfWeek.SATURDAY  -> "土"
                DayOfWeek.SUNDAY    -> "日"
                else                -> ""
            }
            val dateColor = when (dayOfWeek) {
                DayOfWeek.SATURDAY -> satColor
                DayOfWeek.SUNDAY   -> sunColor
                else               -> weekdayColor
            }
            val isToday = entry.date == LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            root.setBackgroundColor(
                if (isToday) todayBgColor
                else if (entry.record != null) surfaceColor
                else dayBgColor
            )

            fun tv(text: String, size: Float = 13f, color: Int = onSurfaceColor,
                   bold: Boolean = false) = TextView(ctx).apply {
                this.text = text; textSize = size; setTextColor(color)
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            // 日付ヘッダー行
            val headerRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val todayMark = if (isToday) " ← 今日" else ""
            headerRow.addView(
                tv("${dateLocal.dayOfMonth}日（$weekdayJP）$todayMark", 14f, dateColor, bold = true)
                    .also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            )

            val r = entry.record
            if (r != null) {
                // ALC バッジ
                when (r.alcCheck) {
                    "○" -> headerRow.addView(tv("✅", 12f))
                    "×" -> headerRow.addView(tv("❌", 12f))
                }
            }
            root.addView(headerRow)

            if (r == null) {
                // 空日 → 記録追加ボタン
                val addRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                addRow.addView(android.widget.Button(ctx).apply {
                    text = "+ 記録する"; isAllCaps = false; textSize = 12f
                    setTextColor(primaryColor); background = null
                    setOnClickListener { onTap(entry) }
                })
                root.addView(addRow)
            } else {
                // 時刻・稼働時間（記録がある場合は雇用形態に関わらず表示）
                if (r.startTime.isNotBlank() || r.endTime.isNotBlank()) {
                    val offsetTxt = if (r.endDateOffset > 0) "(+${r.endDateOffset}日)" else ""
                    val workTxt   = if (r.workingHoursText.isNotBlank()) "  (${r.workingHoursText})" else ""
                    root.addView(tv("🕐 ${r.startTime}〜${r.endTime}$offsetTxt$workTxt",
                        12f, secondaryColor))
                }
                // 走行距離
                if (r.distanceKm > 0f || r.endMeter > 0) {
                    val dist   = if (r.distanceKm > 0f) "${"%.1f".format(r.distanceKm)}km" else "${r.endMeter - r.startMeter}km"
                    val meter  = if (r.startMeter > 0 && r.endMeter > 0) "  (${r.startMeter}km→${r.endMeter}km)" else ""
                    root.addView(tv("🚗 $dist$meter", 12f, secondaryColor))
                }
                // 件数・個数
                val stats = mutableListOf<String>()
                if (r.deliveryCount > 0) stats.add("配達 ${r.deliveryCount}件")
                if (r.packageCount  > 0) stats.add("${r.packageCount}個")
                if (stats.isNotEmpty())
                    root.addView(tv("📦 ${stats.joinToString("  ·  ")}", 13f,
                        accentColor, bold = true))
                // エリア
                if (r.area.isNotBlank())
                    root.addView(tv("📍 ${r.area}", 12f, secondaryColor))
                // 備考
                if (r.remarks.isNotBlank())
                    root.addView(tv("📝 ${r.remarks}", 12f, Color.GRAY))

                // 操作ボタン行（タップで表示）
                val btnRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
                    visibility = View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (4 * dp).toInt() }
                }
                fun actionBtn(label: String, color: Int, action: () -> Unit) =
                    android.widget.Button(ctx).apply {
                        text = label; isAllCaps = false; textSize = 11f
                        setTextColor(color); background = null
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.marginStart = (10 * dp).toInt() }
                        setOnClickListener { action() }
                    }
                btnRow.addView(actionBtn("共有", greenColor)   { onShare(r) })
                btnRow.addView(actionBtn("編集", primaryColor) { onTap(entry) })
                btnRow.addView(actionBtn("削除", redColor)     { onDelete(r) })
                root.addView(btnRow)
                root.setOnClickListener {
                    btnRow.visibility = if (btnRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
