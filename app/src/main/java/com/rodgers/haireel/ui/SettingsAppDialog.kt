package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.util.AppSettings

fun showAppSettingsDialog(ctx: Context) {
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val scroll = ScrollView(ctx)
    val root   = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
    }
    scroll.addView(root)

    val colorOnSurface        = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    val colorOnSurfaceVariant = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
    val colorOutline          = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline, Color.GRAY)

    fun section(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 14f; setTextColor(colorOnSurface)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (18 * dp).toInt(); it.bottomMargin = (6 * dp).toInt() }
    }
    fun field(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 13f; setTextColor(colorOnSurfaceVariant)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    }

    // ── 表示設定
    root.addView(section("── 表示設定"))
    val darkGroup    = RadioGroup(ctx)
    val rbDarkSystem = RadioButton(ctx).apply { text = "システム設定に従う"; id = View.generateViewId() }
    val rbDarkLight  = RadioButton(ctx).apply { text = "ライトモード"; id = View.generateViewId() }
    val rbDarkDark   = RadioButton(ctx).apply { text = "ダークモード"; id = View.generateViewId() }
    darkGroup.addView(rbDarkSystem); darkGroup.addView(rbDarkLight); darkGroup.addView(rbDarkDark)
    when (AppSettings.getDarkMode(ctx)) {
        AppCompatDelegate.MODE_NIGHT_NO  -> rbDarkLight.isChecked = true
        AppCompatDelegate.MODE_NIGHT_YES -> rbDarkDark.isChecked = true
        else -> rbDarkSystem.isChecked = true
    }
    root.addView(darkGroup)

    // ── 事業者情報
    root.addView(section("── 事業者情報"))
    root.addView(field("事業者名"))
    val etCompany = EditText(ctx).apply {
        hint = "例: 〇〇運送株式会社"; inputType = InputType.TYPE_CLASS_TEXT
        setText(AppSettings.getCompanyName(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etCompany)
    val vehicles = AppSettings.getVehicles(ctx)
    root.addView(field("車番１"))
    val etVehicle1 = EditText(ctx).apply {
        hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
        setText(vehicles[0]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etVehicle1)
    root.addView(field("車番２"))
    val etVehicle2 = EditText(ctx).apply {
        hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
        setText(vehicles[1]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etVehicle2)
    root.addView(field("車番３"))
    val etVehicle3 = EditText(ctx).apply {
        hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
        setText(vehicles[2]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etVehicle3)

    // ── セキュリティ設定
    root.addView(section("── セキュリティ設定"))
    val swAppLock = run {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (4 * dp).toInt() }
        }
        row.addView(TextView(ctx).apply {
            text = "アプリロック（指紋・顔・PIN）"; textSize = 15f
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        val sw = androidx.appcompat.widget.SwitchCompat(ctx).apply {
            isChecked = AppSettings.isAppLockEnabled(ctx)
        }
        row.addView(sw); root.addView(row); sw
    }
    root.addView(field("ロックまでの時間（分）"))
    val etLockTimeout = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; hint = "30"
        setText(AppSettings.getLockTimeoutMinutes(ctx).toString())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etLockTimeout)
    root.addView(TextView(ctx).apply {
        text = "バックグラウンドや操作なしでこの時間が経過するとロックされます"
        textSize = 12f; setTextColor(colorOutline)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    })
    root.addView(field("バックアップパスワード（空欄で暗号化なし）"))
    val etBackupPw = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = "パスワードを設定"
        setText(AppSettings.getBackupPassword(ctx))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    root.addView(etBackupPw)

    // ── 操作設定
    root.addView(section("── 操作設定"))
    root.addView(field("削除後の取り消し可能時間（秒）"))
    val undoRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val npUndo = NumberPicker(ctx).apply {
        minValue = 3; maxValue = 30
        value = AppSettings.getUndoSeconds(ctx).coerceIn(3, 30)
    }
    undoRow.addView(npUndo)
    undoRow.addView(TextView(ctx).apply {
        text = " 秒（配達先・点呼・日報など削除全般に適用）"
        textSize = 12f; setTextColor(colorOnSurfaceVariant)
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
    })
    root.addView(undoRow)
    root.addView(field("ルート最適化: 閉店優先しきい値（分）"))
    val urgencyRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val npUrgency = NumberPicker(ctx).apply {
        minValue = 15; maxValue = 120
        value = AppSettings.getUrgencyThresholdMinutes(ctx).coerceIn(15, 120)
    }
    urgencyRow.addView(npUrgency)
    urgencyRow.addView(TextView(ctx).apply {
        text = " 分以内に閉まる場所を距離より優先"
        textSize = 12f; setTextColor(colorOnSurfaceVariant)
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
    })
    root.addView(urgencyRow)

    MaterialAlertDialogBuilder(ctx)
        .setTitle("アプリ設定")
        .setView(scroll)
        .setPositiveButton("保存") { _, _ ->
            AppSettings.setCompanyName(ctx, etCompany.text.toString().trim())
            AppSettings.setVehicles(ctx, listOf(
                etVehicle1.text.toString().trim(),
                etVehicle2.text.toString().trim(),
                etVehicle3.text.toString().trim()
            ))
            val darkMode = when {
                rbDarkDark.isChecked  -> AppCompatDelegate.MODE_NIGHT_YES
                rbDarkLight.isChecked -> AppCompatDelegate.MODE_NIGHT_NO
                else                  -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppSettings.setDarkMode(ctx, darkMode)
            AppCompatDelegate.setDefaultNightMode(darkMode)
            AppSettings.setAppLockEnabled(ctx, swAppLock.isChecked)
            AppSettings.setLockTimeoutMinutes(ctx, etLockTimeout.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 30)
            AppSettings.setBackupPassword(ctx, etBackupPw.text.toString())
            AppSettings.setUndoSeconds(ctx, npUndo.value)
            AppSettings.setUrgencyThresholdMinutes(ctx, npUrgency.value)
            Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("キャンセル", null)
        .show()
}
