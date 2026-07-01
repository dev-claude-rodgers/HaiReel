package com.rodgers.haireel.ui

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
import com.rodgers.haireel.util.themeColor
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.haireel.R
import com.rodgers.haireel.databinding.FragmentDailyReportBinding
import com.rodgers.haireel.excel.ExcelGenerator
import com.rodgers.haireel.model.ColumnType
import com.rodgers.haireel.model.ReportPattern
import com.rodgers.haireel.model.WorkRecord
import com.rodgers.haireel.util.GeocodingClient

import com.rodgers.haireel.util.PatternStorage
import com.rodgers.haireel.util.SignatureStorage
import com.rodgers.haireel.viewmodel.DeliveryViewModel
import com.rodgers.haireel.viewmodel.*
import com.rodgers.haireel.viewmodel.ReportViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DailyReportFragment : Fragment() {

    data class DayEntry(val date: String, val record: WorkRecord?)

    private var _binding: FragmentDailyReportBinding? = null
    private val binding get() = _binding!!

    val deliveryViewModel: DeliveryViewModel by activityViewModels()
    val reportViewModel: ReportViewModel by viewModels()
    val tenkoViewModel: com.rodgers.haireel.viewmodel.TenkoViewModel by activityViewModels()
    private lateinit var adapter: DayEntryAdapter

    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    var fuelPricePerL:     Int    = 0
    var fuelEfficiencyKmL: Float  = 0f
    var vehicleTypeName:   String = "軽自動車"
    var fuelTypeName:      String = "レギュラー"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // AppSettings から燃料費設定を読み込む（自動計算と設定ダイアログを同じ値で統一）
        fuelPricePerL     = com.rodgers.haireel.util.AppSettings.getFuelPricePerLiter(requireContext())
        fuelEfficiencyKmL = com.rodgers.haireel.util.AppSettings.getFuelEfficiencyKmPerL(requireContext())

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

        // 案件名バーの表示を更新するヘルパー（ルート名変更時にも同期するため共通化）
        val updateAssignmentBar = {
            val group = deliveryViewModel.currentGroup()
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
        }

        // ビュー生成時点で正しい案件IDをViewModelに即時反映（collect開始前に確定させる）
        reportViewModel.setAssignmentId(deliveryViewModel.currentGroupId.value)
        updateAssignmentBar()

        // 選択中の案件IDを日報ViewModelに連動させ、案件名バーと帳票パターンを更新する
        viewLifecycleOwner.lifecycleScope.launch {
            deliveryViewModel.currentGroupId.collectLatest { groupId ->
                reportViewModel.setAssignmentId(groupId)
                updateAssignmentBar()
                // 案件に紐づいた帳票パターンを自動適用
                val group = deliveryViewModel.currentGroup()
                val linkedPatternId = group?.patternId ?: -1
                if (linkedPatternId != -1) {
                    val pattern = PatternStorage.get(requireContext(), linkedPatternId)
                    if (pattern != null) {
                        PatternStorage.setActiveId(requireContext(), linkedPatternId)
                        reportViewModel.setClosingDay(pattern.closingDay)
                    }
                }
            }
        }

        // ルート名が変更されたときも案件名バーを同期する
        viewLifecycleOwner.lifecycleScope.launch {
            deliveryViewModel.groups.collectLatest { updateAssignmentBar() }
        }

        // 月ラベル＋集計期間（yearMonth・closingDay どちらが変わっても更新）
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                reportViewModel.yearMonth,
                reportViewModel.closingDay
            ) { ym, cd -> Pair(ym, cd) }.collect { (ym, cd) ->
                val (y, m) = ym.split("-").map { it.toInt() }
                binding.tvMonth.text = "${y}年${m}月"
                val (start, end) = ReportViewModel.computePeriod(ym, cd)
                val s = java.time.LocalDate.parse(start)
                val e = java.time.LocalDate.parse(end)
                val cdLabel = if (cd >= 31) "月末締め" else "${cd}日締め"
                binding.tvPeriod.text = "${s.monthValue}/${s.dayOfMonth}〜${e.monthValue}/${e.dayOfMonth}（$cdLabel）"
            }
        }

        // 日報リスト更新（案件・月・締め日・グループ変化で再描画）
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                reportViewModel.records,
                reportViewModel.yearMonth,
                reportViewModel.closingDay,
                deliveryViewModel.groups
            ) { records, ym, cd, _ -> Triple(records, ym, cd) }
            .collect { (records, ym, cd) ->
                try {
                    val days = generateDayEntries(records, ym, cd)
                    adapter.submitList(days)
                    updateSummary(records)
                    binding.tvEmptyReport.visibility  = View.GONE
                    binding.recyclerReport.visibility = View.VISIBLE
                    val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val nowYm    = todayStr.substring(0, 7)
                    if (ym == nowYm) {
                        val todayIdx = days.indexOfFirst { it.date == todayStr }
                        if (todayIdx >= 0) binding.recyclerReport.scrollToPosition(todayIdx)
                        else binding.recyclerReport.scrollToPosition(0)
                    } else {
                        binding.recyclerReport.scrollToPosition(0)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DailyReport", "日報リスト更新エラー", e)
                }
            }
        }


    }

    private fun generateDayEntries(
        records: List<WorkRecord>, yearMonth: String, closingDay: Int
    ): List<DayEntry> {
        val (startStr, endStr) = ReportViewModel.computePeriod(yearMonth, closingDay)
        val startDate = LocalDate.parse(startStr)
        val endDate   = LocalDate.parse(endStr)
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
        binding.tvSummaryDays.text       = "${records.count { !it.noWork }}日稼働"
        binding.tvSummaryDeliveries.text = "配達 ${records.sumOf { it.deliveryCount }}件"
        binding.tvSummaryDistance.text   = "走行 ${"%.0f".format(records.sumOf { it.distanceKm.toDouble() })}km"
        val pattern     = currentPattern()
        val totalIncome = records.sumOf { it.income }
        val totalFuel   = records.sumOf { it.fuelCost }
        val balance     = totalIncome - totalFuel
        val trackIncome = pattern.excelColumns.any { it.type == ColumnType.INCOME } || pattern.paymentType != 3 || totalIncome > 0
        if (trackIncome && totalIncome > 0) {
            binding.tvSummaryIncome.visibility = View.VISIBLE
            binding.tvSummaryIncome.text = "収入 %,d円".format(totalIncome)
        } else {
            binding.tvSummaryIncome.visibility = View.GONE
        }
        if (totalFuel > 0) {
            binding.tvSummaryFuel.visibility = View.VISIBLE
            binding.tvSummaryFuel.text = "支出 %,d円".format(totalFuel)
        } else {
            binding.tvSummaryFuel.visibility = View.GONE
        }
        if (trackIncome && (totalIncome > 0 || totalFuel > 0)) {
            binding.tvSummaryBalance.visibility = View.VISIBLE
            binding.tvSummaryBalance.text = "収支 %+,d円".format(balance)
            binding.tvSummaryBalance.setTextColor(
                if (balance >= 0) android.graphics.Color.parseColor("#69F0AE")
                else android.graphics.Color.parseColor("#FF6B6B"))
        } else {
            binding.tvSummaryBalance.visibility = View.GONE
        }
    }

    internal fun currentPattern(): com.rodgers.haireel.model.ReportPattern {
        val ctx = requireContext()
        val gid = reportViewModel.assignmentId.value
        val group = deliveryViewModel.groups.value.find { it.id == gid }
        val pid = group?.patternId?.takeIf { it != -1 }
            ?: com.rodgers.haireel.util.PatternStorage.getActiveId(ctx).takeIf { it != -1 }
        return if (pid != null) com.rodgers.haireel.util.PatternStorage.get(ctx, pid)
                                ?: com.rodgers.haireel.util.PatternStorage.ensureDefault(ctx)
               else com.rodgers.haireel.util.PatternStorage.ensureDefault(ctx)
    }

    private fun calcIncome(pattern: com.rodgers.haireel.model.ReportPattern, delivCount: Int, workMinutes: Int, packageCount: Int = delivCount): Int =
        when (pattern.paymentType) {
            0 -> pattern.unitPrice * packageCount   // 個数×単価
            1 -> pattern.unitPrice                  // 車建て（日当）
            2 -> pattern.unitPrice * (workMinutes / 60)  // 時間制
            else -> 0
        }

    // ─────── 今日ボタン ───────
    fun openTodayDialog() {
        reportViewModel.jumpToToday()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date())
        openEditForDate(today)
    }

    // ─────── 任意の日付を編集 ───────
    private fun openEditForDate(date: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val existing = reportViewModel.recordForDate(date)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date())
            val base = if (existing != null) existing
            else if (date == today) {
                val deliveries = deliveryViewModel.deliveries.value
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
        val ctx     = requireContext()
        val dp      = ctx.resources.displayMetrics.density
        val MATCH   = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP    = LinearLayout.LayoutParams.WRAP_CONTENT
        val pattern = currentPattern()

        var selectedDate  = record.date
        var startH        = record.startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 9
        var startM        = record.startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        var endH          = record.endTime.split(":").getOrNull(0)?.toIntOrNull() ?: 18
        var endM          = record.endTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        var endDateOffset = record.endDateOffset
        var isNoWork      = record.noWork

        val alcValues  = listOf("",       "○",      "×")
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

        // ── 稼働なしトグル
        val noWorkColor = ctx.themeColor(com.google.android.material.R.attr.colorError)
        val noWorkBtnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8 * dp).toInt() }
        }
        val btnNoWork = com.google.android.material.button.MaterialButton(ctx).apply {
            isAllCaps = false; textSize = 14f
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        }
        fun refreshNoWorkBtn() {
            if (isNoWork) {
                btnNoWork.text = "休み（稼働なし）"
                btnNoWork.setBackgroundColor(noWorkColor)
                btnNoWork.setTextColor(android.graphics.Color.WHITE)
            } else {
                btnNoWork.text = "稼働あり"
                btnNoWork.backgroundTintList = null
                btnNoWork.setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant))
                btnNoWork.setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }
        refreshNoWorkBtn()
        btnNoWork.setOnClickListener { isNoWork = !isNoWork; refreshNoWorkBtn() }
        noWorkBtnRow.addView(btnNoWork)
        root.addView(noWorkBtnRow)

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

        val hasExistingTime = record.startTime.isNotBlank() || record.endTime.isNotBlank()
        var showTime = hasExistingTime || pattern.excelColumns.any { it.type == ColumnType.START_TIME || it.type == ColumnType.END_TIME }

        // 日またぎボタンを先に宣言して applyOffsetStyle を定義可能にする
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
                b.setTextColor(if (sel) android.graphics.Color.WHITE else offsetTextUnselect)
                b.typeface = if (sel) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
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
            hint = "例: 〇〇区・市内"; setText(record.area)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(areaIn)

        // ── アルコールチェック
        root.addView(label("アルコールチェック"))
        val alcSelectedBg      = listOf("#9E9E9E", "#388E3C", "#D32F2F")
        val alcUnselectedColor = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val alcBorderColor     = ctx.themeColor(com.google.android.material.R.attr.colorOutline)
        val alcUnselectedText  = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val alcLabels = listOf("未実施", "○ 正常", "× 異常")

        val alcRow = LinearLayout(ctx).apply {
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
                b.setTextColor(if (selected) android.graphics.Color.WHITE else alcUnselectedText)
                b.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
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

        // ── 走行距離（メーター差から自動計算）
        root.addView(label("走行距離（km）"))
        var distManuallyEdited = false
        val distIn = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "0.0"
            val gpsKm = 0f
            setText(when {
                record.distanceKm > 0f -> "%.0f".format(record.distanceKm)
                gpsKm > 0f && record.date == LocalDate.now().toString() -> "%.0f".format(gpsKm)
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

        // ── 収入（既存レコードのworkingMinutesを使って時間制パターンでも正しく初期計算）
        val autoIncome = calcIncome(pattern, record.deliveryCount, record.workingMinutes, record.packageCount)
        root.addView(label("収入（円）"))
        val incomeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val incomeIn = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
            setText(if (record.income > 0) record.income.toString()
                    else if (autoIncome > 0) autoIncome.toString() else "")
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val incomeCalcBtn = android.widget.Button(ctx).apply {
            text = "💴 自動計算"; isAllCaps = false; textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.colorReportPrimary)); background = null
            setOnClickListener {
                val dc = delivCntIn.text.toString().toIntOrNull() ?: 0
                val pc = pkgCntIn.text.toString().toIntOrNull() ?: 0
                val wm = ((endH * 60 + endM + endDateOffset * 24 * 60) - (startH * 60 + startM)).coerceAtLeast(0)
                val calc = calcIncome(pattern, dc, wm, pc)
                if (calc > 0) {
                    incomeIn.setText(calc.toString())
                } else {
                    val msg = when (pattern.paymentType) {
                        3    -> "帳票設定の「報酬タイプ」を\n・個建て(個数×単価)\n・車建て(日当制)\n・時間制(時間×単価)\nのいずれかに設定してください"
                        0    -> if (pc == 0) "個数を入力してください" else "帳票設定の「単価」を設定してください"
                        2    -> if (wm == 0) "開始・終了時刻を入力してください" else "帳票設定の「単価」を設定してください"
                        else -> "帳票設定の「単価」を設定してください"
                    }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle("自動計算できません")
                        .setMessage(msg)
                        .setPositiveButton("閉じる", null)
                        .show()
                }
            }
        }
        incomeRow.addView(incomeIn); incomeRow.addView(incomeCalcBtn)
        root.addView(incomeRow)

        // ── 燃料費（走行距離のすぐ下）
        val fuelPrice = com.rodgers.haireel.util.AppSettings.getFuelPricePerLiter(ctx)
        val fuelEff   = com.rodgers.haireel.util.AppSettings.getFuelEfficiencyKmPerL(ctx)
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
                if (dist > 0f && fuelEff > 0f && fuelPrice > 0) {
                    val cost = (dist / fuelEff * fuelPrice).toInt()
                    fuelIn.setText(cost.toString())
                } else {
                    val msg = when {
                        fuelPrice <= 0 && fuelEff <= 0f -> "設定でガソリン単価と燃費(km/L)を入力してください"
                        fuelPrice <= 0                  -> "設定でガソリン単価(円/L)を入力してください"
                        fuelEff   <= 0f                 -> "設定で燃費(km/L)を入力してください"
                        else                            -> "走行距離を先に入力してください"
                    }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle("自動計算できません")
                        .setMessage(msg)
                        .setPositiveButton("閉じる", null)
                        .show()
                }
            }
        }
        fuelRow.addView(fuelIn); fuelRow.addView(fuelCalcBtn)
        root.addView(fuelRow)

        // ── 備考
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
        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val sm = startMeterIn.text.toString().toIntOrNull() ?: 0
            val em = endMeterIn.text.toString().toIntOrNull()   ?: 0
            if (em > 0 && sm == 0) {
                startMeterIn.error = "開始メーターも入力してください"; return@setOnClickListener
            }
            if (sm > 0 && em > 0 && sm >= em) {
                Toast.makeText(ctx, "終了メーターは開始メーターより大きい値にしてください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dist = distIn.text.toString().toFloatOrNull()
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
                income        = if (isNoWork) 0 else (incomeIn.text.toString().toIntOrNull() ?: calcIncome(pattern, delivCount, workMins, pkgCntIn.text.toString().toIntOrNull() ?: 0)),
                fuelCost      = if (isNoWork) 0 else (fuelIn.text.toString().toIntOrNull() ?: 0),
                assignmentId  = reportViewModel.assignmentId.value,
                noWork        = isNoWork
            )
            lifecycleScope.launch {
                // NonCancellable: 画面回転などでコルーチンがキャンセルされてもDB書き込みを完走させる
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    reportViewModel.saveAndWait(updated)
                }
                if (!isAdded) return@launch
                Toast.makeText(ctx, "保存しました（$selectedDate）", Toast.LENGTH_SHORT).show()
                try { dlg.dismiss() } catch (_: Exception) {}
            }
        }
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
            if (record.alcCheck.isNotBlank()) appendLine("アルコール: ${record.alcCheck}")
            if (record.remarks.isNotBlank()) appendLine("備考: ${record.remarks}")
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "日報 ${record.date}")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "日報を共有"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── 燃料費設定ダイアログ ──
    private fun estimateFuelCost(distM: Int): Int =
        if (fuelEfficiencyKmL > 0f && fuelPricePerL > 0)
            (distM / 1000.0 * fuelPricePerL / fuelEfficiencyKmL).toInt()
        else 0

    companion object {
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
        val oldList = items.toList()  // スナップショットで再入時の不整合を防ぐ
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = oldList[o].date == list[n].date
            override fun areContentsTheSame(o: Int, n: Int) = oldList[o] == list[n]
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
            if (r != null && !r.noWork) {
                // ALC バッジ（稼働なしのときは表示しない）
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
            } else if (r.noWork) {
                // 稼働なし
                val noWorkRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                if (r.remarks.isNotBlank()) {
                    noWorkRow.addView(tv("休み  ${r.remarks}", 13f, secondaryColor)
                        .also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                } else {
                    noWorkRow.addView(tv("休み", 13f, secondaryColor)
                        .also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                }
                noWorkRow.addView(android.widget.Button(ctx).apply {
                    text = "編集"; isAllCaps = false; textSize = 11f
                    setTextColor(primaryColor); background = null
                    setOnClickListener { onTap(entry) }
                })
                noWorkRow.addView(android.widget.Button(ctx).apply {
                    text = "削除"; isAllCaps = false; textSize = 11f
                    setTextColor(redColor); background = null
                    setOnClickListener { onDelete(r) }
                })
                root.addView(noWorkRow)
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
                    val dist   = if (r.distanceKm > 0f) "${"%.0f".format(r.distanceKm)}km" else "${r.endMeter - r.startMeter}km"
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
