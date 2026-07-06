package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * BottomSheet メニューの row・divider・header を共通化した拡張関数群。
 * DailyReportFragment / TenkoFragment / DeliveryListFragment で共有する。
 */

fun Context.showErrorDialog(title: String, message: String) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("閉じる", null)
        .show()
}

fun LinearLayout.addMenuRow(
    emoji: String,
    title: String,
    sub: String,
    dp: Float,
    titleColor: Int,
    subColor: Int,
    ripple: Int,
    onDismiss: () -> Unit,
    action: () -> Unit
) {
    val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(ripple)
        setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
    }
    row.addView(TextView(context).apply {
        text = emoji; textSize = 28f; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), WRAP)
    })
    val col = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            .also { it.marginStart = (14 * dp).toInt() }
    }
    col.addView(TextView(context).apply {
        text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(titleColor)
    })
    if (sub.isNotBlank()) col.addView(TextView(context).apply {
        text = sub; textSize = 14f; setTextColor(subColor); maxLines = 2
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP)
            .also { it.topMargin = (2 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
    })
    row.addView(col)
    row.setOnClickListener { onDismiss(); action() }
    addView(row)
}

fun LinearLayout.addMenuDivider(dp: Float, outlineColor: Int) {
    addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
            .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
        setBackgroundColor(outlineColor)
    })
}

fun LinearLayout.addMenuHeader(
    title: String,
    dp: Float,
    titleColor: Int,
    subColor: Int,
    outlineColor: Int,
    onDismiss: () -> Unit
) {
    val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP)
    }
    headerRow.addView(TextView(context).apply {
        text = title; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(titleColor)
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    headerRow.addView(TextView(context).apply {
        text = "✕"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(subColor)
        background = android.util.TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId.let { ContextCompat.getDrawable(context, it) }
        layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
        setOnClickListener { onDismiss() }
    })
    addView(headerRow)
    addView(View(context).apply {
        setBackgroundColor(outlineColor)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
    })
}
