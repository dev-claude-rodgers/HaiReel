package com.rodgers.routist.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geocoding_cache")
data class GeocodingCacheEntity(
    @PrimaryKey val address: String,
    val lat: Double,
    val lng: Double,
    val formattedAddress: String = "",  // APIが返した正式住所（エリア判定に使用）
    val cachedAt: Long = System.currentTimeMillis()
)
