package com.rodgers.routist.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.rodgers.routist.R
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.databinding.FragmentDashboardBinding
import com.rodgers.routist.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPrevYear.setOnClickListener { viewModel.previousYear() }
        binding.btnNextYear.setOnClickListener { viewModel.nextYear() }

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.weekSummary.collectLatest { w -> updateWeekCard(w) }
        }
    }

    private fun updateSummaryCards(summaries: List<DashboardViewModel.MonthlySummary>) {
        val totalIncome = summaries.sumOf { it.income }
        val totalFuel = summaries.sumOf { it.fuelCost }
        val totalProfit = summaries.sumOf { it.profit }
        val totalWorkDays = summaries.sumOf { it.workDays }

        binding.tvTotalIncome.text = formatYen(totalIncome)
        binding.tvTotalFuel.text = formatYen(totalFuel)
        binding.tvTotalProfit.apply {
            text = formatYen(totalProfit)
            setTextColor(if (totalProfit >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        }
        binding.tvTotalWorkDays.text = "${totalWorkDays}日"
    }

    private fun updateChart(summaries: List<DashboardViewModel.MonthlySummary>) {
        val incomeData = summaries.map { it.income.toFloat() }
        val fuelData = summaries.map { it.fuelCost.toFloat() }
        val profitData = summaries.map { it.profit.toFloat().coerceAtLeast(0f) }

        binding.barChart.setData(
            listOf(
                BarChartView.DataSet("収入", incomeData, Color.parseColor("#1565C0")),
                BarChartView.DataSet("燃料費", fuelData, Color.parseColor("#C62828")),
                BarChartView.DataSet("利益", profitData, Color.parseColor("#2E7D32"))
            )
        )
    }

    private fun updateMonthlyTable(summaries: List<DashboardViewModel.MonthlySummary>) {
        val container = binding.monthlyTableBody
        container.removeAllViews()
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        summaries.forEachIndexed { index, s ->
            val row = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    (12 * dp).toInt(), (10 * dp).toInt(),
                    (12 * dp).toInt(), (10 * dp).toInt()
                )
                if (index % 2 == 1) setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            }

            fun cell(text: String, weight: Float, align: Int = android.view.Gravity.START, color: Int? = null, bold: Boolean = false): TextView {
                return TextView(ctx).apply {
                    this.text = text
                    textSize = 13f
                    gravity = align
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    color?.let { setTextColor(it) }
                    if (bold) setTypeface(typeface, Typeface.BOLD)
                }
            }

            val profitColor = when {
                s.profit > 0 -> Color.parseColor("#2E7D32")
                s.profit < 0 -> Color.parseColor("#C62828")
                else -> ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }

            row.addView(cell("${s.month}月", 1f, android.view.Gravity.START))
            val zeroColor = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            row.addView(cell(formatYen(s.income), 2f, android.view.Gravity.END,
                if (s.income > 0) Color.parseColor("#1565C0") else zeroColor))
            row.addView(cell(formatYen(s.fuelCost), 2f, android.view.Gravity.END,
                if (s.fuelCost > 0) Color.parseColor("#C62828") else zeroColor))
            row.addView(cell(formatYen(s.profit), 2f, android.view.Gravity.END, profitColor))
            row.addView(cell(if (s.workDays > 0) "${s.workDays}日" else "-", 1f, android.view.Gravity.END))

            container.addView(row)

            // 区切り線
            if (index < summaries.size - 1) {
                val divider = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
                container.addView(divider)
            }
        }
    }

    private fun updateWeekCard(w: DashboardViewModel.WeekSummary) {
        val card = binding.weekSummaryCard
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        card.removeAllViews()

        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
        }

        root.addView(TextView(ctx).apply {
            text = "今週の実績"
            textSize = 13f
            setTextColor(onSurfaceVariant)
            typeface = Typeface.DEFAULT_BOLD
        })

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (10 * dp).toInt() }
        }

        fun stat(label: String, value: String) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply {
                text = value; textSize = 16f; gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1565C0"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            addView(TextView(ctx).apply {
                text = label; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (2 * dp).toInt() }
            })
        }

        row.addView(stat("稼働日数", if (w.workDays > 0) "${w.workDays}日" else "-"))
        row.addView(stat("配達件数", if (w.deliveryCount > 0) "${w.deliveryCount}件" else "-"))
        row.addView(stat("収入", if (w.income > 0) "%,d円".format(w.income) else "-"))
        row.addView(stat("走行距離", if (w.distanceKm > 0f) "%.0fkm".format(w.distanceKm) else "-"))
        root.addView(row)
        card.addView(root)
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
