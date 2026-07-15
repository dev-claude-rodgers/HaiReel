package com.rodgers.haireel.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.haireel.R
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.TimeSlotColor
import com.rodgers.haireel.util.themeColor

private data class LedgerEntry(
    val key: String,          // "名前|住所" 除外リストと照合するキー
    val displayTitle: String,
    val address: String,
    val timeSlot: String?,
    val note: String?,
    val count: Int,
    val representative: Delivery
)

private class LedgerAdapter(
    private val ctx: Context,
    private val onAdd: (LedgerEntry) -> Unit,
    private val onLongPress: (LedgerEntry) -> Unit
) : RecyclerView.Adapter<LedgerAdapter.VH>() {

    private var items: List<LedgerEntry> = emptyList()

    fun submit(list: List<LedgerEntry>) { items = list; notifyDataSetChanged() }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCount:    TextView = view.findViewById(R.id.tvLedgerCount)
        val tvTitle:    TextView = view.findViewById(R.id.tvLedgerTitle)
        val tvAddress:  TextView = view.findViewById(R.id.tvLedgerAddress)
        val tvTimeSlot: TextView = view.findViewById(R.id.tvLedgerTimeSlot)
        val tvNote:     TextView = view.findViewById(R.id.tvLedgerNote)
        val btnAdd:     TextView = view.findViewById(R.id.btnLedgerAdd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(ctx).inflate(R.layout.item_ledger, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]

        holder.tvCount.text = "${e.count}回"
        holder.tvTitle.text = e.displayTitle

        val showAddr = e.displayTitle != e.address
        holder.tvAddress.visibility = if (showAddr) View.VISIBLE else View.GONE
        if (showAddr) holder.tvAddress.text = e.address

        val slot = e.timeSlot
        if (!slot.isNullOrBlank()) {
            holder.tvTimeSlot.visibility = View.VISIBLE
            holder.tvTimeSlot.text = "🕐 $slot"
            val tpls = AppSettings.getTimeSlotTemplatesWithColor(ctx)
            val color = TimeSlotColor.colorFor(slot, tpls)
            holder.tvTimeSlot.setTextColor(color ?: ctx.getColor(R.color.colorTimeSlot))
        } else {
            holder.tvTimeSlot.visibility = View.GONE
        }

        val note = e.note
        if (!note.isNullOrBlank()) {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = "📝 $note"
        } else {
            holder.tvNote.visibility = View.GONE
        }

        holder.btnAdd.setOnClickListener { onAdd(e) }
        holder.itemView.setOnLongClickListener { onLongPress(e); true }
    }
}

internal fun DeliveryListFragment.showLedger() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val px  = { n: Int -> (n * dp).toInt() }

    val allMap   = viewModel.allDeliveries.value
    val excluded = AppSettings.getLedgerExcluded(ctx)

    // (name + address) キーで重複排除
    val grouped = mutableMapOf<String, MutableList<Pair<String, Delivery>>>()
    allMap.forEach { (groupId, list) ->
        list.forEach { d ->
            val key = "${d.name?.trim() ?: ""}|${d.address.trim()}"
            grouped.getOrPut(key) { mutableListOf() }.add(groupId to d)
        }
    }

    val allEntries: List<LedgerEntry> = grouped.mapNotNull { (key, pairs) ->
        if (key in excluded) return@mapNotNull null
        val rep = pairs.maxByOrNull { (_, d) -> d.id } ?: return@mapNotNull null
        LedgerEntry(
            key          = key,
            displayTitle = rep.second.displayTitle,
            address      = rep.second.address,
            timeSlot     = pairs.mapNotNull { (_, d) -> d.timeSlot?.ifBlank { null } }.lastOrNull(),
            note         = pairs.mapNotNull { (_, d) -> d.note?.ifBlank { null } }.lastOrNull(),
            count        = pairs.size,
            representative = rep.second
        )
    }.sortedBy { it.displayTitle }

    if (allEntries.isEmpty() && excluded.isEmpty()) {
        Toast.makeText(ctx, "配達先の履歴がありません", Toast.LENGTH_SHORT).show()
        return
    }

    val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
    val primaryColor     = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)

    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceColor)
    }

    // ヘッダー
    val header = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(px(20), px(14), px(8), px(8))
    }
    val tvHeaderTitle = TextView(ctx).apply {
        text = "📋 配達先台帳  ${allEntries.size}件"
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceColor)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    header.addView(tvHeaderTitle)

    // 削除済み管理ボタン（除外があるときのみ表示）
    if (excluded.isNotEmpty()) {
        header.addView(TextView(ctx).apply {
            text = "🗑 ${excluded.size}"
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setTextColor(primaryColor)
            setPadding(px(8), 0, px(4), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, px(44))
            setOnClickListener {
                sheet.dismiss()
                showLedgerExcluded()
            }
        })
    }

    header.addView(TextView(ctx).apply {
        text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
        setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        layoutParams = LinearLayout.LayoutParams(px(56), px(56))
        setOnClickListener { sheet.dismiss() }
    })
    root.addView(header)
    root.addView(dividerView(ctx, onSurfaceVariant))

    // 検索バー
    val etSearch = EditText(ctx).apply {
        hint = "🔍 名前・住所で検索"
        textSize = 15f
        setTextColor(onSurfaceColor)
        setHintTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        background = null
        setPadding(px(16), px(10), px(16), px(10))
        inputType = android.text.InputType.TYPE_CLASS_TEXT
    }
    root.addView(etSearch)
    root.addView(dividerView(ctx, onSurfaceVariant))

    // RecyclerView
    var currentFiltered = allEntries
    lateinit var adapter: LedgerAdapter
    adapter = LedgerAdapter(
        ctx   = ctx,
        onAdd = { entry ->
            sheet.dismiss()
            viewModel.appendDelivery(entry.representative)
            Toast.makeText(ctx, "「${entry.displayTitle}」を追加しました", Toast.LENGTH_SHORT).show()
        },
        onLongPress = { entry ->
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(entry.displayTitle)
                .setMessage("この配達先を台帳から削除しますか？\n元の配達履歴には影響しません。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除する") { _, _ ->
                    AppSettings.addLedgerExcluded(ctx, entry.key)
                    currentFiltered = currentFiltered.filter { it.key != entry.key }
                    adapter.submit(currentFiltered)
                    tvHeaderTitle.text = "📋 配達先台帳  ${currentFiltered.size}件"
                    Toast.makeText(ctx, "「${entry.displayTitle}」を削除しました", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    )
    adapter.submit(allEntries)

    val rv = RecyclerView(ctx).apply {
        layoutManager = LinearLayoutManager(ctx)
        setAdapter(adapter)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    root.addView(rv)

    // 検索フィルター
    etSearch.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            val q = s.toString().trim().lowercase()
            currentFiltered = if (q.isEmpty()) allEntries else allEntries.filter {
                it.displayTitle.lowercase().contains(q) || it.address.lowercase().contains(q)
            }
            adapter.submit(currentFiltered)
        }
    })

    sheet.setContentView(root)
    sheet.setOnShowListener {
        val bs = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bs?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.behavior.isDraggable = false
    }
    sheet.show()
}

// 削除済み一覧・復元ダイアログ
private fun DeliveryListFragment.showLedgerExcluded() {
    val ctx      = requireContext()
    val excluded = AppSettings.getLedgerExcluded(ctx).toMutableSet()
    if (excluded.isEmpty()) {
        Toast.makeText(ctx, "削除済みの配達先はありません", Toast.LENGTH_SHORT).show()
        return
    }

    val keys = excluded.sorted()
    val names = keys.map { it.substringBefore("|").ifBlank { it.substringAfter("|") } }.toTypedArray()

    androidx.appcompat.app.AlertDialog.Builder(ctx)
        .setTitle("🗑 削除済みの配達先")
        .setItems(names) { _, idx ->
            val key = keys[idx]
            val name = names[idx]
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setMessage("「$name」を台帳に戻しますか？")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("戻す") { _, _ ->
                    AppSettings.removeLedgerExcluded(ctx, key)
                    Toast.makeText(ctx, "「$name」を台帳に戻しました", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        .setNegativeButton("閉じる", null)
        .show()
}

private fun dividerView(ctx: Context, color: Int) = View(ctx).apply {
    setBackgroundColor(color)
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
}
