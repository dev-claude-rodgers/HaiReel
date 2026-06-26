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

        // ── 音声案内トグル
        val ttsOn = com.rodgers.routist.util.AppSettings.isTtsEnabled(ctx)
        row(
            if (ttsOn) "🔊" else "🔇",
            if (ttsOn) "音声案内 ON" else "音声案内 OFF",
            if (ttsOn) "配達完了後に次の住所を読み上げ中（タップでOFF）"
                       else "タップすると配達完了後に次の住所を読み上げます"
        ) {
            val next = !com.rodgers.routist.util.AppSettings.isTtsEnabled(ctx)
            com.rodgers.routist.util.AppSettings.setTtsEnabled(ctx, next)
            if (next) com.rodgers.routist.util.TtsManager.speak("音声案内をオンにしました", ctx)
            android.widget.Toast.makeText(
                ctx,
                if (next) "🔊 音声案内をONにしました" else "🔇 音声案内をOFFにしました",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        row("📖", "読み替え辞書", "誤読される単語を正しい読みに登録する") {
            sheet.dismiss(); showTtsDictionaryDialog()
        }
        divider()

        // ── 追加・取り込み
        row("🔄", "住所を全角に一括変換", "既存データの半角数字・英字・ハイフンを全角に統一する") {
            sheet.dismiss()
            viewModel.normalizeAllAddresses()
            android.widget.Toast.makeText(requireContext(), "✅ 全角変換を実行しました", android.widget.Toast.LENGTH_SHORT).show()
        }
        row("📥", "名前・住所を追加", "テキスト・CSV・ファイルから追加する") {
            inputLauncher.launch(Intent(requireContext(), InputActivity::class.java))
        }
        row("📚", "過去の配達先から追加", "名前・住所で検索して素早く1件追加する") { sheet.dismiss(); showAddressHistoryDialog() }
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


internal fun DeliveryListFragment.showAddressHistoryDialog() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density

    // ── ダイアログ全体 ─────────────────────────────────────────────
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (8*dp).toInt())
    }

    // ── ヘッダー行（ラベル + 設定ボタン）──────────────────────────
    root.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(android.widget.TextView(ctx).apply {
            text = "名前または住所を入力（1文字から候補を表示）"
            textSize = 12f; setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(android.widget.TextView(ctx).apply {
            text = "⚙️ 設定"
            textSize = 12f; setTextColor(android.graphics.Color.parseColor("#1565C0"))
            setPadding((8*dp).toInt(), 0, 0, 0)
            setOnClickListener { showAddressHistorySettings() }
        })
    })
    val etAddress = android.widget.EditText(ctx).apply {
        hint = "例：港北区日吉、横浜市…"
        textSize = 16f
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    root.addView(etAddress)

    // 候補ラベル
    val tvSuggestLabel = android.widget.TextView(ctx).apply {
        text = "🕐 最近の配達先"
        textSize = 12f; setTextColor(android.graphics.Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (10*dp).toInt() }
    }
    root.addView(tvSuggestLabel)

    // 候補リスト（ListView）- KnownAddressEntity を保持して住所・名前を両方セットできるようにする
    val suggestEntities = mutableListOf<com.rodgers.routist.db.KnownAddressEntity>()

    // 2行カスタムアダプター（名前＋住所＋削除ボタン）
    val suggestAdapter = object : android.widget.BaseAdapter() {
        override fun getCount() = suggestEntities.size
        override fun getItem(pos: Int) = suggestEntities[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val r = suggestEntities[pos]
            val hasName = !r.name.isNullOrBlank()

            // 外枠: 横並び（テキスト部 + 削除ボタン）
            val outer = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((12*dp).toInt(), (8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
            }

            // テキスト部（縦並び）
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            // 1行目: 名前（あれば）または住所
            textCol.addView(android.widget.TextView(ctx).apply {
                text = if (hasName) r.name else r.address
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.WHITE)
            })
            // 2行目: 住所（名前がある場合のみ）
            if (hasName) {
                textCol.addView(android.widget.TextView(ctx).apply {
                    text = r.address
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (2*dp).toInt() }
                })
            }
            outer.addView(textCol)

            // 🗑 削除ボタン
            outer.addView(android.widget.TextView(ctx).apply {
                text = "🗑"
                textSize = 18f
                setPadding((12*dp).toInt(), 0, (4*dp).toInt(), 0)
                setOnClickListener {
                    viewModel.deleteKnownAddress(r)
                    suggestEntities.removeAt(pos)
                    notifyDataSetChanged()
                }
            })

            return outer
        }
    }

    val lvSuggest = android.widget.ListView(ctx).apply {
        adapter = suggestAdapter
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (200 * dp).toInt()
        ).also { it.topMargin = (4*dp).toInt() }
        divider = android.graphics.drawable.ColorDrawable(
            android.graphics.Color.parseColor("#E0E0E0"))
        dividerHeight = (1 * dp).toInt()
    }
    root.addView(lvSuggest)

    // 受取人名
    root.addView(android.widget.TextView(ctx).apply {
        text = "受取人名（任意）"
        textSize = 12f; setTextColor(android.graphics.Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (12*dp).toInt() }
    })
    val etName = android.widget.EditText(ctx).apply {
        hint = "受取人名（任意）"; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    root.addView(etName)

    // 候補を更新するヘルパー
    fun updateSuggestions(prefix: String) {
        lifecycleScope.launch {
            val results = viewModel.searchKnownAddresses(prefix)
            suggestEntities.clear()
            suggestEntities.addAll(results)
            suggestAdapter.notifyDataSetChanged()
            tvSuggestLabel.text = if (prefix.isBlank()) "🕐 最近の配達先" else "🔍 候補（${results.size}件）"
            lvSuggest.visibility = if (results.isEmpty()) android.view.View.GONE
                                   else android.view.View.VISIBLE
        }
    }

    // 候補タップ → 住所・名前を両方セット
    lvSuggest.setOnItemClickListener { _, _, pos, _ ->
        val entity = suggestEntities.getOrNull(pos) ?: return@setOnItemClickListener
        // 住所をセット
        etAddress.setText(entity.address)
        etAddress.setSelection(entity.address.length)
        // 名前がある場合は名前欄も常に反映（上書き）
        if (!entity.name.isNullOrBlank()) {
            etName.setText(entity.name)
        }
        lvSuggest.visibility = android.view.View.GONE
    }

    // 住所欄のテキスト変化で候補を絞り込む
    etAddress.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
            updateSuggestions(s?.toString()?.trim() ?: "")
        }
    })

    // 名前欄のテキスト変化でも候補を絞り込む（名前からも検索できる）
    etName.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
            val nameInput = s?.toString()?.trim() ?: return
            // 住所欄が空のときだけ名前で候補を検索する
            if (etAddress.text.isNullOrBlank()) {
                updateSuggestions(nameInput)
            }
        }
    })

    // ダイアログ表示と同時にクリーンアップ & 最近の候補を表示
    viewModel.cleanupAddressHistory()
    updateSuggestions("")

    val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
        .setTitle("📚 過去の配達先から追加")
        .setView(root)
        .setPositiveButton("追加") { _, _ ->
            val addr = etAddress.text.toString().trim()
            if (addr.isBlank()) {
                android.widget.Toast.makeText(ctx, "住所を入力してください", android.widget.Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            val name = etName.text.toString().trim().ifBlank { null }
            val delivery = com.rodgers.routist.model.Delivery(
                order = (viewModel.deliveries.value.maxOfOrNull { it.order } ?: 0) + 1,
                address = addr,
                name = name
            )
            importListToCurrentGroup(listOf(delivery))
        }
        .setNegativeButton("キャンセル", null)
        .create()

    dialog.show()
}

/** TTS読み替え辞書 BottomSheet */
internal fun DeliveryListFragment.showTtsDictionaryDialog() {
    val ctx   = requireContext()
    val dp    = ctx.resources.displayMetrics.density
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)

    val surfaceColor     = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurfaceColor   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val outlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)

    // ── 全体レイアウト（上から固定順: タイトル・リスト・追加フォーム）──
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceColor)
    }

    // タイトル行
    root.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding((20*dp).toInt(), (16*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
        addView(android.widget.TextView(ctx).apply {
            text = "📖 TTS読み替え辞書"
            textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(android.widget.TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = android.view.Gravity.CENTER
            setTextColor(onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams((56*dp).toInt(), (56*dp).toInt())
            setOnClickListener { sheet.dismiss() }
        })
    })

    // 説明文
    root.addView(android.widget.TextView(ctx).apply {
        text = "誤読される単語をカタカナの読みに登録してください\n例: はなまさ → ハナマサ"
        textSize = 12f; setTextColor(onSurfaceVariant)
        setPadding((20*dp).toInt(), 0, (20*dp).toInt(), (8*dp).toInt())
    })

    // 区切り線
    fun hLine() = android.view.View(ctx).apply {
        setBackgroundColor(outlineVariant)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt())
    }
    root.addView(hLine())

    // ── 登録済みリスト（ScrollView で件数が増えても可変）──────────────
    val listContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20*dp).toInt(), (8*dp).toInt(), (20*dp).toInt(), (8*dp).toInt())
    }
    val listScroll = android.widget.ScrollView(ctx).apply {
        addView(listContainer)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (0*dp).toInt(), 1f
        )
    }
    root.addView(listScroll)
    root.addView(hLine())

    // ── 新規追加フォーム（常に画面下部に固定）──────────────────────────
    val formArea = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
    }
    formArea.addView(android.widget.TextView(ctx).apply {
        text = "新規追加"; textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurfaceColor)
    })
    val etSurface = android.widget.EditText(ctx).apply {
        hint = "表記（例: はなまさ）"; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (6*dp).toInt() }
    }
    val etReading = android.widget.EditText(ctx).apply {
        hint = "読み・カタカナ（例: ハナマサ）"; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (6*dp).toInt() }
    }
    val btnAdd = com.google.android.material.button.MaterialButton(ctx).apply {
        text = "追加してテスト再生"
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (10*dp).toInt() }
    }
    formArea.addView(etSurface); formArea.addView(etReading); formArea.addView(btnAdd)
    root.addView(formArea)

    // ── リスト更新 ──────────────────────────────────────────────────
    fun refreshList() {
        listContainer.removeAllViews()
        val dic = com.rodgers.routist.util.TtsDictionary.getAll(ctx)
        if (dic.isEmpty()) {
            listContainer.addView(android.widget.TextView(ctx).apply {
                text = "（登録なし）"
                textSize = 13f; setTextColor(onSurfaceVariant)
                setPadding(0, (12*dp).toInt(), 0, (12*dp).toInt())
            })
        } else {
            dic.entries.sortedBy { it.key }.forEach { (surface, reading) ->
                listContainer.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, (12*dp).toInt(), 0, (12*dp).toInt())
                    addView(android.widget.TextView(ctx).apply {
                        text = "$surface → $reading"
                        textSize = 15f; setTextColor(onSurfaceColor)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(android.widget.TextView(ctx).apply {
                        text = "🗑"
                        textSize = 18f
                        setPadding((16*dp).toInt(), 0, 0, 0)
                        setOnClickListener {
                            com.rodgers.routist.util.TtsDictionary.remove(ctx, surface)
                            refreshList()
                        }
                    })
                })
                listContainer.addView(android.view.View(ctx).apply {
                    setBackgroundColor(outlineVariant)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt()
                    )
                })
            }
        }
    }

    btnAdd.setOnClickListener {
        val surface = etSurface.text.toString().trim()
        val reading = etReading.text.toString().trim()
        if (surface.isBlank() || reading.isBlank()) {
            Toast.makeText(ctx, "表記と読みを両方入力してください", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        com.rodgers.routist.util.TtsDictionary.put(ctx, surface, reading)
        etSurface.text?.clear(); etReading.text?.clear()
        refreshList()
        com.rodgers.routist.util.TtsManager.speak(reading, ctx)
        Toast.makeText(ctx, "「$reading」と登録しました", Toast.LENGTH_SHORT).show()
    }

    refreshList()

    sheet.setContentView(root)
    sheet.setOnShowListener {
        val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        bs?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.behavior.isDraggable = false  // スワイプ閉じを無効化（✕ボタンで閉じる）
    }
    sheet.show()
}

/** 住所履歴の保持設定ダイアログ */
internal fun DeliveryListFragment.showAddressHistorySettings() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val current = viewModel.getHistorySettings()

    val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((20*dp).toInt(), (12*dp).toInt(), (20*dp).toInt(), (8*dp).toInt())
    }

    // ── 上限件数 ────────────────────────────────────────────────
    col.addView(android.widget.TextView(ctx).apply {
        text = "上限件数（超えた分は古い順に削除）"
        textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD
    })
    val maxOptions   = intArrayOf(100, 300, 500, 1000, 0)
    val maxLabels    = arrayOf("100件", "300件", "500件（推奨）", "1000件", "無制限")
    val maxDefault   = maxOptions.indexOfFirst { it == current.maxCount }.let { if (it < 0) 2 else it }
    val rgMax = android.widget.RadioGroup(ctx).apply {
        orientation = android.widget.RadioGroup.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (4*dp).toInt(); it.bottomMargin = (12*dp).toInt() }
    }
    maxLabels.forEachIndexed { i, label ->
        rgMax.addView(android.widget.RadioButton(ctx).apply {
            text = label; id = i
            isChecked = (i == maxDefault)
        })
    }
    col.addView(rgMax)

    // ── 自動削除期間 ─────────────────────────────────────────────
    col.addView(android.widget.TextView(ctx).apply {
        text = "自動削除期間（配達回数3回未満のみ対象）"
        textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD
    })
    col.addView(android.widget.TextView(ctx).apply {
        text = "※3回以上配達した場所は期間に関わらず保持されます"
        textSize = 11f; setTextColor(android.graphics.Color.GRAY)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (4*dp).toInt() }
    })
    val daysOptions  = intArrayOf(30, 90, 180, 365, 0)
    val daysLabels   = arrayOf("30日", "90日（推奨）", "180日", "365日", "削除しない")
    val daysDefault  = daysOptions.indexOfFirst { it == current.deleteDays }.let { if (it < 0) 1 else it }
    val rgDays = android.widget.RadioGroup(ctx).apply {
        orientation = android.widget.RadioGroup.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = (4*dp).toInt() }
    }
    daysLabels.forEachIndexed { i, label ->
        rgDays.addView(android.widget.RadioButton(ctx).apply {
            text = label; id = i
            isChecked = (i == daysDefault)
        })
    }
    col.addView(rgDays)

    androidx.appcompat.app.AlertDialog.Builder(ctx)
        .setTitle("⚙️ 履歴の保持設定")
        .setView(col)
        .setPositiveButton("保存") { _, _ ->
            val maxCount   = maxOptions[rgMax.checkedRadioButtonId]
            val deleteDays = daysOptions[rgDays.checkedRadioButtonId]
            viewModel.saveHistorySettings(maxCount, deleteDays)
            viewModel.cleanupAddressHistory()
            android.widget.Toast.makeText(ctx, "設定を保存しました", android.widget.Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

internal fun DeliveryListFragment.showRenameGroupDialog() {
        val ctx = requireContext()
        val group = viewModel.currentGroup() ?: return
        val input = EditText(ctx).apply {
            setText(group.name); selectAll()
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(20))
        }
        val dlg = AlertDialog.Builder(ctx)
            .setTitle("ルート名を変更")
            .setView(input)
            .setPositiveButton("変更", null)
            .setNegativeButton("キャンセル", null)
            .show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isBlank()) { input.error = "ルート名を入力してください"; return@setOnClickListener }
            viewModel.renameGroup(group.id, name)
            dlg.dismiss()
        }
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
        val dlg = AlertDialog.Builder(ctx)
            .setTitle("新しいルートを追加")
            .setView(input)
            .setPositiveButton("作成", null)
            .setNegativeButton("キャンセル", null)
            .show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isBlank()) { input.error = "ルート名を入力してください"; return@setOnClickListener }
            val group = viewModel.createGroup(name)
            viewModel.switchGroup(group.id)
            dlg.dismiss()
        }
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

