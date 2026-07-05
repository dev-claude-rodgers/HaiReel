package com.rodgers.haireel.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.themeColor

fun buildBackgroundSummary(ctx: Context): String {
    val items = mutableListOf<String>()
    if (AppSettings.getReminderBeforeEnabled(ctx)) items.add("乗務前リマインダー ON")
    if (AppSettings.getReminderAfterEnabled(ctx))  items.add("乗務後リマインダー ON")
    return if (items.isEmpty()) "すべてOFF" else items.joinToString("・")
}

fun showBackgroundSettingsSheet(ctx: Context, onDismiss: () -> Unit) {
    val dp           = ctx.resources.displayMetrics.density
    val surfaceBg    = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val outlineVar   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
    val MATCH        = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP         = LinearLayout.LayoutParams.WRAP_CONTENT

    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
    val root  = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceBg)
        setPadding(0, (8 * dp).toInt(), 0, (32 * dp).toInt())
    }

    root.addView(TextView(ctx).apply {
        text = "バックグラウンド処理"
        textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(onSurface)
        setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
    })
    root.addView(android.view.View(ctx).apply {
        setBackgroundColor(outlineVar)
        layoutParams = LinearLayout.LayoutParams(MATCH, (1 * dp).toInt())
    })

    fun toggleRow(emoji: String, title: String, isOn: Boolean,
                  onToggle: (Boolean) -> Unit) {
        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(ripple)
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        row.addView(TextView(ctx).apply {
            text = emoji; textSize = 26f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), WRAP)
        })
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                .also { it.marginStart = (14 * dp).toInt() }
        }
        val titleV = TextView(ctx).apply {
            this.text = title; textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
        }
        val stateV = TextView(ctx).apply {
            textSize = 13f; setTextColor(onSurfaceVar)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (2 * dp).toInt() }
            text = if (isOn) "ON" else "OFF"
        }
        col.addView(titleV); col.addView(stateV)
        row.addView(col)

        val indicator = TextView(ctx).apply {
            text = if (isOn) "✅" else "⬜"; textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), WRAP)
            tag = if (isOn) "on" else "off"
        }
        row.addView(indicator)
        row.setOnClickListener {
            val nowOn = indicator.tag == "on"
            onToggle(!nowOn)
            indicator.text = if (!nowOn) "✅" else "⬜"
            indicator.tag  = if (!nowOn) "on"  else "off"
            stateV.text    = if (!nowOn) "ON"  else "OFF"
        }
        root.addView(row)
    }

    fun divider() = root.addView(android.view.View(ctx).apply {
        setBackgroundColor(outlineVar)
        layoutParams = LinearLayout.LayoutParams(MATCH, (1 * dp).toInt())
            .also { it.setMargins((84 * dp).toInt(), 0, 0, 0) }
    })

    toggleRow("🔔", "乗務前リマインダー", AppSettings.getReminderBeforeEnabled(ctx)) { on ->
        AppSettings.setReminderBeforeEnabled(ctx, on)
    }
    divider()
    toggleRow("🔔", "乗務後リマインダー", AppSettings.getReminderAfterEnabled(ctx)) { on ->
        AppSettings.setReminderAfterEnabled(ctx, on)
    }

    sheet.setContentView(android.widget.ScrollView(ctx).apply { addView(root) })
    sheet.setOnDismissListener { onDismiss() }
    sheet.show()
}
