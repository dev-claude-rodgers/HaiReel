package com.rodgers.haireel.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.VanLayout
import com.rodgers.haireel.model.VanLayoutPin
import com.rodgers.haireel.model.VanView
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.viewmodel.DeliveryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** バン荷室レイアウト */
@AndroidEntryPoint
class VanLayoutFragment : Fragment() {

    private val viewModel: DeliveryViewModel by activityViewModels()
    private var pendingPhotoPath: String? = null
    private var currentViewId: String? = null   // 現在選択中のビューID

    private lateinit var chipGroup: ChipGroup
    private lateinit var ivPhoto: ImageView
    private lateinit var pinOverlay: FrameLayout
    private lateinit var tvHint: TextView
    private lateinit var tvPinList: TextView
    private lateinit var seekPinSize: SeekBar
    private lateinit var tvPinSizeLabel: TextView
    private var pinSizeDp = 80

    // カメラ
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingPhotoPath?.let { updateCurrentPhoto(Uri.fromFile(File(it)).toString()) }
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { copyToInternalAndSave(it) }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val dp  = resources.displayMetrics.density
        val ctx = requireContext()
        val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        // ── ツールバー ─────────────────────────────────────────────
        val toolbar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding((12*dp).toInt(), (6*dp).toInt(), (8*dp).toInt(), (6*dp).toInt())
        }
        toolbar.addView(TextView(ctx).apply {
            text = "🚐 荷室レイアウト"
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        toolbar.addView(mkBtn(ctx, "📷 写真", dp) { showPhotoMenu() })
        toolbar.addView(mkBtn(ctx, "💾 保存", dp) { savePhotoWithPins() })
        toolbar.addView(mkBtn(ctx, "+ ビュー", dp) { showAddViewDialog() })
        root.addView(toolbar)

        // ── ビュータブ（ChipGroup）──────────────────────────────────
        chipGroup = ChipGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding((8*dp).toInt(), (4*dp).toInt(), (8*dp).toInt(), (4*dp).toInt())
            setBackgroundColor(Color.parseColor("#111111"))
        }
        root.addView(chipGroup)

        // ── ピンサイズスライダー ──────────────────────────────────
        pinSizeDp = loadPinSize()
        val sliderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding((12*dp).toInt(), (4*dp).toInt(), (12*dp).toInt(), (4*dp).toInt())
        }
        sliderRow.addView(TextView(ctx).apply {
            text = "📍"; textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 0, (6*dp).toInt(), 0)
        })
        seekPinSize = SeekBar(ctx).apply {
            max = 80          // 48dp〜128dp の範囲
            progress = pinSizeDp - 48
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sliderRow.addView(seekPinSize)
        tvPinSizeLabel = TextView(ctx).apply {
            text = "${pinSizeDp}dp"; textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding((8*dp).toInt(), 0, 0, 0)
            minWidth = (52*dp).toInt()
            gravity = Gravity.END
        }
        sliderRow.addView(tvPinSizeLabel)
        root.addView(sliderRow)

        // ── ヒント ─────────────────────────────────────────────────
        tvHint = TextView(ctx).apply {
            text = "「+ ビュー」でドア名を追加して写真を設定してください"
            textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, (16*dp).toInt(), 0, 0)
        }
        root.addView(tvHint)

        // ── 写真 + ピンオーバーレイ ────────────────────────────────
        val photoContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        ivPhoto = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#222222"))
        }
        photoContainer.addView(ivPhoto)

        pinOverlay = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        photoContainer.addView(pinOverlay)
        root.addView(photoContainer)

        // ── ピン一覧（下部）──────────────────────────────────────
        tvPinList = TextView(ctx).apply {
            textSize = 11f; setTextColor(Color.parseColor("#AAAAAA"))
            setPadding((12*dp).toInt(), (6*dp).toInt(), (12*dp).toInt(), (6*dp).toInt())
            setBackgroundColor(Color.parseColor("#111111"))
        }
        root.addView(tvPinList)

        // タッチでピン追加
        pinOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val view = currentView()
                if (view?.photoUri.isNullOrBlank()) { showPhotoMenu(); return@setOnTouchListener true }
                val xPct = event.x / pinOverlay.width.toFloat()
                val yPct = event.y / pinOverlay.height.toFloat()
                showSelectDeliveryDialog(xPct, yPct)
            }
            true
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        seekPinSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                pinSizeDp = 48 + progress
                tvPinSizeLabel.text = "${pinSizeDp}dp"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                savePinSize(pinSizeDp)
                renderLayout(viewModel.vanLayout.value)
            }
        })
        // データ監視はonViewCreated後に行う（viewLifecycleOwnerが確実に使える）
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vanLayout.collectLatest { layout ->
                if (isAdded) renderLayout(layout)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentGroupId.collectLatest { groupId ->
                viewModel.loadVanLayout(groupId)
            }
        }
    }

    // ── 現在のビューを取得 ──────────────────────────────────────
    private fun currentView(): VanView? =
        viewModel.vanLayout.value.views.find { it.id == currentViewId }

    // ── ビュー追加ダイアログ ────────────────────────────────────
    private fun showAddViewDialog(editView: VanView? = null) {
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val input = EditText(ctx).apply {
            hint = "例: 右ドア・左ドア"
            setText(editView?.name ?: "")
            textSize = 16f
            setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(if (editView == null) "ビュー名を入力" else "ビュー名を変更")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val groupId = viewModel.currentGroupId.value
                val layout  = viewModel.vanLayout.value
                if (editView == null) {
                    val newView = VanView(name = name)
                    viewModel.saveVanLayout(groupId, layout.copy(views = layout.views + newView))
                    currentViewId = newView.id
                } else {
                    viewModel.saveVanLayout(groupId, layout.copy(
                        views = layout.views.map { if (it.id == editView.id) it.copy(name = name) else it }
                    ))
                }
            }
            .setNeutralButton(if (editView != null) "削除" else null) { _, _ ->
                if (editView != null) deleteView(editView)
            }
            .setNegativeButton("キャンセル", null)
            .show()
        input.requestFocus()
    }

    private fun deleteView(view: VanView) {
        val ctx     = requireContext()
        val groupId = viewModel.currentGroupId.value
        val layout  = viewModel.vanLayout.value
        val updated = layout.views.filter { it.id != view.id }
        viewModel.saveVanLayout(groupId, layout.copy(views = updated))
        if (currentViewId == view.id) currentViewId = updated.firstOrNull()?.id
    }

    // ── 写真設定 ────────────────────────────────────────────────
    private fun showPhotoMenu() {
        if (currentViewId == null) { Toast.makeText(requireContext(), "先にビューを追加してください", Toast.LENGTH_SHORT).show(); return }
        val ctx = requireContext()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("写真を設定")
            .setItems(arrayOf("📷 カメラで撮影", "🖼 ギャラリーから選択")) { _, which ->
                if (which == 0) {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) launchCamera()
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                } else {
                    galleryLauncher.launch("image/*")
                }
            }
            .setNegativeButton("キャンセル", null).show()
    }

    /** ギャラリーURIはアクセス権が失効するためアプリ内にコピーして保存 */
    private fun copyToInternalAndSave(srcUri: Uri) {
        val ctx = requireContext()
        try {
            val dest = File(ctx.filesDir, "van_layout/gallery_${System.currentTimeMillis()}.jpg")
                .also { it.parentFile?.mkdirs() }
            ctx.contentResolver.openInputStream(srcUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            updateCurrentPhoto(Uri.fromFile(dest).toString())
        } catch (e: Exception) {
            Toast.makeText(ctx, "写真の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        val ctx  = requireContext()
        val file = File(ctx.filesDir, "van_layout/van_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        pendingPhotoPath = file.absolutePath
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    private fun updateCurrentPhoto(uri: String) {
        val id      = currentViewId ?: return
        val groupId = viewModel.currentGroupId.value
        val layout  = viewModel.vanLayout.value
        viewModel.saveVanLayout(groupId, layout.copy(
            views = layout.views.map { if (it.id == id) it.copy(photoUri = uri) else it }
        ))
    }

    private fun loadPinSize(): Int =
        requireContext().getSharedPreferences("van_layout_prefs", 0)
            .getInt("pin_size_dp", 80)

    private fun savePinSize(size: Int) {
        requireContext().getSharedPreferences("van_layout_prefs", 0)
            .edit().putInt("pin_size_dp", size).apply()
    }

    private fun clearPhotoUri(viewId: String) {
        val groupId = viewModel.currentGroupId.value
        val layout  = viewModel.vanLayout.value
        viewModel.saveVanLayout(groupId, layout.copy(
            views = layout.views.map { if (it.id == viewId) it.copy(photoUri = "") else it }
        ))
    }

    // ── ピン追加 ────────────────────────────────────────────────
    private fun showSelectDeliveryDialog(xPct: Float, yPct: Float) {
        val ctx      = requireContext()
        val placed   = currentView()?.pins?.map { it.deliveryId }?.toSet() ?: emptySet()
        val available = viewModel.deliveries.value.filter { !it.isCompleted }
        if (available.isEmpty()) {
            Toast.makeText(ctx, "未完了の配達先がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = available.map { d ->
            val mark = if (d.id in placed) "✅ " else "   "
            "$mark${d.order}. ${d.displayTitle}"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("📍 配達先を選択")
            .setItems(labels) { _, which ->
                val d = available[which]
                addPin(xPct, yPct, d)
            }
            .setNegativeButton("キャンセル", null).show()
    }

    private fun addPin(xPct: Float, yPct: Float, delivery: Delivery) {
        val id      = currentViewId ?: return
        val groupId = viewModel.currentGroupId.value
        val layout  = viewModel.vanLayout.value
        viewModel.saveVanLayout(groupId, layout.copy(
            views = layout.views.map { view ->
                if (view.id != id) view else {
                    val pins = view.pins.filter { it.deliveryId != delivery.id } +
                        VanLayoutPin(xPercent = xPct, yPercent = yPct,
                            deliveryId = delivery.id, orderLabel = delivery.order)
                    view.copy(pins = pins)
                }
            }
        ))
    }

    // ── ピン込み写真をギャラリーに保存 ─────────────────────────
    private fun savePhotoWithPins() {
        val ctx  = requireContext()
        val view = currentView()
        if (view == null || view.photoUri.isNullOrBlank()) {
            Toast.makeText(ctx, "写真を設定してください", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val src = android.graphics.BitmapFactory.decodeStream(
                ctx.contentResolver.openInputStream(Uri.parse(view.photoUri))
            ) ?: return
            val bmp  = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bmp)
            val dp = resources.displayMetrics.density
            val deliveryMap = viewModel.deliveries.value.associateBy { it.id }
            view.pins.forEach { pin ->
                val x = pin.xPercent * bmp.width
                val y = pin.yPercent * bmp.height
                val r = bmp.width * 0.03f * (pinSizeDp / 80f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val groupColor = try {
                    Color.parseColor(viewModel.groups.value
                        .find { it.id == viewModel.currentGroupId.value }?.colorHex ?: "#1565C0")
                } catch (_: Exception) { Color.parseColor("#1565C0") }
                paint.color = groupColor
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.WHITE
                paint.textSize = r * 1.1f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                val m = paint.fontMetrics
                val number = deliveryMap[pin.deliveryId]?.order ?: pin.orderLabel
                canvas.drawText(number.toString(), x, y - (m.ascent + m.descent) / 2f, paint)
            }
            // ギャラリーに保存
            val fname = "van_layout_${view.name}_${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fname)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HaiReel")
                }
                val uri2 = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri2?.let { ctx.contentResolver.openOutputStream(it)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) } }
            } else {
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES), "HaiReel").also { it.mkdirs() }
                File(dir, fname).outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            }
            Toast.makeText(ctx, "📸 ギャラリーに保存しました", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── レイアウト描画 ──────────────────────────────────────────
    private fun renderLayout(layout: VanLayout) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        // タブ再構築
        chipGroup.removeAllViews()
        if (layout.views.isEmpty()) {
            tvHint.visibility = View.VISIBLE
            ivPhoto.setImageDrawable(null)
            pinOverlay.removeAllViews()
            tvPinList.text = ""
            return
        }
        tvHint.visibility = View.GONE
        if (currentViewId == null || layout.views.none { it.id == currentViewId })
            currentViewId = layout.views.first().id

        layout.views.forEach { view ->
            val chip = Chip(ctx).apply {
                text = view.name
                isCheckable = true
                isChecked = (view.id == currentViewId)
                setOnClickListener { currentViewId = view.id; renderLayout(layout) }
                setOnLongClickListener { showAddViewDialog(view); true }
            }
            chipGroup.addView(chip)
        }

        val view = layout.views.find { it.id == currentViewId } ?: return

        // 写真（setImageURIはlayout時に例外を投げるため、BitmapFactory経由で同期読み込み）
        if (view.photoUri.isBlank()) {
            ivPhoto.setImageDrawable(null)
            ivPhoto.setBackgroundColor(Color.parseColor("#222222"))
        } else {
            try {
                val bmp = ctx.contentResolver.openInputStream(Uri.parse(view.photoUri))
                    ?.use { android.graphics.BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    ivPhoto.setImageBitmap(bmp)
                } else {
                    ivPhoto.setImageDrawable(null)
                    clearPhotoUri(view.id)
                }
            } catch (_: Exception) {
                ivPhoto.setImageDrawable(null)
                clearPhotoUri(view.id)
            }
        }

        // ピン
        pinOverlay.removeAllViews()
        ivPhoto.post {
            if (pinOverlay.width == 0) return@post
            view.pins.forEach { pin ->
                val delivery = viewModel.deliveries.value.find { it.id == pin.deliveryId } ?: return@forEach
                val size = (pinSizeDp * dp).toInt()
                val groupColor = try {
                    Color.parseColor(viewModel.groups.value
                        .find { it.id == viewModel.currentGroupId.value }?.colorHex ?: "#1565C0")
                } catch (_: Exception) { Color.parseColor("#1565C0") }
                val bmp = createPinBitmap(delivery.order, groupColor, delivery.isCompleted, dp)
                pinOverlay.addView(ImageView(ctx).apply {
                    setImageBitmap(bmp)
                    layoutParams = FrameLayout.LayoutParams(size, size).also { lp ->
                        lp.leftMargin = (pin.xPercent * pinOverlay.width  - size / 2).toInt()
                        lp.topMargin  = (pin.yPercent * pinOverlay.height - size).toInt()
                    }
                    setOnClickListener { showDeliveryOptions(delivery) }
                    setOnLongClickListener {
                        val gid = viewModel.currentGroupId.value
                        val l   = viewModel.vanLayout.value
                        val vid = currentViewId ?: return@setOnLongClickListener true
                        viewModel.saveVanLayout(gid, l.copy(
                            views = l.views.map { v ->
                                if (v.id != vid) v else v.copy(pins = v.pins.filter { it.id != pin.id })
                            }
                        ))
                        Toast.makeText(ctx, "${pin.orderLabel}番のピンを削除", Toast.LENGTH_SHORT).show()
                        true
                    }
                })
            }
        }

        // 配置済み・未配置リスト
        val allDeliveries = viewModel.deliveries.value.filter { !it.isCompleted }
        val placedIds = view.pins.map { it.deliveryId }.toSet()
        val placed  = allDeliveries.filter { it.id in placedIds }
            .sortedBy { it.order }.joinToString(" ") { "①".replace("①", "${it.order}") + ".${it.displayTitle.take(4)}" }
        val missing = allDeliveries.filter { it.id !in placedIds }
            .sortedBy { it.order }.joinToString(" ") { "${it.order}.${it.displayTitle.take(4)}" }
        val deliveryMap = viewModel.deliveries.value.associateBy { it.id }
        val placedNums  = view.pins.mapNotNull { deliveryMap[it.deliveryId]?.order }.sorted()
        val missingNums = allDeliveries.filter { it.id !in placedIds }.map { it.order }
        tvPinList.text = buildString {
            if (placedNums.isNotEmpty())  append("✅ 配置済み: ${placedNums.joinToString(" ")}番  ")
            if (missingNums.isNotEmpty()) append("📦 未配置: ${missingNums.joinToString(" ")}番")
        }
    }

    private fun showDeliveryOptions(delivery: Delivery) {
        (parentFragment as? DeliveryListFragment)?.showItemOptions(delivery)
    }

    private fun createPinBitmap(number: Int, color: Int, completed: Boolean, dp: Float): Bitmap {
        val size = (pinSizeDp * dp).toInt()
        val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        // 影（視認性向上）
        paint.color = Color.parseColor("#66000000")
        canvas.drawCircle(size / 2f + 2, size / 2f + 2, size / 2f - 2, paint)

        // 丸背景
        paint.color = if (completed) Color.parseColor("#9E9E9E") else color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)

        // 白枠
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3, paint)
        paint.style = Paint.Style.FILL

        // 数字
        paint.color = Color.WHITE
        paint.textSize = size * 0.44f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val m = paint.fontMetrics
        canvas.drawText(number.toString(), size / 2f, size / 2f - (m.ascent + m.descent) / 2f, paint)
        return bmp
    }

    private fun mkBtn(ctx: android.content.Context, text: String, dp: Float, onClick: () -> Unit) =
        com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            this.text = text; textSize = 12f; setTextColor(Color.WHITE)
            minWidth = 0; insetTop = 0; insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (44 * dp).toInt()
            )
            setOnClickListener { onClick() }
        }
}
