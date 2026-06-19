package com.rodgers.routist.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.R
import com.rodgers.routist.util.themeColor

private data class SosStep(val emoji: String, val text: String)

private data class SosSituation(
    val emoji: String,
    val label: String,
    val steps: List<SosStep>,
    val calls: List<Pair<String, String>>
)

private val SITUATIONS = listOf(
    SosSituation(
        emoji = "🚗",
        label = "交通事故",
        steps = listOf(
            SosStep("1️⃣", "エンジンを切り、ハザードランプを点灯する"),
            SosStep("2️⃣", "安全な場所（ガードレール外など）に避難する"),
            SosStep("3️⃣", "後続車に知らせるため三角表示板を設置する"),
            SosStep("4️⃣", "警察（110）に通報する。物損でも必ず必要"),
            SosStep("5️⃣", "けが人がいれば救急（119）を呼ぶ"),
            SosStep("6️⃣", "相手の車番・氏名・保険会社を記録する"),
            SosStep("7️⃣", "現場の写真を複数枚撮影して証拠を残す"),
        ),
        calls = listOf("警察" to "110", "救急" to "119", "JAF" to "0570008139")
    ),
    SosSituation(
        emoji = "🔧",
        label = "車両トラブル",
        steps = listOf(
            SosStep("1️⃣", "ハザードランプをすぐに点灯する"),
            SosStep("2️⃣", "路肩など安全な場所に停車する"),
            SosStep("3️⃣", "後方50〜100mに三角表示板を設置する"),
            SosStep("4️⃣", "高速道路では車外に出ず、ガードレール外に避難"),
            SosStep("5️⃣", "JAF または加入中のロードサービスに連絡する"),
            SosStep("6️⃣", "荷主・配送センターに状況を連絡する"),
        ),
        calls = listOf("JAF" to "0570008139", "損保ジャパン事故専用" to "0120001149", "東京海上事故専用" to "0120101110")
    ),
    SosSituation(
        emoji = "🤕",
        label = "体調不良・急病",
        steps = listOf(
            SosStep("1️⃣", "安全な場所に停車してエンジンを切る"),
            SosStep("2️⃣", "軽症なら数分安静にして症状の変化を確認する"),
            SosStep("3️⃣", "めまい・胸の痛み・意識低下があれば 119 に連絡"),
            SosStep("4️⃣", "判断に迷えば「#7119（救急安心センター）」に相談"),
            SosStep("5️⃣", "荷主・配送センターに連絡して運行を中止する"),
        ),
        calls = listOf("救急" to "119", "救急安心センター" to "7119")
    ),
    SosSituation(
        emoji = "❓",
        label = "その他のトラブル",
        steps = listOf(
            SosStep("🔑", "鍵のトラブル → JAF（0570-00-8139）"),
            SosStep("⛽", "燃料切れ → JAF または最寄りスタンドに連絡"),
            SosStep("🚧", "道路落下物・陥没 → 道路緊急ダイヤル #9910"),
            SosStep("📦", "荷物の紛失・破損 → 配送センターに即時連絡"),
        ),
        calls = listOf("JAF" to "0570008139", "道路緊急ダイヤル" to "9910")
    )
)

internal fun MapFragment.showSosDialog() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val surfaceBg      = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val surfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
    val onSurface      = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVar   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val outlineVariant = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val sheet = BottomSheetDialog(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceBg)
        setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (32*dp).toInt())
    }

    root.addView(TextView(ctx).apply {
        text = "🆘 緊急対応ガイド"
        textSize = 20f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#CC0000"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (4*dp).toInt() }
    })
    root.addView(TextView(ctx).apply {
        text = "状況を選んでください"
        textSize = 13f; setTextColor(onSurfaceVar); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (16*dp).toInt() }
    })

    val ripple = android.util.TypedValue().also {
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
    }.resourceId

    SITUATIONS.forEach { situation ->
        val bg = GradientDrawable().apply { setColor(surfaceVariant); cornerRadius = 12 * dp }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (10*dp).toInt() }
        }
        card.addView(TextView(ctx).apply {
            text = situation.emoji; textSize = 32f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((52*dp).toInt(), WRAP)
        })
        card.addView(TextView(ctx).apply {
            text = situation.label; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also { it.marginStart = (12*dp).toInt() }
        })
        card.addView(TextView(ctx).apply {
            text = "▶"; textSize = 14f; setTextColor(onSurfaceVar)
        })
        val captured = situation
        card.setOnClickListener { sheet.dismiss(); showSosGuide(captured) }
        root.addView(card)
    }

    root.addView(android.view.View(ctx).apply {
        setBackgroundColor(outlineVariant)
        layoutParams = LinearLayout.LayoutParams(MATCH, (1*dp).toInt())
            .also { it.topMargin = (8*dp).toInt(); it.bottomMargin = (12*dp).toInt() }
    })

    root.addView(TextView(ctx).apply {
        text = "📨 連絡先にSMSを送る"
        textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#CC0000"))
        setBackgroundResource(ripple)
        setPadding((16*dp).toInt(), (14*dp).toInt(), (16*dp).toInt(), (14*dp).toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        setOnClickListener { sheet.dismiss(); sendSosSms(ctx) }
    })

    val scrollView = ScrollView(ctx).apply { addView(root) }
    sheet.setContentView(scrollView)
    sheet.setOnShowListener {
        val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        bs?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
    }
    sheet.show()
}

private fun MapFragment.showSosGuide(situation: SosSituation) {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val surfaceBg  = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurface  = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val sheet = BottomSheetDialog(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceBg)
        setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (32*dp).toInt())
    }

    root.addView(TextView(ctx).apply {
        text = "${situation.emoji} ${situation.label}の対処手順"
        textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(onSurface)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (16*dp).toInt() }
    })

    situation.steps.forEach { step ->
        root.addView(TextView(ctx).apply {
            text = "${step.emoji}  ${step.text}"
            textSize = 15f; setTextColor(onSurface)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (10*dp).toInt() }
        })
    }

    val loc = lastKnownLocation
    if (loc != null) {
        root.addView(TextView(ctx).apply {
            text = "📍 現在地: ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}"
            textSize = 13f; setTextColor(onSurfaceVar)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (8*dp).toInt(); it.bottomMargin = (16*dp).toInt() }
        })
    } else {
        root.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, (16*dp).toInt())
        })
    }

    root.addView(TextView(ctx).apply {
        text = "緊急連絡先"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(onSurfaceVar)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (8*dp).toInt() }
    })

    situation.calls.forEach { (label, tel) ->
        val btnBg = GradientDrawable().apply { setColor(Color.parseColor("#1565C0")); cornerRadius = 8 * dp }
        val btn = Button(ctx).apply {
            text = "📞 $label（${tel.formatTel()}）"
            textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE); background = btnBg
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = (8*dp).toInt() }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                try { ctx.startActivity(intent) } catch (_: Exception) {
                    Toast.makeText(ctx, "電話アプリを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(btn)
    }

    val smsBg = GradientDrawable().apply { setColor(Color.parseColor("#CC0000")); cornerRadius = 8 * dp }
    root.addView(Button(ctx).apply {
        text = "📨 連絡先にSMSを送る"
        textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE); background = smsBg
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (4*dp).toInt() }
        setOnClickListener { sheet.dismiss(); sendSosSms(ctx, situation.label) }
    })

    val scrollView = ScrollView(ctx).apply { addView(root) }
    sheet.setContentView(scrollView)
    sheet.setOnShowListener {
        val bs = sheet.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        bs?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        bs?.requestLayout()
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.behavior.isDraggable = false
    }
    sheet.show()
}

private fun MapFragment.sendSosSms(
    ctx: android.content.Context,
    situationType: String = ""
) {
    val prefs = ctx.getSharedPreferences("sos_settings", android.content.Context.MODE_PRIVATE)
    val phone = prefs.getString("sos_phone", "") ?: ""
    if (phone.isBlank()) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("SOS連絡先が未設定")
            .setMessage("地図メニュー →「📞 SOS連絡先を設定」から連絡先を登録してください。")
            .setPositiveButton("OK", null)
            .show()
        return
    }
    val loc = lastKnownLocation
    val locStr = if (loc != null)
        "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
    else "位置情報なし"
    val typeStr = if (situationType.isNotBlank()) "【$situationType】" else ""
    val msg = "${typeStr}トラブルが発生しました。\n現在地: $locStr"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
        putExtra("sms_body", msg)
    }
    try { ctx.startActivity(intent) } catch (_: Exception) {
        Toast.makeText(ctx, "SMSアプリを開けませんでした", Toast.LENGTH_SHORT).show()
    }
}

internal fun MapFragment.showSosContactDialog() {
    val ctx = requireContext()
    val dp  = ctx.resources.displayMetrics.density
    val prefs = ctx.getSharedPreferences("sos_settings", android.content.Context.MODE_PRIVATE)
    val current = prefs.getString("sos_phone", "") ?: ""

    val input = EditText(ctx).apply {
        hint = "090-1234-5678"
        inputType = InputType.TYPE_CLASS_PHONE
        setText(current)
    }
    val container = android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding((24*dp).toInt(), (8*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
        addView(input)
    }

    MaterialAlertDialogBuilder(ctx)
        .setTitle("📞 SOS連絡先を設定")
        .setMessage("緊急時にSMSを送る電話番号を登録してください。\n（家族・会社・配送センターなど）")
        .setView(container)
        .setPositiveButton("保存") { _, _ ->
            val phone = input.text.toString().trim()
            prefs.edit().putString("sos_phone", phone).apply()
            Toast.makeText(ctx, "保存しました", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}

private fun String.formatTel(): String = when {
    this == "110" || this == "119" || this == "7119" || this == "9910" -> this
    this.startsWith("0570") && this.length >= 10 ->
        "${substring(0,4)}-${substring(4,6)}-${substring(6)}"
    this.startsWith("0120") && this.length >= 10 ->
        "${substring(0,4)}-${substring(4,7)}-${substring(7)}"
    else -> this
}
