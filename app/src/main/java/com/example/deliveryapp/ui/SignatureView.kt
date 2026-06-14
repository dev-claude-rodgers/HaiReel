package com.rodgers.routist.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignatureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val path  = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.BLACK
        style       = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin  = Paint.Join.ROUND
        strokeCap   = Paint.Cap.ROUND
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.LTGRAY
        style       = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var lastX = 0f
    private var lastY = 0f
    private var hasContent = false

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(1f, 1f, width - 1f, height - 1f, borderPaint)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                lastX      = event.x
                lastY      = event.y
                hasContent = true
            }
            MotionEvent.ACTION_MOVE -> {
                val mx = (event.x + lastX) / 2
                val my = (event.y + lastY) / 2
                path.quadTo(lastX, lastY, mx, my)
                lastX = event.x
                lastY = event.y
                invalidate()
            }
        }
        return true
    }

    fun clear() {
        path.reset()
        hasContent = false
        invalidate()
    }

    fun isEmpty() = !hasContent

    fun getBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        draw(Canvas(bmp))
        return bmp
    }
}
