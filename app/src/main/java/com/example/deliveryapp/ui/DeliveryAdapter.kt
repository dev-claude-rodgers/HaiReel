package com.rodgers.routist.ui

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.routist.R
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.TimeSlotColor
import java.util.concurrent.Executors

class DeliveryAdapter(
    private val onTap: (Delivery) -> Unit = {},
    private val onLongPress: () -> Unit = {},
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit = {},
    private val onNoteClick: (Delivery) -> Unit = {},
    private val onPhotoClick: (Delivery, Int) -> Unit = { _, _ -> },
    private val onSelectionChanged: () -> Unit = {}
) : RecyclerView.Adapter<DeliveryAdapter.ViewHolder>() {

    private val items = mutableListOf<Delivery>()
    var isSelectMode = false
    val selectedIds = mutableSetOf<String>()
    var groupColor: Int = android.graphics.Color.parseColor("#F44336")
    private val imageExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isDragging = false

    fun submitList(list: List<Delivery>) {
        if (isDragging) return
        val old = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == list[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == list[n]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getCurrentList(): List<Delivery> = items.toList()

    fun clearSelection() { selectedIds.clear() }

    fun selectAll(ids: Set<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_delivery, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        imageExecutor.shutdownNow()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvOrder: TextView = view.findViewById(R.id.tvOrder)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        private val tvGeocodedAddress: TextView = view.findViewById(R.id.tvGeocodedAddress)
        private val tvGeoStatus: TextView = view.findViewById(R.id.tvGeoStatus)
        private val tvNote: TextView = view.findViewById(R.id.tvNote)
        private val layoutSlotPackage: LinearLayout = view.findViewById(R.id.layoutSlotPackage)
        private val tvTimeSlot: TextView = view.findViewById(R.id.tvTimeSlot)
        private val tvPackageCount: TextView = view.findViewById(R.id.tvPackageCount)
        private val scrollPhotos: HorizontalScrollView = view.findViewById(R.id.scrollPhotos)
        private val layoutPhotos: LinearLayout = view.findViewById(R.id.layoutPhotos)
        private val ivDragHandle: android.widget.ImageView = view.findViewById(R.id.ivDragHandle)
        private val checkSelect: CheckBox = view.findViewById(R.id.checkSelect)

        @Suppress("ClickableViewAccessibility")
        fun bind(delivery: Delivery) {
            // TalkBack 向け: 番号・住所・完了状態を組み合わせた説明を設定
            val label = delivery.name?.takeIf { it.isNotBlank() } ?: delivery.address
            val status = if (delivery.isCompleted) "完了" else "未完了"
            itemView.contentDescription = "${delivery.order}番  $label  $status"

            if (!delivery.name.isNullOrBlank()) {
                tvName.visibility = View.VISIBLE
                tvName.text = delivery.name
                tvAddress.visibility = View.GONE
            } else {
                tvName.visibility = View.GONE
                tvAddress.visibility = View.VISIBLE
                tvAddress.text = delivery.address
            }

            if (!delivery.geocodedAddress.isNullOrBlank() && delivery.geocodedAddress != delivery.address) {
                tvGeocodedAddress.visibility = View.VISIBLE
                tvGeocodedAddress.text = "📍 ${delivery.geocodedAddress}"
            } else {
                tvGeocodedAddress.visibility = View.GONE
            }

            tvGeoStatus.text = if (delivery.isGeocoded) "" else "⏳ 検索中"

            val hasSlot = !delivery.timeSlot.isNullOrBlank()
            val hasPkg = delivery.packageCount > 0
            if (hasSlot || hasPkg) {
                layoutSlotPackage.visibility = View.VISIBLE
                tvTimeSlot.text = if (hasSlot) "🕐 ${delivery.timeSlot}" else ""
                tvTimeSlot.visibility = if (hasSlot) View.VISIBLE else View.GONE
                if (hasSlot) {
                    val tpls = AppSettings.getTimeSlotTemplatesWithColor(tvTimeSlot.context)
                    val slotColor = TimeSlotColor.colorFor(delivery.timeSlot, tpls)
                    if (slotColor != null) tvTimeSlot.setTextColor(slotColor)
                    else tvTimeSlot.setTextColor(tvTimeSlot.context.getColor(R.color.colorTimeSlot))
                }
                tvPackageCount.text = if (hasPkg) "📦 ${delivery.packageCount}個" else ""
                tvPackageCount.visibility = if (hasPkg) View.VISIBLE else View.GONE
            } else {
                layoutSlotPackage.visibility = View.GONE
            }

            val hasNote = !delivery.note.isNullOrBlank()
            val hasRooms = delivery.roomList.isNotEmpty()
            when {
                hasNote && hasRooms -> {
                    val withNote = delivery.roomList.count { !it.note.isNullOrBlank() }
                    val roomStr = if (withNote > 0) "🏠 ${delivery.roomList.size}室  メモ${withNote}件"
                                  else "🏠 ${delivery.roomList.size}室"
                    tvNote.visibility = View.VISIBLE
                    tvNote.text = "📝 ${delivery.note}\n$roomStr"
                    tvNote.setOnClickListener { onNoteClick(delivery) }
                }
                hasNote -> {
                    tvNote.visibility = View.VISIBLE
                    tvNote.text = "📝 ${delivery.note}"
                    tvNote.setOnClickListener { onNoteClick(delivery) }
                }
                hasRooms -> {
                    val total = delivery.roomList.size
                    val withNote = delivery.roomList.count { !it.note.isNullOrBlank() }
                    val sb = StringBuilder("🏠 ${total}室")
                    if (withNote > 0) sb.append("  メモ${withNote}件")
                    tvNote.visibility = View.VISIBLE
                    tvNote.text = sb.toString()
                    tvNote.setOnClickListener(null)
                }
                else -> {
                    tvNote.visibility = View.GONE
                    tvNote.setOnClickListener(null)
                }
            }

            val photos = delivery.allPhotoUris
            if (photos.isNotEmpty()) {
                scrollPhotos.visibility = View.VISIBLE
                layoutPhotos.removeAllViews()
                val dp = itemView.context.resources.displayMetrics.density
                val size = (44 * dp).toInt()
                val margin = (4 * dp).toInt()
                photos.forEachIndexed { index, path ->
                    val iv = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(size, size).also { lp -> lp.marginEnd = margin }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        tag = path
                        setOnClickListener { onPhotoClick(delivery, index) }
                    }
                    layoutPhotos.addView(iv)
                    imageExecutor.execute {
                        val bmp = try {
                            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                        } catch (_: Exception) { null }
                        mainHandler.post { if (iv.tag == path) iv.setImageBitmap(bmp) }
                    }
                }
            } else {
                scrollPhotos.visibility = View.GONE
                layoutPhotos.removeAllViews()
            }

            if (isSelectMode) {
                checkSelect.visibility = View.VISIBLE
                tvOrder.visibility = View.GONE
                ivDragHandle.visibility = View.GONE
                checkSelect.setOnCheckedChangeListener(null)
                checkSelect.isChecked = delivery.id in selectedIds
                checkSelect.setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIds.add(delivery.id) else selectedIds.remove(delivery.id)
                    onSelectionChanged()
                }
                itemView.setOnClickListener { checkSelect.isChecked = !checkSelect.isChecked }
                itemView.setOnLongClickListener(null)
                itemView.alpha = if (delivery.id in selectedIds) 1.0f else 0.6f
                ivDragHandle.setOnTouchListener(null)
            } else {
                checkSelect.visibility = View.GONE
                tvOrder.visibility = View.VISIBLE
                ivDragHandle.visibility = View.VISIBLE

                tvOrder.text = "${delivery.order}"
                val ctx = itemView.context
                if (delivery.isCompleted) {
                    itemView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorTodayBg))
                    tvName.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorReportSecondaryText))
                    tvAddress.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorReportSecondaryText))
                    tvOrder.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorReportSecondaryText))
                } else {
                    itemView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorDayBg))
                    tvName.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                    tvAddress.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                    tvOrder.setTextColor(groupColor)
                }
                itemView.alpha = 1.0f
                itemView.setOnClickListener { onTap(delivery) }
                // 長押しで選択モード入場
                itemView.setOnLongClickListener { onLongPress(); true }
                // ドラッグハンドルタッチでドラッグ開始（長押し不要）
                ivDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) onDragStart(this@ViewHolder)
                    false
                }
            }
        }
    }
}
