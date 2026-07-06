package com.rodgers.haireel.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.rodgers.haireel.R
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.viewmodel.DeliveryViewModel
import com.rodgers.haireel.viewmodel.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            } else if (looksLikeCsvOrTsv(text)) {
                // CSV / TSV 形式（カンマ or タブ区切り）
                val lines = text.trim().lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) return
                // セパレータ自動検出
                val firstLine = lines.first()
                val sep = if (firstLine.count { it == '\t' } >= firstLine.count { it == ',' }) '\t' else ','
                val headers = firstLine.split(sep).map { it.trim().trim('"') }
                fun idx(vararg keys: String) = headers.indexOfFirst { h ->
                    keys.any { k -> h.contains(k, ignoreCase = true) }
                }
                val nameIdx = idx(
                    "名前", "店名", "name", "氏名", "受取人", "宛名",
                    "お名前", "届け先名", "受取人名", "お届け先名", "受け取り人"
                )
                val addrIdx = idx(
                    "住所", "address", "addr", "配達先", "お届け先",
                    "届け先", "送り先", "宛先", "配送先", "住所1", "所在地"
                ).let { found ->
                    when {
                        found >= 0 -> found
                        nameIdx > 0 -> 0       // 名前が0列目以外 → 0列目を住所とみなす
                        nameIdx == 0 && headers.size > 1 -> 1  // 名前が0列目 → 1列目を住所とみなす
                        else -> -1
                    }
                }
                val slotIdx = idx("時間帯", "時間", "slot", "配達時間", "お届け時間")
                val pkgIdx  = idx("個数", "数量", "count", "荷物数", "個")
                val noteIdx = idx("メモ", "備考", "note", "備考欄", "連絡事項")
                lines.drop(1).forEachIndexed { i, line ->
                    val cols = line.split(sep).map { it.trim().removeSurrounding("\"") }
                    val address = if (addrIdx >= 0) cols.getOrNull(addrIdx)?.ifBlank { null } else null
                    val name    = if (nameIdx >= 0) cols.getOrNull(nameIdx)?.ifBlank { null } else null
                    // 住所も名前もない行はスキップ
                    if (address.isNullOrBlank() && name.isNullOrBlank()) return@forEachIndexed
                    // 住所がない場合は名前で検索させる
                    val effectiveAddress = address ?: name ?: return@forEachIndexed
                    toAdd.add(Delivery(
                        order = i + 1,
                        address = effectiveAddress,
                        name = name,
                        timeSlot = if (slotIdx >= 0) cols.getOrNull(slotIdx)?.ifBlank { null } else null,
                        packageCount = if (pkgIdx >= 0) cols.getOrNull(pkgIdx)?.toIntOrNull() ?: 0 else 0,
                        note = if (noteIdx >= 0) cols.getOrNull(noteIdx)?.ifBlank { null } else null
                    ))
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
                    } else if (!cleaned.contains(Regex("[都道府県]")) &&
                               !cleaned.contains(Regex("[0-9０-９][丁目番地号]"))) {
                        // 住所の特徴がない → 店名として扱う（ジオコーディング後に address が上書きされる）
                        cleaned to cleaned
                    } else {
                        null to cleaned
                    }
                    toAdd.add(Delivery(order = i + 1, address = address, name = name))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DeliveryImport", "パース失敗", e)
            ctx.showErrorDialog("読み込みエラー", "住所データの読み込みに失敗しました。\nファイルの形式（CSV / TSV / Excel）を確認してください。\n\n詳細: ${e.localizedMessage ?: "不明なエラー"}")
            return
        }
        if (toAdd.isEmpty()) {
            android.widget.Toast.makeText(ctx, "インポートできるデータがありませんでした", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 半角数字・半角英字を全角に統一（住所・名前のすべてのインポート経路に適用）
        val normalized = toAdd.map { d ->
            d.copy(
                address = com.rodgers.haireel.util.AddressParser.toFullWidth(d.address),
                name    = d.name?.let { com.rodgers.haireel.util.AddressParser.toFullWidth(it) }
            )
        }
        toAdd.clear(); toAdd.addAll(normalized)

        // ── Step A: ルート選択
        val groups = viewModel.groups.value
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
                val currentId = viewModel.currentGroupId.value
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
        val existingCount = viewModel.deliveries.value.size
        val routeName = viewModel.currentGroup()?.name ?: "現在のルート"
        if (existingCount > 0) {
            // ── Step B: 追加 or 置き換え
            AlertDialog.Builder(ctx)
                .setTitle("${toAdd.size}件をインポート")
                .setMessage("「$routeName」にはすでに${existingCount}件あります。\nどのように処理しますか？")
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

        // 1件以下はエリアチェック不要 → 直接インポート
        if (toAdd.size <= 1) { doImport(toAdd); return }

        val prefRegex = Regex("""[^\s]+[都道府県]""")

        // 置き換えモードでは既存リストを無視してエリアチェック
        val existingByPref = if (replace) linkedMapOf()
        else {
            val map = linkedMapOf<String, Int>()
            for (d in viewModel.deliveries.value) {
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

        // 単一エリアかつ混在なし → Step B で確認済みのため直接インポート
        doImport(toAdd)
    }

private fun looksLikeCsvOrTsv(text: String): Boolean {
    val first = text.trim().lines().firstOrNull { it.isNotBlank() } ?: return false
    val hasComma = first.count { it == ',' } >= 1
    val hasTab   = first.count { it == '\t' } >= 1
    if (!hasComma && !hasTab) return false
    val keywords = listOf(
        "住所", "address", "名前", "name", "店名", "時間帯", "個数", "メモ", "備考",
        "氏名", "受取人", "宛名", "配達先", "お届け先", "宛先", "所在地", "届け先"
    )
    return keywords.any { first.contains(it, ignoreCase = true) }
}

internal fun DeliveryListFragment.toggleMapView() {
    if (viewMode == DeliveryListFragment.ViewMode.MAP) switchToListView() else showMapView()
}
