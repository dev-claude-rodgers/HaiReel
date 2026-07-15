package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.util.themeColor
import kotlin.math.*

class DistanceItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    companion object {
        // 直線距離→実道路距離の補正係数（概算）
        const val ROAD_FACTOR = 1.3
    }

    private val density = context.resources.displayMetrics.density
    private val rowHeight  = (24 * density).toInt()
    private val depRowHeight = (28 * density).toInt()

    private val interPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize   = 12f * density
        color      = context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        textAlign  = Paint.Align.CENTER
    }
    private val depPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize   = 12f * density
        color      = context.themeColor(com.google.android.material.R.attr.colorPrimary)
        textAlign  = Paint.Align.CENTER
    }

    private var distances: List<Double?> = emptyList()
    private var depToFirst: Double? = null
    private var lastToDep: Double? = null
    private var _depLat = 0.0
    private var _depLng = 0.0
    private var hasDep = false

    // 合計距離（出発地往復含む）を外部から参照できる
    var totalKm: Double = 0.0
        private set

    fun setDeparture(lat: Double, lng: Double) {
        _depLat = lat
        _depLng = lng
        hasDep  = lat != 0.0 || lng != 0.0
    }

    fun update(deliveries: List<Delivery>) {
        distances = (0 until deliveries.size - 1).map { i ->
            val a = deliveries[i]; val b = deliveries[i + 1]
            if (a.hasLocation && b.hasLocation) haversine(a.lat, a.lng, b.lat, b.lng) else null
        }
        if (hasDep && deliveries.isNotEmpty()) {
            val first = deliveries.first()
            val last  = deliveries.last()
            depToFirst = if (first.hasLocation) haversine(_depLat, _depLng, first.lat, first.lng) else null
            lastToDep  = if (last.hasLocation)  haversine(last.lat, last.lng, _depLat, _depLng)  else null
        } else {
            depToFirst = null; lastToDep = null
        }
        totalKm = distances.filterNotNull().sum() + (depToFirst ?: 0.0) + (lastToDep ?: 0.0)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos   = parent.getChildAdapterPosition(view)
        val count = parent.adapter?.itemCount ?: 0
        if (pos < 0) return
        if (pos == 0 && hasDep)          outRect.top    = depRowHeight
        if (pos < count - 1)             outRect.bottom = rowHeight
        if (pos == count - 1 && hasDep)  outRect.bottom = depRowHeight
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.adapter?.itemCount ?: 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos   = parent.getChildAdapterPosition(child)
            if (pos < 0) continue
            val x = parent.width / 2f

            // 🏠 → 1件目
            if (pos == 0 && hasDep) {
                val text = if (depToFirst != null) "🏠 → ${"%.1f".format(depToFirst!!)}km ↓" else "🏠 ↓"
                val y = child.top.toFloat() - depRowHeight / 2f + depPaint.textSize / 3f
                c.drawText(text, x, y, depPaint)
            }

            // 地点間距離
            if (pos < count - 1) {
                val dist = distances.getOrNull(pos)
                val text = if (dist != null) "↓  ${"%.1f".format(dist)}km" else "↓"
                val y = child.bottom.toFloat() + rowHeight / 2f + interPaint.textSize / 3f
                c.drawText(text, x, y, interPaint)
            }

            // 最終地点 → 🏠
            if (pos == count - 1 && hasDep) {
                val text = if (lastToDep != null) "↓ ${"%.1f".format(lastToDep!!)}km → 🏠" else "↓ → 🏠"
                val y = child.bottom.toFloat() + depRowHeight / 2f + depPaint.textSize / 3f
                c.drawText(text, x, y, depPaint)
            }
        }
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * asin(sqrt(a)) * ROAD_FACTOR
    }
}
