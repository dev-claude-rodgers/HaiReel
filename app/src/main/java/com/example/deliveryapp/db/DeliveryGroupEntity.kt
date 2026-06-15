package com.rodgers.routist.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rodgers.routist.model.DeliveryGroup

@Entity(tableName = "delivery_groups")
data class DeliveryGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#F44336",
    @ColumnInfo(name = "pattern_id") val patternId: Int = -1,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)

fun DeliveryGroupEntity.toGroup(): DeliveryGroup =
    DeliveryGroup(id = id, name = name, colorHex = colorHex, patternId = patternId)

fun DeliveryGroup.toEntity(sortOrder: Int): DeliveryGroupEntity =
    DeliveryGroupEntity(id = id, name = name, colorHex = colorHex, patternId = patternId, sortOrder = sortOrder)
