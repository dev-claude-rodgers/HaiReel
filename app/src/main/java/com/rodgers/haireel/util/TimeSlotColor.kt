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
        val hour = when {
            text.contains("午前") -> 8
            else ->
                Regex("""(\d{1,2}):\d{2}""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""^(\d{1,2})""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return null
        }
        return when {
            hour < 12 -> Color.parseColor("#1565C0")
            hour < 14 -> Color.parseColor("#00796B")
            hour < 16 -> Color.parseColor("#E65100")
            hour < 18 -> Color.parseColor("#6A1B9A")
            hour < 20 -> Color.parseColor("#B71C1C")
            else      -> Color.parseColor("#1A237E")
        }
    }
}
