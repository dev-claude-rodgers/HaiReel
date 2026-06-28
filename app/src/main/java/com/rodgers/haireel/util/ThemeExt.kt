package com.rodgers.haireel.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

fun Context.themeColor(@AttrRes attr: Int): Int =
    TypedValue().also { theme.resolveAttribute(attr, it, true) }.data
