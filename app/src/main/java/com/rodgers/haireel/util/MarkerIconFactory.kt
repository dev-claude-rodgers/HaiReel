package com.rodgers.haireel.util

import android.graphics.*
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object MarkerIconFactory {

    private const val SIZE = 80
    private const val TEXT_SIZE = 26f

    fun createWithColor(number: Int, color: Int, completed: Boolean): BitmapDescriptor =
        drawMarker(number, color, completed)

    private fun drawMarker(number: Int, color: Int, completed: Boolean): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (completed) Color.parseColor("#9E9E9E") else color
        }
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f - 4, circlePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = TEXT_SIZE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textY = SIZE / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(number.toString(), SIZE / 2f, textY, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
