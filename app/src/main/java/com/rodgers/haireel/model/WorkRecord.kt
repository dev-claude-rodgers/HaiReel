package com.rodgers.haireel.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_records")
data class WorkRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,               // "2026-06-10"
    val startTime: String = "",     // "09:00"
    val endTime: String = "",       // "18:00"
    val endDateOffset: Int = 0,     // 0=当日 / 1=翌日 / 2=+2日
    val deliveryCount: Int = 0,
    val packageCount: Int = 0,
    val distanceKm: Float = 0f,
    val startMeter: Int = 0,
    val endMeter: Int = 0,
    val area: String = "",
    val alcCheck: String = "",      // "" / "○" / "×"
    val remarks: String = "",
    val income: Int = 0,
    val fuelCost: Int = 0,
    val assignmentId: String = ""
) {
    val workingMinutes: Int get() = try {
        val (sh, sm) = startTime.split(":").map { it.toInt() }
        val (eh, em) = endTime.split(":").map { it.toInt() }
        val start = sh * 60 + sm
        val end   = eh * 60 + em + endDateOffset * 24 * 60
        (end - start).coerceAtLeast(0)
    } catch (_: Exception) { 0 }

    val workingHoursText: String get() {
        val m = workingMinutes
        return if (m <= 0) "" else "%d時間%02d分".format(m / 60, m % 60)
    }
}
