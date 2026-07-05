package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.themeColor

fun showThemePickerDialog(ctx: Context, onThemeChanged: () -> Unit) {
    val dp         = ctx.resources.displayMetrics.density
    val currentKey = AppSettings.getThemeKey(ctx)

    data class ThemeOption(val key: String, val nameJa: String, val colorHex: String)
    val themes = listOf(
        ThemeOption("blue",   "ブルー",    "#1565C0"),
        ThemeOption("teal",   "ティール",  "#006A6A"),
        ThemeOption("green",  "グリーン",  "#2E7D32"),
        ThemeOption("orange", "オレンジ",  "#C84B00"),
        ThemeOption("purple", "パープル",  "#6750A4"),
        ThemeOption("red",    "レッド",    "#BA1A1A"),
        ThemeOption("indigo", "インディゴ","#3949AB"),
        ThemeOption("brown",  "アース",    "#795548"),
    )

    val onSurface = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
    }

    for (row in 0..1) {
        val rowLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (12 * dp).toInt() }
        }
        for (col in 0..3) {
            val t         = themes[row * 4 + col]
            val color     = Color.parseColor(t.colorHex)
            val isSelected = t.key == currentKey

            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                isClickable = true; isFocusable = true
                val ripple = android.util.TypedValue().also {
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
                }.resourceId
                setBackgroundResource(ripple)
            }

            cell.addView(TextView(ctx).apply {
                text = if (isSelected) "✓" else ""
                textSize = 22f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    if (isSelected) setStroke((3 * dp).toInt(), Color.WHITE)
                }
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), (52 * dp).toInt())
                    .also { it.gravity = Gravity.CENTER }
            })

            cell.addView(TextView(ctx).apply {
                text = t.nameJa; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(if (isSelected) color else onSurface)
                if (isSelected) setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (4 * dp).toInt() }
            })

            cell.setOnClickListener {
                AppSettings.setThemeKey(ctx, t.key)
                onThemeChanged()
            }
            rowLayout.addView(cell)
        }
        root.addView(rowLayout)
    }

    MaterialAlertDialogBuilder(ctx)
        .setTitle("テーマカラーを選択")
        .setView(root)
        .setNegativeButton("キャンセル", null)
        .show()
}
