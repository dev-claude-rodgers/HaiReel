package com.rodgers.haireel.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.BuildConfig
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.GeocodingClient
import com.rodgers.haireel.util.themeColor

fun showApiKeyWizardDialog(
    ctx: Context,
    onLaunchIntent: (Intent) -> Unit,
    onTestApiKey: () -> Unit,
    onStatusChanged: (String) -> Unit
) {
    val dp           = ctx.resources.displayMetrics.density
    val MATCH        = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP         = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    val primary      = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
    val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

    val root = android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding((24*dp).toInt(), (16*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
    }

    root.addView(android.widget.TextView(ctx).apply {
        text = "住所を地図座標に変換するには「Google APIキー」が必要です。\nGoogleアカウントがあれば無料で取得でき、個人利用の範囲では料金はかかりません。"
        textSize = 14f; setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })
    root.addView(android.widget.TextView(ctx).apply {
        text = "※ APIキーを設定しなくても、住所管理・日報・点呼は使えます。後からでも変更可能です。"
        textSize = 12f; setTextColor(onSurfaceVar)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (16*dp).toInt() }
    })

    fun openUrl(url: String) {
        try {
            onLaunchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(ctx, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    fun addLinkButton(label: String, url: String, bottomMargin: Int = 8) {
        root.addView(android.widget.Button(ctx).apply {
            text = label
            isAllCaps = false; textSize = 13f
            setTextColor(primary)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke((1*dp).toInt(), primary)
                cornerRadius = 8*dp
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (bottomMargin*dp).toInt() }
            setOnClickListener { openUrl(url) }
        })
    }

    // ── STEP 1 ──────────────────────────────
    root.addView(android.widget.TextView(ctx).apply {
        text = "① Google Cloud プロジェクトを作成する"
        textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (4*dp).toInt() }
    })
    root.addView(android.widget.TextView(ctx).apply {
        text = "Googleアカウントでログイン後、利用規約・プライバシーポリシーへの同意を求められたら「同意する」を選んでください。その後「新しいプロジェクト」を作成してください。（すでにプロジェクトがある場合はスキップ）"
        textSize = 12f; setTextColor(onSurfaceVar)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })
    addLinkButton("プロジェクトを作成する →",
        "https://console.cloud.google.com/projectcreate", 20)

    // ── STEP 2 ──────────────────────────────
    root.addView(android.widget.TextView(ctx).apply {
        text = "② 課金設定をする（必須）"
        textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (4*dp).toInt() }
    })
    root.addView(android.widget.TextView(ctx).apply {
        text = "Google Maps APIを利用するには課金アカウントの登録が必要です。クレジットカードの登録が求められますが、毎月200ドル分の無料枠があるため、個人利用であれば通常は無料で使えます。"
        textSize = 12f; setTextColor(onSurfaceVar)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })
    addLinkButton("課金アカウントを設定する →",
        "https://console.cloud.google.com/billing/create", 20)

    // ── STEP 3 ──────────────────────────────
    root.addView(android.widget.TextView(ctx).apply {
        text = "③ 下の3つのAPIをそれぞれ「有効にする」"
        textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (4*dp).toInt() }
    })
    root.addView(android.widget.TextView(ctx).apply {
        text = "タップするとGoogle Cloudが開きます。「有効にする」ボタンを押してください。"
        textSize = 12f; setTextColor(onSurfaceVar)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })
    addLinkButton("Maps SDK for Android を有効にする →",
        "https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com")
    addLinkButton("Geocoding API を有効にする →",
        "https://console.cloud.google.com/apis/library/geocoding-backend.googleapis.com")
    addLinkButton("Places API を有効にする →",
        "https://console.cloud.google.com/apis/library/places-backend.googleapis.com", 20)

    // ── STEP 4 ──────────────────────────────
    root.addView(android.widget.TextView(ctx).apply {
        text = "④ APIキーを作成してコピーする"
        textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (4*dp).toInt() }
    })
    root.addView(android.widget.TextView(ctx).apply {
        text = "1. 「＋認証情報を作成」→「APIキー」をタップ\n" +
               "2. プライバシーポリシーへの同意を求められたら「同意する」\n" +
               "3. API制限の画面で、③で有効にした3つのAPIにそれぞれチェックを入れる\n" +
               "4. 「鍵を表示します」をタップ\n" +
               "5. 表示された「AIza...」の文字列をコピー"
        textSize = 12f; setTextColor(onSurfaceVar)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })
    root.addView(android.widget.Button(ctx).apply {
        text = "認証情報ページを開く →"
        isAllCaps = false; textSize = 14f
        setTextColor(android.graphics.Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(primary); cornerRadius = 8*dp
        }
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (20*dp).toInt() }
        setOnClickListener { openUrl("https://console.cloud.google.com/apis/credentials") }
    })

    // ── STEP 5 ──────────────────────────────
    root.addView(android.widget.TextView(ctx).apply {
        text = "⑤ コピーしたAPIキーを下に貼り付けて保存"
        textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })

    val inputField = android.widget.EditText(ctx).apply {
        hint = "④ のAPIキーをここに貼り付け（例: AIza...）"
        setText(AppSettings.getUserApiKey(ctx))
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(inputField)

    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
    if (clipText.trim().startsWith("AIza") && inputField.text.isBlank()) {
        root.addView(com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            text = "📋 クリップボードから貼り付け"
            isAllCaps = false
            setTextColor(primary)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            setOnClickListener {
                inputField.setText(clipText.trim())
                Toast.makeText(ctx, "APIキーを貼り付けました", Toast.LENGTH_SHORT).show()
            }
        })
    }

    val dlg = MaterialAlertDialogBuilder(ctx)
        .setTitle("🔑 Google APIキー設定")
        .setView(android.widget.ScrollView(ctx).apply { addView(root) })
        .setPositiveButton("✅ 保存する", null)
        .setNegativeButton("キャンセル", null)
        .create()
    dlg.show()
    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val key = inputField.text.toString().trim()
        AppSettings.setUserApiKey(ctx, key)
        GeocodingClient.configure(key.ifBlank { BuildConfig.GEOCODING_API_KEY })
        onStatusChanged(
            if (key.isNotBlank()) "設定済み（自分のAPIキーを使用中）"
            else "未設定（住所変換・地図機能が使えません）"
        )
        dlg.dismiss()
        if (key.isNotBlank()) onTestApiKey()
        else Toast.makeText(ctx, "APIキーを削除しました", Toast.LENGTH_SHORT).show()
    }
}
