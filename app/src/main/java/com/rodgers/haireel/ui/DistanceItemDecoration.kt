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
        const val ROAD_FACTOR = 1.3
    }

    private val density      = context.resources.displayMetrics.density
    private val rowHeight    = (24 * density).toInt()
    private val depRowHeight = (28 * density).toInt()

    private val interPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 14f * density
        color     = context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        textAlign = Paint.Align.CENTER
    }
    private val depPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 14f * density
        color     = context.themeColor(com.google.android.material.R.attr.colorPrimary)
        textAlign = Paint.Align.CENTER
    }

    var distances:  List<Double?> = emptyList()
        private set
    var depToFirst: Double? = null
        private set
    private var lastToArr:  Double? = null

    private var _depLat = 0.0; private var _depLng = 0.0; private var hasDep = false
    private var _arrLat = 0.0; private var _arrLng = 0.0; private var hasArr = false

    var isEnabled: Boolean = true

    var totalKm: Double = 0.0
        private set

    fun setDeparture(lat: Double, lng: Double) {
        _depLat = lat; _depLng = lng
        hasDep  = lat != 0.0 || lng != 0.0
    }

    // 帰着地。空（0,0）の場合は出発地と同じ扱い
    fun setArrival(lat: Double, lng: Double) {
        _arrLat = lat; _arrLng = lng
        hasArr  = lat != 0.0 || lng != 0.0
    }

    fun update(deliveries: List<Delivery>) {
        distances = (0 until deliveries.size - 1).map { i ->
            val a = deliveries[i]; val b = deliveries[i + 1]
            if (a.hasLocation && b.hasLocation) haversine(a.lat, a.lng, b.lat, b.lng) else null
        }

        if (deliveries.isNotEmpty()) {
            val first = deliveries.first()
            val last  = deliveries.last()
            // 出発地 → 1件目
            depToFirst = if (hasDep && first.hasLocation)
                haversine(_depLat, _depLng, first.lat, first.lng) else null
            // 最終地点 → 帰着地（帰着地未設定なら出発地へ）
            val (rLat, rLng) = if (hasArr) _arrLat to _arrLng else _depLat to _depLng
            lastToArr = if ((hasDep || hasArr) && last.hasLocation)
                haversine(last.lat, last.lng, rLat, rLng) else null
        } else {
            depToFirst = null; lastToArr = null
        }

        totalKm = distances.filterNotNull().sum() + (depToFirst ?: 0.0) + (lastToArr ?: 0.0)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (!isEnabled) return
        val pos   = parent.getChildAdapterPosition(view)
        val count = parent.adapter?.itemCount ?: 0
        if (pos < 0) return
        if (pos == 0 && hasDep)                   outRect.top    = depRowHeight
        if (pos < count - 1)                      outRect.bottom = rowHeight
        if (pos == count - 1 && (hasDep || hasArr)) outRect.bottom = depRowHeight
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!isEnabled) return
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

            // 最終地点 → 帰着地 or 出発地
            if (pos == count - 1 && (hasDep || hasArr)) {
                val icon = if (hasArr) "🏁" else "🏠"
                val text = if (lastToArr != null) "↓ ${"%.1f".format(lastToArr!!)}km → $icon" else "↓ → $icon"
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
