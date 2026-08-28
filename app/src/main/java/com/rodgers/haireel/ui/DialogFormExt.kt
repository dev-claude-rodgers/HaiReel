package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ダイアログ内のフォーム部品（セクションラベル・テキスト入力欄）を共通化した拡張関数群。
 * DailyReportPatternDialogs / TenkoSettingsDialogs / DailyReportEditDialog で共有する。
 */

fun Context.formLabel(text: String, dp: Float, labelColor: Int): TextView = TextView(this).apply {
    this.text = text; textSize = 13f; setTextColor(labelColor)
    typeface = Typeface.DEFAULT_BOLD
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
}

fun Context.formField(value: String, dp: Float, hint: String = ""): EditText = EditText(this).apply {
    setText(value); this.hint = hint
    inputType = InputType.TYPE_CLASS_TEXT
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
}
