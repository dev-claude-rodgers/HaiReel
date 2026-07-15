package com.rodgers.haireel.util

object EtaCalculator {

    fun parseMinutes(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].trim().toIntOrNull() ?: return -1
        val m = parts[1].trim().toIntOrNull() ?: return -1
        if (h < 0 || h > 23 || m < 0 || m > 59) return -1
        return h * 60 + m
    }

    fun formatMinutes(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "%d:%02d".format(h, m)
    }

    // dwellOverrides: 配達先ごとの個別滞在時間（null の場合は defaultDwellMinutes を使用）
    fun compute(
        count: Int,
        depToFirst: Double?,
        interDistances: List<Double?>,
        depMinutes: Int,
        defaultDwellMinutes: Int,
        avgSpeedKmh: Int,
        dwellOverrides: List<Int?> = emptyList()
    ): List<Int?> {
        if (count == 0 || depMinutes < 0 || avgSpeedKmh <= 0) return List(count) { null }
        val result = mutableListOf<Int?>()
        var current = depMinutes.toDouble()

        current += depToFirst?.let { it / avgSpeedKmh * 60.0 } ?: 0.0
        result.add(current.toInt())

        for (i in 1 until count) {
            val dwell = dwellOverrides.getOrNull(i - 1) ?: defaultDwellMinutes
            current += dwell
            current += interDistances.getOrNull(i - 1)?.let { it / avgSpeedKmh * 60.0 } ?: 0.0
            result.add(current.toInt())
        }
        return result
    }
}
