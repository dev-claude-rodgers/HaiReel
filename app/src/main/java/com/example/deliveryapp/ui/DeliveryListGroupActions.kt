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
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.routist.R
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.Room
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.PatternStorage
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.util.TimeSlotColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
import com.rodgers.routist.viewmodel.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
internal fun DeliveryListFragment.showListActions() {
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
                setTextColor(onSurfaceVariant)
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

        // ── 追加・取り込み
        row("📥", "名前・住所を追加", "テキスト・CSV・ファイルから追加する") {
            inputLauncher.launch(Intent(requireContext(), InputActivity::class.java))
        }
        row("📷", "伝票からスキャン", "カメラで伝票を撮影して読み取る") { launchScanActivity() }
        row("☑️", "選択モード", "複数の配達先を選んで一括操作する") {
            if (adapter.isSelectMode) exitSelectMode() else enterSelectMode()
        }
        divider()
        // ── 完了操作
        row("✅", "全件を完了にする", "すべてに完了マークをつける") { confirmMarkAllCompleted() }
        row("↩️", "完了をリセット", "全件を未完了に戻す") { confirmResetCompleted() }
        divider()
        // ── ルート管理
        row("➕", "新しいルートを追加", "新しい配達ルートを作成する") { showCreateGroupDialog() }
        row("✏️", "ルート名を変更", "現在のルート名を編集する") { showRenameGroupDialog() }
        row("📄", "ルートを複製", "同じ内容で別ルートを作成する") {
            val groupId = viewModel.currentGroupId.value
            viewModel.copyGroup(groupId)
        }
        row("📤", "ルートを共有", "LINE・メール等で送る") { shareList() }
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


internal fun DeliveryListFragment.showRenameGroupDialog() {
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

internal fun DeliveryListFragment.showCreateGroupDialog() {
        val ctx = requireContext()
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

internal fun DeliveryListFragment.showLinkPatternDialog(parentSheet: com.google.android.material.bottomsheet.BottomSheetDialog) {
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

internal fun DeliveryListFragment.confirmResetCompleted() {
        val done = viewModel.deliveries.value.count { it.isCompleted }
        if (done == 0) {
            android.widget.Toast.makeText(requireContext(), "完了済みの${"配達先"}がありません", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("完了をリセット")
            .setMessage("${done}件の完了を未完了に戻しますか？")
            .setPositiveButton("リセット") { _, _ -> viewModel.resetAllCompleted() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

internal fun DeliveryListFragment.confirmMarkAllCompleted() {
        val remaining = viewModel.deliveries.value.count { !it.isCompleted }
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

internal fun DeliveryListFragment.showProgressDialog() {
        val list = viewModel.deliveries.value
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

internal fun DeliveryListFragment.confirmDeleteGroup() {
        val group = viewModel.currentGroup() ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("ルートを削除")
            .setMessage("「${group.name}」を削除しますか？\n削除後は元に戻せません。")
            .setPositiveButton("削除") { _, _ -> viewModel.deleteGroup(group.id) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

internal fun DeliveryListFragment.shareList() {
        val deliveries = viewModel.deliveries.value
        if (deliveries.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "リストが空です", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val groupName = viewModel.groups.value.find { it.id == viewModel.currentGroupId.value }?.name ?: "配達リスト"
        val lines = buildString {
            append("$groupName（${deliveries.size}件）\n")
            deliveries.forEachIndexed { i, d ->
                val label = if (!d.name.isNullOrBlank()) "${d.name}（${d.address}）" else d.address
                val extras = buildList {
                    if (!d.timeSlot.isNullOrBlank()) add(d.timeSlot.orEmpty())
                    if (d.packageCount > 0) add("${d.packageCount}個")
                    if (!d.note.isNullOrBlank()) add(d.note.orEmpty())
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

