package com.rodgers.haireel.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.haireel.R
import com.rodgers.haireel.model.WorkRecord
import com.rodgers.haireel.util.themeColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayEntry(val date: String, val record: WorkRecord?)

class DayEntryAdapter(
    private val onTap:    (DayEntry) -> Unit,
    private val onDelete: (WorkRecord) -> Unit,
    private val onShare:  (WorkRecord) -> Unit,
    private val onNoWork: (String, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<DayEntryAdapter.VH>() {

    private val items = mutableListOf<DayEntry>()

    fun submitList(list: List<DayEntry>) {
        val oldList = items.toList()
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

        fun bind(entry: DayEntry) {
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
                when (r.alcCheck) {
                    "○" -> headerRow.addView(tv("✅", 12f))
                    "×" -> headerRow.addView(tv("❌", 12f))
                }
            }
            root.addView(headerRow)

            if (r == null) {
                val addRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                addRow.addView(android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
                addRow.addView(android.widget.Button(ctx).apply {
                    text = "休"; isAllCaps = false; textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#E65100")); background = null
                    setOnClickListener { onNoWork(entry.date, true) }
                })
                addRow.addView(android.widget.Button(ctx).apply {
                    text = "+ 記録する"; isAllCaps = false; textSize = 12f
                    setTextColor(primaryColor); background = null
                    setOnClickListener { onTap(entry) }
                })
                root.addView(addRow)
            } else if (r.noWork) {
                val noWorkRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val restLabel = if (r.remarks.isNotBlank()) "休み  ${r.remarks}" else "休み"
                noWorkRow.addView(tv(restLabel, 13f, android.graphics.Color.parseColor("#E65100"))
                    .also {
                        it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        it.setOnClickListener { onNoWork(entry.date, false) }
                        it.isClickable = true
                    })
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
                if (r.startTime.isNotBlank() || r.endTime.isNotBlank()) {
                    val offsetTxt = if (r.endDateOffset > 0) "(+${r.endDateOffset}日)" else ""
                    val workTxt   = if (r.workingHoursText.isNotBlank()) "  (${r.workingHoursText})" else ""
                    root.addView(tv("🕐 ${r.startTime}〜${r.endTime}$offsetTxt$workTxt",
                        12f, secondaryColor))
                }
                if (r.distanceKm > 0f || r.endMeter > 0) {
                    val dist   = if (r.distanceKm > 0f) "${"%.0f".format(r.distanceKm)}km" else "${r.endMeter - r.startMeter}km"
                    val meter  = if (r.startMeter > 0 && r.endMeter > 0) "  (${r.startMeter}km→${r.endMeter}km)" else ""
                    root.addView(tv("🚗 $dist$meter", 12f, secondaryColor))
                }
                val stats = mutableListOf<String>()
                if (r.deliveryCount > 0) stats.add("配達 ${r.deliveryCount}件")
                if (r.packageCount  > 0) stats.add("${r.packageCount}個")
                if (stats.isNotEmpty())
                    root.addView(tv("📦 ${stats.joinToString("  ·  ")}", 13f,
                        accentColor, bold = true))
                if (r.area.isNotBlank())
                    root.addView(tv("📍 ${r.area}", 12f, secondaryColor))
                if (r.remarks.isNotBlank())
                    root.addView(tv("📝 ${r.remarks}", 12f, Color.GRAY))

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
