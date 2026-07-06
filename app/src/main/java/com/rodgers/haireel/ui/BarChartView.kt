package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.rodgers.haireel.util.themeColor

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class DataSet(val label: String, val values: List<Float>, val color: Int)

    private var datasets: List<DataSet> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    }

    private val dp = context.resources.displayMetrics.density

    private val padLeft = 100f * dp / 3f
    private val padBottom = 44f * dp / 3f
    private val padTop = 16f * dp / 3f
    private val padRight = 8f * dp / 3f

    fun setData(datasets: List<DataSet>) {
        this.datasets = datasets
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (datasets.isEmpty() || width == 0 || height == 0) return

        val cLeft = padLeft
        val cRight = width - padRight
        val cTop = padTop
        val cBottom = height - padBottom
        val cW = cRight - cLeft
        val cH = cBottom - cTop

        val maxVal = datasets.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f

        // grid lines + Y labels
        val steps = 4
        labelPaint.textAlign = Paint.Align.RIGHT
        repeat(steps + 1) { i ->
            val frac = i.toFloat() / steps
            val y = cBottom - frac * cH
            canvas.drawLine(cLeft, y, cRight, y, gridPaint)
            val value = maxVal * frac
            canvas.drawText(formatAxis(value), cLeft - 6f, y + labelPaint.textSize / 3, labelPaint)
        }

        // bars
        val groupCount = 12
        val groupW = cW / groupCount
        val barCount = datasets.size.coerceAtLeast(1)
        val totalBarW = groupW * 0.75f
        val barW = (totalBarW / barCount).coerceAtLeast(4f)
        val groupPad = (groupW - barW * barCount) / 2

        for ((dsIdx, ds) in datasets.withIndex()) {
            barPaint.color = ds.color
            ds.values.take(groupCount).forEachIndexed { gIdx, value ->
                if (value <= 0f) return@forEachIndexed
                val gx = cLeft + gIdx * groupW
                val bx = gx + groupPad + dsIdx * barW
                val bh = (value / maxVal) * cH
                val rect = RectF(bx + 1f, cBottom - bh, bx + barW - 1f, cBottom)
                canvas.drawRoundRect(rect, 4f, 4f, barPaint)
            }
        }

        // X axis labels

        labelPaint.textAlign = Paint.Align.CENTER
        repeat(groupCount) { idx ->
            val x = cLeft + idx * groupW + groupW / 2
            canvas.drawText("${idx + 1}", x, cBottom + padBottom * 0.75f, labelPaint)
        }

        // X axis line
        canvas.drawLine(cLeft, cBottom, cRight, cBottom, gridPaint)
    }

    private fun formatAxis(value: Float): String = when {
        value >= 10000f -> "%.0f万".format(value / 10000f)
        value > 0f -> "%,.0f".format(value)
        else -> "0"
    }
}
