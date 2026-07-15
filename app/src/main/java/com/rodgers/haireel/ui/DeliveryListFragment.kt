package com.rodgers.haireel.ui

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
import com.rodgers.haireel.model.Room
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.haireel.R
import com.rodgers.haireel.databinding.FragmentListBinding
import com.rodgers.haireel.util.AddressParser
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.util.TimeSlotColor
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.viewmodel.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeliveryListFragment : Fragment() {

    internal var _binding: FragmentListBinding? = null
    internal val binding get() = _binding!!
    internal val viewModel: DeliveryViewModel by activityViewModels()
    internal lateinit var adapter: DeliveryAdapter
    internal var filterMode = FilterMode.ALL
    internal var progressDisplayMode = ProgressDisplay.COUNT

    enum class ProgressDisplay { COUNT, PERCENT, REMAINING, HIDDEN }

    enum class ViewMode { LIST, MAP }
    internal var viewMode = ViewMode.LIST

    internal var pendingPhotoDeliveryId: String? = null
    internal var pendingPhotoFilePath: String? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var distanceDecoration: DistanceItemDecoration

    // 積み込みチェック（インメモリ。アプリ再起動でリセット）
    internal val loadedIds = mutableSetOf<String>()

    internal val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val id = pendingPhotoDeliveryId ?: return@registerForActivityResult
        val path = pendingPhotoFilePath ?: return@registerForActivityResult
        pendingPhotoDeliveryId = null; pendingPhotoFilePath = null
        if (success) viewModel.addPhoto(id, path)
    }

    internal val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pendingPhotoDeliveryId ?: return@registerForActivityResult
        pendingPhotoDeliveryId = null
        uri ?: return@registerForActivityResult
        val dest = File(requireContext().filesDir, "camera_photos/delivery_photo_${id}_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
                withContext(Dispatchers.Main) { viewModel.addPhoto(id, dest.absolutePath) }
            } catch (e: Exception) { Log.w("DeliveryListFragment", "カメラ写真コピー失敗", e) }
        }
    }

    internal val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingPhotoDeliveryId?.let { launchCamera(it) }
    }

    internal val inputLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
            onDragStart = { vh -> itemTouchHelper.startDrag(vh) },
            onNoteClick = { delivery -> showNoteView(delivery) },
            onPhotoClick = { delivery, index -> showPhotosViewer(delivery, index) },
            onSelectionChanged = { updateSelectionUI() }
        )

        distanceDecoration = DistanceItemDecoration(requireContext())
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@DeliveryListFragment.adapter
            addItemDecoration(distanceDecoration)
        }

        itemTouchHelper = buildItemTouchHelper()
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        setupFilterChip()
        setupSelectionBar()

        binding.buttonSubToggle.setOnClickListener { cycleViewMode() }
        binding.buttonListMenu.setOnClickListener { showListActions() }
        binding.layoutProgress.setOnClickListener { cycleProgressDisplay() }

        observeFlows()
    }

    private fun buildItemTouchHelper(): ItemTouchHelper {
        var orderChanged = false
        return ItemTouchHelper(object : ItemTouchHelper.Callback() {

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if (adapter.isSelectMode) makeMovementFlags(0, 0)
                else makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(vh.adapterPosition, target.adapterPosition)
                orderChanged = true
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            // ハンドルタッチで startDrag() を呼ぶため長押しドラッグは無効
            override fun isLongPressDragEnabled() = false

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                adapter.isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh?.itemView?.alpha = 0.8f
                    vh?.itemView?.elevation = 12f
                    orderChanged = false
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                adapter.isDragging = false
                vh.itemView.alpha = 1.0f
                vh.itemView.elevation = 0f
                if (orderChanged) {
                    viewModel.reorderDeliveries(adapter.getCurrentList())
                    Snackbar.make(binding.root, "順序を保存しました", Snackbar.LENGTH_SHORT).show()
                    orderChanged = false
                }
            }
        })
    }

    private fun setupFilterChip() {
        binding.chipIncomplete.typeface = android.graphics.Typeface.DEFAULT_BOLD
        binding.chipIncomplete.text = "すべて"
        binding.chipIncomplete.isCheckable = false
        binding.chipIncomplete.setOnClickListener {
            filterMode = when (filterMode) {
                FilterMode.ALL        -> FilterMode.INCOMPLETE
                FilterMode.INCOMPLETE -> FilterMode.COMPLETED
                FilterMode.COMPLETED  -> FilterMode.ALL
            }
            applyFilter()
        }
    }

    private fun setupSelectionBar() {
        binding.buttonSelect.visibility = View.GONE
        binding.buttonSelect.setOnClickListener { exitSelectMode() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::adapter.isInitialized && adapter.isSelectMode) {
                        exitSelectMode()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            })

        binding.buttonMarkCompleted.setOnClickListener {
            val selected = adapter.selectedIds.toSet()
            if (selected.isEmpty()) return@setOnClickListener
            viewModel.markSelectedCompleted(selected)
            exitSelectMode()
        }

        binding.buttonResetCompleted.setOnClickListener {
            val selected = adapter.selectedIds.toSet()
            if (selected.isEmpty()) return@setOnClickListener
            viewModel.resetSelectedCompleted(selected)
            exitSelectMode()
        }

        binding.buttonDeleteSelected.setOnClickListener {
            val selected = adapter.selectedIds.toSet()
            if (selected.isEmpty()) return@setOnClickListener
            val count = selected.size
            exitSelectMode()
            val undoMs = AppSettings.getUndoSeconds(requireContext()) * 1000
            Snackbar.make(binding.root, "${count}件を削除しました", undoMs)
                .setAction("取り消す") { }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(snackbar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION && isAdded) viewModel.deleteDeliveries(selected)
                    }
                })
                .show()
        }

        binding.buttonSetTimeSlot.setOnClickListener {
            val selected = adapter.selectedIds
            if (selected.isEmpty()) return@setOnClickListener
            showBatchTimeSlotDialog(selected)
        }

        binding.buttonSelectAll.setOnClickListener {
            val list = viewModel.deliveries.value
            if (adapter.selectedIds.size == list.size) {
                adapter.clearSelection()
            } else {
                adapter.selectAll(list.map { it.id }.toSet())
            }
            updateSelectionUI()
        }
    }

    private fun cycleProgressDisplay() {
        progressDisplayMode = when (progressDisplayMode) {
            ProgressDisplay.COUNT     -> ProgressDisplay.PERCENT
            ProgressDisplay.PERCENT   -> ProgressDisplay.REMAINING
            ProgressDisplay.REMAINING -> ProgressDisplay.HIDDEN
            ProgressDisplay.HIDDEN    -> ProgressDisplay.COUNT
        }
        applyFilter()
    }

    private fun refreshAdapterGroupColor() {
        val hex = viewModel.currentGroup()?.colorHex ?: "#F44336"
        val c = try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { android.graphics.Color.parseColor("#F44336") }
        if (adapter.groupColor != c) {
            adapter.groupColor = c
            adapter.notifyDataSetChanged()
        }
    }

    private fun observeFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deliveries.collectLatest { applyFilter() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.openEditForDelivery.collectLatest { id ->
                if (id == null) return@collectLatest
                viewModel.clearEditRequest()
                val delivery = viewModel.deliveries.value.find { it.id == id } ?: return@collectLatest
                showItemOptions(delivery, showNavComplete = false)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentGroupId.collectLatest {
                filterMode = FilterMode.ALL
                binding.chipIncomplete.text = "すべて"
                exitSelectMode()
                applyFilter()
                refreshAdapterGroupColor()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groups.collectLatest { refreshAdapterGroupColor() }
        }
    }

    internal fun enterSelectMode() {
        adapter.isSelectMode = true
        adapter.notifyDataSetChanged()
        binding.buttonSelect.text = "✕ 解除"
        binding.buttonSelect.visibility = View.VISIBLE   // ← ボタンを表示
        binding.layoutSelectionBar.visibility = View.VISIBLE
        viewModel.setSelectMode(true)
        updateSelectionUI()

        // 初回のみ: 操作ヒントを Snackbar で表示
        val prefs = requireContext().getSharedPreferences("ui_hints", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("select_mode_hint_shown", false)) {
            prefs.edit().putBoolean("select_mode_hint_shown", true).apply()
            Snackbar.make(binding.root, "長押しで複数選択、ハンドルのドラッグで並べ替えができます", Snackbar.LENGTH_LONG).show()
        }
    }

    internal fun exitSelectMode() {
        adapter.isSelectMode = false
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        binding.buttonSelect.visibility = View.GONE      // ← ボタンを非表示に戻す
        binding.layoutSelectionBar.visibility = View.GONE
        viewModel.setSelectMode(false)
    }

    internal fun updateSelectionUI() {
        val count = adapter.selectedIds.size
        val total = viewModel.deliveries.value.size
        val hasSelection = count > 0
        binding.buttonDeleteSelected.text = if (hasSelection) "削除($count)" else "削除"
        binding.buttonDeleteSelected.isEnabled = hasSelection
        binding.buttonSelectAll.text = if (count == total && total > 0) "全解除" else "全選択"
        binding.buttonSetTimeSlot.isEnabled = hasSelection
        binding.buttonSetTimeSlot.text = if (hasSelection) "🕐($count)" else "🕐 時間帯"

        // 完了のみ表示中は「完了にする」不要、未完了のみ表示中は「未完了に戻す」不要
        val showMarkCompleted  = filterMode != FilterMode.COMPLETED
        val showResetCompleted = filterMode != FilterMode.INCOMPLETE
        binding.buttonMarkCompleted.visibility  = if (showMarkCompleted)  View.VISIBLE else View.GONE
        binding.buttonResetCompleted.visibility = if (showResetCompleted) View.VISIBLE else View.GONE
        binding.buttonMarkCompleted.isEnabled  = hasSelection && showMarkCompleted
        binding.buttonResetCompleted.isEnabled = hasSelection && showResetCompleted
        binding.buttonMarkCompleted.text  = if (hasSelection) "✅ 完了($count)"   else "✅ 完了にする"
        binding.buttonResetCompleted.text = if (hasSelection) "↩ 未完了($count)" else "↩ 未完了に戻す"
    }

    internal fun applyFilter() {
        val list = viewModel.deliveries.value
        val filtered = when (filterMode) {
            FilterMode.ALL -> list
            FilterMode.INCOMPLETE -> list.filter { !it.isCompleted }
            FilterMode.COMPLETED -> list.filter { it.isCompleted }
        }
        val sorted = filtered.sortedBy { it.order }
        adapter.submitList(sorted)
        val ctx = requireContext()
        distanceDecoration.setDeparture(
            com.rodgers.haireel.util.AppSettings.getDepartureLat(ctx),
            com.rodgers.haireel.util.AppSettings.getDepartureLng(ctx)
        )
        distanceDecoration.update(sorted)
        binding.recyclerView.invalidateItemDecorations()
        binding.textEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE

        // 合計距離バッジ更新
        val totalKm = distanceDecoration.totalKm
        if (sorted.size >= 2 && totalKm > 0) {
            binding.tvTotalDistance.visibility = View.VISIBLE
            val hasDep = com.rodgers.haireel.util.AppSettings.getDepartureLat(ctx) != 0.0 ||
                         com.rodgers.haireel.util.AppSettings.getDepartureLng(ctx) != 0.0
            binding.tvTotalDistance.text = if (hasDep) "↻ 概算${"%.1f".format(totalKm)}km" else "⟷ 概算${"%.1f".format(totalKm)}km"
        } else {
            binding.tvTotalDistance.visibility = View.GONE
        }

        val total = list.size
        val done  = list.count { it.isCompleted }

        // チップテキストを現在の状態に合わせて更新
        binding.chipIncomplete.text = when (filterMode) {
            FilterMode.ALL        -> if (total > 0 && done == total) "全完了✓" else "すべて"
            FilterMode.INCOMPLETE -> "未完了のみ"
            FilterMode.COMPLETED  -> "完了のみ"
        }
        if (filterMode == FilterMode.ALL) {
            val allDone = total > 0 && done == total
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

    /** リスト→地図→リスト と切り替え */
    internal fun cycleViewMode() {
        viewMode = when (viewMode) {
            ViewMode.LIST -> ViewMode.MAP
            ViewMode.MAP  -> ViewMode.LIST
        }
        applyViewMode()
    }

    internal fun applyViewMode() {
        val isMap  = viewMode == ViewMode.MAP
        val isList = viewMode == ViewMode.LIST

        binding.mapContainer.visibility   = if (isMap)  View.VISIBLE else View.GONE
        binding.recyclerView.visibility   = if (isList) View.VISIBLE else View.GONE
        binding.layoutProgress.isClickable = isList
        binding.layoutProgress.visibility  = if (isList) View.VISIBLE else View.GONE
        binding.textEmpty.visibility      = if (isList) View.VISIBLE else View.GONE
        binding.chipIncomplete.visibility  = if (isList) View.VISIBLE else View.GONE
        binding.buttonListMenu.visibility  = if (isList) View.VISIBLE else View.GONE

        binding.buttonSubToggle.text = when (viewMode) {
            ViewMode.LIST -> getString(R.string.btn_map_toggle)
            ViewMode.MAP  -> "📋 リスト"
        }

        if (isMap && childFragmentManager.findFragmentByTag("map") == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.mapContainer, MapFragment(), "map")
                .commitAllowingStateLoss()
        }
        if (isList) applyFilter()
    }

    // 既存コードとの互換性のために残す
    internal fun showMapView() {
        viewMode = ViewMode.MAP; applyViewMode()
    }
    internal fun switchToListView() {
        viewMode = ViewMode.LIST; applyViewMode()
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadFromDb()
        if (viewMode != ViewMode.LIST) applyViewMode()
    }

    override fun onStop() {
        super.onStop()
        // タブ切り替え・バックグラウンド移行時に選択モードを自動解除する
        if (::adapter.isInitialized && adapter.isSelectMode) exitSelectMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerView.adapter = null
        _binding = null
    }
}
