package com.rodgers.haireel.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_records")
data class FuelRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,           // "2026-07-02"
    val liters: Float,          // 給油量（L）
    val pricePerLiter: Int,     // 単価（円/L）
    val totalCost: Int,         // 合計金額（円）
    val odometer: Int = 0,      // オドメーター（km）
    val note: String = "",
    val vehicleId: Long = 0     // 0 = 未設定
)
