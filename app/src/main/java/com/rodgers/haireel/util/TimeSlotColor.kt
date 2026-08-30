package com.rodgers.haireel.util

import android.graphics.Color

object TimeSlotColor {

    val PALETTE = listOf(
        "#1565C0", "#00796B", "#E65100", "#6A1B9A",
        "#B71C1C", "#1A237E", "#2E7D32", "#880E4F",
        "#F9A825", "#00838F", "#546E7A", "#6D4C41"
    )

    fun colorFor(
        timeSlot: String?,
        templates: List<AppSettings.TimeSlotTemplate> = emptyList()
    ): Int? {
        timeSlot ?: return null
        val text = timeSlot.trim()
        if (text.isBlank()) return null

        // テンプレートの名前と完全一致する場合はその色を返す
        templates.find { it.name == text }?.let { tmpl ->
            return try { Color.parseColor(tmpl.colorHex) } catch (_: Exception) { null }
        }

        // 一致しない（手入力など）場合は時刻から推測
        val hour = extractHour(text) ?: return null
        return when {
            hour < 12 -> Color.parseColor("#1565C0")
            hour < 14 -> Color.parseColor("#00796B")
            hour < 16 -> Color.parseColor("#E65100")
            hour < 18 -> Color.parseColor("#6A1B9A")
            hour < 20 -> Color.parseColor("#B71C1C")
            else      -> Color.parseColor("#1A237E")
        }
    }

    // 時間帯文字列（"14:00-16:00" や "午前中" 等）から先頭時刻(hour)を推測する
    fun extractHour(timeSlot: String?): Int? {
        val text = timeSlot?.trim().orEmpty()
        if (text.isBlank()) return null
        if (text.contains("午前")) return 8
        return Regex("""(\d{1,2}):\d{2}""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""^(\d{1,2})""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    // 並べ替え用の分単位ソートキー。openTime（HH:mm）を優先し、なければtimeSlotから推定する
    fun sortMinutesFor(openTime: String?, timeSlot: String?): Int? {
        openTime?.let {
            val m = parseTimeMinutes(it)
            if (m >= 0) return m
        }
        return extractHour(timeSlot)?.let { it * 60 }
    }

    // "HH:mm" 形式の文字列を分に変換する（不正な形式は -1）
    private fun parseTimeMinutes(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].trim().toIntOrNull() ?: return -1
        val m = parts[1].trim().toIntOrNull() ?: return -1
        if (h < 0 || h > 23 || m < 0 || m > 59) return -1
        return h * 60 + m
    }
}
