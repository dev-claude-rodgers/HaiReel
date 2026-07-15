package com.rodgers.haireel.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.rodgers.haireel.R
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.databinding.FragmentDashboardBinding
import com.rodgers.haireel.model.ReportPattern
import com.rodgers.haireel.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    // ドロップダウンの選択肢（-1 = 全取引先 + 各パターン）
    private val dropdownIds   = mutableListOf<Int>()    // -1 = 全取引先
    private val dropdownNames = mutableListOf<String>()
    private var dropdownAdapter: ArrayAdapter<String>? = null
    private var suppressSelection = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch { viewModel.refresh() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch { viewModel.refresh() }
        setupDropdown()
        setupYearNavigation()
        observeFlows()
    }

    private fun setupDropdown() {
        dropdownAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dropdownNames)
        binding.spinnerAssignment.setAdapter(dropdownAdapter)
        binding.spinnerAssignment.setOnItemClickListener { _, _, position, _ ->
            if (!suppressSelection) viewModel.setPatternId(dropdownIds.getOrElse(position) { -1 })
        }
    }

    private fun setupYearNavigation() {
        binding.btnPrevYear.setOnClickListener { viewModel.previousYear() }
        binding.btnNextYear.setOnClickListener { viewModel.nextYear() }
    }

    private fun observeFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.patterns.collectLatest { patterns -> updateDropdown(patterns) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.year.collectLatest { year ->
                binding.tvYear.text = "${year}年"
                binding.btnNextYear.isEnabled = !viewModel.isCurrentYear()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.monthlySummaries.collectLatest { summaries ->
                if (summaries.isEmpty()) return@collectLatest
                updateSummaryCards(summaries)
                updateChart(summaries)
                updateMonthlyTable(summaries)
            }
        }
    }

    private fun updateDropdown(patterns: List<ReportPattern>) {
        suppressSelection = true
        val currentId = viewModel.patternId.value

        dropdownIds.clear()
        dropdownNames.clear()
        dropdownIds.add(-1)
        dropdownNames.add("全取引先")
        patterns.forEach { p ->
            dropdownIds.add(p.id)
            // 取引先名 → カスタムタイトル → 番号 の順で優先表示
            val label = when {
                p.clientName.isNotBlank() -> p.clientName
                p.title.isNotBlank() -> p.title
                else -> "帳票${p.id + 1}"
            }
            dropdownNames.add(label)
        }

        dropdownAdapter?.notifyDataSetChanged()

        val idx = dropdownIds.indexOf(currentId).coerceAtLeast(0)
        binding.spinnerAssignment.setText(dropdownNames.getOrElse(idx) { "全取引先" }, false)
        suppressSelection = false
    }

    private fun updateSummaryCards(summaries: List<DashboardViewModel.MonthlySummary>) {
        val totalIncome   = summaries.sumOf { it.income }
        val totalFuel     = summaries.sumOf { it.fuelCost }
        val totalProfit   = summaries.sumOf { it.profit }
        val totalWorkDays = summaries.sumOf { it.workDays }

        binding.tvTotalIncome.text = formatYen(totalIncome)
        binding.tvTotalFuel.text   = formatYen(totalFuel)
        binding.tvTotalProfit.apply {
            text = formatYen(totalProfit)
            setTextColor(if (totalProfit >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        }
        binding.tvTotalWorkDays.text = "${totalWorkDays}日"
    }

    private fun updateChart(summaries: List<DashboardViewModel.MonthlySummary>) {
        val incomeData = summaries.map { it.income.toFloat() }
        val fuelData   = summaries.map { it.fuelCost.toFloat() }
        val profitData = summaries.map { it.profit.toFloat().coerceAtLeast(0f) }

        binding.barChart.setData(
            listOf(
                BarChartView.DataSet("収入",   incomeData, Color.parseColor("#1565C0")),
                BarChartView.DataSet("燃料費", fuelData,   Color.parseColor("#C62828")),
                BarChartView.DataSet("利益",   profitData, Color.parseColor("#2E7D32"))
            )
        )
    }

    private fun updateMonthlyTable(summaries: List<DashboardViewModel.MonthlySummary>) {
        val container = binding.monthlyTableBody
        container.removeAllViews()
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        summaries.forEachIndexed { index, s ->
            val row = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    (12 * dp).toInt(), (10 * dp).toInt(),
                    (12 * dp).toInt(), (10 * dp).toInt()
                )
                if (index % 2 == 1) setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            }

            fun cell(text: String, weight: Float, align: Int = android.view.Gravity.START, color: Int? = null, bold: Boolean = false): TextView {
                return TextView(ctx).apply {
                    this.text = text
                    textSize  = 13f
                    gravity   = align
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    color?.let { setTextColor(it) }
                    if (bold) setTypeface(typeface, Typeface.BOLD)
                }
            }

            val zeroColor = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            val profitColor = when {
                s.profit > 0 -> Color.parseColor("#2E7D32")
                s.profit < 0 -> Color.parseColor("#C62828")
                else         -> zeroColor
            }

            row.addView(cell("${s.month}月", 1f))
            row.addView(cell(formatYen(s.income),   2f, android.view.Gravity.END,
                if (s.income > 0) Color.parseColor("#1565C0") else zeroColor))
            row.addView(cell(formatYen(s.fuelCost), 2f, android.view.Gravity.END,
                if (s.fuelCost > 0) Color.parseColor("#C62828") else zeroColor))
            row.addView(cell(formatYen(s.profit),   2f, android.view.Gravity.END, profitColor))
            row.addView(cell(if (s.workDays > 0) "${s.workDays}日" else "-", 1f, android.view.Gravity.END))

            container.addView(row)

            if (index < summaries.size - 1) {
                container.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                })
            }
        }
    }

    private fun formatYen(amount: Int): String {
        if (amount == 0) return "-"
        return "%,d円".format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
