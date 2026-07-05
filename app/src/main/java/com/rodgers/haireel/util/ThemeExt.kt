package com.rodgers.haireel.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.rodgers.haireel.R

fun Context.themeColor(@AttrRes attr: Int): Int =
    TypedValue().also { theme.resolveAttribute(attr, it, true) }.data

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun android.app.Activity.applyAppTheme() {
    when (AppSettings.getThemeKey(this)) {
        "teal"   -> setTheme(R.style.Theme_HaiReel_Teal)
        "green"  -> setTheme(R.style.Theme_HaiReel_Green)
        "orange" -> setTheme(R.style.Theme_HaiReel_Orange)
        "purple" -> setTheme(R.style.Theme_HaiReel_Purple)
        "red"    -> setTheme(R.style.Theme_HaiReel_Red)
        "indigo" -> setTheme(R.style.Theme_HaiReel_Indigo)
        "brown"  -> setTheme(R.style.Theme_HaiReel_Brown)
        // "blue" = デフォルト、上書き不要
    }
}
