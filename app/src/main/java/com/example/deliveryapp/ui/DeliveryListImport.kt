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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
internal fun DeliveryListFragment.launchScanActivity() {
        scanLauncher.launch(Intent(requireContext(), ScanActivity::class.java))
    }


internal fun DeliveryListFragment.importList(text: String) {
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

internal fun DeliveryListFragment.importListToCurrentGroup(toAdd: List<Delivery>) {
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

internal fun DeliveryListFragment.proceedWithImport(toAdd: List<Delivery>, replace: Boolean) {
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

internal fun DeliveryListFragment.toggleMapView() {
    if (isMapVisible) switchToListView() else showMapView()
}
