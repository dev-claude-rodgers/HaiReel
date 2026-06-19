package com.rodgers.routist.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentTenkoBinding
import com.rodgers.routist.model.TenkoRecord
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.BackupManager
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
import com.rodgers.routist.viewmodel.TenkoViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@AndroidEntryPoint
class TenkoFragment : Fragment() {

    private var _binding: FragmentTenkoBinding? = null
    private val binding get() = _binding!!
    val viewModel: TenkoViewModel by activityViewModels()
    val deliveryViewModel: DeliveryViewModel by activityViewModels()

    lateinit var adapter: TenkoMonthAdapter

    val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val ctx = context ?: return@registerForActivityResult
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    BackupManager.restoreBackup(ctx, uri)
                    if (isAdded) Toast.makeText(ctx, "バックアップから復元しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    if (isAdded) Toast.makeText(ctx, "復元エラー: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ctx = context ?: return@registerForActivityResult
        if (granted) {
            showReminderDialog()
        } else {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("通知の許可が必要です")
                .setMessage("リマインダー機能を使うには通知を許可してください。")
                .setPositiveButton("設定を開く") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", ctx.packageName, null)
                    })
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTenkoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TenkoMonthAdapter(
            onBeforeClick = { date, rec -> showBeforeDialog(date, rec) },
            onAfterClick  = { date, rec -> showAfterDialog(date, rec) },
            onAddTrip     = { date -> showBeforeDialog(date, null) },
            onLongPress   = { rec -> confirmDelete(rec) }
        )
        binding.recyclerTenko.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = this@TenkoFragment.adapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }

        binding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }
        binding.btnTenkoMenu.setOnClickListener { showTenkoMenu() }

        lifecycleScope.launch {
            viewModel.yearMonth.collect { ym ->
                val (y, m) = ym.split("-").map { it.toInt() }
                binding.tvMonth.text = "${y}年${m}月"
                rebuildList(ym)
            }
        }

        lifecycleScope.launch {
            viewModel.monthRecords.collect {
                rebuildList(viewModel.yearMonth.value)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            deliveryViewModel.currentGroupId.collectLatest { groupId ->
                viewModel.setAssignmentId(groupId)
            }
        }
    }

    private fun rebuildList(ym: String) {
        val (y, m) = ym.split("-").map { it.toInt() }
        val daysInMonth = YearMonth.of(y, m).lengthOfMonth()
        val recordsByDate = viewModel.monthRecords.value.groupBy { it.date }
        val today = viewModel.todayDate()
        val items = (1..daysInMonth).map { day ->
            val dateStr = "%04d-%02d-%02d".format(y, m, day)
            TenkoMonthAdapter.DayRow(dateStr, recordsByDate[dateStr] ?: emptyList(), dateStr == today)
        }
        adapter.submitList(items)

        val hasRecords = viewModel.monthRecords.value.isNotEmpty()
        binding.emptyMonthBanner.visibility = if (hasRecords) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvLongPressHint.visibility = if (hasRecords) android.view.View.VISIBLE else android.view.View.GONE

        val todayIdx = items.indexOfFirst { it.isToday }
        if (todayIdx >= 0) binding.recyclerTenko.scrollToPosition(todayIdx)
        else binding.recyclerTenko.scrollToPosition(0)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

// ── 月全日付アダプター ──
class TenkoMonthAdapter(
    private val onBeforeClick: (String, TenkoRecord?) -> Unit,
    private val onAfterClick:  (String, TenkoRecord?) -> Unit,
    private val onAddTrip:     (String) -> Unit,
    private val onLongPress:   (TenkoRecord) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_EMPTY   = 0
        private const val TYPE_RECORDS = 1
    }

    data class DayRow(val date: String, val records: List<TenkoRecord>, val isToday: Boolean)

    private val items = mutableListOf<DayRow>()

    fun submitList(list: List<DayRow>) {
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].date == list[n].date
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) =
        if (items[position].records.isEmpty()) TYPE_EMPTY else TYPE_RECORDS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_EMPTY) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tenko_day_empty, parent, false)
            EmptyDayViewHolder(view)
        } else {
            val ctx = parent.context
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            ViewHolder(root)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EmptyDayViewHolder -> holder.bind(items[position])
            is ViewHolder         -> holder.bind(items[position])
        }
    }

    // ── 記録なし日付（XML レイアウト、removeAllViews 不要）──
    inner class EmptyDayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDayLabel: TextView = view.findViewById(R.id.tvDayLabel)
        private val chipBefore: TextView = view.findViewById(R.id.chipBefore)
        private val chipAfter:  TextView = view.findViewById(R.id.chipAfter)

        fun bind(row: DayRow) {
            val ctx = itemView.context
            val dp  = ctx.resources.displayMetrics.density

            val date  = LocalDate.parse(row.date)
            val day   = date.dayOfMonth
            val wdIdx = date.dayOfWeek.value - 1
            val wd    = listOf("月","火","水","木","金","土","日")[wdIdx]

            val dayColor = when (wdIdx) {
                5    -> ContextCompat.getColor(ctx, R.color.colorSaturdayText)
                6    -> ContextCompat.getColor(ctx, R.color.colorSundayText)
                else -> ContextCompat.getColor(ctx, R.color.colorWeekdayText)
            }
            val todayBg = if (row.isToday) ContextCompat.getColor(ctx, R.color.colorTodayBg)
                          else             ContextCompat.getColor(ctx, R.color.colorDayBg)

            itemView.setBackgroundColor(todayBg)
            tvDayLabel.text = "${day}（${wd}）"
            tvDayLabel.setTextColor(dayColor)

            val hintBg   = ContextCompat.getColor(ctx, R.color.colorHintBg)
            val hintText = ContextCompat.getColor(ctx, R.color.colorHintText)
            for (chip in listOf(chipBefore, chipAfter)) {
                chip.setTextColor(hintText)
                chip.background = GradientDrawable().apply {
                    setColor(hintBg)
                    cornerRadius = 6 * dp
                }
            }

            chipBefore.setOnClickListener { onBeforeClick(row.date, null) }
            chipAfter.setOnClickListener  { onAfterClick(row.date, null) }
        }
    }

    // ── 記録あり日付（既存の動的ビルド方式）──
    inner class ViewHolder(private val root: LinearLayout) : RecyclerView.ViewHolder(root) {

        fun bind(row: DayRow) {
            root.removeAllViews()
            val ctx  = root.context
            val dp   = ctx.resources.displayMetrics.density
            val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
            val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

            val date  = LocalDate.parse(row.date)
            val day   = date.dayOfMonth
            val wdIdx = date.dayOfWeek.value - 1
            val wd    = listOf("月","火","水","木","金","土","日")[wdIdx]
            val isSat = wdIdx == 5
            val isSun = wdIdx == 6

            val dayColor = when {
                isSat -> ContextCompat.getColor(ctx, R.color.colorSaturdayText)
                isSun -> ContextCompat.getColor(ctx, R.color.colorSundayText)
                else  -> ContextCompat.getColor(ctx, R.color.colorWeekdayText)
            }
            val todayBg = if (row.isToday) ContextCompat.getColor(ctx, R.color.colorTodayBg)
                          else             ContextCompat.getColor(ctx, R.color.colorDayBg)

            fun chip(label: String, done: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
                text = label; textSize = 16f
                val hintBg      = ContextCompat.getColor(ctx, R.color.colorHintBg)
                val hintText    = ContextCompat.getColor(ctx, R.color.colorHintText)
                val chipDone    = ctx.themeColor(com.google.android.material.R.attr.colorPrimaryContainer)
                val chipDoneText = ctx.themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
                setTextColor(if (done) chipDoneText else hintText)
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(if (done) chipDone else hintBg)
                    setStroke((1.5f*dp).toInt(), if (done) chipDone else hintBg)
                    cornerRadius = 6*dp
                }
                setPadding((14*dp).toInt(),(8*dp).toInt(),(14*dp).toInt(),(8*dp).toInt())
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginEnd = (10*dp).toInt() }
                setOnClickListener { onClick() }
            }

            val rightDisplay = AppSettings.getTenkoRightDisplay(ctx)

            fun rightInfoCol(rec: TenkoRecord): LinearLayout? {
                return when (rightDisplay) {
                    "alcohol" -> {
                        val ba = rec.beforeAlcohol; val aa = rec.afterAlcohol
                        if (ba == null && aa == null) null
                        else LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.END
                            fun alcView(label: String, v: Double) = TextView(ctx).apply {
                                text = "$label ${"%.2f".format(v)}"; textSize = 13f
                                setTextColor(if (v > 0.0) ContextCompat.getColor(ctx, R.color.colorSundayText)
                                             else ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                                setTypeface(null, Typeface.BOLD); gravity = android.view.Gravity.END
                            }
                            if (ba != null) addView(alcView("前", ba))
                            if (aa != null) addView(alcView("後", aa))
                        }
                    }
                    "time" -> {
                        val bt = rec.beforeTime; val at = rec.afterTime
                        if (bt == null && at == null) null
                        else LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.END
                            fun timeView(label: String, t: String) = TextView(ctx).apply {
                                text = "$label $t"; textSize = 13f
                                setTextColor(ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                                setTypeface(null, Typeface.BOLD); gravity = android.view.Gravity.END
                            }
                            if (bt != null) addView(timeView("前", bt))
                            if (at != null) addView(timeView("後", at))
                        }
                    }
                    else -> null
                }
            }

            // 記録あり → 日付ヘッダー行 + 便ごとの行 + 追加ボタン
            val hPad = (14*dp).toInt()
            val indentPad = (28*dp).toInt()

            val headerRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(todayBg)
                setPadding(hPad, (12*dp).toInt(), hPad, (4*dp).toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }
            headerRow.addView(TextView(ctx).apply {
                text = "${day}（${wd}）"; textSize = 18f; setTextColor(dayColor)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
            })
            root.addView(headerRow)

            row.records.forEachIndexed { idx, rec ->
                // チップ行
                val chipRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(todayBg)
                    setPadding(indentPad, (6*dp).toInt(), hPad, (2*dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    setOnLongClickListener { onLongPress(rec); true }
                }
                chipRow.addView(TextView(ctx).apply {
                    text = "${idx+1}便"; textSize = 12f; setTextColor(dayColor)
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                        .also { it.marginEnd = (10*dp).toInt() }
                })
                chipRow.addView(chip("乗務前", rec.beforeDone) { onBeforeClick(row.date, rec) })
                chipRow.addView(chip("乗務後", rec.afterDone)  { onAfterClick(row.date, rec) })
                chipRow.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
                rightInfoCol(rec)?.also {
                    it.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                    chipRow.addView(it)
                }
                root.addView(chipRow)

                // 時刻サマリー + 車番 + 特記事項（あれば）
                val hasVehicle = !rec.vehicleNumber.isNullOrBlank()
                val hasNote    = !rec.note.isNullOrBlank()
                // rightDisplay=="time" のときは chipRow 右端に既表示のためスキップ
                val hasTime    = rightDisplay != "time" &&
                                 (rec.beforeTime != null || rec.afterTime != null)
                if (hasVehicle || hasNote || hasTime) {
                    val subRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(todayBg)
                        setPadding(indentPad, 0, hPad, (8*dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                        setOnLongClickListener { onLongPress(rec); true }
                    }
                    if (hasTime) {
                        val parts = listOfNotNull(
                            rec.beforeTime?.let { "前 $it" },
                            rec.afterTime?.let  { "後 $it" }
                        )
                        subRow.addView(TextView(ctx).apply {
                            text = parts.joinToString(" / ")
                            textSize = 12f
                            setTextColor(ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                                .also { it.marginEnd = (8*dp).toInt() }
                        })
                    }
                    if (hasVehicle) subRow.addView(TextView(ctx).apply {
                        text = rec.vehicleNumber; textSize = 12f
                        setTextColor(ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                            .also { it.marginEnd = (8*dp).toInt() }
                    })
                    if (hasNote) subRow.addView(TextView(ctx).apply {
                        text = "📝 ${rec.note}"; textSize = 12f
                        setTextColor(ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    })
                    root.addView(subRow)
                } else {
                    root.addView(View(ctx).apply {
                        setBackgroundColor(todayBg)
                        layoutParams = LinearLayout.LayoutParams(MATCH, (8*dp).toInt())
                    })
                }
            }

            // + 便を追加
            root.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.label_add_trip); textSize = 13f
                setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorPrimary))
                setBackgroundColor(todayBg)
                setPadding(indentPad, (4*dp).toInt(), hPad, (12*dp).toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                setOnClickListener { onAddTrip(row.date) }
            })
        }
    }
}
