package com.rodgers.haireel.util

import com.rodgers.haireel.model.Delivery
import kotlin.math.*

object RouteOptimizer {

    data class OptimizeResult(
        val ordered: List<Delivery>,
        val skipped: List<Delivery>  // 既に閉店済みでスキップした配達先
    )

    /**
     * 営業時間制約付きルート最適化（最近傍法ベース）
     *
     * - nowMinutes < 0 の場合は時間制約なし（純粋な最近傍法）
     * - 既に閉店済み（closeTime < nowMinutes）の配達先は skipped に入る
     * - urgencyThresholdMinutes 以内に閉まる場所は距離より優先して先に訪問する
     *   （複数ある場合は閉店時間が早い順。同じなら近い順）
     */
    fun optimize(
        deliveries: List<Delivery>,
        startLat: Double,
        startLng: Double,
        nowMinutes: Int = -1,
        urgencyThresholdMinutes: Int = 60
    ): OptimizeResult {
        val geocoded   = deliveries.filter { it.hasLocation }.toMutableList()
        val ungeocoded = deliveries.filter { !it.hasLocation }

        if (geocoded.size <= 1) return OptimizeResult(deliveries, emptyList())

        // 閉店済みを除外
        val (closed, active) = if (nowMinutes >= 0) {
            geocoded.partition { d ->
                d.closeTime?.let { parseMinutes(it) }?.let { it < nowMinutes } == true
            }
        } else {
            Pair(emptyList(), geocoded)
        }

        val result    = mutableListOf<Delivery>()
        val remaining = active.toMutableList()
        var currentLat = startLat
        var currentLng = startLng

        while (remaining.isNotEmpty()) {
            val next = if (nowMinutes >= 0) {
                // 閉店まで urgencyThresholdMinutes 以内の「急ぎ」配達先
                val urgent = remaining.filter { d ->
                    val close = d.closeTime?.let { parseMinutes(it) } ?: Int.MAX_VALUE
                    (close - nowMinutes) in 0..urgencyThresholdMinutes
                }
                if (urgent.isNotEmpty()) {
                    // 閉店時間が早い順 → 同点なら距離が近い順
                    urgent.minWith(compareBy(
                        { d -> d.closeTime?.let { parseMinutes(it) } ?: Int.MAX_VALUE },
                        { d -> haversine(currentLat, currentLng, d.lat, d.lng) }
                    ))!!
                } else {
                    remaining.minByOrNull { haversine(currentLat, currentLng, it.lat, it.lng) }!!
                }
            } else {
                remaining.minByOrNull { haversine(currentLat, currentLng, it.lat, it.lng) }!!
            }

            result.add(next)
            remaining.remove(next)
            currentLat = next.lat
            currentLng = next.lng
        }

        return OptimizeResult(result + ungeocoded, closed)
    }

    // "HH:mm" を分単位の整数に変換
    fun parseMinutes(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        return h * 60 + m
    }

    // Haversine公式: 2点間の距離(km)
    internal fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
