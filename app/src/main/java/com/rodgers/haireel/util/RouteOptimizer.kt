package com.rodgers.haireel.util

import com.rodgers.haireel.model.Delivery
import kotlin.math.*

object RouteOptimizer {

    // 最近傍法による貪欲なルート最適化
    fun optimize(deliveries: List<Delivery>, startLat: Double, startLng: Double): List<Delivery> {
        val geocoded = deliveries.filter { it.hasLocation }.toMutableList()
        val ungeocoded = deliveries.filter { !it.hasLocation }
        if (geocoded.size <= 1) return deliveries

        val result = mutableListOf<Delivery>()
        var currentLat = startLat
        var currentLng = startLng

        while (geocoded.isNotEmpty()) {
            val nearest = geocoded.minByOrNull { haversine(currentLat, currentLng, it.lat, it.lng) } ?: break
            result.add(nearest)
            geocoded.remove(nearest)
            currentLat = nearest.lat
            currentLng = nearest.lng
        }

        return result + ungeocoded
    }

    // Haversine公式: 2点間の距離(km)
    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
