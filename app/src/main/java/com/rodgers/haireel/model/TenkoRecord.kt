package com.rodgers.haireel.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tenko_records")
data class TenkoRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val assignmentId: String = "",

    // 乗務前
    val beforeMethod: String? = null,
    val beforeTime: String? = null,
    val beforeHealth: Boolean? = null,
    val beforeFatigue: Boolean? = null,
    val beforeAlcohol: Double? = null,
    val beforeInspection: Boolean? = null,
    val beforeInstruction: String? = null,
    val beforeChecker: String? = null,

    // 乗務後
    val afterMethod: String? = null,
    val afterTime: String? = null,
    val afterHealth: Boolean? = null,
    val afterFatigue: Boolean? = null,
    val afterAlcohol: Double? = null,
    val afterAccident: Boolean? = null,
    val afterVehicle: Boolean? = null,
    val afterInstruction: String? = null,
    val afterChecker: String? = null,

    val note: String? = null,
    val vehicleNumber: String? = null   // 乗務前点呼で選択した車番
) {
    val beforeDone: Boolean get() = beforeTime != null
    val afterDone: Boolean get() = afterTime != null
}
