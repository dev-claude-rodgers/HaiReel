package com.rodgers.haireel.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.rodgers.haireel.R
import com.rodgers.haireel.databinding.FragmentDailyReportBinding
import com.rodgers.haireel.model.ColumnType
import com.rodgers.haireel.model.FuelRecord
import com.rodgers.haireel.model.WorkRecord
import com.rodgers.haireel.util.PatternStorage
import com.rodgers.haireel.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class DailyReportFragment : Fragment() {

    private var _binding: FragmentDailyReportBinding? = null
    private val binding get() = _binding!!

    val deliveryViewModel: DeliveryViewModel by activityViewModels()
    val reportViewModel: ReportViewModel by viewModels()
    val tenkoViewModel: TenkoViewModel by activityViewModels()
    val fuelViewModel: FuelViewModel by viewModels()
    private lateinit var adapter: DayEntryAdapter

    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    var fuelPricePerL:     Int    = 0
    var fuelEfficiencyKmL: Float  = 0f
    var vehicleTypeName:   String = "軽自動車"
    var fuelTypeName:      String = "レギュラー"

    private var currentFuelRecords: List<FuelRecord> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fuelPricePerL     = com.rodgers.haireel.util.AppSettings.getFuelPricePerLiter(requireContext())
        fuelEfficiencyKmL = com.rodgers.haireel.util.AppSettings.getFuelEfficiencyKmPerL(requireContext())
        setupRecyclerView()
        setupButtons()
        setupAssignmentBar()
        observeFlows()
    }

    private fun setupRecyclerView() {
        adapter = DayEntryAdapter(
            onTap    = { entry -> openEditForDate(entry.date) },
            onDelete = { record -> confirmDelete(record) },
            onShare  = { record -> shareRecord(record) },
            onNoWork = { date, noWork -> reportViewModel.setNoWork(date, noWork) }
        )
        binding.recyclerReport.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = this@DailyReportFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupButtons() {
        binding.btnPrevMonth.setOnClickListener { reportViewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { reportViewModel.nextMonth() }
        binding.btnMenu.setOnClickListener     { showReportMenu() }
        val initPattern = PatternStorage.ensureDefault(requireContext())
        reportViewModel.setClosingDay(initPattern.closingDay)
    }

    private fun updateAssignmentBar() {
        if (!isAdded) return
        val group = deliveryViewModel.currentGroup()
        if (group != null && group.name.isNotBlank()) {
            val ctx = requireContext()
            val patternId = group.patternId.takeIf { it != -1 }
                ?: PatternStorage.getActiveId(ctx).takeIf { it != -1 }
            val label = patternId?.let { pid ->
                val p = PatternStorage.get(ctx, pid) ?: return@let null
                when {
                    p.clientName.isNotBlank() -> p.clientName
                    p.title.isNotBlank() -> p.title
                    else -> "帳票${p.id + 1}"
                }
            } ?: group.name
            binding.tvAssignment.visibility = View.VISIBLE
            binding.tvAssignment.text = "📦 $label"
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
            binding.tvAssignment.visibility = View.VISIBLE
            binding.tvAssignment.text = "📋 全取引先"
            binding.tvAssignment.setBackgroundColor(android.graphics.Color.parseColor("#333333"))
        }
    }

    private fun setupAssignmentBar() {
        val gid = deliveryViewModel.currentGroupId.value
        if (com.rodgers.haireel.BuildConfig.DEBUG) android.util.Log.d("DailyReport", "setupAssignmentBar: currentGroupId='$gid', groups=${deliveryViewModel.groups.value.map { it.name }}")
        reportViewModel.setAssignmentId(gid)
        updateAssignmentBar()
    }

    private fun observeFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            deliveryViewModel.currentGroupId.collectLatest { groupId ->
                if (com.rodgers.haireel.BuildConfig.DEBUG) android.util.Log.d("DailyReport", "currentGroupId emit: '$groupId'")
                reportViewModel.setAssignmentId(groupId)
                val group = deliveryViewModel.currentGroup()
                val linkedPatternId = group?.patternId ?: -1
                if (linkedPatternId != -1) {
                    val pattern = PatternStorage.get(requireContext(), linkedPatternId)
                    if (pattern != null) {
                        PatternStorage.setActiveId(requireContext(), linkedPatternId)
                        reportViewModel.setClosingDay(pattern.closingDay)
                    }
                }
                updateAssignmentBar()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            deliveryViewModel.groups.collectLatest { updateAssignmentBar() }
        }

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

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                reportViewModel.records,
                fuelViewModel.records,
                reportViewModel.yearMonth,
                reportViewModel.closingDay
            ) { records, fuelRecs, ym, cd ->
                Triple(Pair(records, fuelRecs), ym, cd)
            }
            .collect { (pair, ym, cd) ->
                val (records, fuelRecs) = pair
                currentFuelRecords = fuelRecs
                try {
                    val days = generateDayEntries(records, ym, cd)
                    adapter.submitList(days)
                    updateSummary(records, fuelRecs)
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

    private fun updateSummary(records: List<WorkRecord>, fuelRecords: List<FuelRecord> = emptyList()) {
        if (!isAdded) return
        val (startStr, endStr) = ReportViewModel.computePeriod(
            reportViewModel.yearMonth.value, reportViewModel.closingDay.value)
        val today        = LocalDate.now()
        val startDate    = LocalDate.parse(startStr)
        val endDate      = LocalDate.parse(endStr)
        val effectiveEnd = if (today.isBefore(endDate)) today else endDate
        val periodDays   = java.time.temporal.ChronoUnit.DAYS.between(startDate, effectiveEnd).toInt() + 1
        val noWorkDays   = records.filter { it.noWork }
            .distinctBy { it.date }
            .count { LocalDate.parse(it.date).let { d -> !d.isBefore(startDate) && !d.isAfter(effectiveEnd) } }
        val workDays     = (periodDays - noWorkDays).coerceAtLeast(0)
        binding.tvSummaryDays.text       = "${workDays}日稼働"
        binding.tvSummaryDeliveries.text = "配達 ${records.filter { !it.noWork }.sumOf { it.deliveryCount }}件"
        binding.tvSummaryDistance.text   = "走行 ${"%.0f".format(records.filter { !it.noWork }.sumOf { it.distanceKm.toDouble() })}km"
        val pattern     = currentPattern()
        val totalIncome = records.sumOf { it.income }
        val actualFuelCost = fuelRecords
            .filter { it.date >= startStr && it.date <= endStr }
            .sumOf { it.totalCost }
        val estimatedFuelCost = records.sumOf { it.fuelCost }
        val totalFuel  = if (actualFuelCost > 0) actualFuelCost else estimatedFuelCost
        val fuelIsReal = actualFuelCost > 0

        val balance = totalIncome - totalFuel
        val trackIncome = pattern.excelColumns.any { it.type == ColumnType.INCOME } || pattern.paymentType != 3 || totalIncome > 0
        if (trackIncome && totalIncome > 0) {
            binding.tvSummaryIncome.visibility = View.VISIBLE
            binding.tvSummaryIncome.text = "収入 %,d円".format(totalIncome)
        } else {
            binding.tvSummaryIncome.visibility = View.GONE
        }
        if (totalFuel > 0) {
            binding.tvSummaryFuel.visibility = View.VISIBLE
            binding.tvSummaryFuel.text = if (fuelIsReal) "実燃料費 %,d円".format(totalFuel)
                                         else "支出（推定） %,d円".format(totalFuel)
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
            ?: PatternStorage.getActiveId(ctx).takeIf { it != -1 }
        return if (pid != null) PatternStorage.get(ctx, pid)
                                ?: PatternStorage.ensureDefault(ctx)
               else PatternStorage.ensureDefault(ctx)
    }

    private fun calcIncome(pattern: com.rodgers.haireel.model.ReportPattern, delivCount: Int, workMinutes: Int, packageCount: Int = delivCount): Int =
        com.rodgers.haireel.util.calcIncome(pattern, delivCount, workMinutes, packageCount)

    fun openTodayDialog() {
        reportViewModel.jumpToToday()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date())
        openEditForDate(today)
    }

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

    private fun showEditDialog(record: WorkRecord) {
        if (!isAdded) return
        showDailyReportEditDialog(
            ctx               = requireContext(),
            record            = record,
            pattern           = currentPattern(),
            fuelPricePerL     = fuelPricePerL,
            fuelEfficiencyKmL = fuelEfficiencyKmL,
            fragmentManager   = childFragmentManager,
            isAdded           = { isAdded },
            scope             = viewLifecycleOwner.lifecycleScope,
            assignmentId      = { reportViewModel.assignmentId.value },
            calcIncomeFn      = { pat, dc, wm, pc -> calcIncome(pat, dc, wm, pc) },
            onSave            = { reportViewModel.saveAndWait(it) }
        )
    }

    private fun confirmDelete(record: WorkRecord) {
        if (!isAdded) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setMessage("${record.date} の日報を削除します。この操作は取り消せません。")
            .setPositiveButton("削除") { _, _ -> reportViewModel.delete(record) }
            .setNegativeButton("キャンセル", null).show()
    }

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
