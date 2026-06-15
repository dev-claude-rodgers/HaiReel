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
import com.rodgers.routist.ui.MapFragment
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.util.TimeSlotColor
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.viewmodel.DeliveryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeliveryListFragment : Fragment() {

    internal var _binding: FragmentListBinding? = null
    internal val binding get() = _binding!!
    internal val viewModel: DeliveryViewModel by activityViewModels()
    internal lateinit var adapter: DeliveryAdapter
    internal var filterMode = FilterMode.ALL
    internal var progressDisplayMode = ProgressDisplay.COUNT

    enum class ProgressDisplay { COUNT, PERCENT, REMAINING, HIDDEN }

    internal var isMapVisible = false

    internal var pendingPhotoDeliveryId: String? = null
    internal var pendingPhotoFilePath: String? = null

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

    internal val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringExtra(ScanActivity.EXTRA_SCANNED_TEXT) ?: return@registerForActivityResult
            if (text.isNotBlank()) importList(text)
        }
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

    internal fun enterSelectMode() {
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

    internal fun exitSelectMode() {
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

    internal fun updateSelectionUI() {
        val count = adapter.selectedIds.size
        val total = viewModel.deliveries.value?.size ?: 0
        binding.buttonDeleteSelected.text = if (count > 0) "${count}件を削除" else "削除"
        binding.buttonDeleteSelected.isEnabled = count > 0
        binding.buttonSelectAll.text = if (count == total && total > 0) "全解除" else "全選択"
        binding.buttonSetTimeSlot.isEnabled = count > 0
        binding.buttonSetTimeSlot.text = if (count > 0) "🕐 ${count}件に設定" else "🕐 時間帯"
    }

    internal fun applyFilter() {
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

    internal fun showMapView() {
        isMapVisible = true
        binding.mapContainer.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.layoutProgress.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE
        binding.chipIncomplete.visibility = View.GONE
        binding.buttonListMenu.visibility = View.GONE
        binding.buttonMapToggle.text = "配達先リストへ戻る"
        if (childFragmentManager.findFragmentByTag("map") == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.mapContainer, MapFragment(), "map")
                .commit()
        }
    }

    internal fun switchToListView() {
        isMapVisible = false
        binding.mapContainer.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.chipIncomplete.visibility = View.VISIBLE
        binding.buttonListMenu.visibility = View.VISIBLE
        binding.buttonMapToggle.text = "🗺 地図"
        applyFilter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerView.adapter = null
        _binding = null
    }
}
