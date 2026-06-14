package com.rodgers.routist.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentTenkoBinding
import com.rodgers.routist.excel.TenkoExcelGenerator
import com.rodgers.routist.model.TenkoRecord
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.BackupManager
import com.rodgers.routist.util.ReminderReceiver
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.viewmodel.TenkoViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.util.GeocodingClient
import com.rodgers.routist.viewmodel.DeliveryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TenkoFragment : Fragment() {

    private var _binding: FragmentTenkoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TenkoViewModel by activityViewModels()
    private val deliveryViewModel: DeliveryViewModel by activityViewModels()

    private lateinit var adapter: TenkoMonthAdapter

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val ctx = context ?: return@registerForActivityResult
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    BackupManager.restoreBackup(ctx, uri)
                    if (isAdded) Toast.makeText(ctx, "バックアップから復元しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    if (isAdded) Toast.makeText(ctx, "復元エラー: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ctx = context ?: return@registerForActivityResult
        if (granted) {
            showReminderDialog()
        } else {
            AlertDialog.Builder(ctx)
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

        deliveryViewModel.currentGroupId.observe(viewLifecycleOwner) { groupId ->
            viewModel.setAssignmentId(groupId ?: "")
        }
    }

    private fun rebuildList(ym: String) {
        val (y, m) = ym.split("-").map { it.toInt() }
        val daysInMonth = YearMonth.of(y, m).lengthOfMonth()
        val recordMap = viewModel.monthRecords.value.associateBy { it.date }
        val today = viewModel.todayDate()
        val items = (1..daysInMonth).map { day ->
            val dateStr = "%04d-%02d-%02d".format(y, m, day)
            TenkoMonthAdapter.DayRow(dateStr, recordMap[dateStr], dateStr == today)
        }
        adapter.submitList(items)

        val todayIdx = items.indexOfFirst { it.isToday }
        if (todayIdx >= 0) binding.recyclerTenko.scrollToPosition(todayIdx)
        else binding.recyclerTenko.scrollToPosition(0)
    }

    // ── 乗務前ダイアログ ──
    fun showBeforeDialog(date: String, existing: TenkoRecord?) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val cGreen   = Color.parseColor("#4CAF50"); val cBgGreen = Color.parseColor("#E8F5E9")
        val cRed     = Color.parseColor("#F44336"); val cBgRed   = Color.parseColor("#FFEBEE")
        val cBlue    = Color.parseColor("#1E88E5"); val cBgBlue  = Color.parseColor("#E3F2FD")
        val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*dp).toInt(),(8*dp).toInt(),(20*dp).toInt(),(16*dp).toInt())
        }
        scroll.addView(root)

        fun sectionLabel(icon: String, text: String) = TextView(ctx).apply {
            this.text = "$icon  $text"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (16*dp).toInt(); it.bottomMargin = (6*dp).toInt() }
        }

        fun styledBtn(label: String) = Button(ctx).apply { text = label; isAllCaps = false; textSize = 13f }

        fun applyStyle(btn: Button, active: Boolean, ac: Int, bg: Int) {
            btn.setTextColor(if (active) Color.WHITE else ac)
            btn.background = GradientDrawable().apply { setColor(if (active) ac else bg); cornerRadius = 8*dp }
        }

        fun hRow(vararg btns: Button) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            btns.forEach { addView(it) }
        }

        // ── 確認方法（3+2グリッド）
        root.addView(sectionLabel("📋", "確認方法"))
        val methods = listOf("対面", "電話", "IT点呼", "自己点呼", "その他")
        var selMethod: String = existing?.beforeMethod?.takeIf { methods.contains(it) } ?: "対面"
        val mBtns = methods.map { m ->
            styledBtn(m).also { btn ->
                btn.layoutParams = LinearLayout.LayoutParams(0, (38*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
            }
        }
        fun refreshMethod() = mBtns.forEachIndexed { i, btn -> applyStyle(btn, methods[i] == selMethod, cBlue, cBgBlue) }
        mBtns.forEachIndexed { i, btn -> btn.setOnClickListener { selMethod = methods[i]; refreshMethod() } }
        refreshMethod()
        root.addView(hRow(*mBtns.take(3).toTypedArray()))
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (4*dp).toInt() }
            mBtns.drop(3).forEach { addView(it) }
            // 残り2件の幅を揃えるためスペーサー
            addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        })

        // ── 時刻（タップで TimePickerDialog）
        root.addView(sectionLabel("⏰", "時刻"))
        var selTime: String = existing?.beforeTime ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()).orEmpty()
        val timeBtn = Button(ctx).apply {
            text = "🕐  $selTime"; isAllCaps = false; textSize = 16f; setTextColor(cBlue)
            background = GradientDrawable().apply { setColor(cBgBlue); cornerRadius = 8*dp }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46*dp).toInt())
        }
        timeBtn.setOnClickListener {
            val parts = selTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(ctx, { _, hour, min -> selTime = "%02d:%02d".format(hour, min); timeBtn.text = "🕐  $selTime" }, h, m, true).show()
        }
        root.addView(timeBtn)

        // ── 体調
        root.addView(sectionLabel("💪", "体調"))
        var healthOk = existing?.beforeHealth != false
        val btnHOk = styledBtn("✓ 良好"); val btnHNg = styledBtn("✗ 不良")
        fun refreshHealth() { applyStyle(btnHOk, healthOk, cGreen, cBgGreen); applyStyle(btnHNg, !healthOk, cRed, cBgRed) }
        btnHOk.setOnClickListener { healthOk = true;  refreshHealth() }
        btnHNg.setOnClickListener { healthOk = false; refreshHealth() }
        for (btn in listOf(btnHOk, btnHNg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshHealth(); root.addView(hRow(btnHOk, btnHNg))

        // ── 疲労
        root.addView(sectionLabel("😴", "疲労"))
        var fatigueYes = existing?.beforeFatigue == true
        val btnFNo = styledBtn("なし"); val btnFYes = styledBtn("あり")
        fun refreshFatigue() { applyStyle(btnFNo, !fatigueYes, cGreen, cBgGreen); applyStyle(btnFYes, fatigueYes, cRed, cBgRed) }
        btnFNo.setOnClickListener  { fatigueYes = false; refreshFatigue() }
        btnFYes.setOnClickListener { fatigueYes = true;  refreshFatigue() }
        for (btn in listOf(btnFNo, btnFYes)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshFatigue(); root.addView(hRow(btnFNo, btnFYes))

        // ── アルコール数値
        root.addView(sectionLabel("🍺", "アルコール数値"))
        val alcRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val etAlcohol = EditText(ctx).apply {
            setText(existing?.beforeAlcohol?.let { "%.2f".format(it) } ?: "0.00")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#0D47A1"))
            background = GradientDrawable().apply {
                setColor(cBgBlue); cornerRadius = 8*dp
                setStroke((2*dp).toInt(), cBlue)
            }
            setPadding((16*dp).toInt(),(12*dp).toInt(),(16*dp).toInt(),(12*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, (56*dp).toInt(), 1f)
        }
        alcRow.addView(etAlcohol)
        alcRow.addView(TextView(ctx).apply { text = " mg/L"; textSize = 16f; setTextColor(colorOnSurfaceVariant) })
        root.addView(alcRow)

        // ── 車両点検
        root.addView(sectionLabel("🚗", "車両点検"))
        var inspOk = existing?.beforeInspection != false
        val btnIOk = styledBtn("✓ 異常なし"); val btnINg = styledBtn("⚠ 要確認")
        fun refreshInsp() { applyStyle(btnIOk, inspOk, cGreen, cBgGreen); applyStyle(btnINg, !inspOk, cRed, cBgRed) }
        btnIOk.setOnClickListener { inspOk = true;  refreshInsp() }
        btnINg.setOnClickListener { inspOk = false; refreshInsp() }
        for (btn in listOf(btnIOk, btnINg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshInsp(); root.addView(hRow(btnIOk, btnINg))

        // ── 指示事項
        root.addView(sectionLabel("📝", "指示事項"))
        val etInstruction = EditText(ctx).apply {
            hint = "なし"; setText(existing?.beforeInstruction ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 5
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(etInstruction)

        // ── 確認者
        root.addView(sectionLabel("👤", "確認者"))
        val etChecker = EditText(ctx).apply {
            hint = "氏名"; setText(existing?.beforeChecker ?: AppSettings.getCheckerName(ctx))
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(etChecker)

        val dlgBefore = AlertDialog.Builder(ctx)
            .setTitle("乗務前点呼  $date")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNegativeButton("キャンセル", null).show()

        dlgBefore.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val alc = (etAlcohol.text.toString().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 9.99)
            if (alc > 0.15) {
                AlertDialog.Builder(ctx)
                    .setTitle("⚠️ アルコール値が基準超過")
                    .setMessage("アルコール値が基準を超えています（%.2f mg/L）。\nこのまま保存しますか？".format(alc))
                    .setPositiveButton("保存する") { _, _ ->
                        viewModel.saveBefore(date, existing, selMethod, selTime,
                            healthOk, fatigueYes, alc, inspOk,
                            etInstruction.text.toString().trim(), etChecker.text.toString().trim())
                        dlgBefore.dismiss()
                    }
                    .setNegativeButton("修正する", null).show()
            } else {
                viewModel.saveBefore(date, existing, selMethod, selTime,
                    healthOk, fatigueYes, alc, inspOk,
                    etInstruction.text.toString().trim(), etChecker.text.toString().trim())
                dlgBefore.dismiss()
            }
        }
    }

    // ── 乗務後ダイアログ ──
    fun showAfterDialog(date: String, existing: TenkoRecord?) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val cGreen   = Color.parseColor("#4CAF50"); val cBgGreen = Color.parseColor("#E8F5E9")
        val cRed     = Color.parseColor("#F44336"); val cBgRed   = Color.parseColor("#FFEBEE")
        val cBlue    = Color.parseColor("#1E88E5"); val cBgBlue  = Color.parseColor("#E3F2FD")
        val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*dp).toInt(),(8*dp).toInt(),(20*dp).toInt(),(16*dp).toInt())
        }
        scroll.addView(root)

        fun sectionLabel(icon: String, text: String) = TextView(ctx).apply {
            this.text = "$icon  $text"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (16*dp).toInt(); it.bottomMargin = (6*dp).toInt() }
        }

        fun styledBtn(label: String) = Button(ctx).apply { text = label; isAllCaps = false; textSize = 13f }

        fun applyStyle(btn: Button, active: Boolean, ac: Int, bg: Int) {
            btn.setTextColor(if (active) Color.WHITE else ac)
            btn.background = GradientDrawable().apply { setColor(if (active) ac else bg); cornerRadius = 8*dp }
        }

        fun hRow(vararg btns: Button) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            btns.forEach { addView(it) }
        }

        // ── 確認方法（3+2グリッド）
        root.addView(sectionLabel("📋", "確認方法"))
        val methods = listOf("対面", "電話", "IT点呼", "自己点呼", "その他")
        var selMethod: String = existing?.afterMethod?.takeIf { methods.contains(it) } ?: "対面"
        val mBtns = methods.map { m ->
            styledBtn(m).also { btn ->
                btn.layoutParams = LinearLayout.LayoutParams(0, (38*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
            }
        }
        fun refreshMethod() = mBtns.forEachIndexed { i, btn -> applyStyle(btn, methods[i] == selMethod, cBlue, cBgBlue) }
        mBtns.forEachIndexed { i, btn -> btn.setOnClickListener { selMethod = methods[i]; refreshMethod() } }
        refreshMethod()
        root.addView(hRow(*mBtns.take(3).toTypedArray()))
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (4*dp).toInt() }
            mBtns.drop(3).forEach { addView(it) }
            addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        })

        // ── 時刻
        root.addView(sectionLabel("⏰", "時刻"))
        var selTime: String = existing?.afterTime ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()).orEmpty()
        val timeBtn = Button(ctx).apply {
            text = "🕐  $selTime"; isAllCaps = false; textSize = 16f; setTextColor(cBlue)
            background = GradientDrawable().apply { setColor(cBgBlue); cornerRadius = 8*dp }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46*dp).toInt())
        }
        timeBtn.setOnClickListener {
            val parts = selTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(ctx, { _, hour, min -> selTime = "%02d:%02d".format(hour, min); timeBtn.text = "🕐  $selTime" }, h, m, true).show()
        }
        root.addView(timeBtn)

        // ── 体調
        root.addView(sectionLabel("💪", "体調"))
        var healthOk = existing?.afterHealth != false
        val btnHOk = styledBtn("✓ 良好"); val btnHNg = styledBtn("✗ 不良")
        fun refreshHealth() { applyStyle(btnHOk, healthOk, cGreen, cBgGreen); applyStyle(btnHNg, !healthOk, cRed, cBgRed) }
        btnHOk.setOnClickListener { healthOk = true;  refreshHealth() }
        btnHNg.setOnClickListener { healthOk = false; refreshHealth() }
        for (btn in listOf(btnHOk, btnHNg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshHealth(); root.addView(hRow(btnHOk, btnHNg))

        // ── 疲労
        root.addView(sectionLabel("😴", "疲労"))
        var fatigueYes = existing?.afterFatigue == true
        val btnFNo = styledBtn("なし"); val btnFYes = styledBtn("あり")
        fun refreshFatigue() { applyStyle(btnFNo, !fatigueYes, cGreen, cBgGreen); applyStyle(btnFYes, fatigueYes, cRed, cBgRed) }
        btnFNo.setOnClickListener  { fatigueYes = false; refreshFatigue() }
        btnFYes.setOnClickListener { fatigueYes = true;  refreshFatigue() }
        for (btn in listOf(btnFNo, btnFYes)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshFatigue(); root.addView(hRow(btnFNo, btnFYes))

        // ── アルコール数値
        root.addView(sectionLabel("🍺", "アルコール数値"))
        val alcRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val etAlcohol = EditText(ctx).apply {
            setText(existing?.afterAlcohol?.let { "%.2f".format(it) } ?: "0.00")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#0D47A1"))
            background = GradientDrawable().apply {
                setColor(cBgBlue); cornerRadius = 8*dp
                setStroke((2*dp).toInt(), cBlue)
            }
            setPadding((16*dp).toInt(),(12*dp).toInt(),(16*dp).toInt(),(12*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, (56*dp).toInt(), 1f)
        }
        alcRow.addView(etAlcohol)
        alcRow.addView(TextView(ctx).apply { text = " mg/L"; textSize = 16f; setTextColor(colorOnSurfaceVariant) })
        root.addView(alcRow)

        // ── 事故
        root.addView(sectionLabel("🚨", "事故"))
        var accidentYes = existing?.afterAccident == true
        val btnANo = styledBtn("なし"); val btnAYes = styledBtn("あり")
        fun refreshAccident() { applyStyle(btnANo, !accidentYes, cGreen, cBgGreen); applyStyle(btnAYes, accidentYes, cRed, cBgRed) }
        btnANo.setOnClickListener  { accidentYes = false; refreshAccident() }
        btnAYes.setOnClickListener { accidentYes = true;  refreshAccident() }
        for (btn in listOf(btnANo, btnAYes)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshAccident(); root.addView(hRow(btnANo, btnAYes))

        // ── 車両状態
        root.addView(sectionLabel("🚗", "車両状態"))
        var vehicleOk = existing?.afterVehicle != false
        val btnVOk = styledBtn("✓ 異常なし"); val btnVNg = styledBtn("⚠ 要確認")
        fun refreshVehicle() { applyStyle(btnVOk, vehicleOk, cGreen, cBgGreen); applyStyle(btnVNg, !vehicleOk, cRed, cBgRed) }
        btnVOk.setOnClickListener { vehicleOk = true;  refreshVehicle() }
        btnVNg.setOnClickListener { vehicleOk = false; refreshVehicle() }
        for (btn in listOf(btnVOk, btnVNg)) btn.layoutParams = LinearLayout.LayoutParams(0, (40*dp).toInt(), 1f).also { it.marginEnd = (4*dp).toInt() }
        refreshVehicle(); root.addView(hRow(btnVOk, btnVNg))

        // ── 指示事項
        root.addView(sectionLabel("📝", "指示事項"))
        val etInstruction = EditText(ctx).apply {
            hint = "なし"; setText(existing?.afterInstruction ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 5
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(etInstruction)

        // ── 確認者
        root.addView(sectionLabel("👤", "確認者"))
        val etChecker = EditText(ctx).apply {
            hint = "氏名"; setText(existing?.afterChecker ?: AppSettings.getCheckerName(ctx))
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(etChecker)

        // ── 特記事項
        root.addView(sectionLabel("🗒", "特記事項"))
        val etNote = EditText(ctx).apply {
            hint = "なし"; setText(existing?.note ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 5
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(etNote)

        val dlgAfter = AlertDialog.Builder(ctx)
            .setTitle("乗務後点呼  $date")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNegativeButton("キャンセル", null).show()

        dlgAfter.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val alc = (etAlcohol.text.toString().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 9.99)
            if (alc > 0.15) {
                AlertDialog.Builder(ctx)
                    .setTitle("⚠️ アルコール値が基準超過")
                    .setMessage("アルコール値が基準を超えています（%.2f mg/L）。\nこのまま保存しますか？".format(alc))
                    .setPositiveButton("保存する") { _, _ ->
                        viewModel.saveAfter(date, existing, selMethod, selTime,
                            healthOk, fatigueYes, alc, accidentYes, vehicleOk,
                            etInstruction.text.toString().trim(), etChecker.text.toString().trim(),
                            etNote.text.toString().trim())
                        dlgAfter.dismiss()
                    }
                    .setNegativeButton("修正する", null).show()
            } else {
                viewModel.saveAfter(date, existing, selMethod, selTime,
                    healthOk, fatigueYes, alc, accidentYes, vehicleOk,
                    etInstruction.text.toString().trim(), etChecker.text.toString().trim(),
                    etNote.text.toString().trim())
                dlgAfter.dismiss()
            }
        }
    }

    private fun exportTenko() {
        val ctx = requireContext()
        val ym  = viewModel.yearMonth.value
        val (y, m) = ym.split("-").map { it.toInt() }
        lifecycleScope.launch {
            try {
                val records = viewModel.recordsForMonth(ym)
                if (records.isEmpty()) {
                    Toast.makeText(ctx, "この月の点呼記録はまだありません", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val file = TenkoExcelGenerator(ctx).generate(records, ym)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "${y}年${m}月 点呼簿を共有"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "出力エラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── 休憩アラームダイアログ ──
    private fun show430TimerDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val state = AppSettings.getDriveTimerState(ctx)
        val totalMin = AppSettings.getBreakAlarmMinutes(ctx)
        val totalMs = totalMin * 60_000L

        fun fmtHm(ms: Long): String {
            val m = ms / 60000
            return "%d時間%02d分".format(m / 60, m % 60)
        }

        fun cancelAlarms() {
            listOf(4301, 4302).forEach { code ->
                val pi = PendingIntent.getBroadcast(ctx, code,
                    Intent(ctx, ReminderReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                pi?.let { am.cancel(it) }
            }
        }

        fun scheduleAlarms(startMs: Long) {
            if (totalMin > 30) {
                val warnMs = (totalMin - 30) * 60_000L
                val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                    putExtra(ReminderReceiver.EXTRA_TITLE,    "休憩の準備をしてください")
                    putExtra(ReminderReceiver.EXTRA_TEXT,     "連続運転${fmtHm(warnMs)}が経過しました。あと30分で休憩が必要です。")
                    putExtra(ReminderReceiver.EXTRA_NOTIF_ID, 4301)
                }
                val pi = PendingIntent.getBroadcast(ctx, 4301, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMs + warnMs, pi)
            }
            val intent2 = Intent(ctx, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_TITLE,    "休憩してください")
                putExtra(ReminderReceiver.EXTRA_TEXT,     "連続運転${fmtHm(totalMs)}が経過しました。休憩を取ってください。")
                putExtra(ReminderReceiver.EXTRA_NOTIF_ID, 4302)
            }
            val pi2 = PendingIntent.getBroadcast(ctx, 4302, intent2,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMs + totalMs, pi2)
        }

        val startMs = AppSettings.getDriveSegmentStartMs(ctx)
        val elapsedMs = when (state) {
            "DRIVING" -> AppSettings.getDriveAccumulatedMs(ctx) + (System.currentTimeMillis() - startMs)
            else      -> AppSettings.getDriveAccumulatedMs(ctx)
        }

        val message = when (state) {
            "IDLE" -> "停車中\n設定時間: ${fmtHm(totalMs)}\n\n運転を開始すると、設定時間に通知します。"
            "DRIVING" -> "運転中\n\n経過: ${fmtHm(elapsedMs)}\n残り: ${fmtHm((totalMs - elapsedMs).coerceAtLeast(0))}"
            "ON_BREAK" -> {
                val breakMs = AppSettings.getBreakAccumulatedMs(ctx) + (System.currentTimeMillis() - AppSettings.getBreakSegmentStartMs(ctx))
                "☕ 休憩中\n\n運転経過: ${fmtHm(elapsedMs)}\n休憩経過: ${fmtHm(breakMs)}"
            }
            else -> ""
        }

        val builder = AlertDialog.Builder(ctx).setTitle("連続運転タイマー").setMessage(message)

        when (state) {
            "IDLE" -> {
                builder.setPositiveButton("🚗 運転開始") { _, _ ->
                    val now = System.currentTimeMillis()
                    AppSettings.setDriveTimerState(ctx, "DRIVING")
                    AppSettings.setDriveSegmentStartMs(ctx, now)
                    AppSettings.setDriveAccumulatedMs(ctx, 0L)
                    AppSettings.setBreakAccumulatedMs(ctx, 0L)
                    scheduleAlarms(now)
                    Toast.makeText(ctx, "運転開始。${fmtHm(totalMs)}後に休憩通知します。", Toast.LENGTH_LONG).show()
                }
                builder.setNeutralButton("⚙ 時間を変更") { _, _ ->
                    showBreakAlarmSettingDialog()
                }
            }
            "DRIVING" -> {
                builder.setPositiveButton("☕ 休憩を記録") { _, _ ->
                    val now = System.currentTimeMillis()
                    val driveElapsed = now - AppSettings.getDriveSegmentStartMs(ctx)
                    AppSettings.setDriveAccumulatedMs(ctx, AppSettings.getDriveAccumulatedMs(ctx) + driveElapsed)
                    AppSettings.setDriveTimerState(ctx, "ON_BREAK")
                    AppSettings.setBreakSegmentStartMs(ctx, now)
                }
                builder.setNeutralButton("↺ リセット") { _, _ ->
                    cancelAlarms()
                    AppSettings.setDriveTimerState(ctx, "IDLE")
                    AppSettings.setDriveAccumulatedMs(ctx, 0L)
                    AppSettings.setBreakAccumulatedMs(ctx, 0L)
                    Toast.makeText(ctx, "タイマーをリセットしました", Toast.LENGTH_SHORT).show()
                }
            }
            "ON_BREAK" -> {
                builder.setPositiveButton("🚗 運転再開") { _, _ ->
                    val now = System.currentTimeMillis()
                    val breakElapsed = now - AppSettings.getBreakSegmentStartMs(ctx)
                    AppSettings.setBreakAccumulatedMs(ctx, AppSettings.getBreakAccumulatedMs(ctx) + breakElapsed)
                    AppSettings.setDriveTimerState(ctx, "DRIVING")
                    AppSettings.setDriveSegmentStartMs(ctx, now)
                    val totalDriveMs = AppSettings.getDriveAccumulatedMs(ctx)
                    val fakeStart = now - totalDriveMs
                    scheduleAlarms(fakeStart)
                }
                builder.setNeutralButton("↺ リセット") { _, _ ->
                    cancelAlarms()
                    AppSettings.setDriveTimerState(ctx, "IDLE")
                    AppSettings.setDriveAccumulatedMs(ctx, 0L)
                    AppSettings.setBreakAccumulatedMs(ctx, 0L)
                    Toast.makeText(ctx, "タイマーをリセットしました", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.setNegativeButton("閉じる", null).show()
    }

    private fun showBreakAlarmSettingDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val currentMin = AppSettings.getBreakAlarmMinutes(ctx)

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(64, 40, 64, 16)
        }

        val npHour = NumberPicker(ctx).apply {
            minValue = 0
            maxValue = 8
            displayedValues = Array(9) { "${it}時間" }
            value = currentMin / 60
        }
        val npMin = NumberPicker(ctx).apply {
            minValue = 0
            maxValue = 11
            displayedValues = Array(12) { "%02d分".format(it * 5) }
            value = (currentMin % 60) / 5
        }

        layout.addView(npHour)
        layout.addView(npMin)

        AlertDialog.Builder(ctx)
            .setTitle("アラーム時間を設定")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val totalMin = npHour.value * 60 + npMin.value * 5
                if (totalMin < 5) {
                    Toast.makeText(ctx, "休憩時間は5分以上で設定してください", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AppSettings.setBreakAlarmMinutes(ctx, totalMin)
                show430TimerDialog()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmDelete(record: TenkoRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("点呼記録を削除")
            .setMessage("${record.date} の点呼記録を削除しますか？")
            .setPositiveButton("削除") { _, _ -> viewModel.delete(record) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ── 点呼メニュー（全画面BottomSheet） ──
    private fun showTenkoMenu() {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val sheet = BottomSheetDialog(ctx)

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
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(ctx).apply {
            text = "点呼メニュー"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(onSurfaceVariant)
            background = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(headerRow)

        // ヘッダー下の区切り線
        root.addView(View(ctx).apply {
            setBackgroundColor(outlineVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
        })

        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun row(emoji: String, title: String, sub: String, color: Int = onSurfaceColor, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(ripple)
                setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = emoji; textSize = 28f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color)
            })
            if (sub.isNotBlank()) col.addView(TextView(ctx).apply {
                text = sub; textSize = 14f; setTextColor(onSurfaceVariant)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
            })
            row.addView(col)
            row.setOnClickListener { sheet.dismiss(); action() }
            root.addView(row)
        }

        fun divider() = root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
            setBackgroundColor(outlineVariant)
        })

        // ── 設定
        row("⚙️", "点呼設定", "ドライバー名・確認者・車番・右端表示") { showTenkoSettings() }
        row("🔔", "点呼リマインダー", "乗務前後の通知時刻を設定") { showReminderDialog() }
        val timerState = AppSettings.getDriveTimerState(ctx)
        val timerLabel = when (timerState) {
            "DRIVING"  -> "🚗 運転中 — タップで状態確認"
            "ON_BREAK" -> "☕ 休憩中 — タップで状態確認"
            else       -> "未開始 — タップで運転開始"
        }
        row("⏱", "連続運転タイマー", timerLabel) { sheet.dismiss(); show430TimerDialog() }
        divider()
        // ── 出力・共有
        row("📊", "点呼簿を出力", "今月分をExcelで保存・共有") { exportTenko() }
        row("📈", "今月の集計", "記録日数・アルコール確認状況") { showMonthSummary() }
        row("📤", "テキストで共有", "LINEやメールで今月分を送信") { shareMonthText() }
        divider()
        // ── データ管理
        row("💾", "バックアップ", "データをzipファイルで保存") { backupData() }
        row("📂", "バックアップから復元", "以前のデータを読み込む") {
            restoreLauncher.launch(arrayOf("application/zip", "*/*"))
        }
        divider()
        // ── 危険操作
        row("🗑", "今月の点呼データを削除", "削除後は元に戻せません", redColor) { confirmDeleteMonth() }
        divider()
        row("🚪", "アプリを終了", "Routist を閉じる") { activity?.finishAffinity() }

        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (20 * dp).toInt())
        })

        val scrollView = android.widget.ScrollView(ctx).apply {
            addView(root)
        }
        sheet.setContentView(scrollView)
        sheet.setOnShowListener {
            val bs = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = false
        }
        sheet.show()
    }

    // ── 今月の集計ダイアログ ──
    private fun showMonthSummary() {
        if (!isAdded) return
        val ctx     = requireContext()
        val ym      = viewModel.yearMonth.value
        val (y, m)  = ym.split("-").map { it.toInt() }
        val records = viewModel.monthRecords.value
        val daysInMonth = YearMonth.of(y, m).lengthOfMonth()

        val daysWithBefore    = records.count { it.beforeDone }
        val daysWithAfter     = records.count { it.afterDone }
        val daysWithBoth      = records.count { it.beforeDone && it.afterDone }
        val daysNoRecord      = daysInMonth - records.size
        val alcBeforeAbnormal = records.count { (it.beforeAlcohol ?: 0.0) > 0.0 }
        val alcAfterAbnormal  = records.count { (it.afterAlcohol  ?: 0.0) > 0.0 }
        val totalAbnormal     = alcBeforeAbnormal + alcAfterAbnormal
        val completionPct     = if (daysInMonth > 0) (daysWithBoth * 100 / daysInMonth) else 0

        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val cGreen  = Color.parseColor("#2E7D32")
        val cRed    = Color.parseColor("#C62828")
        val cOrange = Color.parseColor("#E65100")
        val cBlue   = Color.parseColor("#1565C0")
        val cGray   = Color.parseColor("#AAAAAA")
        val cBg     = Color.parseColor("#2A2A2A")
        val cOnSurf = Color.WHITE

        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        }
        scroll.addView(root)

        // ── 達成率バナー
        val pctColor = when {
            completionPct >= 80 -> cGreen
            completionPct >= 50 -> cOrange
            else                -> cRed
        }
        val banner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(pctColor); cornerRadius = 12*dp }
            setPadding((20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (16*dp).toInt() }
        }
        banner.addView(TextView(ctx).apply {
            text = "前後完了率"; textSize = 14f; setTextColor(Color.parseColor("#DDEEEE"))
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        })
        banner.addView(TextView(ctx).apply {
            text = "${completionPct}%"; textSize = 56f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        })
        banner.addView(TextView(ctx).apply {
            text = "${daysWithBoth}日 完了 ／ ${daysInMonth}日"; textSize = 14f
            setTextColor(Color.parseColor("#DDEEEE")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        })
        root.addView(banner)

        // カード（左アクセントバー付き）
        fun accentCard(accent: Int, block: LinearLayout.() -> Unit) {
            val outer = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply { setColor(cBg); cornerRadius = 10*dp }
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (12*dp).toInt() }
            }
            outer.addView(android.view.View(ctx).apply {
                background = GradientDrawable().apply { setColor(accent); cornerRadius = 10*dp }
                layoutParams = LinearLayout.LayoutParams((5*dp).toInt(), MATCH)
            })
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14*dp).toInt(), (14*dp).toInt(), (14*dp).toInt(), (14*dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            inner.block()
            outer.addView(inner)
            root.addView(outer)
        }

        fun LinearLayout.cardTitle(title: String, color: Int) {
            addView(TextView(ctx).apply {
                text = title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (10*dp).toInt() }
            })
        }

        fun LinearLayout.statRow(icon: String, label: String, value: String, valueColor: Int = cOnSurf) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, (44*dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((36*dp).toInt(), WRAP)
            })
            row.addView(TextView(ctx).apply {
                text = label; textSize = 14f; setTextColor(cGray)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            row.addView(TextView(ctx).apply {
                text = value; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(valueColor)
            })
            addView(row)
        }

        fun LinearLayout.progressBar(label: String, done: Int, total: Int, barColor: Int) {
            val pct = if (total > 0) done.toFloat() / total else 0f
            val hdr = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }
            hdr.addView(TextView(ctx).apply {
                text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            hdr.addView(TextView(ctx).apply {
                text = "${done} / ${total}日"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(barColor)
            })
            addView(hdr)
            val barBg = android.widget.FrameLayout(ctx).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#444444")); cornerRadius = 6*dp }
                layoutParams = LinearLayout.LayoutParams(MATCH, (10*dp).toInt())
                    .also { it.topMargin = (6*dp).toInt(); it.bottomMargin = (10*dp).toInt() }
            }
            barBg.addView(android.view.View(ctx).apply {
                background = GradientDrawable().apply { setColor(barColor); cornerRadius = 6*dp }
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    (pct * ctx.resources.displayMetrics.widthPixels * 0.65f).toInt()
                        .coerceAtLeast(if (done > 0) (10*dp).toInt() else 0),
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
            addView(barBg)
        }

        fun LinearLayout.divider() {
            addView(android.view.View(ctx).apply {
                setBackgroundColor(Color.parseColor("#444444"))
                layoutParams = LinearLayout.LayoutParams(MATCH, (1*dp).toInt())
                    .also { it.topMargin = (4*dp).toInt(); it.bottomMargin = (4*dp).toInt() }
            })
        }

        // ── 記録状況カード
        accentCard(cBlue) {
            cardTitle("📋  記録状況", cBlue)
            progressBar("前後 両方完了", daysWithBoth, daysInMonth,
                if (daysWithBoth == daysInMonth) cGreen else cBlue)
            statRow("🌅", "乗務前 完了", "${daysWithBefore}日")
            statRow("🌆", "乗務後 完了", "${daysWithAfter}日")
            divider()
            statRow(if (daysNoRecord > 0) "⚠️" else "✅", "未記録",
                if (daysNoRecord == 0) "なし" else "${daysNoRecord}日",
                if (daysNoRecord > 0) cRed else cGreen)
        }

        // ── アルコール検知カード
        val alcAccent = if (totalAbnormal == 0) cGreen else cRed
        accentCard(alcAccent) {
            cardTitle("🍺  アルコール検知", alcAccent)
            statRow("🌅", "乗務前",
                if (alcBeforeAbnormal == 0) "検知なし" else "${alcBeforeAbnormal}件",
                if (alcBeforeAbnormal == 0) cGreen else cRed)
            statRow("🌆", "乗務後",
                if (alcAfterAbnormal == 0) "検知なし" else "${alcAfterAbnormal}件",
                if (alcAfterAbnormal == 0) cGreen else cRed)
            divider()
            statRow(if (totalAbnormal == 0) "✅" else "🚨", "合計",
                if (totalAbnormal == 0) "異常なし" else "${totalAbnormal}件",
                alcAccent)
        }

        AlertDialog.Builder(ctx)
            .setTitle("${y}年${m}月  点呼集計")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    // ── テキスト共有 ──
    private fun shareMonthText() {
        if (!isAdded) return
        val ctx    = requireContext()
        val ym     = viewModel.yearMonth.value
        val (y, m) = ym.split("-").map { it.toInt() }
        val records = viewModel.monthRecords.value
        if (records.isEmpty()) {
            Toast.makeText(ctx, "この月の点呼記録がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val text = buildString {
            appendLine("【${y}年${m}月 点呼記録】")
            appendLine()
            records.forEach { r ->
                append(r.date)
                if (r.beforeDone) {
                    append("  前:${r.beforeTime}")
                    r.beforeAlcohol?.let { append(" ALC%.2f".format(it)) }
                }
                if (r.afterDone) {
                    append("  後:${r.afterTime}")
                    r.afterAlcohol?.let { append(" ALC%.2f".format(it)) }
                }
                if (!r.note.isNullOrBlank()) append("  📝${r.note}")
                appendLine()
            }
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${y}年${m}月 点呼記録")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "点呼記録を共有"))
    }

    // ── バックアップ ──
    private fun backupData() {
        if (!isAdded) return
        val ctx = requireContext()
        lifecycleScope.launch {
            try {
                val file = BackupManager.createBackup(ctx)
                val uri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "バックアップを共有"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "バックアップエラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── アプリ設定（点呼に関連する項目） ──
    private fun showTenkoSettings() {
        if (!isAdded) return
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(root)

        fun label(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 13f; setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (14 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
        }
        fun field(value: String, hint: String) = android.widget.EditText(ctx).apply {
            setText(value); this.hint = hint; inputType = InputType.TYPE_CLASS_TEXT
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        root.addView(label("ドライバー名"))
        val etDriver  = field(AppSettings.getDriverName(ctx), "氏名")
        root.addView(etDriver)

        root.addView(label("確認者（運行管理者）名"))
        val etChecker = field(AppSettings.getCheckerName(ctx), "自己点呼の場合は自分の名前")
        root.addView(etChecker)

        root.addView(label("事業者名"))
        val etCompany = field(AppSettings.getCompanyName(ctx), "例: 〇〇運送株式会社")
        root.addView(etCompany)

        root.addView(label("車番（ナンバー）"))
        val etVehicle = field(AppSettings.getVehicleNumber(ctx), "例: 品川 100 あ 1234")
        root.addView(etVehicle)

        // ── リスト右端の表示項目
        root.addView(label("リスト右端の表示"))
        val displayOptions = listOf("アルコール数値", "乗務時刻", "非表示")
        val displayValues  = listOf("alcohol", "time", "none")
        val currentDisplay = AppSettings.getTenkoRightDisplay(ctx)
        val rbGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
        val radioButtons = displayOptions.mapIndexed { i, label ->
            android.widget.RadioButton(ctx).apply {
                text = label; id = android.view.View.generateViewId()
                if (displayValues[i] == currentDisplay) isChecked = true
            }.also { rbGroup.addView(it) }
        }
        root.addView(rbGroup)

        AlertDialog.Builder(ctx)
            .setTitle("点呼設定")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                AppSettings.setDriverName(ctx,   etDriver.text.toString().trim())
                AppSettings.setCheckerName(ctx,  etChecker.text.toString().trim())
                AppSettings.setCompanyName(ctx,  etCompany.text.toString().trim())
                AppSettings.setVehicleNumber(ctx, etVehicle.text.toString().trim())
                val selectedIdx = radioButtons.indexOfFirst { it.isChecked }.coerceAtLeast(0)
                AppSettings.setTenkoRightDisplay(ctx, displayValues[selectedIdx])
                adapter.notifyDataSetChanged()
                Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ── リマインダー設定ダイアログ ──
    private fun showReminderDialog() {
        if (!isAdded) return
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        // Android 13+ の通知権限をリクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(ctx,
                android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        var beforeH = AppSettings.getReminderBeforeHour(ctx)
        var beforeM = AppSettings.getReminderBeforeMinute(ctx)
        var afterH  = AppSettings.getReminderAfterHour(ctx)
        var afterM  = AppSettings.getReminderAfterMinute(ctx)

        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(root)

        val onSurface = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)

        fun sectionHeader(text: String) = root.addView(TextView(ctx).apply {
            this.text = text; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (20 * dp).toInt() }
        })

        // ON/OFF 行（ラベル左・Switch右）
        fun switchRow(label: String, checked: Boolean): Switch {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, (56 * dp).toInt())
                    .also { it.topMargin = (10 * dp).toInt() }
            }
            row.addView(TextView(ctx).apply {
                text = label; textSize = 16f; setTextColor(onSurface)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            val sw = Switch(ctx).apply { isChecked = checked }
            row.addView(sw)
            root.addView(row)
            return sw
        }

        // 時刻ボタン（OutlinedButton スタイル・黒テキスト）
        fun timeButton(hour: Int, minute: Int): com.google.android.material.button.MaterialButton {
            return com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                isAllCaps = false; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
                text = "%02d:%02d".format(hour, minute)
                setTextColor(onSurface)
                layoutParams = LinearLayout.LayoutParams(MATCH, (72 * dp).toInt())
                    .also { it.topMargin = (8 * dp).toInt() }
                insetTop = 0; insetBottom = 0
            }
        }

        // 乗務前
        sectionHeader("乗務前点呼リマインダー")
        val switchBefore = switchRow("通知を有効にする", AppSettings.getReminderBeforeEnabled(ctx))
        val btnBefore = timeButton(beforeH, beforeM)
        btnBefore.setOnClickListener {
            TimePickerDialog(ctx, { _, h, min ->
                beforeH = h; beforeM = min
                btnBefore.text = "%02d:%02d".format(h, min)
            }, beforeH, beforeM, true).show()
        }
        root.addView(btnBefore)

        // 乗務後
        sectionHeader("乗務後点呼リマインダー")
        val switchAfter = switchRow("通知を有効にする", AppSettings.getReminderAfterEnabled(ctx))
        val btnAfter = timeButton(afterH, afterM)
        btnAfter.setOnClickListener {
            TimePickerDialog(ctx, { _, h, min ->
                afterH = h; afterM = min
                btnAfter.text = "%02d:%02d".format(h, min)
            }, afterH, afterM, true).show()
        }
        root.addView(btnAfter)

        AlertDialog.Builder(ctx)
            .setTitle("点呼リマインダー設定")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                AppSettings.setReminderBeforeEnabled(ctx, switchBefore.isChecked)
                AppSettings.setReminderBeforeHour(ctx, beforeH)
                AppSettings.setReminderBeforeMinute(ctx, beforeM)
                AppSettings.setReminderAfterEnabled(ctx, switchAfter.isChecked)
                AppSettings.setReminderAfterHour(ctx, afterH)
                AppSettings.setReminderAfterMinute(ctx, afterM)

                applyReminder(ctx, true, beforeH, beforeM, switchBefore.isChecked)
                applyReminder(ctx, false, afterH, afterM, switchAfter.isChecked)

                val nowMin = Calendar.getInstance().let {
                    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
                }
                fun dayLabel(h: Int, m: Int) = if (h * 60 + m <= nowMin) "明日" else "今日"

                val parts = mutableListOf<String>()
                if (switchBefore.isChecked)
                    parts.add("乗務前 %02d:%02d（%sから毎日）".format(beforeH, beforeM, dayLabel(beforeH, beforeM)))
                if (switchAfter.isChecked)
                    parts.add("乗務後 %02d:%02d（%sから毎日）".format(afterH, afterM, dayLabel(afterH, afterM)))
                val msg = if (parts.isEmpty()) "通知をOFFにしました" else parts.joinToString("\n") + "\nに通知を設定しました"
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun applyReminder(ctx: Context, isBefore: Boolean, hour: Int, minute: Int, enable: Boolean) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reqCode = if (isBefore) 3001 else 3002
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, if (isBefore) "乗務前点呼のお時間です" else "乗務後点呼のお時間です")
            putExtra(ReminderReceiver.EXTRA_TEXT,  if (isBefore) "出発前に点呼を記録してください" else "帰着後に点呼を記録してください")
            putExtra(ReminderReceiver.EXTRA_NOTIF_ID, if (isBefore) ReminderReceiver.NOTIF_BEFORE else ReminderReceiver.NOTIF_AFTER)
        }
        val pi = PendingIntent.getBroadcast(ctx, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        am.cancel(pi)
        if (!enable) return

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    // ── 今月データ全削除 ──
    private fun confirmDeleteMonth() {
        if (!isAdded) return
        val ym     = viewModel.yearMonth.value
        val (y, m) = ym.split("-").map { it.toInt() }
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️  データを削除")
            .setMessage("${y}年${m}月のデータをすべて削除します。\n削除後は復元できません。よろしいですか？")
            .setPositiveButton("削除する") { _, _ ->
                viewModel.deleteMonth(ym)
                Toast.makeText(requireContext(), "${y}年${m}月のデータを削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
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
    private val onLongPress:   (TenkoRecord) -> Unit = {}
) : RecyclerView.Adapter<TenkoMonthAdapter.ViewHolder>() {

    data class DayRow(val date: String, val record: TenkoRecord?, val isToday: Boolean)

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context
        val dp  = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding((14*dp).toInt(),(14*dp).toInt(),(14*dp).toInt(),(14*dp).toInt())
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val root: LinearLayout) : RecyclerView.ViewHolder(root) {

        fun bind(row: DayRow) {
            root.removeAllViews()
            val ctx = root.context
            val dp  = ctx.resources.displayMetrics.density

            root.setOnLongClickListener {
                row.record?.let { onLongPress(it) }
                row.record != null
            }

            // 日付部分
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

            root.setBackgroundColor(
                if (row.isToday) ContextCompat.getColor(ctx, R.color.colorTodayBg)
                else ContextCompat.getColor(ctx, R.color.colorDayBg)
            )

            val tvDay = TextView(ctx).apply {
                text = "${day}（${wd}）"
                textSize = 18f
                setTextColor(dayColor)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams((76*dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            root.addView(tvDay)

            val spacer = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((10*dp).toInt(), 0)
            }
            root.addView(spacer)

            // ステータスチップ（完了=白塗り黒文字、未完了=暗い背景グレー枠）
            fun chip(label: String, done: Boolean, onClick: () -> Unit): TextView {
                return TextView(ctx).apply {
                    text = label
                    textSize = 16f
                    val hintBg   = ContextCompat.getColor(ctx, R.color.colorHintBg)
                    val hintText = ContextCompat.getColor(ctx, R.color.colorHintText)
                    setTextColor(if (done) Color.parseColor("#111111") else hintText)
                    setTypeface(null, Typeface.BOLD)
                    background = GradientDrawable().apply {
                        setColor(if (done) Color.parseColor("#EEEEEE") else hintBg)
                        setStroke((1.5f*dp).toInt(), if (done) Color.parseColor("#EEEEEE") else hintBg)
                        cornerRadius = 6*dp
                    }
                    setPadding((14*dp).toInt(),(8*dp).toInt(),(14*dp).toInt(),(8*dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = (10*dp).toInt() }
                    setOnClickListener { onClick() }
                }
            }

            root.addView(chip("乗務前", row.record?.beforeDone == true) {
                onBeforeClick(row.date, row.record)
            })
            root.addView(chip("乗務後", row.record?.afterDone == true) {
                onAfterClick(row.date, row.record)
            })

            // 特記事項マーク
            if (!row.record?.note.isNullOrBlank()) {
                root.addView(TextView(ctx).apply {
                    text = "📝"; textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.marginStart = (6*dp).toInt() }
                })
            }

            // スペーサー
            root.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })

            // 右端表示（設定に応じて切り替え）
            val rightDisplay = AppSettings.getTenkoRightDisplay(ctx)
            when (rightDisplay) {
                "alcohol" -> {
                    val ba = row.record?.beforeAlcohol
                    val aa = row.record?.afterAlcohol
                    if (ba != null || aa != null) {
                        val col = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = android.view.Gravity.END
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        fun alcView(label: String, v: Double) = TextView(ctx).apply {
                            val isOver = v > 0.0
                            text = "$label ${"%.2f".format(v)}"
                            textSize = 15f
                            setTextColor(if (isOver) ContextCompat.getColor(ctx, R.color.colorSundayText)
                                         else ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                            setTypeface(null, Typeface.BOLD)
                            gravity = android.view.Gravity.END
                        }
                        if (ba != null) col.addView(alcView("前", ba))
                        if (aa != null) col.addView(alcView("後", aa))
                        root.addView(col)
                    }
                }
                "time" -> {
                    val bt = row.record?.beforeTime
                    val at = row.record?.afterTime
                    if (bt != null || at != null) {
                        val col = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = android.view.Gravity.END
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        fun timeView(label: String, t: String) = TextView(ctx).apply {
                            text = "$label $t"; textSize = 15f
                            setTextColor(ContextCompat.getColor(ctx, R.color.colorWeekdayText))
                            setTypeface(null, Typeface.BOLD)
                            gravity = android.view.Gravity.END
                        }
                        if (bt != null) col.addView(timeView("前", bt))
                        if (at != null) col.addView(timeView("後", at))
                        root.addView(col)
                    }
                }
                // "none" は何も表示しない
            }

        }
    }
}
