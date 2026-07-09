package com.rodgers.haireel.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object SignatureStorage {
    const val TYPE_DRIVER = "driver"
    const val TYPE_CLIENT = "client"

    private fun fileName(type: String) = "sig_$type.png"

    fun fileFor(context: Context, type: String) = File(context.filesDir, fileName(type))

    fun exists(context: Context, type: String) = fileFor(context, type).exists()

    fun save(context: Context, type: String, bitmap: Bitmap): Boolean = try {
        FileOutputStream(fileFor(context, type)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        true
    } catch (_: java.io.IOException) { false }

    fun clear(context: Context, type: String) = fileFor(context, type).delete()
}
