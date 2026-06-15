package com.rodgers.routist.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputFilter
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rodgers.routist.model.Room
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentListBinding
import com.rodgers.routist.util.LicenseManager
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.util.TimeSlotColor
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.viewmodel.DeliveryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeliveryListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeliveryViewModel by activityViewModels()
    private lateinit var adapter: DeliveryAdapter
    private var filterMode = FilterMode.ALL
    private var progressDisplayMode = ProgressDisplay.COUNT

    enum class ProgressDisplay { COUNT, PERCENT, REMAINING, HIDDEN }

    private var isMapVisible = false

    private var pendingPhotoDeliveryId: String? = null
    private var pendingPhotoFilePath: String? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val id = pendingPhotoDeliveryId ?: return@registerForActivityResult
        val path = pendingPhotoFilePath ?: return@registerForActivityResult
        pendingPhotoDeliveryId = null; pendingPhotoFilePath = null
        if (success) viewModel.addPhoto(id, path)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pendingPhotoDeliveryId ?: return@registerForActivityResult
        pendingPhotoDeliveryId = null
        uri ?: return@registerForActivityResult
        val dest = File(requireContext().filesDir, "camera_photos/delivery_photo_${id}_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
                withContext(Dispatchers.Main) { viewModel.addPhoto(id, dest.absolutePath) }
            } catch (_: Exception) {}
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingPhotoDeliveryId?.let { launchCamera(it) }
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringExtra(ScanActivity.EXTRA_SCANNED_TEXT) ?: return@registerForActivityResult
            if (text.isNotBlank()) importList(text)
        }
    }

    private val inputLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringExtra(InputActivity.EXTRA_ADDRESSES) ?: return@registerForActivityResult
            if (text.isNotBlank()) importList(text)
        }
    }

    enum class FilterMode { ALL, INCOMPLETE, COMPLETED }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeliveryAdapter(
            onTap = { delivery -> showItemOptions(delivery) },
            onLongPress = { if (!adapter.isSelectMode) enterSelectMode() },
            onNoteClick = { delivery -> showNoteView(delivery) },
            onPhotoClick = { delivery, index -> showPhotosViewer(delivery, index) },
            onSelectionChanged = { updateSelectionUI() }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@DeliveryListFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if (adapter.isSelectMode) makeMovementFlags(0, 0)
                else makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(vh.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = !adapter.isSelectMode

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                adapter.isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh?.itemView?.alpha = 0.8f
                    vh?.itemView?.elevation = 12f
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                adapter.isDragging = false
                vh.itemView.alpha = 1.0f
                vh.itemView.elevation = 0f
                viewModel.reorderDeliveries(adapter.getCurrentList())
            }
        }).attachToRecyclerView(binding.recyclerView)

        // フィルターチップ
        binding.chipIncomplete.typeface = android.graphics.Typeface.DEFAULT_BOLD
        binding.chipIncomplete.text = "すべて"
        binding.chipIncomplete.setOnCheckedChangeListener { _, checked ->
            filterMode = if (checked) FilterMode.INCOMPLETE else FilterMode.ALL
            if (checked) binding.chipIncomplete.text = "未完了のみ"
            applyFilter()
        }

        binding.buttonSelect.visibility = View.GONE

        // 選択削除ボタン
        binding.buttonDeleteSelected.setOnClickListener {
            val selected = adapter.selectedIds
            if (selected.isEmpty()) return@setOnClickListener
            viewModel.deleteDeliveries(selected)
            exitSelectMode()
        }

        // 時間帯一括設定ボタン
        binding.buttonSetTimeSlot.setOnClickListener {
            val selected = adapter.selectedIds
            if (selected.isEmpty()) return@setOnClickListener
            showBatchTimeSlotDialog(selected)
        }

        // 全選択/全解除ボタン
        binding.buttonSelectAll.setOnClickListener {
            val list = viewModel.deliveries.value ?: return@setOnClickListener
            if (adapter.selectedIds.size == list.size) {
                adapter.clearSelection()
            } else {
                adapter.selectAll(list.map { it.id }.toSet())
            }
            updateSelectionUI()
        }

        binding.buttonMapToggle.setOnClickListener { toggleMapView() }

        binding.buttonListMenu.setOnClickListener { showListActions() }

        binding.layoutProgress.setOnClickListener {
            progressDisplayMode = when (progressDisplayMode) {
                ProgressDisplay.COUNT     -> ProgressDisplay.PERCENT
                ProgressDisplay.PERCENT   -> ProgressDisplay.REMAINING
                ProgressDisplay.REMAINING -> ProgressDisplay.HIDDEN
                ProgressDisplay.HIDDEN    -> ProgressDisplay.COUNT
            }
            applyFilter()
        }

        viewModel.deliveries.observe(viewLifecycleOwner) { applyFilter() }

        viewModel.openEditForDelivery.observe(viewLifecycleOwner) { id ->
            if (id == null) return@observe
            viewModel.clearEditRequest()
            val delivery = viewModel.deliveries.value?.find { it.id == id } ?: return@observe
            showItemOptions(delivery, showNavComplete = false)
        }

        fun refreshAdapterGroupColor() {
            val hex = viewModel.currentGroup()?.colorHex ?: "#F44336"
            val c = try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { android.graphics.Color.parseColor("#F44336") }
            if (adapter.groupColor != c) {
                adapter.groupColor = c
                adapter.notifyDataSetChanged()
            }
        }

        viewModel.currentGroupId.observe(viewLifecycleOwner) {
            filterMode = FilterMode.ALL
            binding.chipIncomplete.isChecked = false
            binding.chipIncomplete.text = "すべて"
            exitSelectMode()
            applyFilter()
            refreshAdapterGroupColor()
        }

        viewModel.groups.observe(viewLifecycleOwner) { refreshAdapterGroupColor() }

        viewModel.outOfAreaCandidates.observe(viewLifecycleOwner) { items ->
            if (items == null) return@observe
            viewModel.clearOutOfAreaCandidates()
            if (items.isEmpty()) {
                Toast.makeText(requireContext(), "エリア外の住所はありません", Toast.LENGTH_SHORT).show()
            } else {
                showOutOfAreaCandidatesDialog(items)
            }
        }

    }

    private fun enterSelectMode() {
        adapter.isSelectMode = true
        adapter.notifyDataSetChanged()
        binding.buttonSelect.text = "✕ キャンセル"
        binding.buttonSelect.backgroundTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#C62828"))
        binding.buttonSelect.strokeWidth = 0
        binding.layoutSelectionBar.visibility = View.VISIBLE
        viewModel.setSelectMode(true)
        updateSelectionUI()
    }

    private fun exitSelectMode() {
        adapter.isSelectMode = false
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        binding.buttonSelect.text = "選択"
        binding.buttonSelect.backgroundTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        val dp = resources.displayMetrics.density
        binding.buttonSelect.strokeWidth = (1.5f * dp).toInt()
        binding.buttonSelect.strokeColor =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        binding.layoutSelectionBar.visibility = View.GONE
        viewModel.setSelectMode(false)
    }

    private fun updateSelectionUI() {
        val count = adapter.selectedIds.size
        val total = viewModel.deliveries.value?.size ?: 0
        binding.buttonDeleteSelected.text = if (count > 0) "${count}件を削除" else "削除"
        binding.buttonDeleteSelected.isEnabled = count > 0
        binding.buttonSelectAll.text = if (count == total && total > 0) "全解除" else "全選択"
        binding.buttonSetTimeSlot.isEnabled = count > 0
        binding.buttonSetTimeSlot.text = if (count > 0) "🕐 ${count}件に設定" else "🕐 時間帯"
    }

    private fun applyFilter() {
        val list = viewModel.deliveries.value ?: emptyList()
        val filtered = when (filterMode) {
            FilterMode.ALL -> list
            FilterMode.INCOMPLETE -> list.filter { !it.isCompleted }
            FilterMode.COMPLETED -> list.filter { it.isCompleted }
        }
        adapter.submitList(filtered)
        binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        val total = list.size
        val done  = list.count { it.isCompleted }

        // 全完了チップ表示
        if (filterMode == FilterMode.ALL) {
            val allDone = total > 0 && done == total
            binding.chipIncomplete.text = if (allDone) "全完了" else "すべて"
            binding.chipIncomplete.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                if (allDone) android.graphics.Color.parseColor("#2E7D32")
                else android.graphics.Color.parseColor("#2A2A2A")
            )
        } else {
            binding.chipIncomplete.chipBackgroundColor =
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector)
        }

        if (total > 0) {
            binding.layoutProgress.visibility = View.VISIBLE
            if (progressDisplayMode == ProgressDisplay.HIDDEN) {
                binding.progressDelivery.visibility = View.INVISIBLE
                binding.tvProgressLabel.text = ""
            } else {
                binding.progressDelivery.visibility = View.VISIBLE
                binding.progressDelivery.progress = done * 100 / total
                binding.tvProgressLabel.text = when (progressDisplayMode) {
                    ProgressDisplay.COUNT     -> "$done / $total"
                    ProgressDisplay.PERCENT   -> "${done * 100 / total}%"
                    ProgressDisplay.REMAINING -> "残り ${total - done}件"
                    ProgressDisplay.HIDDEN    -> ""
                }
            }
        } else {
            binding.layoutProgress.visibility = View.GONE
        }
    }

    private fun showItemOptions(delivery: Delivery, showNavComplete: Boolean = true) {
        if (adapter.isSelectMode) return
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ── ヘッダー ─────────────────────────────────────────────
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }
        val headerCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerCol.addView(TextView(ctx).apply {
            text = "${delivery.order}. ${delivery.displayTitle}"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
        })
        if (!delivery.name.isNullOrBlank()) {
            headerCol.addView(TextView(ctx).apply {
                text = "📍 ${delivery.address}"
                textSize = 13f; setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt() }
            })
        }
        val statusParts = mutableListOf<String>()
        statusParts.add(if (delivery.isCompleted) "✅ 完了済み" else "⬜ 未完了")
        if (!delivery.timeSlot.isNullOrBlank()) statusParts.add("🕐 ${delivery.timeSlot}")
        if ((delivery.packageCount ?: 0) > 0) statusParts.add("📦 ${delivery.packageCount}個")
        headerCol.addView(TextView(ctx).apply {
            text = statusParts.joinToString("  ")
            textSize = 13f
            setTextColor(if (delivery.isCompleted)
                android.graphics.Color.parseColor("#2E7D32") else onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (4 * dp).toInt() }
        })
        headerRow.addView(headerCol)
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        // ── 行ヘルパー ───────────────────────────────────────────
        val rippleRes = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(rippleRes)
                setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = emoji; textSize = 28f; gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = title; textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(color)
            })
            if (sub.isNotBlank()) col.addView(TextView(ctx).apply {
                text = sub; textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#555555"))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
            })
            row.addView(col)
            row.setOnClickListener { sheet.dismiss(); action() }
            root.addView(row)
        }

        fun divider() = root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
            setBackgroundColor(outlineVariant)
        })

        // ── アクション ───────────────────────────────────────────
        if (showNavComplete) {
            row("🧭", "ナビ開始", "Google マップでルート案内を起動する") { openNavigation(delivery) }
            val toggleEmoji = if (delivery.isCompleted) "↩️" else "✅"
            val toggleTitle = if (delivery.isCompleted) "未完了に戻す" else "完了にする"
            val toggleSub   = if (delivery.isCompleted) "未配達として再度リストに戻す" else "この配達先を完了済みにする"
            row(toggleEmoji, toggleTitle, toggleSub) { viewModel.toggleCompleted(delivery.id) }
            divider()
        }
        row("✏️", "名前・住所を編集", "配達先の名前や住所を変更する") { showEditDialog(delivery) }
        row("🕐", "時間帯・個数を設定",
            if (!delivery.timeSlot.isNullOrBlank()) "現在: ${delivery.timeSlot}${if ((delivery.packageCount ?: 0) > 0) " · ${delivery.packageCount}個" else ""}"
            else "配達時間帯・荷物個数を登録する") { showTimeSlotPackageDialog(delivery) }

        val noteTitle = if (delivery.note.isNullOrBlank()) "メモを追加" else "メモを編集"
        val noteSub   = if (delivery.note.isNullOrBlank()) "受け取り方法・備考などを記録する"
                        else delivery.note!!.take(60).let { if (delivery.note!!.length > 60) "$it…" else it }
        row("📝", noteTitle, noteSub) { showNoteDialog(delivery) }

        val photoCount = delivery.allPhotoUris.size
        val photoTitle = if (photoCount == 0) "写真を追加" else "写真を見る（${photoCount}枚）"
        val photoSub   = if (photoCount == 0) "配達証明・不在票などを撮影して保存する"
                         else "撮影済みの写真を確認・追加する"
        row("📷", photoTitle, photoSub) {
            if (photoCount == 0) showPhotoAddOptions(delivery.id)
            else showPhotosViewer(delivery, 0)
        }

        val roomCount = delivery.roomList.size
        val roomTitle = if (roomCount == 0) "部屋を追加" else "部屋リスト（${roomCount}室）"
        val roomSub   = if (roomCount == 0) "マンションの部屋ごとに訪問状況を管理する"
                        else {
                            val visited = delivery.roomList.count { !it.status.isNullOrBlank() }
                            val apo     = delivery.roomList.count { it.status == "アポ獲得" }
                            "訪問${visited}室${if (apo > 0) " · アポ${apo}件" else ""}"
                        }
        row("🏠", roomTitle, roomSub) { showRoomListDialog(delivery.id) }
        divider()

        row("🗑", "この訪問先を削除", "削除後は元に戻せません",
            ContextCompat.getColor(ctx, R.color.colorActionRed)) {
            AlertDialog.Builder(ctx)
                .setMessage("「${delivery.displayTitle}」を削除しますか？\n削除後は元に戻せません。")
                .setPositiveButton("削除") { _, _ -> viewModel.deleteDelivery(delivery.id) }
                .setNegativeButton("キャンセル", null).show()
        }

        root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(scrollView)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = false
        }
        sheet.show()
    }

    private fun showRoomListDialog(deliveryId: String) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val delivery = viewModel.deliveries.value?.find { it.id == deliveryId } ?: return

        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
        }
        scroll.addView(container)

        var dialog: AlertDialog? = null

        // 自動生成ボタン（常に表示）
        container.addView(android.widget.Button(ctx).apply {
            text = "部屋番号を自動生成（階数・部屋数を入力）"
            isAllCaps = false
            setTextColor(android.graphics.Color.parseColor("#0D47A1"))
            setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10 * dp).toInt() }
            setOnClickListener {
                dialog?.dismiss()
                showRoomGenerationDialog(deliveryId, delivery)
            }
        })

        if (delivery.roomList.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "部屋はまだ登録されていません"
                textSize = 13f
                setTextColor(android.graphics.Color.GRAY)
                setPadding(0, (16 * dp).toInt(), 0, (12 * dp).toInt())
            })
        }

        // 階ごとにグループ化（末尾2桁=部屋番号、それ以前=階数）
        val floorGroups = delivery.roomList
            .groupBy { room ->
                val d = room.number.filter { it.isDigit() }
                if (d.length >= 3) d.dropLast(2).toIntOrNull() else null
            }
            .entries.sortedWith(compareBy { it.key ?: Int.MAX_VALUE })
        val showHeaders = floorGroups.size > 1

        fun addRoomCard(room: Room) {
            val hasNote = !room.note.isNullOrBlank()

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 6 * dp
                    setColor(0x08000000)
                    setStroke(1, 0x20000000)
                }
                setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (6 * dp).toInt() }
            }

            // 部屋番号
            card.addView(TextView(ctx).apply {
                text = room.number
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (10 * dp).toInt() }
            })

            // メモ表示（入力済み：薄い青背景＋ネイビー文字　未入力：プレースホルダー）
            card.addView(TextView(ctx).apply {
                text = if (hasNote) room.note else "メモを入力..."
                textSize = 13f
                if (hasNote) {
                    setTextColor(android.graphics.Color.parseColor("#1A237E"))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#E8EAF6"))
                        cornerRadius = 4 * dp
                    }
                    setPadding((6 * dp).toInt(), (3 * dp).toInt(), (6 * dp).toInt(), (3 * dp).toInt())
                } else {
                    setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
                    background = null
                    setPadding(0, 0, 0, 0)
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // 編集アイコン
            card.addView(TextView(ctx).apply {
                text = "✏️"
                textSize = 14f
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            })

            // 削除ボタン
            card.addView(TextView(ctx).apply {
                text = "✕"
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#B71C1C"))
                gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#FFEBEE"))
                    cornerRadius = 4 * dp
                }
                setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = (4 * dp).toInt() }
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setMessage("「${room.number}」を削除します。この操作は取り消せません。")
                        .setPositiveButton("削除") { _, _ ->
                            viewModel.deleteRoom(deliveryId, room.id)
                            dialog?.dismiss()
                            showRoomListDialog(deliveryId)
                        }
                        .setNegativeButton("キャンセル", null).show()
                }
            })

            // カードをタップ → メモ編集ダイアログ
            card.setOnClickListener {
                val input = EditText(ctx).apply {
                    setText(room.note ?: "")
                    hint = "不在・担当者名・折り返し希望など"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    maxLines = 4
                    setPadding((48 * dp).toInt(), (16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt())
                }
                AlertDialog.Builder(ctx)
                    .setTitle(room.number)
                    .setView(input)
                    .setPositiveButton("保存") { _, _ ->
                        viewModel.updateRoom(deliveryId, room.id, input.text.toString().trim(), room.isCompleted)
                        dialog?.dismiss()
                        showRoomListDialog(deliveryId)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }

            // 長押し → メモクリア
            card.setOnLongClickListener {
                AlertDialog.Builder(ctx)
                    .setTitle(room.number)
                    .setMessage("メモをクリアしますか？")
                    .setPositiveButton("クリア") { _, _ ->
                        viewModel.updateRoom(deliveryId, room.id, "", room.isCompleted)
                        dialog?.dismiss()
                        showRoomListDialog(deliveryId)
                    }
                    .setNegativeButton("キャンセル", null).show()
                true
            }

            container.addView(card)
        }

        floorGroups.forEach { (floor, floorRooms) ->
            if (showHeaders) {
                container.addView(TextView(ctx).apply {
                    text = if (floor != null) "${floor}階" else "その他"
                    textSize = 12f
                    setTextColor(android.graphics.Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#607D8B"))
                        cornerRadius = 4 * dp
                    }
                    setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (10 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
                })
            }
            floorRooms.forEach { room -> addRoomCard(room) }
        }

        // 部屋追加ボタン
        container.addView(android.widget.Button(ctx).apply {
            text = "＋ 部屋を追加"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * dp).toInt() }
            setOnClickListener {
                val input = EditText(ctx).apply {
                    hint = "例：101号室　202　B1-305"
                    inputType = InputType.TYPE_CLASS_TEXT
                    setPadding((48 * dp).toInt(), (16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt())
                }
                val addDlg = AlertDialog.Builder(ctx)
                    .setTitle("部屋を追加")
                    .setView(input)
                    .setPositiveButton("追加", null)
                    .setNegativeButton("キャンセル", null)
                    .show()
                addDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val num = input.text.toString().trim()
                    if (num.isBlank()) {
                        input.error = "部屋番号を入力してください"
                    } else {
                        viewModel.addRoom(deliveryId, num)
                        addDlg.dismiss()
                        dialog?.dismiss()
                        showRoomListDialog(deliveryId)
                    }
                }
            }
        })

        val total = delivery.roomList.size
        val statLine = if (total > 0) "  ${total}室" else ""

        dialog = AlertDialog.Builder(ctx)
            .setTitle("${delivery.order}. ${delivery.displayTitle}$statLine")
            .setView(scroll)
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun showRoomGenerationDialog(deliveryId: String, delivery: Delivery) {
        if (delivery.roomList.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setMessage("すでに${delivery.roomList.size}室が登録されています。\n上書きして自動生成しますか？")
                .setPositiveButton("上書き") { _, _ -> openRoomGenerationForm(deliveryId) }
                .setNegativeButton("キャンセル", null)
                .show()
        } else {
            openRoomGenerationForm(deliveryId)
        }
    }

    private fun openRoomGenerationForm(deliveryId: String) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(root)

        fun sectionLabel(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 13f
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                it.topMargin = (10 * dp).toInt(); it.bottomMargin = (3 * dp).toInt()
            }
        }
        fun blueBorder() = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#F0F4FF"))
            setStroke((2 * dp).toInt(), android.graphics.Color.parseColor("#3F51B5"))
            cornerRadius = 6 * dp
        }
        fun numInput(default: String, large: Boolean = false) = EditText(ctx).apply {
            setText(default)
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = if (large) 24f else 16f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        fun radioBtn(label: String) = android.widget.RadioButton(ctx).apply {
            text = label; textSize = 13f; id = android.view.View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginEnd = (6 * dp).toInt() }
        }

        // === 形式選択 ===
        root.addView(sectionLabel("部屋番号の形式"))
        val rgFormat = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val rb101    = radioBtn("101形式　　（101号室, 102号室, 201号室…）")
        val rbSeq    = radioBtn("1号室形式　（1号室, 2号室, 3号室…）")
        val rbPrefix = radioBtn("記号形式　　（B101, B102, A201…）")
        rgFormat.addView(rb101); rgFormat.addView(rbSeq); rgFormat.addView(rbPrefix)
        rb101.isChecked = true
        root.addView(rgFormat)

        // === 記号入力 ===
        val prefixSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        prefixSection.addView(sectionLabel("記号を入力（例: A, B, 東, 西）"))
        val prefixInput = EditText(ctx).apply {
            setText("A")
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        prefixSection.addView(prefixInput)
        // 開始番号と部屋数の横並び入力
        prefixSection.addView(sectionLabel("開始番号　　　　　　　部屋数"))
        val pfxRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val pfxStartInput = EditText(ctx).apply {
            setText("101")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val pfxCountInput = EditText(ctx).apply {
            setText("5")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
            background = blueBorder()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also { it.marginStart = (8 * dp).toInt() }
        }
        pfxRow.addView(pfxStartInput)
        pfxRow.addView(pfxCountInput)
        prefixSection.addView(pfxRow)
        root.addView(prefixSection)

        // === 階数入力 ===
        val floorSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        floorSection.addView(sectionLabel("何階建てですか？"))
        val floorInput = numInput("3", large = true)
        floorSection.addView(floorInput)
        root.addView(floorSection)

        // === 部屋数モード ===
        val roomModeSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        roomModeSection.addView(sectionLabel("各階の部屋数"))
        val rgRoomMode = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val rbCommon   = radioBtn("全階共通")
        val rbPerFloor = radioBtn("階ごとに指定")
        rgRoomMode.addView(rbCommon); rgRoomMode.addView(rbPerFloor)
        rbCommon.isChecked = true
        roomModeSection.addView(rgRoomMode)
        root.addView(roomModeSection)

        // === 共通部屋数入力 ===
        val commonSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val commonInput = numInput("4", large = true)
        commonSection.addView(commonInput)
        root.addView(commonSection)

        // === 階ごと部屋数入力 ===
        val perFloorSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(perFloorSection)
        val perFloorInputs = mutableListOf<EditText>()

        // === 通し番号用: 総部屋数 ===
        val totalSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        totalSection.addView(sectionLabel("総部屋数"))
        val totalInput = numInput("10", large = true)
        totalSection.addView(totalInput)
        root.addView(totalSection)

        // === プレビュー ===
        val tvPreview = TextView(ctx).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#E65100"))
            setPadding(0, (12 * dp).toInt(), 0, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(tvPreview)

        // --- ロジック ---
        fun buildRoomNumbers(): List<String>? {
            if (rbSeq.isChecked) {
                val total = totalInput.text.toString().toIntOrNull()?.coerceIn(1, 999) ?: return null
                return (1..total).map { "${it}号室" }
            }
            if (rbPrefix.isChecked) {
                val prefix = prefixInput.text.toString().trim()
                val start  = pfxStartInput.text.toString().toIntOrNull()?.coerceIn(1, 9999) ?: return null
                val count  = pfxCountInput.text.toString().toIntOrNull()?.coerceIn(1, 999)  ?: return null
                return (0 until count).map { "${prefix}${start + it}" }
            }
            val f = floorInput.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: return null
            return if (rbPerFloor.isChecked) {
                if (perFloorInputs.size < f) return null
                (1..f).flatMap { floor ->
                    val u = perFloorInputs[floor - 1].text.toString().toIntOrNull()?.coerceIn(1, 99) ?: return null
                    (1..u).map { unit -> "${floor}${unit.toString().padStart(2, '0')}号室" }
                }
            } else {
                val u = commonInput.text.toString().toIntOrNull()?.coerceIn(1, 99) ?: return null
                (1..f).flatMap { floor ->
                    (1..u).map { unit -> "${floor}${unit.toString().padStart(2, '0')}号室" }
                }
            }
        }

        fun updatePreview() {
            val rooms = buildRoomNumbers() ?: run { tvPreview.text = ""; return }
            tvPreview.text = rooms.take(6).joinToString("  ") + "…\n計 ${rooms.size} 部屋を生成します"
        }

        fun buildPerFloorRows() {
            perFloorSection.removeAllViews()
            perFloorInputs.clear()
            val f = floorInput.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: return
            for (floor in 1..f) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (4 * dp).toInt() }
                }
                row.addView(TextView(ctx).apply {
                    text = "${floor}階："
                    textSize = 15f
                    setTextColor(android.graphics.Color.parseColor("#1A237E"))
                    minWidth = (52 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                })
                val unitInput = EditText(ctx).apply {
                    setText("4")
                    inputType = InputType.TYPE_CLASS_NUMBER
                    textSize = 18f
                    setTextColor(android.graphics.Color.parseColor("#1A237E"))
                    background = blueBorder()
                    setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                }
                row.addView(unitInput)
                row.addView(TextView(ctx).apply {
                    text = "部屋"; textSize = 14f
                    setPadding((8 * dp).toInt(), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                })
                perFloorSection.addView(row)
                perFloorInputs.add(unitInput)
                unitInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) { updatePreview() }
                })
            }
        }

        fun updateVisibility() {
            val isSeq = rbSeq.isChecked
            val isPfx = rbPrefix.isChecked
            val isPer = rbPerFloor.isChecked
            prefixSection.visibility   = if (isPfx) android.view.View.VISIBLE else android.view.View.GONE
            floorSection.visibility    = if (isSeq || isPfx) android.view.View.GONE else android.view.View.VISIBLE
            roomModeSection.visibility = if (isSeq || isPfx) android.view.View.GONE else android.view.View.VISIBLE
            commonSection.visibility   = if (isSeq || isPfx || isPer) android.view.View.GONE else android.view.View.VISIBLE
            totalSection.visibility    = if (isSeq) android.view.View.VISIBLE else android.view.View.GONE
            if (!isSeq && !isPfx && isPer) {
                perFloorSection.visibility = android.view.View.VISIBLE
                buildPerFloorRows()
            } else {
                perFloorSection.visibility = android.view.View.GONE
            }
            updatePreview()
        }

        val basicWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updatePreview() }
        }
        floorInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (rbPerFloor.isChecked) buildPerFloorRows()
                updatePreview()
            }
        })
        commonInput.addTextChangedListener(basicWatcher)
        totalInput.addTextChangedListener(basicWatcher)
        prefixInput.addTextChangedListener(basicWatcher)
        pfxStartInput.addTextChangedListener(basicWatcher)
        pfxCountInput.addTextChangedListener(basicWatcher)
        rgFormat.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        rgRoomMode.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        updatePreview()

        AlertDialog.Builder(ctx)
            .setTitle("部屋番号を自動生成")
            .setView(scroll)
            .setPositiveButton("生成する") { _, _ ->
                val rooms = buildRoomNumbers() ?: return@setPositiveButton
                if (rooms.isEmpty()) return@setPositiveButton
                viewModel.generateRooms(deliveryId, rooms)
                android.widget.Toast.makeText(ctx, "${rooms.size}室を生成しました", android.widget.Toast.LENGTH_SHORT).show()
                showRoomListDialog(deliveryId)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showRoomNoteEditDialog(deliveryId: String, room: Room, onSaved: () -> Unit) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val input = EditText(ctx).apply {
            hint = "例：不在・興味あり・次回訪問"
            setText(room.note ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            setPadding((48 * dp).toInt(), (16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt())
        }
        AlertDialog.Builder(ctx)
            .setTitle("${room.number}　メモを編集")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                viewModel.updateRoom(deliveryId, room.id, input.text.toString().trim(), room.isCompleted)
                onSaved()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showNoteView(delivery: Delivery) {
        if (delivery.note.isNullOrBlank()) { showNoteDialog(delivery); return }
        AlertDialog.Builder(requireContext())
            .setTitle("メモ")
            .setMessage(delivery.note)
            .setPositiveButton("編集") { _, _ -> showNoteDialog(delivery) }
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun showPhotosViewer(delivery: Delivery, startIndex: Int = 0) {
        val photos = delivery.allPhotoUris
        if (photos.isEmpty()) { showPhotoAddOptions(delivery.id); return }
        var currentIndex = startIndex.coerceIn(0, photos.size - 1)
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 0) }
        val imgView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (280 * dp).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true
        }
        val tvCounter = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER; textSize = 13f; setPadding(0, 8, 0, 4)
        }
        val navRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val btnPrev = Button(ctx).apply { text = "◀ 前"; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val btnNext = Button(ctx).apply { text = "次 ▶"; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        navRow.addView(btnPrev); navRow.addView(btnNext)
        root.addView(imgView); root.addView(tvCounter); root.addView(navRow)

        fun reload() {
            tvCounter.text = "${currentIndex + 1} / ${photos.size}"
            btnPrev.isEnabled = currentIndex > 0; btnNext.isEnabled = currentIndex < photos.size - 1
            val path = photos[currentIndex]
            lifecycleScope.launch(Dispatchers.IO) {
                val bmp = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                withContext(Dispatchers.Main) { imgView.setImageBitmap(bmp) }
            }
        }
        btnPrev.setOnClickListener { if (currentIndex > 0) { currentIndex--; reload() } }
        btnNext.setOnClickListener { if (currentIndex < photos.size - 1) { currentIndex++; reload() } }
        reload()

        AlertDialog.Builder(ctx)
            .setTitle("写真 (${photos.size}枚)")
            .setView(root)
            .setPositiveButton("追加") { _, _ -> showPhotoAddOptions(delivery.id) }
            .setNeutralButton("この写真を削除") { _, _ ->
                File(photos[currentIndex]).delete()
                viewModel.removePhoto(delivery.id, currentIndex)
                val updated = viewModel.deliveries.value?.find { it.id == delivery.id }
                if (updated != null && updated.allPhotoUris.isNotEmpty()) {
                    showPhotosViewer(updated, currentIndex.coerceAtMost(updated.allPhotoUris.size - 1))
                }
            }
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun showPhotoAddOptions(deliveryId: String) {
        pendingPhotoDeliveryId = deliveryId
        AlertDialog.Builder(requireContext())
            .setTitle("写真を追加")
            .setItems(arrayOf("カメラで撮影", "ギャラリーから選択")) { _, which ->
                when (which) {
                    0 -> if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                            launchCamera(deliveryId)
                         else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    1 -> galleryLauncher.launch("image/*")
                }
            }.show()
    }

    private fun showNoteDialog(delivery: Delivery) {
        val input = EditText(requireContext()).apply {
            hint = "メモを入力"
            setText(delivery.note ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            setPadding(48, 16, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("メモの編集")
            .setView(input)
            .setPositiveButton("保存") { _, _ -> viewModel.editNote(delivery.id, input.text.toString().trim()) }
            .setNegativeButton("キャンセル", null)
            .also { if (!delivery.note.isNullOrBlank()) it.setNeutralButton("削除") { _, _ -> viewModel.editNote(delivery.id, "") } }
            .show()
    }


    private fun launchCamera(deliveryId: String) {
        val photoFile = File(requireContext().filesDir,
            "camera_photos/delivery_photo_${deliveryId}_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        pendingPhotoFilePath = photoFile.absolutePath
        pendingPhotoDeliveryId = deliveryId
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
        cameraLauncher.launch(uri)
    }

    private fun showEditDialog(delivery: Delivery) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 24, 64, 8)
        }
        val nameLabel = TextView(ctx).apply { text = "名前" }
        val nameInput = EditText(ctx).apply {
            hint = "例: ファミリーマート渋谷店"
            setText(delivery.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val addrLabel = TextView(ctx).apply {
            text = "住所"
            setPadding(0, 20, 0, 0)
        }
        val addrInput = EditText(ctx).apply {
            hint = "例: 東京都渋谷区〇〇1-2-3"
            setText(delivery.address)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(nameLabel)
        layout.addView(nameInput)
        layout.addView(addrLabel)
        layout.addView(addrInput)

        AlertDialog.Builder(ctx)
            .setTitle("名前・住所を編集")
            .setView(layout)
            .setPositiveButton("修正して再検索") { _, _ ->
                val newName = nameInput.text.toString().trim()
                val newAddress = addrInput.text.toString().trim()
                if (newAddress.isNotBlank()) viewModel.editDelivery(delivery.id, newName, newAddress)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showTimeSlotPackageDialog(delivery: Delivery) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        // テンプレートクイック選択
        val templates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(ctx)
        val timeInput = EditText(ctx).apply {
            hint = "例: 14:00-16:00"
            setText(delivery.timeSlot ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val templateRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val scrollView = HorizontalScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        templates.forEach { tmpl ->
            val btn = Button(ctx).apply {
                text = tmpl.name
                textSize = 12f
                setPadding((10 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
                try { setTextColor(android.graphics.Color.parseColor(tmpl.colorHex)) } catch (_: Exception) {}
                setOnClickListener { timeInput.setText(tmpl.name) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * dp).toInt()
                ).apply { marginEnd = (6 * dp).toInt() }
            }
            chipRow.addView(btn)
        }
        scrollView.addView(chipRow)
        templateRow.addView(scrollView)

        val editBtn = Button(ctx).apply {
            text = "編集"
            textSize = 12f
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            setOnClickListener { showTemplateEditDialog() }
        }
        templateRow.addView(editBtn)

        val timeLabel = TextView(ctx).apply {
            text = "時間帯指定"
            setPadding(0, (8 * dp).toInt(), 0, (2 * dp).toInt())
        }
        val pkgLabel = TextView(ctx).apply {
            text = "個数"
            setPadding(0, (16 * dp).toInt(), 0, (2 * dp).toInt())
        }
        val pkgInput = EditText(ctx).apply {
            hint = "0"
            setText(if (delivery.packageCount > 0) delivery.packageCount.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(templateRow)
        layout.addView(timeLabel)
        layout.addView(timeInput)
        layout.addView(pkgLabel)
        layout.addView(pkgInput)

        AlertDialog.Builder(ctx)
            .setTitle("時間帯・個数の設定")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val slot = timeInput.text.toString().trim().ifBlank { null }
                val pkg  = pkgInput.text.toString().toIntOrNull() ?: 0
                viewModel.updateTimeSlotAndPackage(delivery.id, slot, pkg)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showBatchTimeSlotDialog(ids: Set<String>) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        val templates = com.rodgers.routist.util.AppSettings.getTimeSlotTemplates(ctx)
        val timeInput = EditText(ctx).apply {
            hint = "例: 14:00-16:00　（空白でクリア）"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val scrollView = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        templates.forEach { tmpl ->
            val btn = Button(ctx).apply {
                text = tmpl
                textSize = 12f
                setPadding((10 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
                setOnClickListener { timeInput.setText(tmpl) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * dp).toInt()
                ).apply { marginEnd = (6 * dp).toInt() }
            }
            chipRow.addView(btn)
        }
        scrollView.addView(chipRow)

        val hintText = TextView(ctx).apply {
            text = "時間帯を選択または入力"
            textSize = 12f
            setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, 0, 0, (4 * dp).toInt())
        }

        layout.addView(hintText)
        layout.addView(scrollView)
        layout.addView(timeInput)

        AlertDialog.Builder(ctx)
            .setTitle("${ids.size}件の時間帯を一括設定")
            .setView(layout)
            .setPositiveButton("適用") { _, _ ->
                val slot = timeInput.text.toString().trim().ifBlank { null }
                viewModel.batchUpdateTimeSlot(ids, slot)
                exitSelectMode()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showTemplateEditDialog() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val AppSettings = com.rodgers.routist.util.AppSettings
        val templates = AppSettings.getTimeSlotTemplatesWithColor(ctx).toMutableList()
        var selectedNewHex = TimeSlotColor.PALETTE.first()

        fun makeDotDrawable(hexColor: String) = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(try { android.graphics.Color.parseColor(hexColor) } catch (_: Exception) { android.graphics.Color.GRAY })
        }

        // 2行×6列のカラーパレット
        fun paletteGrid(currentHex: String, onPick: (String) -> Unit): LinearLayout {
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }
            val palette = TimeSlotColor.PALETTE
            val dotViews = mutableListOf<android.view.View>()
            for (r in 0..1) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0..5) {
                    val idx = r * 6 + c
                    val hex = palette[idx]
                    val sz = (28 * dp).toInt()
                    val dot = android.view.View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(sz, sz)
                            .also { it.marginEnd = (6 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
                        background = makeDotDrawable(hex).also {
                            if (hex.equals(currentHex, ignoreCase = true))
                                it.setStroke((3 * dp).toInt(), android.graphics.Color.BLACK)
                        }
                        setOnClickListener {
                            dotViews.forEachIndexed { j, v ->
                                (v.background as? android.graphics.drawable.GradientDrawable)
                                    ?.setStroke(if (j == idx) (3 * dp).toInt() else 0, android.graphics.Color.BLACK)
                                v.invalidate()
                            }
                            onPick(hex)
                        }
                    }
                    dotViews.add(dot)
                    row.addView(dot)
                }
                container.addView(row)
            }
            return container
        }

        // テンプレート一覧 RecyclerView
        lateinit var rvAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        rvAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = templates.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val handle = android.widget.ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_drag_handle)
                    alpha = 0.35f
                    layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                        .also { it.marginEnd = (10 * dp).toInt() }
                }
                val dot = android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                        .also { it.marginEnd = (10 * dp).toInt() }
                    background = makeDotDrawable("#888888")
                }
                val label = TextView(ctx).apply {
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val del = android.widget.ImageButton(ctx).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    background = null
                    layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                }
                row.addView(handle); row.addView(dot); row.addView(label); row.addView(del)
                return object : RecyclerView.ViewHolder(row) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tmpl = templates[position]
                val row = holder.itemView as LinearLayout
                val dot   = row.getChildAt(1)
                val label = row.getChildAt(2) as TextView
                val del   = row.getChildAt(3) as android.widget.ImageButton

                (dot.background as? android.graphics.drawable.GradientDrawable)
                    ?.setColor(try { android.graphics.Color.parseColor(tmpl.colorHex) } catch (_: Exception) { android.graphics.Color.GRAY })
                dot.invalidate()
                label.text = tmpl.name
                try { label.setTextColor(android.graphics.Color.parseColor(tmpl.colorHex)) } catch (_: Exception) {}

                dot.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos < 0) return@setOnClickListener
                    val cur = templates[pos]
                    var colorDlg: AlertDialog? = null
                    val inner = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                    }
                    inner.addView(paletteGrid(cur.colorHex) { newHex ->
                        templates[pos] = cur.copy(colorHex = newHex)
                        AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                        rvAdapter.notifyItemChanged(pos)
                        colorDlg?.dismiss()
                    })
                    colorDlg = AlertDialog.Builder(ctx)
                        .setTitle("「${cur.name}」の色")
                        .setView(inner)
                        .setNegativeButton("キャンセル", null)
                        .show()
                }

                del.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos < 0) return@setOnClickListener
                    templates.removeAt(pos)
                    AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                    rvAdapter.notifyItemRemoved(pos)
                }
            }
        }

        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = rvAdapter
            isNestedScrollingEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: androidx.recyclerview.widget.RecyclerView, vh: RecyclerView.ViewHolder) =
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition; val to = target.adapterPosition
                templates.add(to, templates.removeAt(from))
                rvAdapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, d: Int) {}
            override fun isLongPressDragEnabled() = true
            override fun clearView(rv: androidx.recyclerview.widget.RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
            }
        }).attachToRecyclerView(rv)

        // 新規追加エリア
        val previewDot = android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                .also { it.marginEnd = (8 * dp).toInt() }
            background = makeDotDrawable(selectedNewHex)
        }
        val addInput = EditText(ctx).apply {
            hint = "テンプレート名"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(previewDot); addView(addInput)
        }

        val colorPickerWrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            addView(paletteGrid(selectedNewHex) { hex ->
                selectedNewHex = hex
                (previewDot.background as? android.graphics.drawable.GradientDrawable)
                    ?.setColor(try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { android.graphics.Color.GRAY })
                previewDot.invalidate()
            })
        }

        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(rv); addView(addRow); addView(colorPickerWrapper)
        }

        val tmplDlg = AlertDialog.Builder(ctx)
            .setTitle("テンプレートを編集")
            .setView(outer)
            .setPositiveButton("追加", null)
            .setNegativeButton("閉じる", null)
            .show()
        tmplDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = addInput.text.toString().trim()
            if (newName.isBlank()) {
                addInput.error = "テンプレート名を入力してください"
            } else {
                templates.add(com.rodgers.routist.util.AppSettings.TimeSlotTemplate(newName, selectedNewHex))
                AppSettings.saveTimeSlotTemplatesWithColor(ctx, templates)
                rvAdapter.notifyItemInserted(templates.size - 1)
                addInput.setText("")
                Toast.makeText(ctx, "「$newName」を追加しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNavigation(delivery: Delivery) {
        val uri = if (delivery.hasLocation) {
            Uri.parse("google.navigation:q=${delivery.lat},${delivery.lng}&mode=d")
        } else {
            Uri.parse("google.navigation:q=${Uri.encode(delivery.address)}&mode=d")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(delivery.address)}")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }


    // ────────────────────────────────────────────────────────────

    private fun showListActions() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val redColor         = ContextCompat.getColor(ctx, R.color.colorActionRed)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー（タイトル + × ボタン）
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val currentRouteColor = try {
            android.graphics.Color.parseColor(viewModel.currentGroup()?.colorHex ?: "#F44336")
        } catch (_: Exception) { android.graphics.Color.parseColor("#F44336") }
        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(android.view.View(ctx).apply {
            val s = (14 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = (10 * dp).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(currentRouteColor)
            }
        })
        titleRow.addView(TextView(ctx).apply {
            text = viewModel.currentGroup()?.name ?: "ルートメニュー"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
        })
        headerRow.addView(titleRow)
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        val rippleRes = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(rippleRes)
                setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = emoji; textSize = 28f; gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = title; textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(color)
            })
            if (sub.isNotBlank()) col.addView(TextView(ctx).apply {
                text = sub; textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#555555"))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
            })
            row.addView(col)
            row.setOnClickListener { sheet.dismiss(); action() }
            root.addView(row)
        }

        fun divider() = root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
            setBackgroundColor(outlineVariant)
        })

        // ── データ追加
        row("☑️", "選択モード", "複数の配達先を選んで操作する") {
            if (adapter.isSelectMode) exitSelectMode() else enterSelectMode()
        }
        row("📷", "伝票からスキャン", "カメラで伝票を撮影して住所を読み取る") { launchScanActivity() }
        row("📥", "住所をインポート", "テキスト・CSV・Excelファイルから追加する") {
            inputLauncher.launch(Intent(requireContext(), InputActivity::class.java))
        }
        divider()
        // ── ルート管理
        row("✏️", "ルート名を変更", "現在のルート名を編集する") { showRenameGroupDialog() }
        row("➕", "新しいルートを追加", "新しい配達ルートを作成する") { showCreateGroupDialog() }
        row("📄", "ルートを複製", "同じ内容で新しいルートを作成する") {
            if (!LicenseManager.isPro(ctx)) { LicenseManager.showUpgradeDialog(ctx); return@row }
            val groupId = viewModel.currentGroupId.value ?: return@row
            viewModel.copyGroup(groupId)
        }
        row("📤", "ルートを共有", "LINE・SMS等で送る") { shareList() }
        divider()
        // ── 状態・設定
        row("↩️", "完了をリセット", "全件を未完了に戻す") { confirmResetCompleted() }
        row("✅", "全件を完了にする", "すべてに完了マークをつける") { confirmMarkAllCompleted() }
        val areaLabel = viewModel.areaHint.value?.ifBlank { null } ?: "未設定"
        row("⚙", "配達地域", "現在: $areaLabel") { showAreaSettingDialog() }
        if (areaLabel != "未設定") {
            row("🔧", "エリア外住所を修正", "配達地域と照合してエリア外の住所を修正する") {
                viewModel.fetchOutOfAreaCandidates()
                sheet.dismiss()
            }
        }
        divider()
        // ── 危険操作
        row("🗑", "このルートを削除", "削除後は元に戻せません", redColor) { confirmDeleteGroup() }

        root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = android.widget.ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(scrollView)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = false
        }
        sheet.show()
    }

    private fun launchScanActivity() {
        scanLauncher.launch(Intent(requireContext(), ScanActivity::class.java))
    }

    private fun showOutOfAreaCandidatesDialog(items: List<DeliveryViewModel.OutOfAreaItem>) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val primaryColor     = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)

        val selectedResults = mutableMapOf<String, com.rodgers.routist.util.GeocodingClient.GeoResult>()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(ctx).apply {
            text = "🔧 エリア外住所の修正（${items.size}件）"
            textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        // 各エリア外アイテム
        val bodyLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt())
        }

        items.forEachIndexed { index, item ->
            val delivery = item.delivery

            if (index > 0) bodyLayout.addView(android.view.View(ctx).apply {
                setBackgroundColor(outlineVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                    .also { it.setMargins(0, (12 * dp).toInt(), 0, (12 * dp).toInt()) }
            })

            // 名前
            bodyLayout.addView(TextView(ctx).apply {
                text = if (!delivery.name.isNullOrBlank()) delivery.name else "（名前なし）"
                textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(onSurfaceColor)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (8 * dp).toInt() }
            })
            // 現在住所
            bodyLayout.addView(TextView(ctx).apply {
                text = "現在: ${delivery.address}"; textSize = 13f
                setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (8 * dp).toInt() }
            })

            if (item.candidates.isEmpty()) {
                bodyLayout.addView(TextView(ctx).apply {
                    text = "候補が見つかりませんでした"; textSize = 14f
                    setTextColor(onSurfaceVariant)
                })
            } else {
                val radioGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
                item.candidates.forEachIndexed { idx, candidate ->
                    val rb = android.widget.RadioButton(ctx)
                    rb.id = View.generateViewId()
                    rb.text = candidate.formattedAddress; rb.textSize = 14f
                    rb.setTextColor(onSurfaceColor)
                    rb.isChecked = idx == 0
                    radioGroup.addView(rb)
                    if (idx == 0) selectedResults[delivery.id] = candidate
                    rb.setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedResults[delivery.id] = candidate
                    }
                }
                bodyLayout.addView(radioGroup)
            }
        }

        val scroll = ScrollView(ctx).apply {
            addView(bodyLayout)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(scroll)

        // 保存ボタン
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })
        val saveBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "保存"; textSize = 16f
            setBackgroundColor(primaryColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * dp).toInt())
                .also { it.setMargins((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt()) }
            setOnClickListener {
                selectedResults.forEach { (deliveryId, result) ->
                    viewModel.applyOutOfAreaFix(deliveryId, result)
                }
                Toast.makeText(ctx, "${selectedResults.size}件を修正しました", Toast.LENGTH_SHORT).show()
                sheet.dismiss()
            }
        }
        root.addView(saveBtn)

        sheet.setContentView(root)
        sheet.show()
    }

    private fun showAreaSettingDialog() {
        val ctx = requireContext()
        val edit = EditText(ctx).apply {
            setText(viewModel.areaHint.value ?: "")
            hint = "例：横浜市, 静岡市（漢字で入力）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(64, 24, 64, 24)
        }
        AlertDialog.Builder(ctx)
            .setTitle("配達地域")
            .setMessage("都道府県・市区町村を設定すると、住所の検索精度が上がります。複数のエリアはカンマ区切りで入力できます。")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val area = edit.text.toString().trim()
                viewModel.setAreaHint(area)
                if (area.isNotBlank()) showAddressCheckResult(area)
            }
            .setNeutralButton("クリア") { _, _ ->
                viewModel.setAreaHint("")
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun areaMatches(address: String, area: String): Boolean {
        val areas = area.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
        return areas.any { single ->
            if (address.contains(single)) return@any true
            // 都道府県プレフィックスを除いた市区町村以降で再確認
            val cityPart = single.replace(Regex("^.+[都道府県]"), "")
            cityPart.length >= 2 && address.contains(cityPart)
        }
    }

    private fun showAddressCheckResult(area: String) {
        val deliveries = viewModel.deliveries.value ?: return
        if (deliveries.isEmpty()) return
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val notGeocoded = deliveries.filter { !it.isGeocoded }
        val mismatched  = deliveries.filter { it.isGeocoded && !areaMatches(it.address, area) }

        if (mismatched.isEmpty() && notGeocoded.isEmpty()) {
            Toast.makeText(ctx, "✅ 全 ${deliveries.size} 件が「$area」内の住所です", Toast.LENGTH_SHORT).show()
            return
        }

        val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val warnColor        = ContextCompat.getColor(ctx, R.color.colorActionRed)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
        }

        // ヘッダー
        root.addView(TextView(ctx).apply {
            text = "住所精査結果"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
        })
        root.addView(View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        fun sectionLabel(text: String, color: Int) = root.addView(TextView(ctx).apply {
            this.text = text; textSize = 15f; setTextColor(color)
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt())
        })

        fun itemRow(delivery: Delivery) = root.addView(TextView(ctx).apply {
            val label = buildString {
                append("${delivery.order}. ")
                if (!delivery.name.isNullOrBlank()) append("${delivery.name}  ")
                append(delivery.address)
            }
            text = label; textSize = 13f; setTextColor(onSurfaceVariant); maxLines = 2
            setPadding((36 * dp).toInt(), (4 * dp).toInt(), (20 * dp).toInt(), (4 * dp).toInt())
        })

        // エリア外の可能性がある件
        if (mismatched.isNotEmpty()) {
            sectionLabel("⚠️  ${mismatched.size}件がエリア外の可能性があります", warnColor)
            mismatched.forEach { itemRow(it) }
        }

        // 検索未完了の件
        if (notGeocoded.isNotEmpty()) {
            if (mismatched.isNotEmpty()) root.addView(View(ctx).apply {
                setBackgroundColor(outlineVariant)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                    .also { it.setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) }
            })
            sectionLabel("⏳  ${notGeocoded.size}件は住所検索中です（精査待ち）", onSurfaceVariant)
            notGeocoded.forEach { itemRow(it) }
        }

        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (32 * dp).toInt())
        })

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        sheet.setContentView(ScrollView(ctx).apply { addView(root) })
        sheet.show()
    }

    private fun showRenameGroupDialog() {
        val ctx = requireContext()
        val group = viewModel.currentGroup() ?: return
        val input = EditText(ctx).apply {
            setText(group.name); selectAll()
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(20))
        }
        AlertDialog.Builder(ctx)
            .setTitle("ルート名を変更")
            .setView(input)
            .setPositiveButton("変更") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) viewModel.renameGroup(group.id, name)
            }
            .setNegativeButton("キャンセル", null)
            .show()
        input.postDelayed({
            input.requestFocus()
            val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun showCreateGroupDialog() {
        val ctx = requireContext()
        val existingCount = viewModel.groups.value?.size ?: 0
        if (existingCount >= 1 && !LicenseManager.isPro(ctx)) {
            LicenseManager.showUpgradeDialog(ctx)
            return
        }
        val input = EditText(ctx).apply {
            hint = "ルート名を入力"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(20))
        }
        AlertDialog.Builder(ctx)
            .setTitle("新しいルートを追加")
            .setView(input)
            .setPositiveButton("作成") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    val group = viewModel.createGroup(name)
                    viewModel.switchGroup(group.id)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showLinkPatternDialog(parentSheet: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val ctx = requireContext()
        val group = viewModel.currentGroup() ?: return
        val patterns = com.rodgers.routist.util.PatternStorage.getAll(ctx)
        if (patterns.isEmpty()) {
            android.widget.Toast.makeText(ctx, "帳票パターンがありません。先に帳票設定から作成してください。", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val items = (listOf("設定しない") + patterns.map { it.title }).toTypedArray()
        val currentIdx = if (group.patternId == -1) 0 else patterns.indexOfFirst { it.id == group.patternId }.let { if (it < 0) 0 else it + 1 }
        parentSheet.dismiss()
        AlertDialog.Builder(ctx)
            .setTitle("「${group.name}」の帳票パターン")
            .setSingleChoiceItems(items, currentIdx, null)
            .setPositiveButton("設定") { dialog, _ ->
                val lv = (dialog as AlertDialog).listView
                val sel = lv.checkedItemPosition
                val newPatternId = if (sel <= 0) -1 else patterns[sel - 1].id
                viewModel.linkPatternToGroup(group.id, newPatternId)
                android.widget.Toast.makeText(ctx,
                    if (newPatternId == -1) "帳票パターンの設定を解除しました"
                    else "「${patterns[sel - 1].title}」を設定しました",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmResetCompleted() {
        val done = viewModel.deliveries.value?.count { it.isCompleted } ?: 0
        if (done == 0) {
            android.widget.Toast.makeText(requireContext(), "完了済みの件数がありません", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("完了をリセット")
            .setMessage("${done}件の完了を未完了に戻しますか？")
            .setPositiveButton("リセット") { _, _ -> viewModel.resetAllCompleted() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmMarkAllCompleted() {
        val remaining = viewModel.deliveries.value?.count { !it.isCompleted } ?: 0
        if (remaining == 0) {
            android.widget.Toast.makeText(requireContext(), "全件すでに完了しています", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("全件を完了にする")
            .setMessage("残り${remaining}件を完了にしますか？")
            .setPositiveButton("完了にする") { _, _ -> viewModel.markAllCompleted() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showProgressDialog() {
        val list = viewModel.deliveries.value ?: emptyList()
        val total = list.size
        if (total == 0) {
            android.widget.Toast.makeText(requireContext(), "リストが空です", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val done = list.count { it.isCompleted }
        val remaining = total - done
        val percent = done * 100 / total
        val groupName = viewModel.currentGroup()?.name ?: "配達リスト"
        AlertDialog.Builder(requireContext())
            .setTitle("📈  $groupName")
            .setMessage("完了　　$done 件\n残り　　$remaining 件\n合計　　$total 件\n\n進捗　　$percent%")
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun confirmDeleteGroup() {
        val group = viewModel.currentGroup() ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("ルートを削除")
            .setMessage("「${group.name}」を削除しますか？\n削除後は元に戻せません。")
            .setPositiveButton("削除") { _, _ -> viewModel.deleteGroup(group.id) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun shareList() {
        val deliveries = viewModel.deliveries.value
        if (deliveries.isNullOrEmpty()) {
            android.widget.Toast.makeText(requireContext(), "リストが空です", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val groupName = viewModel.groups.value?.find { it.id == viewModel.currentGroupId.value }?.name ?: "配達リスト"
        val lines = buildString {
            append("$groupName（${deliveries.size}件）\n")
            deliveries.forEachIndexed { i, d ->
                val label = if (!d.name.isNullOrBlank()) "${d.name}（${d.address}）" else d.address
                val extras = buildList {
                    if (!d.timeSlot.isNullOrBlank()) add(d.timeSlot!!)
                    if (d.packageCount > 0) add("${d.packageCount}個")
                    if (!d.note.isNullOrBlank()) add(d.note!!)
                }.joinToString(" / ")
                append("${i + 1}. $label")
                if (extras.isNotBlank()) append("  [$extras]")
                append("\n")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$groupName（${deliveries.size}件）")
            putExtra(Intent.EXTRA_TEXT, lines.trim())
        }
        startActivity(Intent.createChooser(intent, "ルートを共有"))
    }

    private fun importList(text: String) {
        val ctx = requireContext()
        val toAdd = mutableListOf<Delivery>()
        try {
            if (text.startsWith("[DLIST:")) {
                // 旧 JSON 形式（アプリ間ドッキング用）
                val jsonStr = text.substringAfter("\n").trim()
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val address = obj.optString("a")
                    if (address.isBlank()) continue
                    toAdd.add(
                        Delivery(
                            order = i + 1,
                            address = address,
                            name = obj.optString("n").ifBlank { null },
                            timeSlot = obj.optString("t").ifBlank { null },
                            packageCount = obj.optInt("c", 0),
                            note = obj.optString("m").ifBlank { null }
                        )
                    )
                }
            } else {
                // プレーンテキスト形式（1行1件の住所リスト）
                val stripPrefix = Regex("^\\s*[0-9]+[.．。)）]\\s*|^\\s*[・\\-→▶]\\s*")
                val lines = text.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val startIdx = if (lines.firstOrNull()?.contains("件") == true) 1 else 0
                lines.drop(startIdx).forEachIndexed { i, raw ->
                    val withoutExtras = raw.substringBefore("  [").substringBefore("\t[")
                    val cleaned = stripPrefix.replace(withoutExtras, "").trim()
                    if (cleaned.isBlank()) return@forEachIndexed
                    val nameAddrMatch = Regex("^(.+?)（(.+)）$").find(cleaned)
                    val (name, address) = if (nameAddrMatch != null) {
                        nameAddrMatch.groupValues[1].trim() to nameAddrMatch.groupValues[2].trim()
                    } else {
                        null to cleaned
                    }
                    toAdd.add(Delivery(order = i + 1, address = address, name = name))
                }
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "読み込みに失敗しました", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (toAdd.isEmpty()) {
            android.widget.Toast.makeText(ctx, "インポートできるデータがありませんでした", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // ── Step A: ルート選択
        val groups = viewModel.groups.value ?: emptyList()
        when {
            groups.isEmpty() -> {
                // ルートなし → 新しいルートを作成してインポート
                val input = android.widget.EditText(ctx).apply {
                    hint = "例: 月曜ルート"
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    setPadding(64, 32, 64, 16)
                }
                AlertDialog.Builder(ctx)
                    .setTitle("新しいルートを作成")
                    .setMessage("ルートがありません。インポート先のルート名を入力してください。")
                    .setView(input)
                    .setPositiveButton("作成してインポート") { _, _ ->
                        val name = input.text.toString().trim().ifBlank { "ルート1" }
                        val group = viewModel.createGroup(name)
                        viewModel.switchGroup(group.id)
                        importListToCurrentGroup(toAdd)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            groups.size > 1 -> {
                // 複数ルートあり → どのルートに追加するか選択
                val currentId = viewModel.currentGroupId.value ?: ""
                val items = (groups.map { g ->
                    if (g.id == currentId) "${g.name}（現在）" else g.name
                } + listOf("＋ 新しいルートを作成")).toTypedArray()
                AlertDialog.Builder(ctx)
                    .setTitle("インポート先のルートを選択")
                    .setItems(items) { _, which ->
                        if (which == groups.size) {
                            // 新しいルートを作成
                            val input = android.widget.EditText(ctx).apply {
                                hint = "例: 月曜ルート"
                                inputType = android.text.InputType.TYPE_CLASS_TEXT
                                setPadding(64, 32, 64, 16)
                            }
                            AlertDialog.Builder(ctx)
                                .setTitle("新しいルートを作成")
                                .setView(input)
                                .setPositiveButton("作成してインポート") { _, _ ->
                                    val name = input.text.toString().trim().ifBlank { "ルート${groups.size + 1}" }
                                    val group = viewModel.createGroup(name)
                                    viewModel.switchGroup(group.id)
                                    importListToCurrentGroup(toAdd)
                                }
                                .setNegativeButton("キャンセル", null)
                                .show()
                        } else {
                            val chosen = groups[which]
                            if (chosen.id != currentId) viewModel.switchGroup(chosen.id)
                            importListToCurrentGroup(toAdd)
                        }
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            else -> {
                // ルートが1つのみ → そのまま Step B へ
                importListToCurrentGroup(toAdd)
            }
        }
    }

    private fun importListToCurrentGroup(toAdd: List<Delivery>) {
        val ctx = requireContext()
        val existingCount = viewModel.deliveries.value?.size ?: 0
        val routeName = viewModel.currentGroup()?.name ?: "現在のルート"
        if (existingCount > 0) {
            // ── Step B: 追加 or 置き換え
            AlertDialog.Builder(ctx)
                .setTitle("${toAdd.size}件をインポート")
                .setMessage("「$routeName」にはすでに${existingCount}件あります。\nどちらで処理しますか？")
                .setPositiveButton("追加する") { _, _ -> proceedWithImport(toAdd, replace = false) }
                .setNeutralButton("置き換える") { _, _ -> proceedWithImport(toAdd, replace = true) }
                .setNegativeButton("キャンセル", null)
                .show()
        } else {
            proceedWithImport(toAdd, replace = false)
        }
    }

    private fun proceedWithImport(toAdd: List<Delivery>, replace: Boolean) {
        val ctx = requireContext()
        val routeName = viewModel.currentGroup()?.name ?: "現在のルート"

        fun doImport(items: List<Delivery>, excludedCount: Int = 0) {
            if (replace) viewModel.replaceDeliveries(items)
            else viewModel.appendDeliveries(items)
            val action = if (replace) "件で置き換えました" else "件を追加しました"
            val msg = if (excludedCount > 0) "${items.size}$action（${excludedCount}件を除外）"
                      else "${items.size}$action"
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        // ── Step C: エリアチェック（1件以下はスキップ）
        if (toAdd.size <= 1) {
            val verb = if (replace) "置き換え" else "追加"
            AlertDialog.Builder(ctx)
                .setTitle("${toAdd.size}件をインポート")
                .setMessage("「$routeName」に${verb}します。よろしいですか？")
                .setPositiveButton(verb) { _, _ -> doImport(toAdd) }
                .setNegativeButton("キャンセル", null)
                .show()
            return
        }

        val prefRegex = Regex("""[^\s]+[都道府県]""")

        // 置き換えモードでは既存リストを無視してエリアチェック
        val existingByPref = if (replace) linkedMapOf()
        else {
            val map = linkedMapOf<String, Int>()
            for (d in viewModel.deliveries.value ?: emptyList()) {
                val p = prefRegex.find(d.address)?.value ?: continue
                map[p] = (map[p] ?: 0) + 1
            }
            map
        }

        val newByPref = linkedMapOf<String, MutableList<Delivery>>()
        val noPref = mutableListOf<Delivery>()
        for (d in toAdd) {
            val p = prefRegex.find(d.address)?.value
            if (p != null) newByPref.getOrPut(p) { mutableListOf() }.add(d)
            else noPref.add(d)
        }

        val newPrefs = newByPref.keys.toSet()
        val existingPrefs = existingByPref.keys.toSet()
        val wouldMix = newByPref.size > 1 || (existingPrefs.isNotEmpty() && newPrefs.any { it !in existingPrefs })

        if (wouldMix) {
            val newPrefList = newByPref.keys.toList()
            val items = newPrefList.map { p ->
                val existCount = existingByPref[p]
                if (existCount != null) "$p（追加${newByPref[p]!!.size}件 / 既存${existCount}件）"
                else "$p（${newByPref[p]!!.size}件）※新しいエリア"
            }.toTypedArray()
            val checked = BooleanArray(newPrefList.size) { true }

            val existingInfo = if (existingPrefs.isNotEmpty()) {
                val others = existingPrefs - newPrefs
                if (others.isNotEmpty()) "既存リスト: ${others.joinToString("・") { "$it（${existingByPref[it]}件）" }}\n\n"
                else ""
            } else ""

            val verb = if (replace) "置き換え" else "追加"
            AlertDialog.Builder(ctx)
                .setTitle("${verb}するエリアを選択")
                .setMessage("「$routeName」に${verb}します。\n${existingInfo}${verb}するエリアのチェックを確認してください。")
                .setMultiChoiceItems(items, checked) { _, i, v -> checked[i] = v }
                .setPositiveButton(verb) { _, _ ->
                    val selected = mutableListOf<Delivery>()
                    newPrefList.forEachIndexed { i, p -> if (checked[i]) selected.addAll(newByPref[p]!!) }
                    selected.addAll(noPref)
                    if (selected.isEmpty()) return@setPositiveButton
                    val ordered = selected.sortedBy { toAdd.indexOf(it) }
                    doImport(ordered, toAdd.size - ordered.size)
                }
                .setNegativeButton("キャンセル", null)
                .show()
            return
        }

        // 単一エリアかつ混在なし → 通常確認
        val verb = if (replace) "置き換え" else "追加"
        AlertDialog.Builder(ctx)
            .setTitle("${toAdd.size}件をインポート")
            .setMessage("「$routeName」に${verb}します。よろしいですか？")
            .setPositiveButton(verb) { _, _ -> doImport(toAdd) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun toggleMapView() {
        if (isMapVisible) switchToListView() else showMapView()
    }

    fun showMapView() {
        isMapVisible = true
        binding.mapContainer.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.layoutProgress.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE
        binding.chipIncomplete.visibility = View.GONE
        binding.buttonListMenu.visibility = View.GONE
        binding.buttonMapToggle.text = "一覧"
        if (childFragmentManager.findFragmentByTag("map") == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.mapContainer, MapFragment(), "map")
                .commit()
        }
    }

    fun switchToListView() {
        isMapVisible = false
        binding.mapContainer.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.chipIncomplete.visibility = View.VISIBLE
        binding.buttonListMenu.visibility = View.VISIBLE
        binding.buttonMapToggle.text = "🗺"
        applyFilter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerView.adapter = null
        _binding = null
    }
}

class DeliveryAdapter(
    private val onTap: (Delivery) -> Unit = {},
    private val onLongPress: () -> Unit = {},
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
        imageExecutor.shutdown()
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
            // 店名があれば店名のみ表示、なければ住所を表示
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

            // 時間帯・個数表示
            val hasSlot = !delivery.timeSlot.isNullOrBlank()
            val hasPkg = delivery.packageCount > 0
            if (hasSlot || hasPkg) {
                layoutSlotPackage.visibility = View.VISIBLE
                tvTimeSlot.text = if (hasSlot) "🕐 ${delivery.timeSlot}" else ""
                tvTimeSlot.visibility = if (hasSlot) View.VISIBLE else View.GONE
                if (hasSlot) {
                    val tpls = com.rodgers.routist.util.AppSettings.getTimeSlotTemplatesWithColor(tvTimeSlot.context)
                    val slotColor = TimeSlotColor.colorFor(delivery.timeSlot, tpls)
                    if (slotColor != null) tvTimeSlot.setTextColor(slotColor)
                    else tvTimeSlot.setTextColor(tvTimeSlot.context.getColor(R.color.colorTimeSlot))
                }
                tvPackageCount.text = if (hasPkg) "📦 ${delivery.packageCount}個" else ""
                tvPackageCount.visibility = if (hasPkg) View.VISIBLE else View.GONE
            } else {
                layoutSlotPackage.visibility = View.GONE
            }

            // メモ・部屋リスト表示
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

            // 写真サムネイル（複数対応・横スクロール）
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
                itemView.alpha = if (delivery.id in selectedIds) 1.0f else 0.6f
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
                itemView.setOnLongClickListener { true }
            }
        }
    }
}
